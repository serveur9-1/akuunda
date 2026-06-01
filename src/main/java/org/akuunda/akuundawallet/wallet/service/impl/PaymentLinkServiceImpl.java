package org.akuunda.akuundawallet.wallet.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.utils.PinHashUtil;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.dao.OperationRepository;
import org.akuunda.akuundawallet.wallet.api.dao.PaymentLinkRepository;
import org.akuunda.akuundawallet.wallet.api.dao.PaymentLinkTransactionRepository;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.api.dto.CreatePaymentLinkRequest;
import org.akuunda.akuundawallet.wallet.api.dto.PaymentLinkInfoResponse;
import org.akuunda.akuundawallet.wallet.api.dto.PaymentLinkResponse;
import org.akuunda.akuundawallet.wallet.api.dto.ProcessPaymentRequest;
import org.akuunda.akuundawallet.wallet.api.dto.WebPaymentInitRequest;
import org.akuunda.akuundawallet.wallet.api.dto.WebPaymentRequest;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaYellowCardClientService;
import org.akuunda.akuundawallet.wallet.api.entities.Operation;
import org.akuunda.akuundawallet.wallet.api.entities.PaymentLink;
import org.akuunda.akuundawallet.wallet.api.entities.PaymentLinkTransaction;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.wallet.service.PaymentLinkService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentLinkServiceImpl implements PaymentLinkService {

    private final PaymentLinkRepository paymentLinkRepository;
    private final PaymentLinkTransactionRepository paymentLinkTransactionRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final OperationRepository operationRepository;
    private final AkuundaYellowCardClientService yellowCardClientService;

    @Value("${payment.link.base-url:https://akuunda-pay.io}")
    private String baseUrl;

    @Value("${payment.link.path:/pay}")
    private String paymentPath;

    private static final int UNIQUE_CODE_LENGTH = 5; // Longueur du code unique (ex: "gn1mb")
    private static final int MAX_RETRIES = 10; // Nombre maximum de tentatives pour générer un code unique

    @Override
    @Transactional
    public ResponseEntity<PaymentLinkResponse> createPaymentLink(String username, CreatePaymentLinkRequest request) {
        try {
            log.info("Creating payment link for username: {}", username);

            // 1. Vérifier que la requête n'est pas null
            if (request == null) {
                log.error("CreatePaymentLinkRequest is null for username: {}", username);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(null);
            }

            // 2. Vérifier que l'utilisateur existe
            Users creator = userRepository.getUsersByUsername(username);
            if (creator == null) {
                log.error("User not found: {}", username);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(null);
            }

            // 3. Valider la date d'expiration (si fournie, doit être dans le futur)
            if (request.getExpiresAt() != null && request.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.error("Invalid expiration date provided: {} (must be in the future)", request.getExpiresAt());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(null);
            }

            // 4. Générer un code unique
            String uniqueCode = generateUniqueCode();
            if (uniqueCode == null) {
                log.error("Failed to generate unique code after {} retries", MAX_RETRIES);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(null);
            }

            // 5. Créer le lien de paiement
            PaymentLink paymentLink = PaymentLink.builder()
                    .uniqueCode(uniqueCode)
                    .creator(creator)
                    .description(request.getDescription())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .isActive(true)
                    .expiresAt(request.getExpiresAt())
                    .totalPayments(0)
                    .totalAmountReceived(0.0)
                    .build();

            paymentLink = paymentLinkRepository.save(paymentLink);
            log.info("Payment link created with code: {} for user: {}", uniqueCode, username);

            // 6. Construire la réponse
            PaymentLinkResponse response = buildPaymentLinkResponse(paymentLink);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (Exception e) {
            log.error("Error creating payment link for username: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }

    @Override
    public ResponseEntity<PaymentLinkResponse> getPaymentLinkByCode(String uniqueCode) {
        try {
            log.info("Getting payment link by code: {}", uniqueCode);

            PaymentLink paymentLink = paymentLinkRepository.findByUniqueCode(uniqueCode)
                    .orElse(null);

            if (paymentLink == null) {
                log.warn("Payment link not found: {}", uniqueCode);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(null);
            }

            // Vérifier si le lien est expiré
            if (paymentLink.getExpiresAt() != null && paymentLink.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.warn("Payment link expired: {} (expired at: {}, current time: {})", 
                        uniqueCode, paymentLink.getExpiresAt(), LocalDateTime.now());
                return ResponseEntity.status(HttpStatus.GONE)
                        .body(null);
            }

            // Vérifier si le lien est actif
            if (!paymentLink.getIsActive()) {
                log.warn("Payment link is inactive: {}", uniqueCode);
                return ResponseEntity.status(HttpStatus.GONE)
                        .body(null);
            }

            PaymentLinkResponse response = buildPaymentLinkResponse(paymentLink);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting payment link by code: {}", uniqueCode, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }

    @Override
    public ResponseEntity<List<PaymentLinkResponse>> getUserPaymentLinks(String username) {
        try {
            log.info("Getting payment links for user: {}", username);

            Users user = userRepository.getUsersByUsername(username);
            if (user == null) {
                log.error("User not found: {}", username);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(null);
            }

            List<PaymentLink> paymentLinks = paymentLinkRepository.findByCreatorOrderByCreatedAtDesc(user);
            List<PaymentLinkResponse> responses = paymentLinks.stream()
                    .map(this::buildPaymentLinkResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(responses);

        } catch (Exception e) {
            log.error("Error getting payment links for user: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }

    @Override
    @Transactional
    public ResponseEntity<String> deactivatePaymentLink(String username, String uniqueCode) {
        try {
            log.info("Deactivating payment link: {} for user: {}", uniqueCode, username);

            Users user = userRepository.getUsersByUsername(username);
            if (user == null) {
                log.error("User not found: {}", username);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("{\"success\": false, \"error\": \"User not found\"}");
            }

            PaymentLink paymentLink = paymentLinkRepository.findByUniqueCode(uniqueCode)
                    .orElse(null);

            if (paymentLink == null) {
                log.warn("Payment link not found: {}", uniqueCode);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("{\"success\": false, \"error\": \"Payment link not found\"}");
            }

            // Vérifier que l'utilisateur est le créateur
            if (!paymentLink.getCreator().getUsername().equals(username)) {
                log.warn("User {} is not the creator of payment link {}", username, uniqueCode);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("{\"success\": false, \"error\": \"You are not authorized to deactivate this link\"}");
            }

            paymentLink.setIsActive(false);
            paymentLinkRepository.save(paymentLink);

            log.info("Payment link deactivated: {}", uniqueCode);
            return ResponseEntity.ok("{\"success\": true, \"message\": \"Payment link deactivated successfully\"}");

        } catch (Exception e) {
            log.error("Error deactivating payment link: {} for user: {}", uniqueCode, username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"success\": false, \"error\": \"Internal server error\"}");
        }
    }

    @Override
    @Transactional
    public ResponseEntity<String> processPayment(ProcessPaymentRequest request) {
        try {
            log.info("Processing payment for link: {} by user: {}", request.getUniqueCode(), request.getPayerUsername());

            // 1. Récupérer le lien de paiement
            PaymentLink paymentLink = paymentLinkRepository.findByUniqueCode(request.getUniqueCode())
                    .orElse(null);

            if (paymentLink == null) {
                log.warn("Payment link not found: {}", request.getUniqueCode());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("{\"success\": false, \"error\": \"Payment link not found\"}");
            }

            // 2. Vérifier que le lien est actif
            if (!paymentLink.getIsActive()) {
                log.warn("Payment link is not active: {}", request.getUniqueCode());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("{\"success\": false, \"error\": \"Payment link is not active\"}");
            }

            // 3. Vérifier que le lien n'est pas expiré
            if (paymentLink.getExpiresAt() != null && paymentLink.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.warn("Payment link expired: {}", request.getUniqueCode());
                return ResponseEntity.status(HttpStatus.GONE)
                        .body("{\"success\": false, \"error\": \"Payment link has expired\"}");
            }

            // 4. Vérifier le montant si le lien a un montant fixe
            if (paymentLink.getAmount() != null && !paymentLink.getAmount().equals(request.getAmount())) {
                log.warn("Amount mismatch for payment link: {}. Expected: {}, Got: {}", 
                        request.getUniqueCode(), paymentLink.getAmount(), request.getAmount());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("{\"success\": false, \"error\": \"Amount mismatch\"}");
            }

            // 5. Récupérer le payeur
            Users payer = userRepository.getUsersByUsername(request.getPayerUsername());
            if (payer == null) {
                log.error("Payer not found: {}", request.getPayerUsername());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("{\"success\": false, \"error\": \"Payer not found\"}");
            }

            // 6. Récupérer le wallet du payeur
            Wallet payerWallet = walletRepository.findByUsers(payer);
            if (payerWallet == null) {
                log.error("Payer wallet not found for user: {}", request.getPayerUsername());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("{\"success\": false, \"error\": \"Payer wallet not found\"}");
            }

            // 7. Vérifier le solde du payeur
            if (payerWallet.getDeviseBalance() < request.getAmount()) {
                log.warn("Insufficient balance for payer: {}. Balance: {}, Required: {}", 
                        request.getPayerUsername(), payerWallet.getDeviseBalance(), request.getAmount());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("{\"success\": false, \"error\": \"Insufficient balance\"}");
            }

            // 8. Récupérer le wallet du créateur
            Wallet creatorWallet = walletRepository.findByUsers(paymentLink.getCreator());
            if (creatorWallet == null) {
                log.error("Creator wallet not found for user: {}", paymentLink.getCreator().getUsername());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("{\"success\": false, \"error\": \"Creator wallet not found\"}");
            }

            // 9. Effectuer le transfert
            // Débiter le payeur
            payerWallet.setDeviseBalance(payerWallet.getDeviseBalance() - request.getAmount());
            payerWallet.setBalance(payerWallet.getBalance() - request.getAmount());
            walletRepository.save(payerWallet);

            // Créditer le créateur
            creatorWallet.setDeviseBalance(creatorWallet.getDeviseBalance() + request.getAmount());
            creatorWallet.setBalance(creatorWallet.getBalance() + request.getAmount());
            walletRepository.save(creatorWallet);

            // 10. Créer l'opération pour le payeur (DEBIT)
            Operation payerOperation = new Operation();
            payerOperation.setType("DEBIT");
            payerOperation.setStatus("VALIDEE");
            payerOperation.setUsername(payer.getUsername());
            payerOperation.setDevise(paymentLink.getCurrency());
            payerOperation.setAmount(request.getAmount());
            payerOperation.setDesignation("PAYMENT_LINK");
            payerOperation.setOperationHash("PL-" + System.currentTimeMillis() + "-" + request.getUniqueCode());
            payerOperation.setCreatedAt(LocalDateTime.now());
            payerOperation.setUpdatedAt(LocalDateTime.now());
            // ✅ Contrepartie AVANT le save — créateur du lien
            payerOperation.setCounterpartUsername(paymentLink.getCreator().getUsername());
            payerOperation.setCounterpartDisplayName(resolveDisplayName(paymentLink.getCreator()));
            payerOperation = operationRepository.save(payerOperation);

            // 11. Créer l'opération pour le créateur (CREDIT)
            Operation creatorOperation = new Operation();
            creatorOperation.setType("CREDIT");
            creatorOperation.setStatus("VALIDEE");
            creatorOperation.setUsername(paymentLink.getCreator().getUsername());
            creatorOperation.setDevise(paymentLink.getCurrency());
            creatorOperation.setAmount(request.getAmount());
            creatorOperation.setDesignation("PAYMENT_LINK");
            creatorOperation.setOperationHash("PL-" + System.currentTimeMillis() + "-" + request.getUniqueCode() + "-CR");
            creatorOperation.setCreatedAt(LocalDateTime.now());
            creatorOperation.setUpdatedAt(LocalDateTime.now());
            // ✅ Contrepartie AVANT le save — payeur
            creatorOperation.setCounterpartUsername(payer.getUsername());
            creatorOperation.setCounterpartDisplayName(resolveDisplayName(payer));
            creatorOperation = operationRepository.save(creatorOperation);

            // 12. Créer la transaction de lien de paiement
            PaymentLinkTransaction transaction = PaymentLinkTransaction.builder()
                    .paymentLink(paymentLink)
                    .payer(payer)
                    .amount(request.getAmount())
                    .currency(paymentLink.getCurrency())
                    .status("COMPLETED")
                    .note(request.getNote())
                    .operationId(payerOperation.getId())
                    .build();
            paymentLinkTransactionRepository.save(transaction);

            // 13. Mettre à jour les statistiques du lien
            paymentLink.setTotalPayments(paymentLink.getTotalPayments() + 1);
            paymentLink.setTotalAmountReceived(paymentLink.getTotalAmountReceived() + request.getAmount());
            paymentLinkRepository.save(paymentLink);

            log.info("Payment processed successfully. Link: {}, Amount: {}, Payer: {}", 
                    request.getUniqueCode(), request.getAmount(), request.getPayerUsername());

            return ResponseEntity.ok(String.format(
                    "{\"success\": true, \"message\": \"Payment processed successfully\", \"amount\": %s, \"currency\": \"%s\"}",
                    request.getAmount(), paymentLink.getCurrency()));

        } catch (Exception e) {
            log.error("Error processing payment for link: {}", request.getUniqueCode(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"success\": false, \"error\": \"Internal server error\"}");
        }
    }

    /**
     * Génère un code unique court pour le lien de paiement
     */
    private String generateUniqueCode() {
        // Utiliser des caractères alphanumériques minuscules pour un code court et lisible
        final String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
        
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            String code = PinHashUtil.generateRandomString(UNIQUE_CODE_LENGTH);
            // Convertir en minuscules et utiliser seulement alphanumérique
            code = code.toLowerCase().replaceAll("[^a-z0-9]", "");
            if (code.length() < UNIQUE_CODE_LENGTH) {
                // Si le code est trop court après filtrage, le compléter
                while (code.length() < UNIQUE_CODE_LENGTH) {
                    int randomIndex = (int) (Math.random() * alphabet.length());
                    code += alphabet.charAt(randomIndex);
                }
            }
            
            // Vérifier l'unicité
            if (!paymentLinkRepository.existsByUniqueCode(code)) {
                return code;
            }
            
            log.debug("Code {} already exists, retrying... (attempt {}/{})", code, attempt + 1, MAX_RETRIES);
        }
        
        return null; // Échec après MAX_RETRIES tentatives
    }

    @Override
    public ResponseEntity<PaymentLinkInfoResponse> getPaymentLinkInfoForWeb(String uniqueCode) {
        try {
            log.info("Getting payment link info for web by code: {}", uniqueCode);

            PaymentLink paymentLink = paymentLinkRepository.findByUniqueCode(uniqueCode)
                    .orElse(null);

            if (paymentLink == null) {
                log.warn("Payment link not found: {}", uniqueCode);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(null);
            }

            // Vérifier si le lien est expiré
            if (paymentLink.getExpiresAt() != null && paymentLink.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.warn("Payment link expired: {} (expired at: {}, current time: {})", 
                        uniqueCode, paymentLink.getExpiresAt(), LocalDateTime.now());
                return ResponseEntity.status(HttpStatus.GONE)
                        .body(null);
            }

            // Vérifier si le lien est actif
            if (!paymentLink.getIsActive()) {
                log.warn("Payment link is inactive: {}", uniqueCode);
                return ResponseEntity.status(HttpStatus.GONE)
                        .body(null);
            }

            PaymentLinkInfoResponse response = buildPaymentLinkInfoResponse(paymentLink);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error getting payment link info for web by code: {}", uniqueCode, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(null);
        }
    }

    @Override
    public ResponseEntity<String> processWebPayment(WebPaymentRequest request) {
        try {
            log.info("Initiating web payment for link: {} by phone: {}", request.getUniqueCode(), request.getPhoneNumber());

            // 1. Récupérer le lien de paiement
            PaymentLink paymentLink = paymentLinkRepository.findByUniqueCode(request.getUniqueCode())
                    .orElse(null);

            if (paymentLink == null) {
                log.warn("Payment link not found: {}", request.getUniqueCode());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("{\"success\": false, \"error\": \"Payment link not found\"}");
            }

            // 2. Vérifier que le lien est actif
            if (!paymentLink.getIsActive()) {
                log.warn("Payment link is not active: {}", request.getUniqueCode());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("{\"success\": false, \"error\": \"Payment link is not active\"}");
            }

            // 3. Vérifier que le lien n'est pas expiré
            if (paymentLink.getExpiresAt() != null && paymentLink.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.warn("Payment link expired: {}", request.getUniqueCode());
                return ResponseEntity.status(HttpStatus.GONE)
                        .body("{\"success\": false, \"error\": \"Payment link has expired\"}");
            }

            // 4. Vérifier le montant si le lien a un montant fixe
            if (paymentLink.getAmount() != null && !paymentLink.getAmount().equals(request.getAmount())) {
                log.warn("Amount mismatch for payment link: {}. Expected: {}, Got: {}", 
                        request.getUniqueCode(), paymentLink.getAmount(), request.getAmount());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("{\"success\": false, \"error\": \"Amount mismatch\"}");
            }

            // 5. Vérifier la devise si le lien a une devise fixe
            if (paymentLink.getCurrency() != null && !paymentLink.getCurrency().equals(request.getCurrency())) {
                log.warn("Currency mismatch for payment link: {}. Expected: {}, Got: {}", 
                        request.getUniqueCode(), paymentLink.getCurrency(), request.getCurrency());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("{\"success\": false, \"error\": \"Currency mismatch\"}");
            }

            // 6. Récupérer le wallet du créateur
            Wallet creatorWallet = walletRepository.findByUsers(paymentLink.getCreator());
            if (creatorWallet == null) {
                log.error("Creator wallet not found for user: {}", paymentLink.getCreator().getUsername());
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("{\"success\": false, \"error\": \"Creator wallet not found\"}");
            }

            // 7. Vérifier le type de moyen de paiement et router vers le bon provider
            String paymentMethodType = request.getPaymentMethodType() != null 
                    ? request.getPaymentMethodType() 
                    : "momo"; // Par défaut, mobile money
            
            // 7a. Pour mobile money (momo) : utiliser YellowCard
            if ("momo".equalsIgnoreCase(paymentMethodType)) {
                // Vérifier que le téléphone est fourni pour mobile money
                if (request.getPhoneNumber() == null || request.getPhoneNumber().isEmpty()) {
                    log.warn("Phone number is required for mobile money payment");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body("{\"success\": false, \"error\": \"Phone number is required for mobile money payment\"}");
                }

                
                // Construire la requête d'initiation de paiement web
                WebPaymentInitRequest initRequest = new WebPaymentInitRequest();
                initRequest.setUniqueCode(request.getUniqueCode());
                initRequest.setAmount(request.getAmount());
                initRequest.setCurrency(request.getCurrency());
                initRequest.setPhoneNumber(request.getPhoneNumber());
                initRequest.setCountryCode(request.getCountryCode());
                initRequest.setOperatorId(request.getPaymentMethodId()); // Utiliser paymentMethodId comme operatorId
                initRequest.setPayerName(request.getPayerName());
                initRequest.setPayerEmail(request.getPayerEmail());
                initRequest.setPayerAddress(null);
                initRequest.setPayerDob(null);

                // 8. Appeler YellowCard pour créer la collection et obtenir le redirectUrl
                ResponseEntity<Object> yellowCardResponse = yellowCardClientService.createWebPaymentCollection(
                        initRequest,
                        creatorWallet.getAddress(),
                        paymentLink.getCreator().getUserId()
                );

                if (!yellowCardResponse.getStatusCode().is2xxSuccessful()) {
                    log.error("YellowCard API error: {}", yellowCardResponse.getBody());
                    return ResponseEntity.status(yellowCardResponse.getStatusCode())
                            .body(String.format("{\"success\": false, \"error\": \"YellowCard API error: %s\"}", 
                                    yellowCardResponse.getBody()));
                }

                // 9. Extraire le redirectUrl de la réponse
                Object responseBody = yellowCardResponse.getBody();
                String redirectUrl = null;
                String transactionId = null;
                
                if (responseBody instanceof java.util.Map) {
                    @SuppressWarnings("unchecked")
                    java.util.Map<String, Object> responseMap = (java.util.Map<String, Object>) responseBody;
                    redirectUrl = (String) responseMap.get("redirectUrl");
                    transactionId = (String) responseMap.get("transactionId");
                }

                if (redirectUrl == null) {
                    log.error("Redirect URL not found in YellowCard response");
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("{\"success\": false, \"error\": \"Redirect URL not found in response\"}");
                }

                // 10. Créer une transaction PENDING (sera mise à jour via webhook YellowCard)
                PaymentLinkTransaction transaction = PaymentLinkTransaction.builder()
                        .paymentLink(paymentLink)
                        .payer(null) // Pas d'utilisateur local pour les paiements web
                        .amount(request.getAmount())
                        .currency(request.getCurrency())
                        .status("PENDING") // Statut initial, sera mis à jour via webhook
                        .note(request.getNote())
                        .operationId(null) // Sera créé après confirmation du paiement
                        .build();
                paymentLinkTransactionRepository.save(transaction);

                log.info("Web payment (mobile money) initiated successfully. Link: {}, Redirect URL: {}, Transaction ID: {}", 
                        request.getUniqueCode(), redirectUrl, transactionId);

                // Retourner le redirectUrl pour que le frontend redirige l'utilisateur
                return ResponseEntity.ok(String.format(
                        "{\"success\": true, \"message\": \"Payment initiated successfully\", \"redirectUrl\": \"%s\", \"transactionId\": \"%s\", \"amount\": %s, \"currency\": \"%s\", \"paymentMethodType\": \"momo\"}",
                        redirectUrl, transactionId != null ? transactionId : "", request.getAmount(), request.getCurrency()));
            
            } else {
                // 7b. Pour carte/virement (bank, card) : utiliser Guardarian ou autre partenaire
                // TODO: Implémenter l'intégration avec Guardarian pour les paiements carte/virement
                // Pour l'instant, on retourne une erreur indiquant que ce type de paiement n'est pas encore supporté
                log.warn("Payment method type '{}' not yet implemented for web payments. Only 'momo' is supported.", paymentMethodType);
                return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                        .body(String.format("{\"success\": false, \"error\": \"Payment method type '%s' is not yet supported. Only mobile money (momo) is currently available.\"}", 
                                paymentMethodType));
            }

        } catch (Exception e) {
            log.error("Error processing web payment for link: {}", request.getUniqueCode(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"success\": false, \"error\": \"Internal server error\"}");
        }
    }

    // ========== Méthodes utilitaires ==========

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
     * Construit une réponse PaymentLinkResponse à partir d'une entité PaymentLink
     */
    private PaymentLinkResponse buildPaymentLinkResponse(PaymentLink paymentLink) {
        String paymentUrl = baseUrl + paymentPath + "/" + paymentLink.getUniqueCode();
        
        return PaymentLinkResponse.builder()
                .id(paymentLink.getId())
                .uniqueCode(paymentLink.getUniqueCode())
                .paymentUrl(paymentUrl)
                .description(paymentLink.getDescription())
                .amount(paymentLink.getAmount())
                .currency(paymentLink.getCurrency())
                .isActive(paymentLink.getIsActive())
                .expiresAt(paymentLink.getExpiresAt())
                .totalPayments(paymentLink.getTotalPayments())
                .totalAmountReceived(paymentLink.getTotalAmountReceived())
                .creatorUsername(paymentLink.getCreator().getUsername())
                .createdAt(paymentLink.getCreatedAt())
                .updatedAt(paymentLink.getUpdatedAt())
                .build();
    }

    /**
     * Construit une réponse PaymentLinkInfoResponse à partir d'une entité PaymentLink (pour l'interface web)
     */
    private PaymentLinkInfoResponse buildPaymentLinkInfoResponse(PaymentLink paymentLink) {
        String creatorName = paymentLink.getCreator().getFirstname() != null && paymentLink.getCreator().getLastname() != null
                ? paymentLink.getCreator().getFirstname() + " " + paymentLink.getCreator().getLastname()
                : paymentLink.getCreator().getUsername();
        
        return PaymentLinkInfoResponse.builder()
                .uniqueCode(paymentLink.getUniqueCode())
                .description(paymentLink.getDescription())
                .amount(paymentLink.getAmount())
                .currency(paymentLink.getCurrency())
                .isActive(paymentLink.getIsActive())
                .expiresAt(paymentLink.getExpiresAt())
                .totalPayments(paymentLink.getTotalPayments())
                .totalAmountReceived(paymentLink.getTotalAmountReceived())
                .creatorName(creatorName)
                .build();
    }
}
