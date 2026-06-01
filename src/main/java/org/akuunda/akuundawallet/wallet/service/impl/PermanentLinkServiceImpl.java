package org.akuunda.akuundawallet.wallet.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.Exceptions.ErrorResponse;
import org.akuunda.akuundawallet.common.utils.PinHashUtil;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.dao.PartnerContractPaymentRepository;
import org.akuunda.akuundawallet.wallet.api.dao.PartnerContractRepository;
import org.akuunda.akuundawallet.wallet.api.dao.PermanentLinkRepository;
import org.akuunda.akuundawallet.wallet.api.dao.PermanentLinkSessionRepository;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.api.dto.CreatePermanentLinkRequest;
import org.akuunda.akuundawallet.wallet.api.dto.CreatePermanentLinkSessionRequest;
import org.akuunda.akuundawallet.wallet.api.dto.PermanentLinkResponse;
import org.akuunda.akuundawallet.wallet.api.dto.PermanentLinkSessionResponse;
import org.akuunda.akuundawallet.wallet.api.dto.PermanentLinkStatsResponse;
import org.akuunda.akuundawallet.wallet.api.entities.PartnerContract;
import org.akuunda.akuundawallet.wallet.api.entities.PartnerContractPayment;
import org.akuunda.akuundawallet.wallet.api.entities.PermanentLink;
import org.akuunda.akuundawallet.wallet.api.entities.PermanentLinkSession;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.wallet.service.PaymentFactoryContractService;
import org.akuunda.akuundawallet.wallet.service.PermanentLinkService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PermanentLinkServiceImpl implements PermanentLinkService {

    private final PermanentLinkRepository permanentLinkRepository;
    private final PermanentLinkSessionRepository permanentLinkSessionRepository;
    private final PartnerContractRepository partnerContractRepository;
    private final PartnerContractPaymentRepository partnerContractPaymentRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PaymentFactoryContractService paymentFactoryContractService;

    @Value("${akuunda.intermediate.wallet.id:}")
    private String adminWalletId;

    @Value("${akuunda.intermediate.wallet.address:}")
    private String adminWalletAddress;

    @Value("${akuunda.permanent.link.base-url:https://qr.akuunda-pay.io}")
    private String baseUrl;

    private static final int SESSION_CODE_LENGTH = 12;
    private static final int MAX_RETRIES = 10;
    private static final long DEFAULT_SESSION_EXPIRY_HOURS = 24;
    private static final double MINIMAL_ONCHAIN_AMOUNT_USDC = 0.000001;

    @Override
    public ResponseEntity<?> createPermanentLink(String username, CreatePermanentLinkRequest request) {
        try {
            log.info("🔵 Creating permanent link for username: {}", username);

            if (request == null) {
                log.error("❌ CreatePermanentLinkRequest is null for username: {}", username);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
            }

            Users creator = userRepository.getUsersByUsername(username);
            if (creator == null) {
                log.error("❌ User not found: {}", username);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            Wallet creatorWallet = resolveCreatorWallet(creator);
            if (creatorWallet == null) {
                log.warn("⚠️ Merchant {} has no usable wallet — cannot create permanent link", username);
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body(buildMerchantWalletRequiredError(username));
            }

            if (permanentLinkRepository.existsByMerchantSlug(request.getMerchantSlug())) {
                log.warn("⚠️ Slug already exists: {}", request.getMerchantSlug());
                return ResponseEntity.status(HttpStatus.CONFLICT).body(null);
            }

            PermanentLink link = PermanentLink.builder()
                    .merchantSlug(request.getMerchantSlug())
                    .creator(creator)
                    .description(request.getDescription())
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .isActive(true)
                    .totalSessions(0)
                    .totalCompletedPayments(0)
                    .totalAmountReceived(0.0)
                    .build();

            link = permanentLinkRepository.save(link);
            log.info("✅ Permanent link created with slug: {} for user: {}", request.getMerchantSlug(), username);

            return ResponseEntity.status(HttpStatus.CREATED).body(buildPermanentLinkResponse(link));

        } catch (Exception e) {
            log.error("❌ Error creating permanent link for username: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @Override
    public ResponseEntity<PermanentLinkResponse> getPermanentLinkBySlug(String merchantSlug) {
        try {
            log.info("Getting permanent link by slug: {}", merchantSlug);

            PermanentLink link = permanentLinkRepository.findByMerchantSlug(merchantSlug).orElse(null);
            if (link == null) {
                log.warn("⚠️ Permanent link not found: {}", merchantSlug);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            return ResponseEntity.ok(buildPermanentLinkResponse(link));

        } catch (Exception e) {
            log.error("❌ Error getting permanent link by slug: {}", merchantSlug, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @Override
    public ResponseEntity<List<PermanentLinkResponse>> getUserPermanentLinks(String username) {
        try {
            log.info("Getting permanent links for user: {}", username);

            Users user = userRepository.getUsersByUsername(username);
            if (user == null) {
                log.error("❌ User not found: {}", username);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            List<PermanentLink> links = permanentLinkRepository.findByCreatorOrderByCreatedAtDesc(user);
            List<PermanentLinkResponse> responses = links.stream()
                    .map(this::buildPermanentLinkResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(responses);

        } catch (Exception e) {
            log.error("❌ Error getting permanent links for user: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @Override
    public ResponseEntity<?> createSession(String merchantSlug, CreatePermanentLinkSessionRequest request) {
        try {
            log.info("🔵 Creating session for permanent link slug: {}", merchantSlug);

            PermanentLink permanentLink = permanentLinkRepository.findByMerchantSlug(merchantSlug).orElse(null);
            if (permanentLink == null) {
                log.warn("⚠️ Permanent link not found: {}", merchantSlug);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            if (!Boolean.TRUE.equals(permanentLink.getIsActive())) {
                log.warn("⚠️ Permanent link is inactive: {}", merchantSlug);
                return ResponseEntity.status(HttpStatus.GONE).body(null);
            }

            String sessionCode = generateSessionCode();
            if (sessionCode == null) {
                log.error("❌ Failed to generate session code after {} retries", MAX_RETRIES);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }

            String paymentIdBytes32 = paymentFactoryContractService.generatePaymentId(sessionCode);

            Wallet creatorWallet = resolveCreatorWallet(permanentLink.getCreator());
            if (creatorWallet == null) {
                String u = permanentLink.getCreator().getUsername();
                log.error("❌ Creator wallet not found for user: {} (no row in wallet, or all archived)", u);
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                        .body(buildMerchantWalletRequiredError(u));
            }

            Wallet adminWallet = getAdminWallet();
            if (adminWallet == null) {
                log.error("❌ Admin wallet not configured");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(null);
            }

            LocalDateTime expiresAt = LocalDateTime.now().plusHours(DEFAULT_SESSION_EXPIRY_HOURS);
            long durationSeconds = java.time.Duration.between(LocalDateTime.now(), expiresAt).getSeconds();
            if (durationSeconds <= 0) {
                durationSeconds = DEFAULT_SESSION_EXPIRY_HOURS * 3600;
            }

            String create2Address = paymentFactoryContractService.createPaymentLink(
                    paymentIdBytes32,
                    creatorWallet.getAddress(),
                    MINIMAL_ONCHAIN_AMOUNT_USDC,
                    permanentLink.getDescription(),
                    durationSeconds,
                    adminWallet
            );

            if (create2Address == null || create2Address.isEmpty()) {
                log.error("❌ Failed to create payment link on smart contract for session: {}", sessionCode);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }

            // If the permanent link has a fixed amount and the request amount differs, use the fixed amount
            double sessionAmount = request.getAmount();
            if (permanentLink.getAmount() != null
                    && (request.getAmount() == null || Double.compare(permanentLink.getAmount(), request.getAmount()) != 0)) {
                sessionAmount = permanentLink.getAmount();
                log.info("ℹ️ Using fixed amount {} from permanent link (requested: {})", sessionAmount, request.getAmount());
            }

            PermanentLinkSession session = PermanentLinkSession.builder()
                    .sessionCode(sessionCode)
                    .permanentLink(permanentLink)
                    .amount(sessionAmount)
                    .currency(request.getCurrency() != null ? request.getCurrency() : "USDC")
                    .reference(request.getReference())
                    .status("CREATED")
                    .payerName(request.getPayerName())
                    .payerPhone(request.getPayerPhone())
                    .payerEmail(request.getPayerEmail())
                    .paymentIdBytes32(paymentIdBytes32)
                    .create2WalletAddress(create2Address)
                    .expiresAt(expiresAt)
                    .build();

            session = permanentLinkSessionRepository.save(session);

            // Si ce lien permanent est lié à un contrat partenaire, créer un PartnerContractPayment
            if (permanentLink.getPartnerContractCode() != null) {
                PartnerContract contract = partnerContractRepository
                        .findByContractCode(permanentLink.getPartnerContractCode()).orElse(null);
                if (contract != null) {
                    String paymentCode = "PERM-" + sessionCode;
                    String onChainPaymentId = paymentFactoryContractService.generatePaymentId(paymentCode);
                    String clientId = request.getPayerEmail() != null ? request.getPayerEmail()
                            : (request.getPayerPhone() != null ? request.getPayerPhone() : "guest-" + sessionCode);

                    PartnerContractPayment partnerPayment = PartnerContractPayment.builder()
                            .paymentCode(paymentCode)
                            .contract(contract)
                            .clientUsername(clientId)
                            .amount(sessionAmount)
                            .currency(session.getCurrency() != null ? session.getCurrency() : "USDC")
                            .status("pending_condition")
                            .onChainPaymentId(onChainPaymentId)
                            .create2WalletAddress(create2Address)
                            .create2Status("waiting_payment")
                            .qrUrl(baseUrl + "/m/" + merchantSlug)
                            .distributions(new java.util.ArrayList<>())
                            .build();
                    partnerContractPaymentRepository.save(partnerPayment);

                    session.setPartnerContractPaymentCode(paymentCode);
                    session = permanentLinkSessionRepository.save(session);
                    log.info("PartnerContractPayment créé depuis session permanente: paymentCode={}", paymentCode);
                }
            }

            permanentLink.setTotalSessions(permanentLink.getTotalSessions() + 1);
            permanentLinkRepository.save(permanentLink);

            log.info("✅ Session created: {} for link: {}, CREATE2: {}", sessionCode, merchantSlug, create2Address);

            return ResponseEntity.status(HttpStatus.CREATED).body(buildSessionResponse(session));

        } catch (Exception e) {
            log.error("❌ Error creating session for permanent link: {}", merchantSlug, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @Override
    public ResponseEntity<PermanentLinkSessionResponse> getSessionByCode(String sessionCode) {
        try {
            log.info("Getting session by code: {}", sessionCode);

            PermanentLinkSession session = permanentLinkSessionRepository.findBySessionCode(sessionCode).orElse(null);
            if (session == null) {
                log.warn("⚠️ Session not found: {}", sessionCode);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            // Check if expired and update status
            if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(LocalDateTime.now())
                    && "CREATED".equals(session.getStatus())) {
                session.setStatus("EXPIRED");
                permanentLinkSessionRepository.save(session);
                log.info("ℹ️ Session {} marked as EXPIRED", sessionCode);
            }

            return ResponseEntity.ok(buildSessionResponse(session));

        } catch (Exception e) {
            log.error("❌ Error getting session by code: {}", sessionCode, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @Override
    public ResponseEntity<PermanentLinkStatsResponse> getPermanentLinkStats(String username, String merchantSlug) {
        try {
            log.info("Getting stats for permanent link: {} by user: {}", merchantSlug, username);

            PermanentLink link = permanentLinkRepository.findByMerchantSlug(merchantSlug).orElse(null);
            if (link == null) {
                log.warn("⚠️ Permanent link not found: {}", merchantSlug);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            if (!link.getCreator().getUsername().equals(username)) {
                log.warn("⚠️ User {} is not the creator of permanent link {}", username, merchantSlug);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(null);
            }

            long activeSessions = permanentLinkSessionRepository.countByPermanentLinkAndStatus(link, "CREATED")
                    + permanentLinkSessionRepository.countByPermanentLinkAndStatus(link, "PENDING");

            PermanentLinkStatsResponse stats = PermanentLinkStatsResponse.builder()
                    .merchantSlug(link.getMerchantSlug())
                    .description(link.getDescription())
                    .isActive(link.getIsActive())
                    .totalSessions(link.getTotalSessions())
                    .totalCompletedPayments(link.getTotalCompletedPayments())
                    .totalAmountReceived(link.getTotalAmountReceived())
                    .activeSessions(activeSessions)
                    .createdAt(link.getCreatedAt())
                    .build();

            return ResponseEntity.ok(stats);

        } catch (Exception e) {
            log.error("❌ Error getting stats for permanent link: {} by user: {}", merchantSlug, username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @Override
    public ResponseEntity<String> deactivatePermanentLink(String username, String merchantSlug) {
        try {
            log.info("Deactivating permanent link: {} for user: {}", merchantSlug, username);

            PermanentLink link = permanentLinkRepository.findByMerchantSlug(merchantSlug).orElse(null);
            if (link == null) {
                log.warn("⚠️ Permanent link not found: {}", merchantSlug);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("{\"success\": false, \"error\": \"Permanent link not found\"}");
            }

            if (!link.getCreator().getUsername().equals(username)) {
                log.warn("⚠️ User {} is not the creator of permanent link {}", username, merchantSlug);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("{\"success\": false, \"error\": \"You are not authorized to deactivate this link\"}");
            }

            link.setIsActive(false);
            permanentLinkRepository.save(link);

            log.info("✅ Permanent link deactivated: {}", merchantSlug);
            return ResponseEntity.ok("{\"success\": true, \"message\": \"Permanent link deactivated successfully\"}");

        } catch (Exception e) {
            log.error("❌ Error deactivating permanent link: {} for user: {}", merchantSlug, username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"success\": false, \"error\": \"Internal server error\"}");
        }
    }

    @Override
    public ResponseEntity<String> activatePermanentLink(String username, String merchantSlug) {
        try {
            log.info("Activating permanent link: {} for user: {}", merchantSlug, username);

            PermanentLink link = permanentLinkRepository.findByMerchantSlug(merchantSlug).orElse(null);
            if (link == null) {
                log.warn("⚠️ Permanent link not found: {}", merchantSlug);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("{\"success\": false, \"error\": \"Permanent link not found\"}");
            }

            if (!link.getCreator().getUsername().equals(username)) {
                log.warn("⚠️ User {} is not the creator of permanent link {}", username, merchantSlug);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("{\"success\": false, \"error\": \"You are not authorized to activate this link\"}");
            }

            link.setIsActive(true);
            permanentLinkRepository.save(link);

            log.info("✅ Permanent link activated: {}", merchantSlug);
            return ResponseEntity.ok("{\"success\": true, \"message\": \"Permanent link activated successfully\"}");

        } catch (Exception e) {
            log.error("❌ Error activating permanent link: {} for user: {}", merchantSlug, username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"success\": false, \"error\": \"Internal server error\"}");
        }
    }

    /**
     * Résout le wallet marchand : requête native LIMIT 1, sinon liste JPA (primaire / non archivé / plus récent).
     */
    private Wallet resolveCreatorWallet(Users creator) {
        if (creator == null) {
            return null;
        }
        Wallet direct = walletRepository.findByUsers(creator);
        if (direct != null) {
            return direct;
        }
        List<Wallet> wallets = walletRepository.findWalletByUsers(creator);
        if (wallets == null || wallets.isEmpty()) {
            return null;
        }
        return wallets.stream()
                .filter(w -> w.getArchived() == null || !Boolean.TRUE.equals(w.getArchived()))
                .min(Comparator
                        .comparing((Wallet w) -> Boolean.TRUE.equals(w.getIsPrimary()) ? 0 : 1)
                        .thenComparing(Wallet::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .orElseGet(() -> wallets.get(0));
    }

    private static ErrorResponse buildMerchantWalletRequiredError(String username) {
        ArrayList<ErrorResponse.Error> errors = new ArrayList<>();
        errors.add(new ErrorResponse.Error(
                "MERCHANT_WALLET_REQUIRED",
                "Le marchand doit disposer d'au moins un portefeuille Akuunda actif pour encaisser. "
                        + "Créez ou activez un wallet depuis l'application pour le compte : " + username + ".",
                "merchantUsername"));
        ErrorResponse body = new ErrorResponse();
        body.setSuccess(false);
        body.setErrors(errors);
        return body;
    }

    private String generateSessionCode() {
        final String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            String code = PinHashUtil.generateRandomString(SESSION_CODE_LENGTH);
            code = code.toLowerCase().replaceAll("[^a-z0-9]", "");
            if (code.length() < SESSION_CODE_LENGTH) {
                StringBuilder sb = new StringBuilder(code);
                while (sb.length() < SESSION_CODE_LENGTH) {
                    int randomIndex = (int) (Math.random() * alphabet.length());
                    sb.append(alphabet.charAt(randomIndex));
                }
                code = sb.toString();
            }
            if (!permanentLinkSessionRepository.existsBySessionCode(code)) {
                return code;
            }
            log.debug("Session code {} already exists, retrying... (attempt {}/{})", code, attempt + 1, MAX_RETRIES);
        }
        return null;
    }

    private Wallet getAdminWallet() {
        if (adminWalletId != null && !adminWalletId.isEmpty()) {
            Wallet wallet = walletRepository.findById(adminWalletId).orElse(null);
            if (wallet != null) return wallet;
        }
        if (adminWalletAddress != null && !adminWalletAddress.isEmpty()) {
            Wallet wallet = walletRepository.findByAddress(adminWalletAddress);
            if (wallet != null) return wallet;
        }
        log.error("❌ Admin wallet not configured. Set 'akuunda.intermediate.wallet.id' or 'akuunda.intermediate.wallet.address' in application properties.");
        return null;
    }

    private PermanentLinkResponse buildPermanentLinkResponse(PermanentLink link) {
        return PermanentLinkResponse.builder()
                .id(link.getId())
                .merchantSlug(link.getMerchantSlug())
                .paymentUrl(baseUrl + "/m/" + link.getMerchantSlug())
                .description(link.getDescription())
                .amount(link.getAmount())
                .currency(link.getCurrency())
                .isActive(link.getIsActive())
                .totalSessions(link.getTotalSessions())
                .totalCompletedPayments(link.getTotalCompletedPayments())
                .totalAmountReceived(link.getTotalAmountReceived())
                .creatorUsername(link.getCreator().getUsername())
                .merchantUsername(link.getCreator().getUsername())
                .merchantUserId(link.getCreator().getUserId())
                .creatorFirstname(link.getCreator().getFirstname())
                .creatorLastname(link.getCreator().getLastname())
                .createdAt(link.getCreatedAt())
                .updatedAt(link.getUpdatedAt())
                .build();
    }

    private PermanentLinkSessionResponse buildSessionResponse(PermanentLinkSession session) {
        return PermanentLinkSessionResponse.builder()
                .id(session.getId())
                .sessionCode(session.getSessionCode())
                .permanentLinkSlug(session.getPermanentLink().getMerchantSlug())
                .amount(session.getAmount())
                .currency(session.getCurrency())
                .reference(session.getReference())
                .status(session.getStatus())
                .create2WalletAddress(session.getCreate2WalletAddress())
                .paymentIdBytes32(session.getPaymentIdBytes32())
                .payerName(session.getPayerName())
                .payerPhone(session.getPayerPhone())
                .payerEmail(session.getPayerEmail())
                .externalTransactionId(session.getExternalTransactionId())
                .paymentProvider(session.getPaymentProvider())
                .countryCode(session.getCountryCode())
                .expiresAt(session.getExpiresAt())
                .paidAt(session.getPaidAt())
                .createdAt(session.getCreatedAt())
                .merchantUsername(session.getPermanentLink().getCreator().getUsername())
                .merchantUserId(session.getPermanentLink().getCreator().getUserId())
                .build();
    }
}
