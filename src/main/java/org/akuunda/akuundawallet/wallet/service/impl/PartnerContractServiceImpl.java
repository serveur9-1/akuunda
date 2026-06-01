package org.akuunda.akuundawallet.wallet.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.dto.PushNotificationRequest;
import org.akuunda.akuundawallet.common.enums.NotificationType;
import org.akuunda.akuundawallet.common.service.FcmNotificationService;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.dao.OneTimePaymentLinkRepository;
import org.akuunda.akuundawallet.wallet.api.dao.PartnerContractPaymentRepository;
import org.akuunda.akuundawallet.wallet.api.dao.PartnerContractRepository;
import org.akuunda.akuundawallet.wallet.api.dao.PartnerPaymentBeneficiaryRepository;
import org.akuunda.akuundawallet.wallet.api.dao.PermanentLinkRepository;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.api.dto.partner.*;
import org.akuunda.akuundawallet.wallet.api.entities.*;
import org.akuunda.akuundawallet.wallet.service.PartnerContractService;
import org.akuunda.akuundawallet.wallet.service.PaymentEscrowNotificationService;
import org.akuunda.akuundawallet.wallet.service.SmartContractEscrowService;
import org.akuunda.akuundawallet.wallet.service.listener.UsdcTransferEventListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.akuunda.akuundawallet.wallet.service.PaymentFactoryContractService;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerContractServiceImpl implements PartnerContractService {

    private final PartnerContractRepository contractRepository;
    private final PartnerContractPaymentRepository paymentRepository;
    private final PartnerPaymentBeneficiaryRepository paymentBeneficiaryRepository;
    private final OneTimePaymentLinkRepository oneTimePaymentLinkRepository;
    private final PermanentLinkRepository permanentLinkRepository;
    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final SmartContractEscrowService escrowService;
    private final FcmNotificationService fcmNotificationService;
    private final PaymentFactoryContractService paymentFactoryContractService;
    private final PaymentEscrowNotificationService escrowNotificationService;
    @Autowired(required = false)
    private UsdcTransferEventListener eventListener;

    @Value("${akuunda.escrow.contract.wallet.address:}")
    private String escrowContractAddress;

    @Value("${akuunda.intermediate.wallet.address:}")
    private String intermediateWalletAddress;

    @Value("${akuunda.intermediate.wallet.id:}")
    private String intermediateWalletId;

    @Value("${akuunda.web-pay.base-url:https://qr.akuunda-pay.io}")
    private String webPayBaseUrl;

    @Value("${akuunda.qrcode.base-url:https://qr.akuunda-pay.io/partner/validate}")
    private String qrBaseUrl;

    @Value("${akuunda.qrcode.default-expiration-hours:24}")
    private Integer defaultQrExpirationHours;

    @Value("${akuunda.partner.confirmation.base-url:https://pay.akuunda-pay.io/partner/confirm}")
    private String confirmationBaseUrl;

    @Value("${akuunda.partner.confirmation.expiration-hours:48}")
    private Integer confirmationExpirationHours;

    @Value("${akuunda.partner.otp.expiration-minutes:15}")
    private Integer otpExpirationMinutes;

    @Value("${akuunda.admin.username:akuunda-admin}")
    private String defaultAdminUsername;

    // =========================================================================
    // GESTION DES CONTRATS
    // =========================================================================

    @Override
    @Transactional
    public PartnerContractResponse createContract(String partnerUsername, CreatePartnerContractRequest request) {
        log.info("Création contrat partenaire: partner={}, name={}, mode={}", partnerUsername, request.getName(), request.getTriggerType());

        String triggerType = request.getTriggerType() != null ? request.getTriggerType() : "QR_CODE";
        boolean isDynamic = "DYNAMIC_ASSIGNMENT".equals(triggerType);

        List<BeneficiaryRequest> beneficiaryList = (request.getBeneficiaries() != null)
                ? request.getBeneficiaries() : List.of();

        if (!isDynamic) {
            if (beneficiaryList.isEmpty()) {
                throw new IllegalArgumentException("Au moins un bénéficiaire est requis");
            }
            double totalPercentage = beneficiaryList.stream()
                    .mapToDouble(BeneficiaryRequest::getPercentage).sum();
            if (totalPercentage > 1.0 || totalPercentage <= 0.0) {
                throw new IllegalArgumentException(
                    "La somme des pourcentages des bénéficiaires doit être entre 0% et 100%. " +
                    "Total actuel : " + (totalPercentage * 100) + "%");
            }
            for (BeneficiaryRequest b : beneficiaryList) {
                if ((b.getWalletAddress() == null || b.getWalletAddress().isBlank()) &&
                    (b.getUsername() == null || b.getUsername().isBlank())) {
                    throw new IllegalArgumentException(
                        "Le bénéficiaire '" + b.getLabel() + "' doit avoir un walletAddress ou un username");
                }
            }
        }
        validateTriggerTypeConfig(triggerType, request);

        String contractCode = "PC-" + partnerUsername.substring(0, Math.min(6, partnerUsername.length())).toUpperCase()
                + "-" + System.currentTimeMillis();

        PartnerContract contract = PartnerContract.builder()
                .contractCode(contractCode)
                .partnerUsername(partnerUsername)
                .name(request.getName())
                .description(request.getDescription())
                .serviceType(request.getServiceType())
                .triggerType(triggerType)
                .paymentLinkType(request.getPaymentLinkType() != null ? request.getPaymentLinkType() : "BOTH")
                .cancellationPenaltyRate(request.getCancellationPenaltyRate() != null ? request.getCancellationPenaltyRate() : 0.0)
                .freeCancellationHours(request.getFreeCancellationHours() != null ? request.getFreeCancellationHours() : 24)
                .autoReleaseHours(request.getAutoReleaseHours())
                .disputeWindowHours(request.getDisputeWindowHours())
                .expectedLatitude(request.getExpectedLatitude())
                .expectedLongitude(request.getExpectedLongitude())
                .geolocationRadiusMeters(request.getGeolocationRadiusMeters() != null ? request.getGeolocationRadiusMeters() : 200.0)
                .active(true)
                .beneficiaries(new ArrayList<>())
                .build();

        contract = contractRepository.save(contract);

        int order = 1;
        for (BeneficiaryRequest br : beneficiaryList) {
            String resolvedAddress = br.getWalletAddress();
            if ((resolvedAddress == null || resolvedAddress.isBlank()) && br.getUsername() != null) {
                resolvedAddress = resolveWalletAddress(br.getUsername());
            }
            PartnerContractBeneficiary b = PartnerContractBeneficiary.builder()
                    .contract(contract)
                    .beneficiaryType(br.getBeneficiaryType())
                    .label(br.getLabel())
                    .walletAddress(resolvedAddress)
                    .username(br.getUsername())
                    .percentage(br.getPercentage())
                    .executionOrder(br.getExecutionOrder() != null ? br.getExecutionOrder() : order)
                    .build();
            contract.getBeneficiaries().add(b);
            order++;
        }

        // Générer l'ID on-chain et enregistrer la config sur le smart contract
        String onChainConfigId = paymentFactoryContractService.generatePaymentId(contractCode);
        contract.setOnChainConfigId(onChainConfigId);
        contract = contractRepository.save(contract);

        // DYNAMIC_ASSIGNMENT : pas de config on-chain (les bénéficiaires sont inconnus à la création)
        if (!isDynamic && escrowContractAddress != null && !escrowContractAddress.isBlank()) {
            Wallet serviceWallet = walletRepository.findByAddress(intermediateWalletAddress);
            if (serviceWallet != null) {
                List<String> addrs = contract.getBeneficiaries().stream()
                        .map(PartnerContractBeneficiary::getWalletAddress).collect(Collectors.toList());
                List<Integer> shares = contract.getBeneficiaries().stream()
                        .map(b -> (int)(b.getPercentage() * 10_000)).collect(Collectors.toList());
                escrowService.registerConfig(escrowContractAddress, onChainConfigId, addrs, shares, serviceWallet);
            }
        }

        // Créer un PermanentLink réutilisable uniquement si le type le permet
        boolean createPermanentLink = !"ONE_TIME_ONLY".equals(contract.getPaymentLinkType());
        if (createPermanentLink && !permanentLinkRepository.existsByMerchantSlug(contractCode)) {
            List<Users> partnerUsers = userRepository.findAllByUsername(partnerUsername);
            Users partnerUser = partnerUsers.isEmpty() ? null : partnerUsers.get(0);
            if (partnerUser != null) {
                PermanentLink permanentLink = PermanentLink.builder()
                        .merchantSlug(contractCode)
                        .creator(partnerUser)
                        .description(contract.getName())
                        .amount(null) // montant libre, saisi par le client
                        .currency("USDC")
                        .isActive(true)
                        .totalSessions(0)
                        .totalCompletedPayments(0)
                        .totalAmountReceived(0.0)
                        .partnerContractCode(contractCode)
                        .build();
                permanentLinkRepository.save(permanentLink);
                log.info("PermanentLink créé: slug={}", contractCode);
            }
        }

        log.info("Contrat créé: contractCode={}, onChainId={}, mode={}, bénéficiaires={}",
                contractCode, onChainConfigId, triggerType, contract.getBeneficiaries().size());
        return mapContractToResponse(contract);
    }

    @Override
    public PartnerContractResponse getContract(String contractCode) {
        return mapContractToResponse(findContractOrThrow(contractCode));
    }

    @Override
    public List<PartnerContractResponse> getPartnerContracts(String partnerUsername) {
        return contractRepository.findByPartnerUsernameAndActiveTrue(partnerUsername)
                .stream().map(this::mapContractToResponse).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deactivateContract(String contractCode, String partnerUsername) {
        PartnerContract contract = findContractOrThrow(contractCode);
        if (!contract.getPartnerUsername().equals(partnerUsername)) {
            throw new SecurityException("Vous n'êtes pas autorisé à désactiver ce contrat");
        }
        contract.setActive(false);
        contractRepository.save(contract);

        if (escrowContractAddress != null && !escrowContractAddress.isBlank()
                && contract.getOnChainConfigId() != null) {
            Wallet serviceWallet = walletRepository.findByAddress(intermediateWalletAddress);
            if (serviceWallet != null) {
                escrowService.deactivateConfig(escrowContractAddress, contract.getOnChainConfigId(), serviceWallet);
            }
        }
        log.info("Contrat désactivé: {}", contractCode);
    }

    // =========================================================================
    // INITIATION DE PAIEMENT
    // =========================================================================

    @Override
    @Transactional
    public PartnerPaymentResponse initiatePayment(String contractCode, InitiatePartnerPaymentRequest request) {
        log.info("Initiation paiement partenaire: contractCode={}, client={}, amount={}, mode={}",
                contractCode, request.getClientUsername(), request.getAmount(), "");

        PartnerContract contract = findContractOrThrow(contractCode);
        if (!contract.getActive()) {
            throw new IllegalStateException("Le contrat " + contractCode + " est inactif");
        }

        // Le client peut ne pas avoir de compte Akuunda — phone/email suffisent
        Users client = null;
        if (request.getClientUsername() != null && !request.getClientUsername().isBlank()) {
            List<Users> clientUsers = userRepository.findAllByUsername(request.getClientUsername());
            client = clientUsers.isEmpty() ? null : clientUsers.get(0);
        }
        String clientWalletId = null;
        if (client != null) {
            List<Wallet> clientWallets = walletRepository.findWalletByUsers(client);
            if (clientWallets != null && !clientWallets.isEmpty()) {
                clientWalletId = clientWallets.get(0).getId();
            }
        }

        Wallet intermediateWallet = walletRepository.findById(intermediateWalletId).orElse(null);
        if (intermediateWallet == null) {
            intermediateWallet = walletRepository.findByAddress(intermediateWalletAddress);
        }
        if (intermediateWallet == null) throw new IllegalStateException("Wallet intermédiaire introuvable");

        String paymentCode = "PCP-" + System.currentTimeMillis() + "-"
                + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        LocalDateTime cancellationDeadline = null;
        if (request.getServiceStartDate() != null && contract.getFreeCancellationHours() != null) {
            cancellationDeadline = request.getServiceStartDate().minusHours(contract.getFreeCancellationHours());
        }

        String clientIdentifier = request.getClientUsername() != null && !request.getClientUsername().isBlank()
                ? request.getClientUsername()
                : (request.getClientPhone() != null ? request.getClientPhone() : request.getClientEmail());

        PartnerContractPayment payment = PartnerContractPayment.builder()
                .paymentCode(paymentCode)
                .contract(contract)
                .clientUsername(clientIdentifier != null ? clientIdentifier : "external")
                .clientPhone(request.getClientPhone())
                .clientEmail(request.getClientEmail())
                .clientWalletId(clientWalletId)
                .intermediateWalletId(intermediateWallet.getId())
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "USDC")
                .localAmount(request.getLocalAmount())
                .localCurrency(request.getLocalCurrency())
                .status("pending_condition")
                .serviceStartDate(request.getServiceStartDate())
                .cancellationDeadline(cancellationDeadline)
                .webhookUrl(request.getWebhookUrl())
                .webhookSecret(request.getWebhookSecret())
                .distributions(new ArrayList<>())
                .build();

        // Générer l'ID on-chain du paiement
        String onChainPaymentId = paymentFactoryContractService.generatePaymentId(paymentCode);
        payment.setOnChainPaymentId(onChainPaymentId);

        // Initialisation selon le mode de libération
        initLiberationMode(payment, contract, request);

        payment = paymentRepository.save(payment);

        // Étape 1 : créer le payment link → adresse CREATE2 pour que le client envoie ses USDC
        try {
            Wallet serviceWallet = walletRepository.findById(intermediateWalletId).orElse(null);
            if (serviceWallet == null) serviceWallet = walletRepository.findByAddress(intermediateWalletAddress);
            if (serviceWallet == null) throw new IllegalStateException("Wallet de service introuvable");

            // merchantAddress = wallet intermédiaire (qui recevra les fonds avant de les déposer dans le contrat)
            String create2Address = paymentFactoryContractService.createPaymentLink(
                    onChainPaymentId,
                    intermediateWalletAddress,
                    request.getAmount(),
                    "Paiement partenaire : " + contract.getName(),
                    86400L, // 24h d'expiration
                    serviceWallet);

            if (create2Address == null) throw new RuntimeException("Échec création du payment link CREATE2");

            payment.setCreate2WalletAddress(create2Address);
            payment.setCreate2Status("waiting_payment");
            if (eventListener != null) eventListener.registerPayment(create2Address);

            // Créer le OneTimePaymentLink pour que le client puisse payer fiat→USDC via web-pay.
            // creator = client (toujours présent). Le partnerUser peut ne pas être en base locale.
            LocalDateTime expiresAt = LocalDateTime.now().plusHours(24);
            OneTimePaymentLink payLink = OneTimePaymentLink.builder()
                    .uniqueCode(paymentCode)
                    .creator(client) // null si client sans compte Akuunda
                    .description("Paiement partenaire : " + contract.getName())
                    .amount(request.getAmount())
                    .currency(request.getCurrency() != null ? request.getCurrency() : "USDC")
                    .sourceAmount(request.getAmount())
                    .sourceCurrency("USDC")
                    .status("CREATED")
                    .expiresAt(expiresAt)
                    .paymentIdBytes32(onChainPaymentId)
                    .create2WalletAddress(create2Address)
                    .linkType("PARTNER_CONTRACT")
                    .build();
            oneTimePaymentLinkRepository.save(payLink);

            // URL web-pay utilisable par le client pour payer fiat→USDC
            payment.setQrUrl(webPayBaseUrl + "/" + paymentCode);
            payment = paymentRepository.save(payment);

            log.info("Payment link créé: paymentCode={}, CREATE2={}, url={}", paymentCode, create2Address, payment.getQrUrl());

            // Notifications post-création selon le mode de libération
            sendPostDepositNotification(payment, contract);
        } catch (Exception e) {
            log.error("Échec création payment link: paymentCode={}", paymentCode, e);
            payment.setStatus("failed");
            paymentRepository.save(payment);
            throw new RuntimeException("Échec de la création du lien de paiement : " + e.getMessage(), e);
        }

        return mapPaymentToResponse(payment);
    }

    // =========================================================================
    // MODES EXISTANTS : QR_CODE / MANUAL / WEBHOOK
    // =========================================================================

    @Override
    @Transactional
    public PartnerPaymentResponse validateByQrCode(String qrToken, String scannedBy) {
        log.info("Validation QR partenaire: token={}, scannedBy={}", qrToken, scannedBy);

        PartnerContractPayment payment = paymentRepository.findByQrToken(qrToken)
                .orElseThrow(() -> new IllegalArgumentException("QR code invalide : " + qrToken));

        assertPending(payment);
        if (payment.getQrExpiresAt() != null && LocalDateTime.now().isAfter(payment.getQrExpiresAt())) {
            throw new IllegalStateException("Le QR code a expiré");
        }

        payment.setQrScannedAt(LocalDateTime.now());
        payment.setQrScannedBy(scannedBy);
        payment.setStatus("condition_validated");
        payment = paymentRepository.save(payment);
        return executeDistribution(payment);
    }

    @Override
    @Transactional
    public PartnerPaymentResponse validateManually(String paymentCode, String validatedBy) {
        log.info("Validation manuelle: paymentCode={}, by={}", paymentCode, validatedBy);

        PartnerContractPayment payment = findPaymentOrThrow(paymentCode);
        assertPending(payment);
        payment.setStatus("condition_validated");
        payment = paymentRepository.save(payment);
        return executeDistribution(payment);
    }

    @Override
    @Transactional
    public PartnerPaymentResponse validateByWebhook(String paymentCode, String webhookPayload, String signature) {
        log.info("Validation webhook: paymentCode={}", paymentCode);

        PartnerContractPayment payment = findPaymentOrThrow(paymentCode);
        if (payment.getWebhookSecret() != null && !payment.getWebhookSecret().isBlank()) {
            if (!verifyWebhookSignature(webhookPayload, signature, payment.getWebhookSecret())) {
                throw new SecurityException("Signature webhook invalide");
            }
        }
        assertPending(payment);
        payment.setWebhookValidatedAt(LocalDateTime.now());
        payment.setStatus("condition_validated");
        payment = paymentRepository.save(payment);
        return executeDistribution(payment);
    }

    // =========================================================================
    // NOUVEAU MODE : REMOTE_CONFIRMATION
    // =========================================================================

    @Override
    @Transactional
    public PartnerPaymentResponse confirmReceiptByClient(String confirmationToken) {
        log.info("Confirmation réception client: token={}", confirmationToken);

        PartnerContractPayment payment = paymentRepository.findByConfirmationToken(confirmationToken)
                .orElseThrow(() -> new IllegalArgumentException("Token de confirmation invalide"));

        // DYNAMIC_ASSIGNMENT uses beneficiaries_assigned; other modes use pending_condition
        if (!"pending_condition".equals(payment.getStatus()) && !"beneficiaries_assigned".equals(payment.getStatus())) {
            throw new IllegalStateException("Ce paiement ne peut plus être validé (statut: " + payment.getStatus() + ")");
        }
        if (payment.getConfirmationTokenExpiresAt() != null
                && LocalDateTime.now().isAfter(payment.getConfirmationTokenExpiresAt())) {
            throw new IllegalStateException("Le lien de confirmation a expiré");
        }

        payment.setConfirmedByClientAt(LocalDateTime.now());
        payment.setStatus("condition_validated");
        payment = paymentRepository.save(payment);
        return executeDistribution(payment);
    }

    // =========================================================================
    // NOUVEAU MODE : OTP
    // =========================================================================

    @Override
    @Transactional
    public PartnerPaymentResponse validateByOtp(String paymentCode, String otpCode, String validatedBy) {
        log.info("Validation OTP: paymentCode={}, by={}", paymentCode, validatedBy);

        PartnerContractPayment payment = findPaymentOrThrow(paymentCode);
        assertPending(payment);

        if (payment.getOtpCode() == null || !payment.getOtpCode().equals(otpCode)) {
            throw new IllegalArgumentException("Code OTP invalide");
        }
        if (payment.getOtpExpiresAt() != null && LocalDateTime.now().isAfter(payment.getOtpExpiresAt())) {
            throw new IllegalStateException("Le code OTP a expiré");
        }
        if (payment.getOtpUsedAt() != null) {
            throw new IllegalStateException("Ce code OTP a déjà été utilisé");
        }

        payment.setOtpUsedAt(LocalDateTime.now());
        payment.setOtpUsedBy(validatedBy);
        payment.setStatus("condition_validated");
        payment = paymentRepository.save(payment);
        return executeDistribution(payment);
    }

    // =========================================================================
    // NOUVEAU MODE : DUAL_CONFIRMATION
    // =========================================================================

    @Override
    @Transactional
    public PartnerPaymentResponse confirmByProvider(String paymentCode, String providerUsername) {
        log.info("Confirmation provider: paymentCode={}, provider={}", paymentCode, providerUsername);

        PartnerContractPayment payment = findPaymentOrThrow(paymentCode);
        assertPending(payment);

        payment.setProviderConfirmedAt(LocalDateTime.now());
        payment.setProviderConfirmedBy(providerUsername);
        payment = paymentRepository.save(payment);

        if (payment.getClientDualConfirmedAt() != null) {
            log.info("Les deux parties ont confirmé → redistribution: paymentCode={}", paymentCode);
            payment.setStatus("condition_validated");
            payment = paymentRepository.save(payment);
            return executeDistribution(payment);
        }

        // Notifier le client qu'il doit aussi confirmer
        sendNotification(payment.getClientUsername(),
                "En attente de votre confirmation",
                "Le prestataire a confirmé la livraison. Confirmez la réception pour libérer les fonds.",
                NotificationType.PAYMENT_DUAL_CONFIRM_PENDING,
                payment.getPaymentCode());

        return mapPaymentToResponse(payment);
    }

    @Override
    @Transactional
    public PartnerPaymentResponse confirmByClient(String paymentCode, String clientUsername) {
        log.info("Confirmation client (dual): paymentCode={}, client={}", paymentCode, clientUsername);

        PartnerContractPayment payment = findPaymentOrThrow(paymentCode);
        assertPending(payment);

        if (!payment.getClientUsername().equals(clientUsername)) {
            throw new SecurityException("Vous n'êtes pas le client de ce paiement");
        }

        payment.setClientDualConfirmedAt(LocalDateTime.now());
        payment = paymentRepository.save(payment);

        if (payment.getProviderConfirmedAt() != null) {
            log.info("Les deux parties ont confirmé → redistribution: paymentCode={}", paymentCode);
            payment.setStatus("condition_validated");
            payment = paymentRepository.save(payment);
            return executeDistribution(payment);
        }

        // Notifier le partenaire qu'il doit aussi confirmer
        sendNotification(payment.getContract().getPartnerUsername(),
                "En attente de votre confirmation",
                "Le client a confirmé la réception. Confirmez la livraison pour libérer les fonds.",
                NotificationType.PAYMENT_DUAL_CONFIRM_PENDING,
                payment.getPaymentCode());

        return mapPaymentToResponse(payment);
    }

    // =========================================================================
    // NOUVEAU MODE : GEOLOCATION
    // =========================================================================

    @Override
    @Transactional
    public PartnerPaymentResponse validateByGeolocation(String paymentCode, double latitude, double longitude) {
        log.info("Validation géolocalisation: paymentCode={}, lat={}, lon={}", paymentCode, latitude, longitude);

        PartnerContractPayment payment = findPaymentOrThrow(paymentCode);
        assertPending(payment);

        PartnerContract contract = payment.getContract();
        if (contract.getExpectedLatitude() == null || contract.getExpectedLongitude() == null) {
            throw new IllegalStateException("Ce contrat n'a pas de coordonnées de référence configurées");
        }

        double distanceMeters = calculateDistanceMeters(
                contract.getExpectedLatitude(), contract.getExpectedLongitude(), latitude, longitude);
        double radiusMeters = contract.getGeolocationRadiusMeters() != null ? contract.getGeolocationRadiusMeters() : 200.0;

        log.info("Distance calculée: {}m, rayon autorisé: {}m", Math.round(distanceMeters), radiusMeters);

        if (distanceMeters > radiusMeters) {
            throw new IllegalStateException(
                String.format("Position trop éloignée du lieu de service (%.0fm, rayon autorisé: %.0fm)", distanceMeters, radiusMeters));
        }

        payment.setClientLatitude(latitude);
        payment.setClientLongitude(longitude);
        payment.setLocationConfirmedAt(LocalDateTime.now());
        payment.setStatus("condition_validated");
        payment = paymentRepository.save(payment);
        return executeDistribution(payment);
    }

    // =========================================================================
    // NOUVEAU MODE : ADMIN_APPROVAL
    // =========================================================================

    @Override
    @Transactional
    public PartnerPaymentResponse approveByAdmin(String paymentCode, String adminUsername) {
        log.info("Approbation admin: paymentCode={}, admin={}", paymentCode, adminUsername);

        PartnerContractPayment payment = findPaymentOrThrow(paymentCode);
        assertPending(payment);

        payment.setAdminApprovedAt(LocalDateTime.now());
        payment.setAdminApprovedBy(adminUsername);
        payment.setStatus("condition_validated");
        payment = paymentRepository.save(payment);
        return executeDistribution(payment);
    }

    // =========================================================================
    // DYNAMIC_ASSIGNMENT — Admin assigne les bénéficiaires après réception du paiement
    // =========================================================================

    @Override
    @Transactional
    public PartnerPaymentResponse assignBeneficiaries(String paymentCode,
                                                       AssignPaymentBeneficiariesRequest request) {
        log.info("Assignation bénéficiaires: paymentCode={}, admin={}", paymentCode, request.getAdminUsername());

        PartnerContractPayment payment = findPaymentOrThrow(paymentCode);

        if (!"DYNAMIC_ASSIGNMENT".equals(payment.getContract().getTriggerType())) {
            throw new IllegalStateException("Ce mode n'est pas DYNAMIC_ASSIGNMENT");
        }
        if (!"pending_condition".equals(payment.getStatus())) {
            throw new IllegalStateException("Impossible d'assigner dans cet état : " + payment.getStatus());
        }
        // Les USDC doivent être dans le wallet intermédiaire (scheduler déjà passé)
        if (!"payment_received".equals(payment.getCreate2Status())) {
            throw new IllegalStateException(
                "Le paiement n'a pas encore été reçu on-chain (create2Status: " + payment.getCreate2Status() + ")");
        }

        // Valider que la somme des pourcentages = 1.0
        double total = request.getBeneficiaries().stream()
                .mapToDouble(BeneficiaryRequest::getPercentage).sum();
        if (Math.abs(total - 1.0) > 0.001) {
            throw new IllegalArgumentException("La somme des pourcentages doit être 1.0 (actuel: " + total + ")");
        }

        // Enregistrer les bénéficiaires dynamiques en base
        paymentBeneficiaryRepository.deleteByPaymentId(payment.getId());
        payment.getDynamicBeneficiaries().clear();

        List<String> addrs = new ArrayList<>();
        List<Integer> shares = new ArrayList<>();
        int bpsTotal = 0;
        List<BeneficiaryRequest> bens = request.getBeneficiaries();

        int order = 1;
        for (int i = 0; i < bens.size(); i++) {
            BeneficiaryRequest br = bens.get(i);
            String resolvedAddress = br.getWalletAddress();
            if ((resolvedAddress == null || resolvedAddress.isBlank()) && br.getUsername() != null) {
                resolvedAddress = resolveWalletAddress(br.getUsername());
            }
            PartnerPaymentBeneficiary ppb = PartnerPaymentBeneficiary.builder()
                    .payment(payment)
                    .beneficiaryType(br.getBeneficiaryType())
                    .label(br.getLabel())
                    .walletAddress(resolvedAddress)
                    .username(br.getUsername())
                    .percentage(br.getPercentage())
                    .executionOrder(br.getExecutionOrder() != null ? br.getExecutionOrder() : order)
                    .build();
            payment.getDynamicBeneficiaries().add(ppb);
            addrs.add(resolvedAddress);

            // Dernier bénéficiaire absorbe le dust d'arrondi pour que total = 10 000 BPS
            int bps = (i == bens.size() - 1) ? (10_000 - bpsTotal) : (int)(br.getPercentage() * 10_000);
            shares.add(bps);
            bpsTotal += bps;
            order++;
        }

        // ── Verrouiller les fonds dans le smart contract ──────────────────────
        Wallet serviceWallet = walletRepository.findById(intermediateWalletId).orElse(null);
        if (serviceWallet == null) serviceWallet = walletRepository.findByAddress(intermediateWalletAddress);
        if (serviceWallet == null) throw new IllegalStateException("Wallet intermédiaire introuvable");

        // configId unique à ce paiement : on-chain, immuable une fois enregistré
        String dynamicConfigId = paymentFactoryContractService
                .generatePaymentId(paymentCode + "-cfg-" + System.currentTimeMillis());

        String registerTxHash = escrowService.registerConfig(
                escrowContractAddress, dynamicConfigId, addrs, shares, serviceWallet);
        if (registerTxHash == null) {
            throw new IllegalStateException("Échec registerConfig on-chain pour " + paymentCode);
        }

        String depositTxHash = escrowService.approveAndDeposit(
                escrowContractAddress, payment.getOnChainPaymentId(),
                dynamicConfigId, payment.getAmount(), serviceWallet, null);
        if (depositTxHash == null || depositTxHash.startsWith("DEPOSIT-FAILED")) {
            throw new IllegalStateException("Échec approveAndDeposit on-chain pour " + paymentCode);
        }

        payment.setDynamicOnChainConfigId(dynamicConfigId);
        payment.setDepositTxHash(depositTxHash);
        payment.setEscrowWalletId(escrowContractAddress);
        payment.setCreate2Status("deposited");
        // ─────────────────────────────────────────────────────────────────────

        payment.setBeneficiariesAssignedAt(LocalDateTime.now());
        payment.setAssignedBy(request.getAdminUsername());
        payment.setStatus("beneficiaries_assigned");

        // QR token : le client le présente au professionnel après la prestation
        String qrToken = UUID.randomUUID().toString().replace("-", "");
        payment.setQrToken(qrToken);
        payment.setQrUrl(qrBaseUrl + "?token=" + qrToken);
        payment.setQrExpiresAt(LocalDateTime.now().plusHours(48));
        payment = paymentRepository.save(payment);

        // Notifier le client par WhatsApp + Email (il n'a pas Akuunda Pay)
        // Priorité : données stockées sur le paiement, sinon lookup si compte Akuunda
        String clientPhone = payment.getClientPhone();
        String clientEmail = payment.getClientEmail();
        String clientName  = payment.getClientUsername();

        if ((clientPhone == null || clientEmail == null) && payment.getClientUsername() != null
                && !payment.getClientUsername().equals("external")) {
            List<Users> clientUsers = userRepository.findAllByUsername(payment.getClientUsername());
            Users clientUser = clientUsers.isEmpty() ? null : clientUsers.get(0);
            if (clientPhone == null) clientPhone = (clientUser != null) ? clientUser.getMobilePhone() : null;
            if (clientEmail == null) clientEmail = (clientUser != null) ? clientUser.getEmail() : null;
            clientName = buildFullName(clientUser, payment.getClientUsername());
        }
        String amountLabel = String.format("%.2f USDC", payment.getAmount());
        String vendorName  = payment.getContract().getName();
        String serviceDate = payment.getServiceStartDate() != null
                ? payment.getServiceStartDate().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                : "À confirmer";

        if (clientPhone != null && !clientPhone.isBlank()) {
            escrowNotificationService.notifyEscrowFunded(
                    clientPhone, clientName, amountLabel, paymentCode,
                    vendorName, serviceDate, qrToken, "fr");
        }
        if (clientEmail != null && !clientEmail.isBlank()) {
            escrowNotificationService.notifyEscrowFundedByEmail(
                    clientEmail, clientName, amountLabel, paymentCode,
                    vendorName, serviceDate, payment.getQrUrl(), "fr");
        }

        // Notifier chaque professionnel assigné (si username connu)
        for (PartnerPaymentBeneficiary b : payment.getDynamicBeneficiaries()) {
            if (b.getUsername() != null && !b.getUsername().isBlank()) {
                double earn = payment.getAmount() * b.getPercentage();
                sendNotification(b.getUsername(),
                        "Prestation à effectuer",
                        String.format("Un paiement de %.2f USDC vous attend. "
                                + "Scannez le QR code du client après la prestation.", earn),
                        NotificationType.PAYMENT_PROFESSIONAL_ASSIGNED,
                        paymentCode);
            }
        }

        log.info("Bénéficiaires assignés + fonds verrouillés on-chain + QR généré: "
                + "paymentCode={}, nb={}, configId={}, depositTx={}",
                paymentCode, payment.getDynamicBeneficiaries().size(), dynamicConfigId, depositTxHash);
        return mapPaymentToResponse(payment);
    }

    // =========================================================================
    // GRANT RELAYER ROLE — opération unique post-déploiement
    // =========================================================================

    @Override
    public String grantRelayerRole() {
        log.info("grantRelayerRole: contract={}, account={}", escrowContractAddress, intermediateWalletAddress);
        Wallet adminWallet = walletRepository.findById(intermediateWalletId).orElse(null);
        if (adminWallet == null) adminWallet = walletRepository.findByAddress(intermediateWalletAddress);
        if (adminWallet == null) throw new IllegalStateException("Wallet admin introuvable");

        // RELAYER_ROLE = keccak256("RELAYER_ROLE")
        String relayerRole = "0xe2b7fb3b832174769106daebcfd6d1970523240dda11281102db9363b83b0dc4";
        return escrowService.grantRole(escrowContractAddress, relayerRole,
                adminWallet.getAddress(), adminWallet);
    }

    // =========================================================================
    // RETRY DISTRIBUTION — rejoue distribute() sur un paiement en échec
    // =========================================================================

    @Override
    @Transactional
    public PartnerPaymentResponse retryDistribution(String paymentCode) {
        log.info("Retry distribution: paymentCode={}", paymentCode);

        PartnerContractPayment payment = findPaymentOrThrow(paymentCode);

        if (!"failed".equals(payment.getStatus()) && !"distributed".equals(payment.getStatus())) {
            throw new IllegalStateException(
                "Retry uniquement possible sur un paiement en échec ou distribué on-chain (statut actuel: " + payment.getStatus() + ")");
        }
        if (!"deposited".equals(payment.getCreate2Status())) {
            throw new IllegalStateException(
                "Les fonds ne sont pas dans le smart contract (create2Status: " + payment.getCreate2Status() + ")");
        }

        // Effacer les distributions en échec pour repartir proprement
        payment.getDistributions().clear();
        payment.setDistributionTxHashes(null);
        payment.setStatus("condition_validated");
        payment = paymentRepository.save(payment);

        log.info("Statut réinitialisé → condition_validated, relance distribute(): paymentCode={}", paymentCode);
        return executeDistribution(payment);
    }

    // =========================================================================
    // CONTESTATION (DISPUTE_WINDOW / TIME_BASED)
    // =========================================================================

    @Override
    @Transactional
    public PartnerPaymentResponse disputePayment(String paymentCode, String clientUsername, String reason) {
        log.info("Contestation paiement: paymentCode={}, client={}", paymentCode, clientUsername);

        PartnerContractPayment payment = findPaymentOrThrow(paymentCode);

        if (!"pending_condition".equals(payment.getStatus())) {
            throw new IllegalStateException("Ce paiement ne peut plus être contesté (statut: " + payment.getStatus() + ")");
        }
        if (!payment.getClientUsername().equals(clientUsername)) {
            throw new SecurityException("Vous n'êtes pas le client de ce paiement");
        }
        if (payment.getDisputedAt() != null) {
            throw new IllegalStateException("Ce paiement est déjà contesté");
        }

        payment.setDisputedAt(LocalDateTime.now());
        payment.setDisputedBy(clientUsername);
        payment.setDisputeReason(reason);
        payment.setStatus("disputed");
        payment = paymentRepository.save(payment);

        // Notifier le partenaire et l'admin
        sendNotification(payment.getContract().getPartnerUsername(),
                "Paiement contesté",
                "Le client a contesté le paiement " + paymentCode + ". Motif : " + reason,
                NotificationType.PAYMENT_DISPUTED, paymentCode);
        sendNotification(defaultAdminUsername,
                "Paiement contesté - Action requise",
                "Paiement " + paymentCode + " contesté par " + clientUsername + ". Motif : " + reason,
                NotificationType.PAYMENT_DISPUTED, paymentCode);

        log.info("Paiement contesté et libération automatique bloquée: paymentCode={}", paymentCode);
        return mapPaymentToResponse(payment);
    }

    // =========================================================================
    // ANNULATION
    // =========================================================================

    @Override
    @Transactional
    public PartnerPaymentResponse cancelPayment(String paymentCode, String reason) {
        log.info("Annulation paiement partenaire: paymentCode={}", paymentCode);

        PartnerContractPayment payment = findPaymentOrThrow(paymentCode);
        if (!"pending_condition".equals(payment.getStatus()) && !"disputed".equals(payment.getStatus())) {
            throw new IllegalStateException("Paiement non annulable (statut: " + payment.getStatus() + ")");
        }

        PartnerContract contract = payment.getContract();
        Wallet relayerWallet = walletRepository.findByAddress(intermediateWalletAddress);
        String onChainPaymentId = payment.getOnChainPaymentId();

        double penaltyRate = contract.getCancellationPenaltyRate();
        boolean isPastFreeWindow = payment.getCancellationDeadline() != null
                && LocalDateTime.now().isAfter(payment.getCancellationDeadline());

        if (penaltyRate > 0 && isPastFreeWindow) {
            // distributeAndRefund() : pénalité → bénéficiaires, reste → client (1 tx atomique)
            int penaltyBps = (int)(penaltyRate * 10_000);
            double penaltyAmount = payment.getAmount() * penaltyRate;
            double refundAmount  = payment.getAmount() - penaltyAmount;

            String txHash = escrowService.distributeAndRefund(
                    escrowContractAddress, onChainPaymentId, penaltyBps, relayerWallet);

            payment.setRefundedAmount(refundAmount);
            payment.setRetainedAmount(penaltyAmount);
            payment.setRefundTxHash(txHash);
            payment.setStatus("refunded_partial");
        } else {
            // refund() : remboursement intégral au client (1 tx)
            String txHash = escrowService.refund(escrowContractAddress, onChainPaymentId, relayerWallet);
            payment.setRefundedAmount(payment.getAmount());
            payment.setRetainedAmount(0.0);
            payment.setRefundTxHash(txHash);
            payment.setStatus("refunded");
        }

        payment.setCancellationReason(reason);
        payment = paymentRepository.save(payment);
        log.info("Paiement annulé: paymentCode={}, statut={}", paymentCode, payment.getStatus());
        return mapPaymentToResponse(payment);
    }

    // =========================================================================
    // LECTURE
    // =========================================================================

    @Override
    public PartnerPaymentResponse getPayment(String paymentCode) {
        return mapPaymentToResponse(findPaymentOrThrow(paymentCode));
    }

    @Override
    public List<PartnerPaymentResponse> getClientPayments(String clientUsername) {
        return paymentRepository.findByClientUsernameOrderByCreatedAtDesc(clientUsername)
                .stream().map(this::mapPaymentToResponse).collect(Collectors.toList());
    }

    @Override
    public List<PartnerPaymentResponse> getContractPayments(String contractCode, String partnerUsername) {
        PartnerContract contract = findContractOrThrow(contractCode);
        if (!contract.getPartnerUsername().equals(partnerUsername)) {
            throw new SecurityException("Accès non autorisé aux paiements de ce contrat");
        }
        return paymentRepository.findByContractIdOrderByCreatedAtDesc(contract.getId())
                .stream().map(this::mapPaymentToResponse).collect(Collectors.toList());
    }

    // =========================================================================
    // REDISTRIBUTION DES FONDS
    // =========================================================================

    public PartnerPaymentResponse executeDistribution(PartnerContractPayment payment) {
        PartnerContract contract = payment.getContract();
        Wallet relayerWallet = walletRepository.findByAddress(intermediateWalletAddress);
        boolean isDynamic = "DYNAMIC_ASSIGNMENT".equals(contract.getTriggerType());

        log.info("distribute() on-chain: paymentCode={}, onChainId={}, mode={}",
                payment.getPaymentCode(), payment.getOnChainPaymentId(), contract.getTriggerType());

        String txHash = escrowService.distribute(
                escrowContractAddress, payment.getOnChainPaymentId(), relayerWallet);

        boolean success = txHash != null;

        // Pour DYNAMIC_ASSIGNMENT, les distributions sont tracées depuis les bénéficiaires dynamiques
        if (isDynamic) {
            for (PartnerPaymentBeneficiary b : payment.getDynamicBeneficiaries()) {
                double amount = payment.getAmount() * b.getPercentage();
                payment.getDistributions().add(PartnerPaymentDistribution.builder()
                        .payment(payment)
                        .beneficiary(null)
                        .walletAddress(b.getWalletAddress())
                        .amount(amount)
                        .percentage(b.getPercentage())
                        .txHash(txHash)
                        .status(success ? "success" : "failed")
                        .executedAt(success ? LocalDateTime.now() : null)
                        .build());
            }
        } else {
            for (PartnerContractBeneficiary beneficiary : contract.getBeneficiaries()) {
                double amount = payment.getAmount() * beneficiary.getPercentage();
                payment.getDistributions().add(PartnerPaymentDistribution.builder()
                        .payment(payment)
                        .beneficiary(beneficiary)
                        .walletAddress(resolveTargetAddress(beneficiary))
                        .amount(amount)
                        .percentage(beneficiary.getPercentage())
                        .txHash(txHash)
                        .status(success ? "success" : "failed")
                        .executedAt(success ? LocalDateTime.now() : null)
                        .build());
            }
        }

        payment.setDistributionTxHashes(txHash != null ? txHash : "");
        payment.setStatus(success ? "distributed" : "failed");
        payment.setDistributedAt(success ? LocalDateTime.now() : null);
        payment = paymentRepository.save(payment);

        log.info("Distribution terminée: paymentCode={}, statut={}, txHash={}",
                payment.getPaymentCode(), payment.getStatus(), txHash);

        return mapPaymentToResponse(payment);
    }

    // =========================================================================
    // INITIALISATION DES MODES DE LIBÉRATION
    // =========================================================================

    private void initLiberationMode(PartnerContractPayment payment, PartnerContract contract,
                                     InitiatePartnerPaymentRequest request) {
        String mode = contract.getTriggerType();

        switch (mode) {
            case "QR_CODE" -> {
                String qrToken = UUID.randomUUID().toString();
                payment.setQrToken(qrToken);
                payment.setQrUrl(qrBaseUrl + "?token=" + qrToken);
                payment.setQrExpiresAt(LocalDateTime.now().plusHours(defaultQrExpirationHours));
            }
            case "REMOTE_CONFIRMATION" -> {
                String token = UUID.randomUUID().toString().replace("-", "");
                payment.setConfirmationToken(token);
                payment.setConfirmationUrl(confirmationBaseUrl + "?token=" + token);
                payment.setConfirmationTokenExpiresAt(LocalDateTime.now().plusHours(confirmationExpirationHours));
            }
            case "OTP" -> {
                payment.setOtpCode(generateOtp());
                payment.setOtpExpiresAt(LocalDateTime.now().plusMinutes(otpExpirationMinutes));
            }
            case "TIME_BASED" -> {
                int hours = contract.getAutoReleaseHours() != null ? contract.getAutoReleaseHours() : 24;
                LocalDateTime base = request.getServiceStartDate() != null
                        ? request.getServiceStartDate() : LocalDateTime.now();
                payment.setAutoReleaseScheduledAt(base.plusHours(hours));
            }
            case "DISPUTE_WINDOW" -> {
                int windowHours = contract.getDisputeWindowHours() != null ? contract.getDisputeWindowHours() : 48;
                payment.setAutoReleaseScheduledAt(LocalDateTime.now().plusHours(windowHours));
            }
            case "ADMIN_APPROVAL" -> payment.setAdminApprovalRequestedAt(LocalDateTime.now());
            case "DYNAMIC_ASSIGNMENT" -> {
                // Rien à initialiser — l'admin assignera les bénéficiaires après réception du paiement.
                // Une notification FCM sera envoyée à l'admin quand les USDC arrivent (voir scheduler).
            }
            case "WEBHOOK" -> {
                // l'URL et le secret viennent de la requête (déjà setté dans l'entité)
            }
            // MANUAL, DUAL_CONFIRMATION, GEOLOCATION n'ont pas d'init spécifique
        }
    }

    private void sendPostDepositNotification(PartnerContractPayment payment, PartnerContract contract) {
        String mode = contract.getTriggerType();
        String paymentCode = payment.getPaymentCode();

        switch (mode) {
            case "REMOTE_CONFIRMATION" ->
                sendNotification(payment.getClientUsername(),
                        "Confirmez votre réception",
                        "Vos fonds sont sécurisés. Cliquez sur le lien pour confirmer la réception du service.",
                        NotificationType.PAYMENT_CONFIRMATION_REQUESTED, paymentCode);

            case "OTP" ->
                sendNotification(payment.getClientUsername(),
                        "Code OTP : " + payment.getOtpCode(),
                        "Donnez ce code au prestataire à la livraison du service. Valable " + otpExpirationMinutes + " minutes.",
                        NotificationType.PAYMENT_OTP_SENT, paymentCode);

            case "DUAL_CONFIRMATION" -> {
                sendNotification(payment.getClientUsername(),
                        "Paiement en attente de double confirmation",
                        "Confirmez la réception du service pour libérer vos fonds.",
                        NotificationType.PAYMENT_DUAL_CONFIRM_PENDING, paymentCode);
                sendNotification(contract.getPartnerUsername(),
                        "Paiement en attente de double confirmation",
                        "Confirmez la livraison du service pour libérer les fonds.",
                        NotificationType.PAYMENT_DUAL_CONFIRM_PENDING, paymentCode);
            }
            case "TIME_BASED", "DISPUTE_WINDOW" ->
                sendNotification(payment.getClientUsername(),
                        "Libération automatique programmée",
                        "Vos fonds seront libérés automatiquement le "
                                + (payment.getAutoReleaseScheduledAt() != null ? payment.getAutoReleaseScheduledAt() : "date inconnue")
                                + ". Contestez avant si nécessaire.",
                        NotificationType.PAYMENT_AUTO_RELEASE_PENDING, paymentCode);

            case "GEOLOCATION" ->
                sendNotification(payment.getClientUsername(),
                        "Confirmez votre présence sur place",
                        "Activez la géolocalisation dans l'app pour confirmer votre présence et libérer les fonds.",
                        NotificationType.PAYMENT_LOCATION_CONFIRM_NEEDED, paymentCode);

            case "ADMIN_APPROVAL" -> {
                sendNotification(payment.getClientUsername(),
                        "Paiement en attente d'approbation",
                        "Votre paiement est en cours de vérification par notre équipe.",
                        NotificationType.PAYMENT_ADMIN_APPROVAL_NEEDED, paymentCode);
                sendNotification(defaultAdminUsername,
                        "Approbation requise",
                        "Le paiement " + paymentCode + " nécessite votre approbation.",
                        NotificationType.PAYMENT_ADMIN_APPROVAL_NEEDED, paymentCode);
            }
        }
    }

    // =========================================================================
    // MÉTHODES UTILITAIRES
    // =========================================================================

    private void validateTriggerTypeConfig(String triggerType, CreatePartnerContractRequest req) {
        switch (triggerType) {
            case "TIME_BASED" -> {
                if (req.getAutoReleaseHours() == null || req.getAutoReleaseHours() < 1)
                    throw new IllegalArgumentException("autoReleaseHours est requis pour le mode TIME_BASED (>= 1)");
            }
            case "DISPUTE_WINDOW" -> {
                if (req.getDisputeWindowHours() == null || req.getDisputeWindowHours() < 1)
                    throw new IllegalArgumentException("disputeWindowHours est requis pour le mode DISPUTE_WINDOW (>= 1)");
            }
            case "GEOLOCATION" -> {
                if (req.getExpectedLatitude() == null || req.getExpectedLongitude() == null)
                    throw new IllegalArgumentException("expectedLatitude et expectedLongitude sont requis pour le mode GEOLOCATION");
            }
        }
    }

    private void assertPending(PartnerContractPayment payment) {
        String s = payment.getStatus();
        if (!"pending_condition".equals(s) && !"beneficiaries_assigned".equals(s)) {
            throw new IllegalStateException("Ce paiement ne peut plus être validé (statut: " + s + ")");
        }
    }

    private String generateOtp() {
        SecureRandom random = new SecureRandom();
        return String.valueOf(100000 + random.nextInt(900000));
    }

    private double calculateDistanceMeters(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6_371_000.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                  * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return R * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private void sendNotification(String username, String title, String body,
                                   NotificationType type, String referenceId) {
        try {
            fcmNotificationService.sendToUser(username, PushNotificationRequest.builder()
                    .title(title).body(body).type(type).referenceId(referenceId).build());
        } catch (Exception e) {
            log.warn("Échec notification push pour {}: {}", username, e.getMessage());
        }
    }

    private PartnerContract findContractOrThrow(String contractCode) {
        return contractRepository.findByContractCode(contractCode)
                .orElseThrow(() -> new IllegalArgumentException("Contrat introuvable : " + contractCode));
    }

    private PartnerContractPayment findPaymentOrThrow(String paymentCode) {
        return paymentRepository.findByPaymentCode(paymentCode)
                .orElseThrow(() -> new IllegalArgumentException("Paiement introuvable : " + paymentCode));
    }

    private String buildFullName(Users user, String fallback) {
        if (user == null) return fallback;
        String fn = user.getFirstname() != null ? user.getFirstname() : "";
        String ln = user.getLastname()  != null ? user.getLastname()  : "";
        String full = (fn + " " + ln).trim();
        return full.isBlank() ? fallback : full;
    }

    private String resolveWalletAddress(String username) {
        List<Users> users = userRepository.findAllByUsername(username);
        if (users.isEmpty()) throw new IllegalArgumentException("Utilisateur introuvable : " + username);
        List<Wallet> wallets = walletRepository.findWalletByUsers(users.get(0));
        if (wallets.isEmpty()) throw new IllegalStateException("Wallet introuvable pour : " + username);
        return wallets.get(0).getAddress();
    }

    private String resolveTargetAddress(PartnerContractBeneficiary b) {
        if (b.getWalletAddress() != null && !b.getWalletAddress().isBlank()) return b.getWalletAddress();
        if (b.getUsername() != null && !b.getUsername().isBlank()) return resolveWalletAddress(b.getUsername());
        throw new IllegalStateException("Impossible de résoudre l'adresse pour : " + b.getLabel());
    }

    private boolean verifyWebhookSignature(String payload, String signature, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            String expected = HexFormat.of().formatHex(hash);
            return expected.equalsIgnoreCase(signature);
        } catch (Exception e) {
            log.warn("Erreur vérification signature webhook: {}", e.getMessage());
            return false;
        }
    }

    // =========================================================================
    // MAPPING
    // =========================================================================

    private PartnerContractResponse mapContractToResponse(PartnerContract c) {
        return PartnerContractResponse.builder()
                .id(c.getId())
                .contractCode(c.getContractCode())
                .partnerUsername(c.getPartnerUsername())
                .name(c.getName())
                .description(c.getDescription())
                .serviceType(c.getServiceType())
                .triggerType(c.getTriggerType())
                .paymentLinkType(c.getPaymentLinkType())
                .cancellationPenaltyRate(c.getCancellationPenaltyRate())
                .freeCancellationHours(c.getFreeCancellationHours())
                .autoReleaseHours(c.getAutoReleaseHours())
                .disputeWindowHours(c.getDisputeWindowHours())
                .expectedLatitude(c.getExpectedLatitude())
                .expectedLongitude(c.getExpectedLongitude())
                .geolocationRadiusMeters(c.getGeolocationRadiusMeters())
                .active(c.getActive())
                .createdAt(c.getCreatedAt())
                .beneficiaries(c.getBeneficiaries().stream().map(b ->
                        PartnerContractResponse.BeneficiaryResponse.builder()
                                .id(b.getId())
                                .beneficiaryType(b.getBeneficiaryType())
                                .label(b.getLabel())
                                .walletAddress(b.getWalletAddress())
                                .username(b.getUsername())
                                .percentage(b.getPercentage())
                                .executionOrder(b.getExecutionOrder())
                                .build()
                ).collect(Collectors.toList()))
                .build();
    }

    private PartnerPaymentResponse mapPaymentToResponse(PartnerContractPayment p) {
        String mode = p.getContract().getTriggerType();

        PartnerPaymentResponse.LiberationInfo liberationInfo = PartnerPaymentResponse.LiberationInfo.builder()
                .mode(mode)
                .confirmationUrl(p.getConfirmationUrl())
                .confirmationExpiry(p.getConfirmationTokenExpiresAt())
                .clientConfirmed(p.getConfirmedByClientAt() != null)
                .otpExpiry(p.getOtpExpiresAt())
                .otpUsed(p.getOtpUsedAt() != null)
                .providerConfirmed(p.getProviderConfirmedAt() != null)
                .clientDualConfirmed(p.getClientDualConfirmedAt() != null)
                .autoReleaseScheduledAt(p.getAutoReleaseScheduledAt())
                .disputed(p.getDisputedAt() != null)
                .locationConfirmed(p.getLocationConfirmedAt() != null)
                .adminApprovalRequestedAt(p.getAdminApprovalRequestedAt())
                .adminApproved(p.getAdminApprovedAt() != null)
                .build();

        return PartnerPaymentResponse.builder()
                .paymentCode(p.getPaymentCode())
                .contractCode(p.getContract().getContractCode())
                .contractName(p.getContract().getName())
                .clientUsername(p.getClientUsername())
                .amount(p.getAmount())
                .currency(p.getCurrency())
                .status(p.getStatus())
                .paymentAddress(p.getCreate2WalletAddress())
                .create2Status(p.getCreate2Status())
                .qrToken(p.getQrToken())
                .qrUrl(p.getQrUrl())
                .qrExpiresAt(p.getQrExpiresAt())
                .webhookUrl(p.getWebhookUrl())
                .serviceStartDate(p.getServiceStartDate())
                .cancellationDeadline(p.getCancellationDeadline())
                .distributedAt(p.getDistributedAt())
                .createdAt(p.getCreatedAt())
                .liberationInfo(liberationInfo)
                .distributions(p.getDistributions().stream().map(d ->
                        PartnerPaymentResponse.DistributionDetail.builder()
                                .beneficiaryLabel(d.getBeneficiary() != null ? d.getBeneficiary().getLabel() : d.getWalletAddress())
                                .beneficiaryType(d.getBeneficiary() != null ? d.getBeneficiary().getBeneficiaryType() : "DYNAMIC")
                                .walletAddress(d.getWalletAddress())
                                .amount(d.getAmount())
                                .percentage(d.getPercentage())
                                .txHash(d.getTxHash())
                                .status(d.getStatus())
                                .executedAt(d.getExecutedAt())
                                .build()
                ).collect(Collectors.toList()))
                .build();
    }
}
