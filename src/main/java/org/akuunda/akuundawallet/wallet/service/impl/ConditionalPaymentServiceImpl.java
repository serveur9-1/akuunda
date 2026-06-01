package org.akuunda.akuundawallet.wallet.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.dao.ConditionalPaymentRepository;
import org.akuunda.akuundawallet.wallet.api.dao.ConditionalPaymentRuleRepository;
import org.akuunda.akuundawallet.wallet.api.dao.OneTimePaymentLinkRepository;
import org.akuunda.akuundawallet.wallet.api.dao.OperationRepository;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.api.dto.CancelConditionalPaymentRequest;
import org.akuunda.akuundawallet.wallet.api.dto.ConditionalPaymentRequest;
import org.akuunda.akuundawallet.wallet.api.dto.ConditionalPaymentResponse;
import org.akuunda.akuundawallet.wallet.api.dto.QRCodeValidationRequest;
import org.akuunda.akuundawallet.wallet.api.entities.ConditionalPayment;
import org.akuunda.akuundawallet.wallet.api.entities.ConditionalPaymentRule;
import org.akuunda.akuundawallet.wallet.api.entities.OneTimePaymentLink;
import org.akuunda.akuundawallet.wallet.api.entities.Operation;
import org.akuunda.akuundawallet.wallet.api.entities.QRCode;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.wallet.service.ConditionalPaymentService;
import org.akuunda.akuundawallet.wallet.service.QRCodeService;
import org.akuunda.akuundawallet.wallet.service.SmartContractEscrowService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.CurrencyFreaksClientService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConditionalPaymentServiceImpl implements ConditionalPaymentService {

    private static final String EXTERNAL_PAYER_USERNAME = "external_payer";

    private final ConditionalPaymentRepository conditionalPaymentRepository;
    private final OneTimePaymentLinkRepository oneTimePaymentLinkRepository;
    private final QRCodeService qrCodeService;
    private final SmartContractEscrowService smartContractEscrowService;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final OperationRepository operationRepository;
    private final ConditionalPaymentRuleRepository conditionalPaymentRuleRepository;
    private final CurrencyFreaksClientService currencyFreaksClientService;

    @Value("${akuunda.escrow.contract.wallet.address:}")
    private String smartContractWalletAddress;

    @Value("${akuunda.intermediate.wallet.id:}")
    private String intermediateWalletId;

    @Value("${akuunda.intermediate.wallet.address:}")
    private String intermediateWalletAddress;

    @Override
    public ResponseEntity<ConditionalPaymentResponse> createConditionalPayment(String clientUsername, ConditionalPaymentRequest request) {
        log.info("🔵 Création d'un paiement conditionnel: client={}, vendor={}, amount={}",
                clientUsername, request.getVendorUsername(), request.getAmount());

        try {
            Users client = userRepository.getUsersByUsername(clientUsername);
            if (client == null) {
                return errorResponse(HttpStatus.NOT_FOUND, "Client non trouvé");
            }

            Users vendor = userRepository.getUsersByUsername(request.getVendorUsername());
            if (vendor == null) {
                return errorResponse(HttpStatus.NOT_FOUND, "Vendeur non trouvé");
            }

            Wallet clientWallet = walletRepository.findByUsers(client);
            if (clientWallet == null) {
                return errorResponse(HttpStatus.NOT_FOUND, "Wallet client non trouvé");
            }

            Wallet vendorWallet = walletRepository.findByUsers(vendor);
            if (vendorWallet == null) {
                return errorResponse(HttpStatus.NOT_FOUND, "Wallet vendeur non trouvé");
            }

            String escrowAddress = smartContractWalletAddress != null && !smartContractWalletAddress.isEmpty()
                    ? smartContractWalletAddress
                    : null;
            if (escrowAddress == null || escrowAddress.isEmpty() || "0x...".equals(escrowAddress) || escrowAddress.length() < 42) {
                log.error("❌ Adresse du smart contract non configurée ou invalide (placeholder). Configurer akuunda.escrow.contract.wallet.address avec l'adresse réelle du smart contract déployé.");
                return errorResponse(HttpStatus.SERVICE_UNAVAILABLE,
                        "Paiement conditionnel non disponible : adresse du smart contract non configurée. Configurer akuunda.escrow.contract.wallet.address.");
            }

            Double amountInUSDC = request.getAmount();
            String requestCurrency = request.getCurrency();
            String clientLocalCurrency = clientWallet.getCurrency() != null ? clientWallet.getCurrency().getCurrencyCode() : null;

            if (requestCurrency != null && !requestCurrency.isEmpty() && !requestCurrency.equalsIgnoreCase("USDC")) {
                log.info("🔄 Conversion {} {} → USDC pour paiement conditionnel", request.getAmount(), requestCurrency);
                try {
                    var conversionResponse = currencyFreaksClientService.convertCurrency(requestCurrency, "USD", request.getAmount());
                    if (conversionResponse != null && conversionResponse.getStatusCode().is2xxSuccessful() && conversionResponse.getBody() != null) {
                        String convertedAmountStr = conversionResponse.getBody().getConvertedAmount();
                        if (convertedAmountStr != null && !convertedAmountStr.isEmpty()) {
                            amountInUSDC = Double.parseDouble(convertedAmountStr);
                            log.info("✅ Conversion réussie: {} {} → {} USDC", request.getAmount(), requestCurrency, amountInUSDC);
                        } else {
                            log.warn("⚠️ Réponse de conversion vide, utilisation du montant original");
                        }
                    } else {
                        log.warn("⚠️ Échec de la conversion, utilisation du montant original en USDC");
                    }
                } catch (Exception e) {
                    log.error("❌ Erreur lors de la conversion devise: {}", e.getMessage(), e);
                    return errorResponse(HttpStatus.BAD_REQUEST, "Erreur lors de la conversion de devise: " + e.getMessage());
                }
            } else if (requestCurrency == null || requestCurrency.isEmpty()) {
                if (clientLocalCurrency != null && !clientLocalCurrency.equalsIgnoreCase("USDC")) {
                    log.info("🔄 Aucune devise fournie, conversion depuis devise locale du client: {} {} → USDC", request.getAmount(), clientLocalCurrency);
                    try {
                        var conversionResponse = currencyFreaksClientService.convertCurrency(clientLocalCurrency, "USD", request.getAmount());
                        if (conversionResponse != null && conversionResponse.getStatusCode().is2xxSuccessful() && conversionResponse.getBody() != null) {
                            String convertedAmountStr = conversionResponse.getBody().getConvertedAmount();
                            if (convertedAmountStr != null && !convertedAmountStr.isEmpty()) {
                                amountInUSDC = Double.parseDouble(convertedAmountStr);
                                log.info("✅ Conversion réussie: {} {} → {} USDC", request.getAmount(), clientLocalCurrency, amountInUSDC);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("⚠️ Erreur lors de la conversion depuis devise locale du client: {}", e.getMessage());
                    }
                }
            }

            Wallet intermediateWallet;
            try {
                intermediateWallet = getOrCreateAdminWallet();
            } catch (IllegalStateException e) {
                log.error("❌ {}", e.getMessage());
                return errorResponse(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
            }

            if (clientWallet.getBalance() < amountInUSDC) {
                log.warn("Solde insuffisant pour le client: balance={} USDC, required={} USDC",
                        clientWallet.getBalance(), amountInUSDC);
                return errorResponse(HttpStatus.BAD_REQUEST, "Solde insuffisant");
            }

            clientWallet.setBalance(clientWallet.getBalance() - amountInUSDC);
            Double amountInLocalCurrency = request.getAmount();
            if (clientLocalCurrency != null && !clientLocalCurrency.equalsIgnoreCase("USDC")) {
                if (requestCurrency == null || requestCurrency.isEmpty() || requestCurrency.equalsIgnoreCase("USDC")) {
                    try {
                        var reverseConversion = currencyFreaksClientService.convertCurrency("USD", clientLocalCurrency, amountInUSDC);
                        if (reverseConversion != null && reverseConversion.getStatusCode().is2xxSuccessful() && reverseConversion.getBody() != null) {
                            String convertedAmountStr = reverseConversion.getBody().getConvertedAmount();
                            if (convertedAmountStr != null && !convertedAmountStr.isEmpty()) {
                                amountInLocalCurrency = Double.parseDouble(convertedAmountStr);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("⚠️ Erreur conversion inverse USDC → devise locale: {}", e.getMessage());
                    }
                }
            }
            clientWallet.setDeviseBalance(clientWallet.getDeviseBalance() - amountInLocalCurrency);
            walletRepository.save(clientWallet);

            // ✅ Opération client DEBIT → contrepartie = vendeur
            Operation clientOperation = createOperation("DEBIT", clientUsername, amountInUSDC,
                    "CONDITIONAL_PAYMENT", "pending_condition",
                    vendor.getUsername(), resolveDisplayName(vendor));
            clientOperation = operationRepository.save(clientOperation);

            String paymentCode = "CP-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String escrowContractAddress = escrowAddress;
            Long serviceStartDateTimestamp = request.getServiceStartDate() != null ?
                    request.getServiceStartDate().toEpochSecond(java.time.ZoneOffset.UTC) :
                    System.currentTimeMillis() / 1000;

            String depositTxHash = smartContractEscrowService.depositToEscrow(
                    escrowContractAddress, amountInUSDC, clientWallet, intermediateWallet, escrowContractAddress, null,
                    paymentCode, vendorWallet.getAddress(), serviceStartDateTimestamp);

            boolean depositFailed = depositTxHash != null &&
                    (depositTxHash.startsWith("ESCROW-DEPOSIT-FAILED") ||
                     depositTxHash.startsWith("ESCROW-DEPOSIT-ERROR"));

            boolean depositPending = depositTxHash != null && depositTxHash.startsWith("ESCROW-DEPOSIT-DB");
            boolean depositSuccess = depositTxHash != null && depositTxHash.startsWith("0x");

            if (depositFailed) {
                log.error("❌ Transaction Venly échouée, remboursement du client en BDD: depositTxHash={}", depositTxHash);
                clientWallet.setBalance(clientWallet.getBalance() + amountInUSDC);
                clientWallet.setDeviseBalance(clientWallet.getDeviseBalance() + amountInLocalCurrency);
                walletRepository.save(clientWallet);
                if (clientOperation != null) {
                    operationRepository.delete(clientOperation);
                }
                return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Échec du transfert blockchain. Le montant a été remboursé. Hash: " + depositTxHash);
            }

            if (depositSuccess) {
                Wallet smartContractWallet = getOrCreateSmartContractWallet(escrowContractAddress);
                smartContractWallet.setBalance(smartContractWallet.getBalance() + amountInUSDC);
                smartContractWallet.setDeviseBalance(smartContractWallet.getDeviseBalance() + amountInUSDC);
                walletRepository.save(smartContractWallet);
                log.info("✅ Transaction Venly réussie immédiatement, soldes mis à jour: depositTxHash={}", depositTxHash);
            } else if (depositPending) {
                log.info("ℹ️ Paiement créé avec hash temporaire (DB). Transaction Venly à exécuter via executeDeposit avec PIN client: depositTxHash={}", depositTxHash);
            }

            ConditionalPayment payment = ConditionalPayment.builder()
                    .paymentCode(paymentCode)
                    .client(client)
                    .vendor(vendor)
                    .serviceType(request.getServiceType())
                    .description(request.getDescription())
                    .amount(amountInUSDC)
                    .currency("USDC")
                    .status("pending_condition")
                    .escrowContractAddress(escrowContractAddress)
                    .depositTransactionHash(depositTxHash)
                    .serviceStartDate(request.getServiceStartDate())
                    .cancellationDeadline(request.getCancellationDeadline())
                    .intermediateWallet(intermediateWallet)
                    .clientWallet(clientWallet)
                    .vendorWallet(vendorWallet)
                    .clientOperationId(clientOperation.getId())
                    .build();

            payment = conditionalPaymentRepository.save(payment);

            QRCode qrCode = qrCodeService.generateQRCode(payment.getId(), 24);

            log.info("✅ Paiement conditionnel créé avec succès: paymentCode={}", paymentCode);

            return ResponseEntity.ok(mapToResponse(payment, qrCode));

        } catch (IllegalStateException e) {
            log.error("❌ Erreur de configuration lors de la création du paiement conditionnel: {}", e.getMessage(), e);
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("❌ Erreur de validation lors de la création du paiement conditionnel: {}", e.getMessage(), e);
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("❌ Erreur inattendue lors de la création du paiement conditionnel: {}", e.getMessage(), e);
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur serveur : " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    private ResponseEntity<ConditionalPaymentResponse> errorResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(ConditionalPaymentResponse.builder().error(message).build());
    }

    @Override
    public ResponseEntity<ConditionalPaymentResponse> validateQRCode(QRCodeValidationRequest request) {
        log.info("🔵 Validation du QR code: token={}", request.getQrCodeToken());

        try {
            QRCode qrCode = qrCodeService.validateQRCode(request.getQrCodeToken(), request.getScannedBy());

            ConditionalPayment payment = qrCode.getConditionalPayment();
            if (!"pending_condition".equals(payment.getStatus())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ConditionalPaymentResponse.builder()
                                .error("Le paiement n'est pas en statut 'pending_condition'. Statut actuel: " + payment.getStatus())
                                .build());
            }

            String depositHash = payment.getDepositTransactionHash();
            if (depositHash == null || depositHash.startsWith("ESCROW-DEPOSIT-DB")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ConditionalPaymentResponse.builder()
                                .error("Le dépôt blockchain n'a pas encore été effectué. Veuillez d'abord appeler executeDeposit avec le PIN du client.")
                                .build());
            }

            if (depositHash.startsWith("ESCROW-DEPOSIT-FAILED") || depositHash.startsWith("ESCROW-DEPOSIT-ERROR")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ConditionalPaymentResponse.builder()
                                .error("Le dépôt blockchain a échoué. Hash: " + depositHash + ". Veuillez réessayer avec executeDeposit.")
                                .build());
            }

            payment.setStatus("condition_validated");
            payment.setServiceActualStartDate(LocalDateTime.now());
            payment = conditionalPaymentRepository.save(payment);

            String scannedByUsername = request.getScannedBy();
            if (scannedByUsername == null || scannedByUsername.isEmpty()) {
                scannedByUsername = qrCode.getScannedBy();
            }

            if (scannedByUsername == null || scannedByUsername.isEmpty()) {
                log.error("❌ Impossible de déterminer qui a scanné le QR code");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(null);
            }

            Users scannerUser = userRepository.getUsersByUsername(scannedByUsername);
            if (scannerUser == null) {
                log.error("❌ Utilisateur qui a scanné non trouvé: {}", scannedByUsername);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(null);
            }

            Wallet recipientWallet = walletRepository.findByUsers(scannerUser);
            if (recipientWallet == null) {
                log.error("❌ Wallet de l'utilisateur qui a scanné non trouvé: {}", scannedByUsername);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(null);
            }

            Wallet smartContractWallet = getOrCreateSmartContractWallet(payment.getEscrowContractAddress());

            log.info("📤 Smart contract release() → directement vers vendeur: scannedBy={}, recipientWallet={}",
                    scannedByUsername, recipientWallet.getAddress());

            String releaseTxHash = smartContractEscrowService.releaseFunds(
                    payment.getEscrowContractAddress(),
                    payment.getAmount(),
                    recipientWallet.getAddress(),
                    payment.getPaymentCode(),
                    payment.getIntermediateWallet()
            );

            boolean releaseSuccess = releaseTxHash != null &&
                    !releaseTxHash.startsWith("ESCROW-RELEASE-FAILED") &&
                    !releaseTxHash.startsWith("ESCROW-RELEASE-ERROR");

            if (!releaseSuccess) {
                log.error("❌ Transaction Venly de libération échouée: releaseTxHash={}. Le paiement reste en statut 'condition_validated'.", releaseTxHash);
                payment.setReleaseTransactionHash(releaseTxHash);
                payment = conditionalPaymentRepository.save(payment);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ConditionalPaymentResponse.builder()
                                .error("Échec du transfert blockchain lors de la libération. Hash: " + releaseTxHash)
                                .build());
            }

            log.info("✅ Étape 1 OK: releaseTxHash={}", releaseTxHash);

            payment.setReleaseTransactionHash(releaseTxHash);
            payment.setStatus("released");
            payment.setReleasedAt(LocalDateTime.now());

            populateReceivedAmount(payment);

            payment = conditionalPaymentRepository.save(payment);

            smartContractWallet.setBalance(smartContractWallet.getBalance() - payment.getAmount());
            smartContractWallet.setDeviseBalance(smartContractWallet.getDeviseBalance() - payment.getAmount());
            walletRepository.save(smartContractWallet);

            recipientWallet.setBalance(recipientWallet.getBalance() + payment.getAmount());
            recipientWallet.setDeviseBalance(recipientWallet.getDeviseBalance() + payment.getAmount());
            walletRepository.save(recipientWallet);

            // ✅ CORRIGÉ : Utiliser la bonne designation selon payeur interne ou externe
            String counterpartUsername = payment.getClient() != null ? payment.getClient().getUsername() : EXTERNAL_PAYER_USERNAME;
            String counterpartDisplayName = payment.getClient() != null ? resolveDisplayName(payment.getClient()) : resolveExternalPayerName(payment);
            String creditDesignation = payment.getClient() != null
                    ? "CONDITIONAL_PAYMENT_RECEIVED"
                    : "CONDITIONAL_PAYMENT_RECEIVED_EXTERNAL";
            Operation recipientOperation = createOperation("CREDIT", scannedByUsername,
                    payment.getAmount(), creditDesignation, "VALIDEE",
                    counterpartUsername, counterpartDisplayName);
            recipientOperation = operationRepository.save(recipientOperation);
            payment.setVendorOperationId(recipientOperation.getId());
            payment = conditionalPaymentRepository.save(payment);

            log.info("✅ Transaction Venly de libération réussie, soldes mis à jour: releaseTxHash={}", releaseTxHash);

            updateParentPaymentLinkStatus(payment.getId(), "PAID");

            log.info("✅ QR code validé et fonds libérés: paymentCode={}", payment.getPaymentCode());

            return ResponseEntity.ok(mapToResponse(payment, qrCode));

        } catch (IllegalArgumentException | IllegalStateException e) {
            log.error("❌ Erreur de validation du QR code: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(null);
        } catch (Exception e) {
            log.error("❌ Erreur lors de la validation du QR code", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }

    @Override
    public ResponseEntity<ConditionalPaymentResponse> cancelPayment(String paymentCode, CancelConditionalPaymentRequest request) {
        log.info("🔵 Annulation du paiement conditionnel: paymentCode={}", paymentCode);

        try {
            ConditionalPayment payment = conditionalPaymentRepository.findByPaymentCode(paymentCode)
                    .orElseThrow(() -> new IllegalArgumentException("Paiement conditionnel non trouvé"));

            if (!"pending_condition".equals(payment.getStatus())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(null);
            }

            RefundCalculation refundCalc = calculateRefund(payment);

            Wallet smartContractWallet = getOrCreateSmartContractWallet(payment.getEscrowContractAddress());

            if (refundCalc.isFullRefund()) {
                log.info("📤 Smart contract refund() → directement vers le client");
                Wallet refundTargetWallet = payment.getClientWallet() != null
                        ? payment.getClientWallet()
                        : payment.getIntermediateWallet();
                String refundTxHash = smartContractEscrowService.refundToClient(
                        payment.getEscrowContractAddress(),
                        payment.getAmount(),
                        refundTargetWallet.getAddress(),
                        payment.getPaymentCode(),
                        payment.getIntermediateWallet()
                );

                boolean refundSuccess = refundTxHash != null &&
                        !refundTxHash.startsWith("ESCROW-REFUND-FAILED") &&
                        !refundTxHash.startsWith("ESCROW-REFUND-ERROR");

                if (!refundSuccess) {
                    log.error("❌ Étape 1 échouée: refundTxHash={}. Le paiement reste en statut 'pending_condition'.", refundTxHash);
                    payment.setRefundTransactionHash(refundTxHash);
                    conditionalPaymentRepository.save(payment);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ConditionalPaymentResponse.builder()
                                    .error("Échec du remboursement smart contract. Hash: " + refundTxHash)
                                    .build());
                }

                log.info("✅ Étape 1 OK: refundTxHash={}", refundTxHash);

                payment.setRefundTransactionHash(refundTxHash);
                payment.setStatus("refunded");
                payment.setRefundedAmount(payment.getAmount());
                payment.setRetainedAmount(0.0);
                payment.setCancellationReason(request.getReason());

                smartContractWallet.setBalance(smartContractWallet.getBalance() - payment.getAmount());
                smartContractWallet.setDeviseBalance(smartContractWallet.getDeviseBalance() - payment.getAmount());
                walletRepository.save(smartContractWallet);

                refundTargetWallet.setBalance(refundTargetWallet.getBalance() + payment.getAmount());
                refundTargetWallet.setDeviseBalance(refundTargetWallet.getDeviseBalance() + payment.getAmount());
                walletRepository.save(refundTargetWallet);

            } else {
                log.info("📤 Smart contract partialRefund() → directement vers vendeur et client");
                Wallet refundTargetWallet = payment.getClientWallet() != null
                        ? payment.getClientWallet()
                        : payment.getIntermediateWallet();
                String refundTxHash = smartContractEscrowService.partialRefund(
                        payment.getEscrowContractAddress(),
                        refundCalc.getVendorAmount(),
                        refundCalc.getClientAmount(),
                        payment.getVendorWallet().getAddress(),
                        refundTargetWallet.getAddress(),
                        payment.getPaymentCode(),
                        payment.getIntermediateWallet()
                );

                boolean refundSuccess = refundTxHash != null &&
                        !refundTxHash.startsWith("ESCROW-REFUND-FAILED") &&
                        !refundTxHash.startsWith("ESCROW-REFUND-ERROR");

                if (!refundSuccess) {
                    log.error("❌ partialRefundTxHash={}. Le paiement reste en statut 'pending_condition'.", refundTxHash);
                    payment.setRefundTransactionHash(refundTxHash);
                    conditionalPaymentRepository.save(payment);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(ConditionalPaymentResponse.builder()
                                    .error("Échec du remboursement partiel smart contract. Hash: " + refundTxHash)
                                    .build());
                }

                log.info("✅ partialRefundTxHash={}", refundTxHash);

                payment.setRefundTransactionHash(refundTxHash);
                payment.setStatus("refunded_partial");
                payment.setRefundedAmount(refundCalc.getClientAmount());
                payment.setRetainedAmount(refundCalc.getVendorAmount());
                payment.setCancellationReason(request.getReason());

                Double totalRefunded = refundCalc.getVendorAmount() + refundCalc.getClientAmount();
                smartContractWallet.setBalance(smartContractWallet.getBalance() - totalRefunded);
                smartContractWallet.setDeviseBalance(smartContractWallet.getDeviseBalance() - totalRefunded);
                walletRepository.save(smartContractWallet);

                refundTargetWallet.setBalance(refundTargetWallet.getBalance() + refundCalc.getClientAmount());
                refundTargetWallet.setDeviseBalance(refundTargetWallet.getDeviseBalance() + refundCalc.getClientAmount());
                walletRepository.save(refundTargetWallet);

                Wallet vendorWallet = payment.getVendorWallet();
                vendorWallet.setBalance(vendorWallet.getBalance() + refundCalc.getVendorAmount());
                vendorWallet.setDeviseBalance(vendorWallet.getDeviseBalance() + refundCalc.getVendorAmount());
                walletRepository.save(vendorWallet);

                // ✅ Opération vendeur CREDIT (pénalité) → contrepartie = client
                String cancelCounterpartUsername = payment.getClient() != null ? payment.getClient().getUsername() : EXTERNAL_PAYER_USERNAME;
                String cancelCounterpartDisplayName = payment.getClient() != null ? resolveDisplayName(payment.getClient()) : resolveExternalPayerName(payment);
                Operation vendorOperation = createOperation("CREDIT", payment.getVendor().getUsername(),
                        refundCalc.getVendorAmount(), "CONDITIONAL_PAYMENT_CANCELLED", "VALIDEE",
                        cancelCounterpartUsername, cancelCounterpartDisplayName);
                vendorOperation = operationRepository.save(vendorOperation);
            }

            if (payment.getClient() != null) {
                // ✅ Opération client CREDIT (remboursement) → contrepartie = vendeur
                Operation refundOperation = createOperation("CREDIT", payment.getClient().getUsername(),
                        refundCalc.getClientAmount(), "CONDITIONAL_PAYMENT_REFUND", "VALIDEE",
                        payment.getVendor().getUsername(), resolveDisplayName(payment.getVendor()));
                refundOperation = operationRepository.save(refundOperation);
                payment.setRefundOperationId(refundOperation.getId());
            }

            payment = conditionalPaymentRepository.save(payment);

            String linkStatus = refundCalc.isFullRefund() ? "REFUNDED" : "REFUNDED_PARTIAL";
            updateParentPaymentLinkStatus(payment.getId(), linkStatus);

            log.info("✅ Paiement conditionnel annulé: paymentCode={}, refunded={}, retained={}",
                    paymentCode, refundCalc.getClientAmount(), refundCalc.getVendorAmount());

            return ResponseEntity.ok(mapToResponse(payment, null));

        } catch (IllegalArgumentException e) {
            log.error("❌ Erreur d'annulation: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(null);
        } catch (Exception e) {
            log.error("❌ Erreur lors de l'annulation du paiement conditionnel", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }

    @Override
    public ResponseEntity<ConditionalPaymentResponse> getPaymentByCode(String paymentCode) {
        ConditionalPayment payment = conditionalPaymentRepository.findByPaymentCode(paymentCode)
                .orElse(null);

        if (payment == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(null);
        }

        QRCode qrCode = qrCodeService.getQRCodeByPaymentId(payment.getId());

        return ResponseEntity.ok(mapToResponse(payment, qrCode));
    }

    @Override
    public ResponseEntity<List<ConditionalPaymentResponse>> getUserPayments(String username) {
        Users user = userRepository.getUsersByUsername(username);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(null);
        }

        List<ConditionalPayment> payments = conditionalPaymentRepository.findByClientOrVendor(user);
        List<ConditionalPaymentResponse> responses = payments.stream()
                .map(p -> mapToResponse(p, null))
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    @Override
    public ResponseEntity<ConditionalPaymentResponse> releaseFunds(String paymentCode) {
        log.info("🔵 Libération manuelle des fonds: paymentCode={}", paymentCode);

        try {
            ConditionalPayment payment = conditionalPaymentRepository.findByPaymentCode(paymentCode)
                    .orElseThrow(() -> new IllegalArgumentException("Paiement conditionnel non trouvé"));

            if (!"condition_validated".equals(payment.getStatus())) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(null);
            }

            QRCode qrCode = qrCodeService.getQRCodeByPaymentId(payment.getId());
            if (qrCode == null || qrCode.getScannedBy() == null || qrCode.getScannedBy().isEmpty()) {
                log.error("❌ Impossible de déterminer qui a scanné le QR code pour la libération manuelle");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(null);
            }

            Users scannerUser = userRepository.getUsersByUsername(qrCode.getScannedBy());
            if (scannerUser == null) {
                log.error("❌ Utilisateur qui a scanné non trouvé: {}", qrCode.getScannedBy());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(null);
            }

            Wallet recipientWallet = walletRepository.findByUsers(scannerUser);
            if (recipientWallet == null) {
                log.error("❌ Wallet de l'utilisateur qui a scanné non trouvé: {}", qrCode.getScannedBy());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(null);
            }

            Wallet smartContractWallet = getOrCreateSmartContractWallet(payment.getEscrowContractAddress());

            log.info("📤 Smart contract release() signé par wallet admin: scannedBy={}, recipientWallet={}",
                    qrCode.getScannedBy(), recipientWallet.getAddress());

            String releaseTxHash = smartContractEscrowService.releaseFunds(
                    payment.getEscrowContractAddress(),
                    payment.getAmount(),
                    recipientWallet.getAddress(),
                    payment.getPaymentCode(),
                    payment.getIntermediateWallet()
            );

            boolean releaseSuccess = releaseTxHash != null &&
                    !releaseTxHash.startsWith("ESCROW-RELEASE-FAILED") &&
                    !releaseTxHash.startsWith("ESCROW-RELEASE-ERROR");

            payment.setReleaseTransactionHash(releaseTxHash);

            if (!releaseSuccess) {
                log.error("❌ Transaction Venly de libération échouée lors du retry: releaseTxHash={}. Le paiement reste en statut '{}'.",
                        releaseTxHash, payment.getStatus());
                payment = conditionalPaymentRepository.save(payment);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(ConditionalPaymentResponse.builder()
                                .error("Échec du transfert blockchain lors de la libération. Hash: " + releaseTxHash + ". Vous pouvez réessayer avec /release.")
                                .build());
            }

            log.info("✅ releaseTxHash={}", releaseTxHash);

            payment.setStatus("released");
            payment.setReleasedAt(LocalDateTime.now());

            populateReceivedAmount(payment);

            payment = conditionalPaymentRepository.save(payment);

            smartContractWallet.setBalance(smartContractWallet.getBalance() - payment.getAmount());
            smartContractWallet.setDeviseBalance(smartContractWallet.getDeviseBalance() - payment.getAmount());
            walletRepository.save(smartContractWallet);

            recipientWallet.setBalance(recipientWallet.getBalance() + payment.getAmount());
            recipientWallet.setDeviseBalance(recipientWallet.getDeviseBalance() + payment.getAmount());
            walletRepository.save(recipientWallet);

            // ✅ CORRIGÉ : Utiliser la bonne designation selon payeur interne ou externe
            String releaseCounterpartUsername = payment.getClient() != null ? payment.getClient().getUsername() : EXTERNAL_PAYER_USERNAME;
            String releaseCounterpartDisplayName = payment.getClient() != null ? resolveDisplayName(payment.getClient()) : resolveExternalPayerName(payment);
            String releaseCreditDesignation = payment.getClient() != null
                    ? "CONDITIONAL_PAYMENT_RECEIVED"
                    : "CONDITIONAL_PAYMENT_RECEIVED_EXTERNAL";
            Operation vendorOperation = createOperation("CREDIT", payment.getVendor().getUsername(),
                    payment.getAmount(), releaseCreditDesignation, "VALIDEE",
                    releaseCounterpartUsername, releaseCounterpartDisplayName);
            vendorOperation = operationRepository.save(vendorOperation);
            payment.setVendorOperationId(vendorOperation.getId());
            payment = conditionalPaymentRepository.save(payment);

            updateParentPaymentLinkStatus(payment.getId(), "PAID");

            log.info("✅ Fonds libérés manuellement avec succès: paymentCode={}, releaseTxHash={}", paymentCode, releaseTxHash);

            return ResponseEntity.ok(mapToResponse(payment, null));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(null);
        } catch (Exception e) {
            log.error("❌ Erreur lors de la libération manuelle des fonds", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }

    @Override
    public ResponseEntity<ConditionalPaymentResponse> executeDeposit(Long paymentId, String clientPin) {
        log.info("🔵 Exécution du dépôt blockchain pour paiement conditionnel: paymentId={}", paymentId);

        ConditionalPayment payment = conditionalPaymentRepository.findById(paymentId).orElse(null);
        if (payment == null) {
            return errorResponse(HttpStatus.NOT_FOUND, "Paiement conditionnel non trouvé");
        }
        if (!"pending_condition".equals(payment.getStatus())) {
            return errorResponse(HttpStatus.BAD_REQUEST, "Paiement non éligible à l'exécution du dépôt (statut: " + payment.getStatus() + ")");
        }
        String currentHash = payment.getDepositTransactionHash();
        boolean canRetry = currentHash != null && (
                currentHash.startsWith("ESCROW-DEPOSIT-DB") ||
                currentHash.startsWith("ESCROW-DEPOSIT-FAILED") ||
                currentHash.startsWith("ESCROW-DEPOSIT-ERROR")
        );
        if (!canRetry && currentHash != null && !currentHash.startsWith("0x")) {
            return errorResponse(HttpStatus.BAD_REQUEST, "Dépôt déjà exécuté ou paiement invalide. Hash actuel: " + currentHash);
        }
        Wallet clientWallet = payment.getClientWallet();
        Wallet intermediateWallet = payment.getIntermediateWallet();
        if (clientWallet == null || intermediateWallet == null) {
            return errorResponse(HttpStatus.BAD_REQUEST, "Wallet client ou intermédiaire manquant sur le paiement");
        }
        String escrowAddress = payment.getEscrowContractAddress();
        if (escrowAddress == null || escrowAddress.isEmpty()) {
            return errorResponse(HttpStatus.BAD_REQUEST, "Adresse du smart contract manquante");
        }

        try {
            String paymentCode = payment.getPaymentCode();
            String vendorAddress = payment.getVendorWallet() != null ? payment.getVendorWallet().getAddress() : null;
            Long serviceStartDate = payment.getServiceStartDate() != null ?
                    payment.getServiceStartDate().toEpochSecond(java.time.ZoneOffset.UTC) :
                    System.currentTimeMillis() / 1000;

            if (paymentCode == null || vendorAddress == null) {
                return errorResponse(HttpStatus.BAD_REQUEST,
                    "Paramètres manquants pour l'appel smart contract: paymentCode=" + paymentCode + ", vendorAddress=" + vendorAddress);
            }

            String realTxHash;
            if (currentHash != null && currentHash.startsWith("ESCROW-DEPOSIT-FAILED")) {
                log.info("🔄 Retry dépôt (approve + deposit depuis wallet client): paymentId={}", paymentId);
                realTxHash = smartContractEscrowService.depositToEscrowStep2Only(
                        escrowAddress, payment.getAmount(), intermediateWallet,
                        paymentCode, vendorAddress, serviceStartDate, clientWallet, clientPin);
            } else {
                realTxHash = smartContractEscrowService.depositToEscrow(
                        escrowAddress, payment.getAmount(), clientWallet, intermediateWallet, escrowAddress, clientPin,
                        paymentCode, vendorAddress, serviceStartDate);
            }

            boolean depositSuccess = realTxHash != null &&
                    !realTxHash.startsWith("ESCROW-DEPOSIT-FAILED") &&
                    !realTxHash.startsWith("ESCROW-DEPOSIT-ERROR") &&
                    !realTxHash.startsWith("ESCROW-DEPOSIT-DB");

            payment.setDepositTransactionHash(realTxHash);
            conditionalPaymentRepository.save(payment);

            if (!depositSuccess) {
                log.error("❌ Dépôt blockchain échoué: paymentId={}, txHash={}", paymentId, realTxHash);
                return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Échec du transfert blockchain. Hash: " + realTxHash + ". Vous pouvez réessayer avec execute-deposit.");
            }

            Wallet smartContractWallet = getOrCreateSmartContractWallet(escrowAddress);
            double currentSmartContractBalance = smartContractWallet.getBalance();
            if (currentSmartContractBalance < payment.getAmount() * 0.9) {
                smartContractWallet.setBalance(smartContractWallet.getBalance() + payment.getAmount());
                smartContractWallet.setDeviseBalance(smartContractWallet.getDeviseBalance() + payment.getAmount());
                walletRepository.save(smartContractWallet);
                log.info("✅ Transaction Venly réussie, soldes smart contract mis à jour: paymentId={}, txHash={}, nouveau solde={}",
                        paymentId, realTxHash, smartContractWallet.getBalance());
            } else {
                log.info("ℹ️ Soldes smart contract déjà à jour, pas de modification: paymentId={}, txHash={}, solde actuel={}",
                        paymentId, realTxHash, currentSmartContractBalance);
            }

            log.info("✅ Dépôt blockchain exécuté: paymentId={}, txHash={}", paymentId, realTxHash);
            return ResponseEntity.ok(mapToResponse(payment, null));
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("❌ Échec exécution dépôt: {}", e.getMessage());
            return errorResponse(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            log.error("❌ Erreur lors de l'exécution du dépôt", e);
            return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Erreur serveur : " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()));
        }
    }

    // ========== Méthodes privées ==========

    /**
     * Résout le nom d'affichage d'un utilisateur (prénom + nom, sinon username).
     */
    private String resolveDisplayName(Users user) {
        if (user == null) {
            return null;
        }
        String firstName = user.getFirstname();
        String lastName = user.getLastname();
        if (firstName != null || lastName != null) {
            String displayName = ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
            if (!displayName.isEmpty()) {
                return displayName;
            }
        }
        return user.getUsername();
    }

    /**
     * Résout le nom d'affichage d'un payeur externe via OneTimePaymentLink.
     */
    private String resolveExternalPayerName(ConditionalPayment payment) {
        try {
            Optional<OneTimePaymentLink> optLink = oneTimePaymentLinkRepository
                    .findByConditionalPaymentId(payment.getId());
            if (optLink.isPresent()) {
                OneTimePaymentLink link = optLink.get();
                if (link.getPayerName() != null && !link.getPayerName().isEmpty()) {
                    return link.getPayerName();
                }
                if (link.getPayerPhone() != null && !link.getPayerPhone().isEmpty()) {
                    return link.getPayerPhone();
                }
                if (link.getPayerEmail() != null && !link.getPayerEmail().isEmpty()) {
                    return link.getPayerEmail();
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ Impossible de résoudre le nom du payeur externe: {}", e.getMessage());
        }
        return "Payeur externe";
    }

    private void populateReceivedAmount(ConditionalPayment payment) {
        Double receivedAmt = payment.getAmount();
        String receivedCcy = "USDC";

        try {
            if (payment.getVendor() != null && payment.getVendor().getCountryCurrency() != null) {
                String vendorCurrency = payment.getVendor().getCountryCurrency().getCurrencyCode();
                if (vendorCurrency != null && !vendorCurrency.equalsIgnoreCase("USDC")) {
                    receivedCcy = vendorCurrency;
                    var conv = currencyFreaksClientService.convertCurrency("USD", vendorCurrency, payment.getAmount());
                    if (conv != null && conv.getStatusCode().is2xxSuccessful() && conv.getBody() != null) {
                        String converted = conv.getBody().getConvertedAmount();
                        if (converted != null && !converted.isEmpty()) {
                            receivedAmt = Double.parseDouble(converted);
                            log.info("✅ Conversion receivedAmount: {} USDC → {} {}", payment.getAmount(), receivedAmt, vendorCurrency);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ Erreur conversion receivedAmount: {}. Fallback USDC.", e.getMessage());
            receivedAmt = payment.getAmount();
            receivedCcy = "USDC";
        }

        payment.setReceivedAmount(receivedAmt);
        payment.setReceivedCurrency(receivedCcy);
    }

    private void updateParentPaymentLinkStatus(Long conditionalPaymentId, String newStatus) {
        try {
            Optional<OneTimePaymentLink> optLink = oneTimePaymentLinkRepository
                    .findByConditionalPaymentId(conditionalPaymentId);

            if (optLink.isPresent()) {
                OneTimePaymentLink link = optLink.get();
                String oldStatus = link.getStatus();
                link.setStatus(newStatus);
                link.setUpdatedAt(LocalDateTime.now());

                if ("PAID".equals(newStatus)) {
                    link.setPaidAt(LocalDateTime.now());
                }

                oneTimePaymentLinkRepository.save(link);
                log.info("✅ Parent OneTimePaymentLink updated: id={}, uniqueCode={}, {} → {}",
                        link.getId(), link.getUniqueCode(), oldStatus, newStatus);
            } else {
                log.debug("ℹ️ No parent OneTimePaymentLink found for conditionalPaymentId={} (standalone conditional payment)",
                        conditionalPaymentId);
            }
        } catch (Exception e) {
            log.warn("⚠️ Failed to update parent OneTimePaymentLink for conditionalPaymentId={}: {}",
                    conditionalPaymentId, e.getMessage());
        }
    }

    private Wallet getOrCreateAdminWallet() {
        if (intermediateWalletId != null && !intermediateWalletId.isEmpty()) {
            Wallet wallet = walletRepository.findById(intermediateWalletId).orElse(null);
            if (wallet != null) {
                return wallet;
            }
            log.error("❌ Wallet intermédiaire non trouvé en base pour l'ID: {}. Créer un enregistrement dans la table wallet avec id='{}'.", intermediateWalletId, intermediateWalletId);
        }
        if (intermediateWalletAddress != null && !intermediateWalletAddress.isEmpty()) {
            Wallet wallet = walletRepository.findByAddress(intermediateWalletAddress);
            if (wallet != null) {
                return wallet;
            }
            log.error("❌ Wallet intermédiaire non trouvé en base pour l'adresse: {}", intermediateWalletAddress);
        }
        if ((intermediateWalletId == null || intermediateWalletId.isEmpty())
                && (intermediateWalletAddress == null || intermediateWalletAddress.isEmpty())) {
            throw new IllegalStateException("Wallet intermédiaire non configuré. Veuillez configurer akuunda.intermediate.wallet.id ou akuunda.intermediate.wallet.address");
        }
        throw new IllegalStateException("Wallet intermédiaire configuré (id=" + intermediateWalletId + " ou address) mais aucun enregistrement trouvé en base. Créer un enregistrement dans la table wallet correspondant au wallet Venly.");
    }

    private Wallet getOrCreateSmartContractWallet(String contractWalletAddress) {
        if (contractWalletAddress == null || contractWalletAddress.isEmpty()) {
            throw new IllegalStateException("Adresse du wallet du smart contract non configurée. Veuillez configurer akuunda.escrow.contract.wallet.address");
        }

        Wallet wallet = walletRepository.findByAddress(contractWalletAddress);
        if (wallet != null) {
            return wallet;
        }

        log.warn("⚠️ Wallet du smart contract non trouvé en base de données pour l'adresse: {}. Création d'une référence.", contractWalletAddress);

        String walletId = contractWalletAddress.length() >= 10
                ? "smart-contract-" + contractWalletAddress.substring(0, 10)
                : "smart-contract-escrow-" + Math.abs(contractWalletAddress.hashCode());

        Wallet smartContractWallet = Wallet.builder()
                .id(walletId)
                .address(contractWalletAddress)
                .walletType("SMART_CONTRACT")
                .description("Wallet du smart contract de séquestre")
                .balance(0.0)
                .deviseBalance(0.0)
                .users(null)
                .currency(null)
                .custodial(false)
                .archived(false)
                .isPrimary(false)
                .hasCustomPin(false)
                .gasBalance(0.0)
                .decimals(6)
                .symbol("USDC")
                .secretType("MATIC")
                .createdAt(new Date())
                .build();

        try {
            return walletRepository.save(smartContractWallet);
        } catch (Exception e) {
            log.error("❌ Erreur lors de la création du wallet du smart contract: {}", e.getMessage(), e);
            throw new IllegalStateException("Impossible de créer le wallet du smart contract: " + e.getMessage(), e);
        }
    }

    /**
     * Crée une opération avec les informations de contrepartie.
     */
    private Operation createOperation(String type, String username, Double amount, String designation, String status,
                                      String counterpartUsername, String counterpartDisplayName) {
        Operation operation = new Operation();
        operation.setType(type);
        operation.setStatus(status);
        operation.setUsername(username);
        operation.setDevise("USDC");
        operation.setAmount(amount);
        operation.setConvertedAmount(amount);
        operation.setDesignation(designation);
        operation.setOperationHash("CP-" + System.currentTimeMillis() + "-" + UUID.randomUUID().toString().substring(0, 8));
        operation.setCreatedAt(LocalDateTime.now());
        operation.setUpdatedAt(LocalDateTime.now());
        operation.setCounterpartUsername(counterpartUsername);
        operation.setCounterpartDisplayName(counterpartDisplayName);

        // ✅ Renseigner providerAmount/providerDevise (montant USDC)
        operation.setProviderAmount(amount);
        operation.setProviderDevise("USDC");

        // ✅ Renseigner transactionType et provider pour éviter "Inconnu"
        operation.setTransactionType("DEBIT".equals(type) ? "Paiement" : "Encaissement");
        operation.setProvider("Interne");

        return operation;
    }

    private RefundCalculation calculateRefund(ConditionalPayment payment) {
        List<ConditionalPaymentRule> rules = conditionalPaymentRuleRepository.findApplicableRules(
                payment.getServiceType(),
                payment.getVendor()
        );

        if (rules.isEmpty()) {
            return new RefundCalculation(true, payment.getAmount(), 0.0);
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime serviceStart = payment.getServiceStartDate();

        if (serviceStart == null) {
            return new RefundCalculation(true, payment.getAmount(), 0.0);
        }

        long hoursBefore = ChronoUnit.HOURS.between(now, serviceStart);

        ConditionalPaymentRule applicableRule = null;
        ConditionalPaymentRule bestMatch = null;

        for (ConditionalPaymentRule rule : rules) {
            if (rule.getHoursBeforeService() >= hoursBefore) {
                if (bestMatch == null || rule.getHoursBeforeService() < bestMatch.getHoursBeforeService()) {
                    bestMatch = rule;
                }
            }
        }

        applicableRule = bestMatch;

        if (applicableRule == null) {
            return new RefundCalculation(true, payment.getAmount(), 0.0);
        }

        Double penaltyAmount;
        if (applicableRule.getFixedPenaltyAmount() != null) {
            penaltyAmount = applicableRule.getFixedPenaltyAmount();
        } else {
            penaltyAmount = payment.getAmount() * applicableRule.getPenaltyPercentage();
        }

        Double clientAmount = payment.getAmount() - penaltyAmount;
        Double vendorAmount = penaltyAmount;

        return new RefundCalculation(false, clientAmount, vendorAmount);
    }

    // ========================================================================
    // ✅ mapToResponse — AVEC FALLBACK OneTimePaymentLink pour payeur externe
    // ========================================================================
    private ConditionalPaymentResponse mapToResponse(ConditionalPayment payment, QRCode qrCode) {
        Double amountInUSDC = payment.getAmount();
        Double amountInLocalCurrency = amountInUSDC;
        String clientLocalCurrency = "USDC";

        if (payment.getClient() != null && payment.getClient().getCountryCurrency() != null) {
            clientLocalCurrency = payment.getClient().getCountryCurrency().getCurrencyCode();

            if (clientLocalCurrency != null && !clientLocalCurrency.equalsIgnoreCase("USDC")) {
                try {
                    var conversionResponse = currencyFreaksClientService.convertCurrency("USD", clientLocalCurrency, amountInUSDC);
                    if (conversionResponse != null && conversionResponse.getStatusCode().is2xxSuccessful() && conversionResponse.getBody() != null) {
                        String convertedAmountStr = conversionResponse.getBody().getConvertedAmount();
                        if (convertedAmountStr != null && !convertedAmountStr.isEmpty()) {
                            amountInLocalCurrency = Double.parseDouble(convertedAmountStr);
                            log.debug("✅ Conversion pour affichage: {} USDC → {} {}", amountInUSDC, amountInLocalCurrency, clientLocalCurrency);
                        }
                    }
                } catch (Exception e) {
                    log.warn("⚠️ Erreur conversion USDC → devise locale pour affichage: {}", e.getMessage());
                    amountInLocalCurrency = amountInUSDC;
                    clientLocalCurrency = "USDC";
                }
            }
        }

        // ── creatorName / creatorUsername depuis le vendor (Users) ──
        String creatorName = null;
        String creatorUsername = null;
        if (payment.getVendor() != null) {
            Users vendor = payment.getVendor();
            creatorUsername = vendor.getUsername();
            String firstName = vendor.getFirstname();
            String lastName = vendor.getLastname();
            if (firstName != null || lastName != null) {
                creatorName = ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
            }
            if (creatorName == null || creatorName.isEmpty()) {
                creatorName = creatorUsername;
            }
        }

        // ── payerName / payerPhone / payerEmail ──────────────────────────────
        String payerName = null;
        String payerPhone = null;
        String payerEmail = null;
        Double sourceAmount = null;
        String sourceCurrency = null;

        if (payment.getClient() != null) {
            Users client = payment.getClient();
            String firstName = client.getFirstname();
            String lastName = client.getLastname();
            if (firstName != null || lastName != null) {
                payerName = ((firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "")).trim();
            }
            payerPhone = client.getUsername();
            payerEmail = client.getEmail();

            if (client.getCountryCurrency() != null) {
                String clientCurrencyCode = client.getCountryCurrency().getCurrencyCode();
                if (clientCurrencyCode != null && !clientCurrencyCode.equalsIgnoreCase("USDC")) {
                    sourceCurrency = clientCurrencyCode;
                    sourceAmount = amountInLocalCurrency;
                }
            }
        } else {
            try {
                Optional<OneTimePaymentLink> optLink = oneTimePaymentLinkRepository
                        .findByConditionalPaymentId(payment.getId());
                if (optLink.isPresent()) {
                    OneTimePaymentLink link = optLink.get();
                    payerName = link.getPayerName();
                    payerPhone = link.getPayerPhone();
                    payerEmail = link.getPayerEmail();

                    if (link.getAmount() != null && link.getCurrency() != null) {
                        sourceAmount = link.getAmount();
                        sourceCurrency = link.getCurrency();
                    }

                    log.debug("✅ Info payeur externe récupérées depuis OneTimePaymentLink: name={}, phone={}, email={}",
                            payerName, payerPhone, payerEmail);
                }
            } catch (Exception e) {
                log.warn("⚠️ Impossible de récupérer les infos du payeur externe: {}", e.getMessage());
            }
        }

        String vendorWalletAddress = null;
        if (payment.getVendorWallet() != null) {
            vendorWalletAddress = payment.getVendorWallet().getAddress();
        }

        ConditionalPaymentResponse.ConditionalPaymentResponseBuilder builder = ConditionalPaymentResponse.builder()
                .id(payment.getId())
                .paymentCode(payment.getPaymentCode())
                .clientUsername(payment.getClient() != null ? payment.getClient().getUsername() : EXTERNAL_PAYER_USERNAME)
                .vendorUsername(payment.getVendor().getUsername())
                .serviceType(payment.getServiceType())
                .description(payment.getDescription())
                .amount(amountInLocalCurrency)
                .currency(clientLocalCurrency)
                .status(payment.getStatus())
                .escrowContractAddress(payment.getEscrowContractAddress())
                .depositTransactionHash(payment.getDepositTransactionHash())
                .releaseTransactionHash(payment.getReleaseTransactionHash())
                .refundTransactionHash(payment.getRefundTransactionHash())
                .serviceStartDate(payment.getServiceStartDate())
                .serviceActualStartDate(payment.getServiceActualStartDate())
                .cancellationDeadline(payment.getCancellationDeadline())
                .retainedAmount(payment.getRetainedAmount() != null ? convertAmountToLocalCurrency(payment.getRetainedAmount(), clientLocalCurrency) : null)
                .refundedAmount(payment.getRefundedAmount() != null ? convertAmountToLocalCurrency(payment.getRefundedAmount(), clientLocalCurrency) : null)
                .cancellationReason(payment.getCancellationReason())
                .createdAt(payment.getCreatedAt())
                .updatedAt(payment.getUpdatedAt())
                .receivedAmount(payment.getReceivedAmount())
                .receivedCurrency(payment.getReceivedCurrency())
                .releasedAt(payment.getReleasedAt())
                .creatorName(creatorName)
                .creatorUsername(creatorUsername)
                .payerName(payerName)
                .payerPhone(payerPhone)
                .payerEmail(payerEmail)
                .sourceAmount(sourceAmount)
                .sourceCurrency(sourceCurrency)
                .vendorWalletAddress(vendorWalletAddress);

        if (qrCode != null) {
            builder.qrCodeUrl(qrCode.getQrCodeUrl())
                   .qrCodeToken(qrCode.getToken());
        }

        return builder.build();
    }

    private Double convertAmountToLocalCurrency(Double amountInUSDC, String localCurrency) {
        if (amountInUSDC == null || localCurrency == null || localCurrency.equalsIgnoreCase("USDC")) {
            return amountInUSDC;
        }
        try {
            var conversionResponse = currencyFreaksClientService.convertCurrency("USD", localCurrency, amountInUSDC);
            if (conversionResponse != null && conversionResponse.getStatusCode().is2xxSuccessful() && conversionResponse.getBody() != null) {
                String convertedAmountStr = conversionResponse.getBody().getConvertedAmount();
                if (convertedAmountStr != null && !convertedAmountStr.isEmpty()) {
                    return Double.parseDouble(convertedAmountStr);
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ Erreur conversion montant pour affichage: {}", e.getMessage());
        }
        return amountInUSDC;
    }

    private static class RefundCalculation {
        private final boolean fullRefund;
        private final Double clientAmount;
        private final Double vendorAmount;

        public RefundCalculation(boolean fullRefund, Double clientAmount, Double vendorAmount) {
            this.fullRefund = fullRefund;
            this.clientAmount = clientAmount;
            this.vendorAmount = vendorAmount;
        }

        public boolean isFullRefund() {
            return fullRefund;
        }

        public Double getClientAmount() {
            return clientAmount;
        }

        public Double getVendorAmount() {
            return vendorAmount;
        }
    }
}
