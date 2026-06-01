package org.akuunda.akuundawallet.wallet.service.task;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.service.TransactionNotificationHelper;
import org.akuunda.akuundawallet.wallet.api.dao.ConditionalPaymentRepository;
import org.akuunda.akuundawallet.wallet.api.dao.OneTimePaymentLinkRepository;
import org.akuunda.akuundawallet.wallet.api.dao.OperationRepository;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.api.entities.ConditionalPayment;
import org.akuunda.akuundawallet.wallet.api.entities.OneTimePaymentLink;
import org.akuunda.akuundawallet.wallet.api.entities.Operation;
import org.akuunda.akuundawallet.wallet.api.entities.QRCode;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.wallet.service.PaymentEscrowNotificationService;
import org.akuunda.akuundawallet.wallet.service.PaymentFactoryContractService;
import org.akuunda.akuundawallet.wallet.service.QRCodeService;
import org.akuunda.akuundawallet.wallet.service.SmartContractEscrowService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Tâche planifiée qui vérifie périodiquement si les USDC sont arrivés sur les wallets CREATE2,
 * puis appelle executePayment() pour les transférer au wallet du marchand.
 * Pour les liens de type CONDITIONAL, les fonds sont déposés dans le smart contract escrow.
 *
 * <p>Design transactionnel :
 * <ul>
 *   <li>Étape A (sans transaction) : appels blockchain irreversibles (executePayment, depositToEscrow)</li>
 *   <li>Étape B (transaction propre via TransactionTemplate) : persistance BDD</li>
 * </ul>
 * Si l'étape B échoue après que l'étape A a réussi, le lien est mis à l'état
 * {@code ESCROW_FUNDED_PENDING_DB} afin que le polling suivant puisse relancer uniquement la
 * persistance BDD, sans rejouer les transactions blockchain.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentDepositPollingTask {

    private final OneTimePaymentLinkRepository oneTimePaymentLinkRepository;
    private final PaymentFactoryContractService paymentFactoryContractService;
    private final WalletRepository walletRepository;
    private final SmartContractEscrowService smartContractEscrowService;
    private final ConditionalPaymentRepository conditionalPaymentRepository;
    private final QRCodeService qrCodeService;
    private final OperationRepository operationRepository;
    private final PlatformTransactionManager transactionManager;
    private final PaymentEscrowNotificationService escrowNotificationService;
    private final TransactionNotificationHelper notificationHelper;

    @Value("${akuunda.intermediate.wallet.id:}")
    private String adminWalletId;

    @Value("${akuunda.intermediate.wallet.address:}")
    private String adminWalletAddress;

    @Value("${akuunda.escrow.contract.wallet.address:}")
    private String smartContractWalletAddress;

    @Value("${akuunda.escrow.service.pin:PIN:000000}")
    private String servicePin;

    @Value("${akuunda.payment.deposit.polling.interval-ms:30000}")
    private long pollingInterval;

    @PostConstruct
    public void init() {
        log.info("✅ PaymentDepositPollingTask initialized — polling interval: {}ms", pollingInterval);
    }

    @Scheduled(fixedDelayString = "${akuunda.payment.deposit.polling.interval-ms:30000}")
    public void pollPendingPayments() {
        List<OneTimePaymentLink> pendingLinks = oneTimePaymentLinkRepository
                .findByStatusIn(List.of("CREATED", "PENDING", "ESCROW_FUNDED_PENDING_DB"));

        if (pendingLinks.isEmpty()) {
            log.debug("🔍 No pending one-time payment links found");
            return;
        }

        log.info("🔍 Polling {} pending one-time payment links", pendingLinks.size());

        Wallet adminWallet = getAdminWallet();
        if (adminWallet == null) {
            log.error("❌ Admin wallet not configured, cannot execute payments");
            return;
        }

        for (OneTimePaymentLink link : pendingLinks) {
            try {
                // PARTNER_CONTRACT links sont gérés par PartnerPaymentMonitorScheduler
                if ("PARTNER_CONTRACT".equals(link.getLinkType())) {
                    continue;
                }

                // ── Recovery: DB persistence previously failed after blockchain success ──
                if ("ESCROW_FUNDED_PENDING_DB".equals(link.getStatus())) {
                    log.info("🔄 [{}] Retrying DB persistence for ESCROW_FUNDED_PENDING_DB link", link.getUniqueCode());
                    Wallet vendorWallet = walletRepository.findByUsers(link.getCreator());
                    persistConditionalPaymentToDb(link, derivePaymentCode(link),
                            link.getDepositTxHash(), link.getAmount(), vendorWallet, adminWallet);
                    continue;
                }

                // Vérifier l'expiration avant de tester la réception
                if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(LocalDateTime.now())) {
                    log.warn("⏰ [{}] Payment link expired", link.getUniqueCode());
                    saveStatus(link, "EXPIRED");
                    continue;
                }

                // Vérifier si le paymentIdBytes32 est disponible (nécessaire pour l'appel smart contract)
                if (link.getPaymentIdBytes32() == null || link.getPaymentIdBytes32().isEmpty()) {
                    log.warn("⚠️ [{}] paymentIdBytes32 non défini", link.getUniqueCode());
                    continue;
                }

                // Vérifier si les USDC sont arrivés sur le wallet CREATE2
                String create2Address = link.getCreate2WalletAddress();
                if (create2Address == null || create2Address.isEmpty()) {
                    log.warn("⚠️ [{}] CREATE2 address not set", link.getUniqueCode());
                    continue;
                }

                Double usdcBalance = paymentFactoryContractService.getUsdcBalance(create2Address);
                log.info("💰 [{}] USDC balance on CREATE2 {}: {} USDC", link.getUniqueCode(), create2Address, usdcBalance);

                boolean received = usdcBalance != null && usdcBalance > 0;

                if (!received) {
                    // ── Recovery: balance=0 but blockchain already ran (executeTxHash is set) ──
                    if ("CONDITIONAL".equals(link.getLinkType())
                            && link.getExecuteTxHash() != null && !link.getExecuteTxHash().isEmpty()
                            && link.getDepositTxHash() != null && !link.getDepositTxHash().isEmpty()) {
                        log.warn("🔄 [{}] Balance=0 but executeTxHash+depositTxHash already set — attempting DB recovery",
                                link.getUniqueCode());
                        Wallet vendorWallet = walletRepository.findByUsers(link.getCreator());
                        persistConditionalPaymentToDb(link, derivePaymentCode(link),
                                link.getDepositTxHash(), link.getAmount(), vendorWallet, adminWallet);
                    }
                    continue;
                }

                log.info("✅ [{}] USDC received on CREATE2 wallet", link.getUniqueCode());

                if ("CONDITIONAL".equals(link.getLinkType())) {
                    // ─── Mode conditionnel : déposer dans l'escrow ───
                    handleConditionalPayment(link, usdcBalance, adminWallet);
                } else {
                    // ─── Mode simple : transfert direct au marchand ───
                    // ✅ CORRIGÉ : on passe usdcBalance pour créer l'opération CREDIT avec le vrai montant
                    handleSimplePayment(link, adminWallet, usdcBalance);
                }

            } catch (Exception e) {
                log.error("❌ [{}] Error polling payment link", link.getUniqueCode(), e);
            }
        }
    }

    /**
     * Traitement déclenché par le listener ERC20 (événement Transfer temps réel).
     * Cherche le OneTimePaymentLink par adresse CREATE2 et exécute le flux de paiement approprié.
     * Pas de @Transactional ici : handleSimplePayment et handleConditionalPayment gèrent leurs
     * propres transactions via TransactionTemplate pour découpler les appels blockchain de la BDD.
     */
    public void processLinkByCreate2Address(String create2Address, Double usdcAmount) {
        OneTimePaymentLink link = oneTimePaymentLinkRepository
                .findByCreate2WalletAddress(create2Address).orElse(null);
        if (link == null) {
            log.warn("[EventTrigger] Aucun OneTimePaymentLink pour: {}", create2Address);
            return;
        }
        if ("PARTNER_CONTRACT".equals(link.getLinkType())) return;
        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            saveStatus(link, "EXPIRED");
            return;
        }
        Wallet adminWallet = getAdminWallet();
        if (adminWallet == null) {
            log.error("[EventTrigger] Admin wallet introuvable");
            return;
        }
        log.info("[EventTrigger] Traitement OneTimePaymentLink {} — {} USDC", link.getUniqueCode(), usdcAmount);
        if ("CONDITIONAL".equals(link.getLinkType())) {
            handleConditionalPayment(link, usdcAmount, adminWallet);
        } else {
            handleSimplePayment(link, adminWallet, usdcAmount);
        }
    }

    /**
     * Handles a SIMPLE (non-conditional) payment.
     * Blockchain call runs outside any transaction; the DB update runs in its own transaction.
     *
     * ✅ CORRIGÉ : Accepte usdcBalance et crée une opération CREDIT pour le marchand.
     */
    void handleSimplePayment(OneTimePaymentLink link, Wallet adminWallet, Double usdcBalance) {
        // Step A — blockchain call (no transaction)
        log.info("⚡ [{}] Calling executePayment() for SIMPLE link", link.getUniqueCode());
        String txHash = paymentFactoryContractService.executePayment(
                link.getPaymentIdBytes32(), adminWallet);

        if (txHash != null && !txHash.startsWith("PAYMENT-FAILED")) {
            log.info("✅ [{}] executePayment succeeded, tx={}", link.getUniqueCode(), txHash);
            // Step B — DB update in its own transaction
            TransactionTemplate tx = new TransactionTemplate(transactionManager);
            tx.execute(status -> {
                try {
                    OneTimePaymentLink fresh = oneTimePaymentLinkRepository
                            .findById(link.getId()).orElse(link);
                    fresh.setStatus("PAID");
                    fresh.setPaidAt(LocalDateTime.now());
                    fresh.setExecuteTxHash(txHash);

                    // ✅ CORRIGÉ : Mettre à jour le sourceAmount avec le vrai montant USDC reçu
                    if (usdcBalance != null && usdcBalance > 0) {
                        fresh.setSourceAmount(usdcBalance);
                        fresh.setSourceCurrency("USDC");
                    }

                    oneTimePaymentLinkRepository.save(fresh);
                    log.info("✅ [{}] SIMPLE payment persisted, tx={}", fresh.getUniqueCode(), txHash);

                    // ✅ AJOUTÉ : Créer une opération CREDIT pour le marchand
                    if (fresh.getCreator() != null) {
                        Double creditAmount = usdcBalance != null && usdcBalance > 0
                                ? usdcBalance
                                : (fresh.getAmount() != null ? fresh.getAmount() : 0.0);

                        Operation creditOperation = new Operation();
                        creditOperation.setType("CREDIT");
                        creditOperation.setStatus("VALIDEE");
                        creditOperation.setUsername(fresh.getCreator().getUsername());
                        creditOperation.setDevise(fresh.getCurrency() != null ? fresh.getCurrency() : "USDC");
                        creditOperation.setAmount(fresh.getAmount() != null ? fresh.getAmount() : creditAmount);
                        creditOperation.setConvertedAmount(fresh.getAmount() != null ? fresh.getAmount() : creditAmount);
                        creditOperation.setDesignation("ONE_TIME_PAYMENT_RECEIVED");
                        creditOperation.setOperationHash("OTP-" + System.currentTimeMillis() + "-" + fresh.getUniqueCode());
                        creditOperation.setCreatedAt(LocalDateTime.now());
                        creditOperation.setUpdatedAt(LocalDateTime.now());

                        // Montant provider (USDC on-chain)
                        creditOperation.setProviderAmount(creditAmount);
                        creditOperation.setProviderDevise("USDC");

                        // TransactionType et Provider
                        creditOperation.setTransactionType("Encaissement");
                        creditOperation.setProvider("Externe");

                        // Contrepartie (payeur externe)
                        if (fresh.getPayerName() != null && !fresh.getPayerName().isEmpty()) {
                            creditOperation.setCounterpartDisplayName(fresh.getPayerName());
                        } else if (fresh.getPayerPhone() != null && !fresh.getPayerPhone().isEmpty()) {
                            creditOperation.setCounterpartDisplayName(fresh.getPayerPhone());
                        } else {
                            creditOperation.setCounterpartDisplayName("Payeur externe");
                        }
                        if (fresh.getPayerPhone() != null && !fresh.getPayerPhone().isEmpty()) {
                            creditOperation.setCounterpartUsername(fresh.getPayerPhone());
                        } else {
                            creditOperation.setCounterpartUsername("external_payer");
                        }

                        operationRepository.save(creditOperation);
                        log.info("✅ [{}] Operation CREDIT created for merchant: {}, amount={} {}, providerAmount={} USDC",
                                fresh.getUniqueCode(), fresh.getCreator().getUsername(),
                                creditOperation.getAmount(), creditOperation.getDevise(),
                                creditAmount);
                    }

                    // 🔔 Notification push : le marchand est notifié qu'il a reçu un paiement
                    if (fresh.getCreator() != null) {
                        String formattedAmount = String.format("%.2f %s",
                                fresh.getAmount() != null ? fresh.getAmount() : 0.0,
                                fresh.getCurrency() != null ? fresh.getCurrency() : "USDC");
                        notificationHelper.notifyTransactionSuccess(
                                fresh.getCreator().getUsername(),
                                formattedAmount,
                                txHash);
                    }

                    return null;
                } catch (Exception e) {
                    log.error("❌ [{}] DB update failed after executePayment, tx={}", link.getUniqueCode(), txHash, e);
                    status.setRollbackOnly();
                    return null;
                }
            });
        } else {
            log.error("❌ [{}] executePayment failed for SIMPLE link", link.getUniqueCode());
            saveStatus(link, "FAILED");
        }
    }

    /**
     * Handles a CONDITIONAL payment (escrow flow).
     * All blockchain calls run outside any transaction.
     * executeTxHash and depositTxHash are persisted immediately after each blockchain success
     * so that a future polling cycle can recover if DB persistence subsequently fails.
     */
    void handleConditionalPayment(OneTimePaymentLink link, Double usdcBalance, Wallet adminWallet) {
        // Step A1 — executePayment (no transaction)
        log.info("⚡ [{}] Calling executePayment() for CONDITIONAL link", link.getUniqueCode());
        String executeTxHash = paymentFactoryContractService.executePayment(
                link.getPaymentIdBytes32(), adminWallet);

        if (executeTxHash == null || executeTxHash.startsWith("PAYMENT-FAILED")) {
            log.error("❌ [{}] executePayment failed for CONDITIONAL link", link.getUniqueCode());
            saveStatus(link, "FAILED");
            return;
        }

        log.info("✅ [{}] executePayment succeeded, tx={}", link.getUniqueCode(), executeTxHash);

        // Persist executeTxHash immediately so recovery is possible if subsequent steps fail
        saveExecuteTxHash(link, executeTxHash);

        // Retrieve vendor wallet
        Wallet vendorWallet = walletRepository.findByUsers(link.getCreator());
        if (vendorWallet == null) {
            log.error("❌ [{}] Vendor wallet not found for CONDITIONAL link", link.getUniqueCode());
            saveStatus(link, "FAILED");
            return;
        }

        // Step A2 — depositToEscrow (no transaction)
        // paymentCode is derived deterministically so retries always produce the same value
        String escrowAddress = smartContractWalletAddress;
        String paymentCode = derivePaymentCode(link);
        Long serviceStartTimestamp = link.getServiceStartDate() != null
                ? link.getServiceStartDate().toEpochSecond(ZoneOffset.UTC)
                : System.currentTimeMillis() / 1000;

        log.info("⚡ [{}] Calling depositToEscrow(), paymentCode={}", link.getUniqueCode(), paymentCode);
        String depositTxHash = smartContractEscrowService.depositToEscrow(
                escrowAddress, usdcBalance, adminWallet, adminWallet, escrowAddress, servicePin,
                paymentCode, vendorWallet.getAddress(), serviceStartTimestamp);

        boolean depositFailed = depositTxHash != null &&
                (depositTxHash.startsWith("ESCROW-DEPOSIT-FAILED") ||
                 depositTxHash.startsWith("ESCROW-DEPOSIT-ERROR"));

        if (depositFailed) {
            log.error("❌ [{}] depositToEscrow failed, hash={}", link.getUniqueCode(), depositTxHash);
            saveStatus(link, "FAILED");
            return;
        }

        log.info("✅ [{}] depositToEscrow succeeded, hash={}", link.getUniqueCode(), depositTxHash);

        // Persist depositTxHash immediately before attempting DB persistence
        saveDepositTxHash(link, depositTxHash);

        // Step B — persist everything to DB in its own transaction
        persistConditionalPaymentToDb(link, paymentCode, depositTxHash, usdcBalance, vendorWallet, adminWallet);
    }

    /**
     * Persists the DB side of a conditional payment: ConditionalPayment, QRCode, link status.
     * Runs in its own dedicated transaction.
     * Idempotent: if a ConditionalPayment with the same paymentCode already exists, skips creation
     * and only updates the link status (handles partial-success retries transparently).
     * If persistence fails, the link is set to {@code ESCROW_FUNDED_PENDING_DB} for retry on the next poll.
     *
     * ✅ CORRIGÉ : Suppression du faux DEBIT sur le vendeur.
     * Le marchand n'est jamais débité dans un paiement conditionnel externe.
     * Le payeur externe est débité hors du système (via Meld/YellowCard).
     * L'opération CREDIT pour le marchand sera créée au moment du scan QR
     * dans ConditionalPaymentServiceImpl.validateQRCode().
     */
    private void persistConditionalPaymentToDb(OneTimePaymentLink link, String paymentCode,
                                               String depositTxHash, Double amount,
                                               Wallet vendorWallet, Wallet adminWallet) {
        String escrowAddress = smartContractWalletAddress;

        log.info("💾 [{}] Persisting ConditionalPayment to DB, paymentCode={}", link.getUniqueCode(), paymentCode);

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        try {
            tx.execute(status -> {
                try {
                    OneTimePaymentLink fresh = oneTimePaymentLinkRepository
                            .findById(link.getId()).orElse(link);

                    // Idempotency: reuse existing ConditionalPayment if already created (retry scenario)
                    ConditionalPayment payment = conditionalPaymentRepository
                            .findByPaymentCode(paymentCode)
                            .orElseGet(() -> {
                                ConditionalPayment newPayment = ConditionalPayment.builder()
                                        .paymentCode(paymentCode)
                                        .client(null) // Payeur externe, pas d'utilisateur dans le système
                                        .vendor(fresh.getCreator())
                                        .serviceType(fresh.getServiceType())
                                        .description(fresh.getDescription())
                                        .amount(amount)
                                        .currency("USDC")
                                        .status("pending_condition")
                                        .escrowContractAddress(escrowAddress)
                                        .depositTransactionHash(depositTxHash)
                                        .serviceStartDate(fresh.getServiceStartDate())
                                        .cancellationDeadline(fresh.getCancellationDeadline())
                                        .intermediateWallet(adminWallet)
                                        .vendorWallet(vendorWallet)
                                        .clientWallet(vendorWallet)
                                        .createdAt(LocalDateTime.now())
                                        .updatedAt(LocalDateTime.now())
                                        .build();
                                return conditionalPaymentRepository.save(newPayment);
                            });

                    // ✅ CORRIGÉ : Pas d'opération DEBIT ici.
                    // Le marchand n'est pas débité. Le payeur externe est débité hors du système.
                    // L'opération CREDIT pour le marchand sera créée au moment du scan QR
                    // dans ConditionalPaymentServiceImpl.validateQRCode() ou releaseFunds().

                    // Generate QR code (or reuse existing URL stored in link)
                    QRCode qrCode = (fresh.getQrCodeToken() != null && !fresh.getQrCodeToken().isEmpty())
                            ? buildQrCodeFromLink(fresh)
                            : generateQrCode(payment.getId(), fresh.getServiceStartDate());

                    // Update link status to ESCROW_FUNDED (funds deposited in escrow, awaiting QR validation)
                    fresh.setStatus("ESCROW_FUNDED");
                    fresh.setUpdatedAt(LocalDateTime.now());
                    fresh.setConditionalPaymentId(payment.getId());
                    fresh.setQrCodeUrl(qrCode.getQrCodeUrl());
                    fresh.setQrCodeToken(qrCode.getToken());
                    oneTimePaymentLinkRepository.save(fresh);
                    log.info("✅ [{}] Conditional payment persisted (ESCROW_FUNDED): paymentCode={}, qrToken={}",
                            fresh.getUniqueCode(), paymentCode, qrCode.getToken());

                    // ═══════════════════════════════════════════════════════════
                    // 📤 NOTIFICATIONS ESCROW_FUNDED AU PAYEUR (WHATSAPP + EMAIL)
                    // ═══════════════════════════════════════════════════════════
                    sendEscrowFundedNotification(fresh, paymentCode, qrCode.getToken());

                    // 🔔 Notification push FCM : le VENDOR est notifié que
                    //    son client a effectué le paiement et que les fonds sont conditionnés
                    if (fresh.getCreator() != null) {
                        String formattedAmount = String.format("%.2f %s",
                                fresh.getAmount() != null ? fresh.getAmount() : 0.0,
                                fresh.getCurrency() != null ? fresh.getCurrency() : "EUR");
                        String payerName = fresh.getPayerName() != null ? fresh.getPayerName() : "Un client";
                        notificationHelper.notifyVendorEscrowFunded(
                                fresh.getCreator().getUsername(),
                                formattedAmount,
                                paymentCode,
                                payerName);
                    }

                    return null;
                } catch (Exception e) {
                    log.error("❌ [{}] DB persistence failed after blockchain success, depositTxHash={}",
                            link.getUniqueCode(), depositTxHash, e);
                    status.setRollbackOnly();
                    throw new RuntimeException(e);
                }
            });
        } catch (Exception e) {
            // Blockchain succeeded but DB failed — mark for retry on next poll
            log.error("❌ [{}] Marking link as ESCROW_FUNDED_PENDING_DB for retry", link.getUniqueCode());
            saveStatus(link, "ESCROW_FUNDED_PENDING_DB");
        }
    }

    /**
     * Envoie les notifications WhatsApp + Email ESCROW_FUNDED au payeur (client).
     * Utilise le montant ORIGINAL saisi par le marchand (link.getAmount() + link.getCurrency()),
     * pas le montant USDC de la blockchain.
     * Best-effort : ne bloque jamais le flux principal en cas d'erreur.
     */
    private void sendEscrowFundedNotification(OneTimePaymentLink link, String paymentCode,
                                              String qrToken) {
        try {
            String payerPhone = link.getPayerPhone();
            String payerName = link.getPayerName();
            String payerEmail = link.getPayerEmail();

            // ── Montant ORIGINAL saisi par le marchand (ex: "15.56 EUR") ──
            String formattedAmount = String.format("%.2f %s",
                    link.getAmount() != null ? link.getAmount() : 0.0,
                    link.getCurrency() != null ? link.getCurrency() : "EUR");

            String serviceDate = "N/A";
            if (link.getServiceStartDate() != null) {
                serviceDate = link.getServiceStartDate()
                        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            }

            // Nom du prestataire : raisonSociale > firstname+lastname > username
            String vendorName = "Prestataire";
            if (link.getCreator() != null) {
                if (link.getCreator().getRaisonSociale() != null && !link.getCreator().getRaisonSociale().isBlank()) {
                    vendorName = link.getCreator().getRaisonSociale();
                } else if (link.getCreator().getFirstname() != null && !link.getCreator().getFirstname().isBlank()) {
                    vendorName = link.getCreator().getFirstname();
                    if (link.getCreator().getLastname() != null && !link.getCreator().getLastname().isBlank()) {
                        vendorName += " " + link.getCreator().getLastname();
                    }
                } else {
                    vendorName = link.getCreator().getUsername();
                }
            }

            // ── URL de confirmation QR (nouveau format) ──
            String confirmationUrl = "https://qr.akuunda-pay.io/confirmation/" + link.getUniqueCode();

            // ── 1. Notification WhatsApp — uniqueCode pour le bouton (template Meta) ──
            if (payerPhone != null && !payerPhone.isBlank()) {
                escrowNotificationService.notifyEscrowFunded(
                        payerPhone, payerName, formattedAmount, paymentCode,
                        vendorName, serviceDate, link.getUniqueCode(), "fr");
                log.info("📤 [{}] WhatsApp ESCROW_FUNDED envoyé à {} ({})",
                        link.getUniqueCode(), payerName, payerPhone);
            } else {
                log.warn("⚠️ [{}] Pas de payerPhone — WhatsApp non envoyé", link.getUniqueCode());
            }

            // ── 2. Notification Email (via Microsoft Graph API) ──
            if (payerEmail != null && !payerEmail.isBlank()) {
                escrowNotificationService.notifyEscrowFundedByEmail(
                        payerEmail, payerName, formattedAmount, paymentCode,
                        vendorName, serviceDate, confirmationUrl, "fr");
                log.info("📧 [{}] Email ESCROW_FUNDED envoyé à {} ({})",
                        link.getUniqueCode(), payerName, payerEmail);
            } else {
                log.warn("⚠️ [{}] Pas de payerEmail — Email non envoyé", link.getUniqueCode());
            }

        } catch (Exception e) {
            log.warn("⚠️ [{}] Échec notifications ESCROW_FUNDED (best-effort): {}",
                    link.getUniqueCode(), e.getMessage());
        }
    }

    /** Builds a lightweight QRCode value object from already-stored link fields (retry path). */
    private QRCode buildQrCodeFromLink(OneTimePaymentLink link) {
        QRCode qr = new QRCode();
        qr.setQrCodeUrl(link.getQrCodeUrl());
        qr.setToken(link.getQrCodeToken());
        return qr;
    }

    /** Generates a new QR code with an expiry derived from the service start date. */
    private QRCode generateQrCode(Long conditionalPaymentId, LocalDateTime serviceStartDate) {
        long hoursUntilService = serviceStartDate != null
                ? ChronoUnit.HOURS.between(LocalDateTime.now(), serviceStartDate)
                : 720;
        int qrExpiryHours = (int) Math.max(hoursUntilService + 24, 48);
        return qrCodeService.generateQRCode(conditionalPaymentId, qrExpiryHours);
    }

    /**
     * Derives a stable, deterministic paymentCode from the link's unique code.
     * Using a deterministic code ensures that retry attempts use the same on-chain identifier
     * that was registered during the original depositToEscrow() call.
     */
    private String derivePaymentCode(OneTimePaymentLink link) {
        return "CP-" + link.getUniqueCode().toUpperCase();
    }

    /**
     * Updates the link status via a direct UPDATE — avoids reloading the full entity
     * (with EAGER creator) just to change the status field.
     */
    private void saveStatus(OneTimePaymentLink link, String newStatus) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            try {
                oneTimePaymentLinkRepository.updateStatusById(
                        link.getId(), newStatus, LocalDateTime.now());
            } catch (Exception e) {
                log.error("❌ [{}] Failed to save status={}", link.getUniqueCode(), newStatus, e);
                status.setRollbackOnly();
            }
            return null;
        });
    }

    /** UPDATE direct — évite findById + save qui recharge le creator EAGER (SELECT users inutile). */
    private void saveExecuteTxHash(OneTimePaymentLink link, String executeTxHash) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            try {
                oneTimePaymentLinkRepository.updateExecuteTxHashById(
                        link.getId(), executeTxHash, LocalDateTime.now());
                log.debug("💾 [{}] executeTxHash saved: {}", link.getUniqueCode(), executeTxHash);
            } catch (Exception e) {
                log.error("❌ [{}] Failed to save executeTxHash", link.getUniqueCode(), e);
                status.setRollbackOnly();
            }
            return null;
        });
    }

    /** UPDATE direct — évite findById + save qui recharge le creator EAGER (SELECT users inutile). */
    private void saveDepositTxHash(OneTimePaymentLink link, String depositTxHash) {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.execute(status -> {
            try {
                oneTimePaymentLinkRepository.updateDepositTxHashById(
                        link.getId(), depositTxHash, LocalDateTime.now());
                log.debug("💾 [{}] depositTxHash saved: {}", link.getUniqueCode(), depositTxHash);
            } catch (Exception e) {
                log.error("❌ [{}] Failed to save depositTxHash", link.getUniqueCode(), e);
                status.setRollbackOnly();
            }
            return null;
        });
    }

    private Wallet getAdminWallet() {
        if (adminWalletId != null && !adminWalletId.isEmpty()) {
            return walletRepository.findByIdWithFetch(adminWalletId).orElse(null);
        }
        if (adminWalletAddress != null && !adminWalletAddress.isEmpty()) {
            return walletRepository.findByAddress(adminWalletAddress);
        }
        return null;
    }
}
