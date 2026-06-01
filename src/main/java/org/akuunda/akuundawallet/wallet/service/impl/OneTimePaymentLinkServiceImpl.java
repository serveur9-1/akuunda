package org.akuunda.akuundawallet.wallet.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.utils.PinHashUtil;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.dao.ConditionalPaymentRepository;
import org.akuunda.akuundawallet.wallet.api.dao.OneTimePaymentLinkRepository;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.api.dto.ConditionalPaymentConfirmationResponse;
import org.akuunda.akuundawallet.wallet.api.dto.CreateAndPayRequest;
import org.akuunda.akuundawallet.wallet.api.dto.CreateAndPayResponse;
import org.akuunda.akuundawallet.wallet.api.dto.CreateConditionalAndPayRequest;
import org.akuunda.akuundawallet.wallet.api.dto.CreateConditionalAndPayYcRequest;
import org.akuunda.akuundawallet.wallet.api.dto.CreateConditionalAndPayYcResponse;
import org.akuunda.akuundawallet.wallet.api.dto.CreateConditionalLinkYcRequest;
import org.akuunda.akuundawallet.wallet.api.dto.CreateConditionalLinkYcResponse;
import org.akuunda.akuundawallet.wallet.api.dto.CreateConditionalOneTimeLinkRequest;
import org.akuunda.akuundawallet.wallet.api.dto.CreateOneTimePaymentLinkRequest;
import org.akuunda.akuundawallet.wallet.api.dto.OneTimePaymentLinkResponse;
import org.akuunda.akuundawallet.wallet.api.dto.OneTimePaymentPayRequest;
import org.akuunda.akuundawallet.wallet.api.dto.RefundResponse;
import org.akuunda.akuundawallet.wallet.api.dto.GenereLinkRequest;
import org.akuunda.akuundawallet.wallet.api.dto.GenereLinkResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.MeldSessionResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.OnRampRequest;
import org.akuunda.akuundawallet.wallet.api.entities.ConditionalPayment;
import org.akuunda.akuundawallet.wallet.api.entities.OneTimePaymentLink;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.wallet.api.entities.QRCode;
import org.akuunda.akuundawallet.wallet.service.GenereLinkYcService;
import org.akuunda.akuundawallet.wallet.service.OneTimePaymentLinkService;
import org.akuunda.akuundawallet.wallet.service.OneTimePaymentMeldService;
import org.akuunda.akuundawallet.wallet.service.PaymentFactoryContractService;
import org.akuunda.akuundawallet.wallet.service.QRCodeService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaYellowCardClientService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OneTimePaymentLinkServiceImpl implements OneTimePaymentLinkService {

    private final OneTimePaymentLinkRepository oneTimePaymentLinkRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PaymentFactoryContractService paymentFactoryContractService;
    private final OneTimePaymentMeldService oneTimePaymentMeldService;
    private final ConditionalPaymentRepository conditionalPaymentRepository;
    private final QRCodeService qrCodeService;
    private final AkuundaYellowCardClientService akuundaYellowCardClientService;
    private final GenereLinkYcService genereLinkYcService;
    private final ObjectMapper objectMapper;

    @Value("${akuunda.intermediate.wallet.id:}")
    private String adminWalletId;

    @Value("${akuunda.intermediate.wallet.address:}")
    private String adminWalletAddress;

    @Value("${akuunda.web-pay.base-url:https://qr.akuunda-pay.io}")
    private String webPayBaseUrl;

    private static final int UNIQUE_CODE_LENGTH = 8;
    private static final int MAX_RETRIES = 10;
    private static final long DEFAULT_EXPIRY_HOURS = 24;
    private static final double MINIMAL_ONCHAIN_AMOUNT_USDC = 0.000001;
    private static final String LINK_TYPE_CONDITIONAL = "CONDITIONAL";
    private static final String PAYMENT_METHOD_LINK = "link";
    private static final String PAYMENT_METHOD_BANK = "bank";
    private static final String PAYMENT_METHOD_PUSH = "push";

    @Override
    public ResponseEntity<OneTimePaymentLinkResponse> createOneTimePaymentLink(String username, CreateOneTimePaymentLinkRequest request) {
        try {
            log.info("Creating one-time payment link for username: {}", username);

            if (request == null) {
                log.error("CreateOneTimePaymentLinkRequest is null for username: {}", username);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
            }

            Users creator = userRepository.getUsersByUsername(username);
            if (creator == null) {
                log.error("User not found: {}", username);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            String uniqueCode = generateUniqueCode();
            if (uniqueCode == null) {
                log.error("Failed to generate unique code after {} retries", MAX_RETRIES);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }

            LocalDateTime expiresAt = request.getExpiresAt() != null
                    ? request.getExpiresAt()
                    : LocalDateTime.now().plusHours(DEFAULT_EXPIRY_HOURS);

            String paymentIdBytes32 = paymentFactoryContractService.generatePaymentId(uniqueCode);

            Wallet creatorWallet = walletRepository.findByUsers(creator);
            if (creatorWallet == null) {
                log.error("Creator wallet not found for user: {}", username);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            Wallet adminWallet = getAdminWallet();
            if (adminWallet == null) {
                log.error("Admin wallet not configured");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(null);
            }

            long durationSeconds = java.time.Duration.between(LocalDateTime.now(), expiresAt).getSeconds();
            if (durationSeconds <= 0) {
                durationSeconds = DEFAULT_EXPIRY_HOURS * 3600;
            }

            String create2Address = paymentFactoryContractService.createPaymentLink(
                    paymentIdBytes32,
                    creatorWallet.getAddress(),
                    MINIMAL_ONCHAIN_AMOUNT_USDC,
                    request.getDescription(),
                    durationSeconds,
                    adminWallet
            );

            if (create2Address == null || create2Address.isEmpty()) {
                log.error("Failed to create payment link on smart contract for code: {}", uniqueCode);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }

            // ★ MODIFIÉ : ajout sourceAmount + sourceCurrency
            OneTimePaymentLink link = OneTimePaymentLink.builder()
                    .uniqueCode(uniqueCode)
                    .creator(creator)
                    .description(request.getDescription())
                    .amount(request.getAmount() != null ? request.getAmount() : 0.0)
                    .currency(request.getCurrency() != null ? request.getCurrency() : "USDC")
                    .sourceAmount(MINIMAL_ONCHAIN_AMOUNT_USDC)
                    .sourceCurrency("USDC")
                    .status("CREATED")
                    .expiresAt(expiresAt)
                    .paymentIdBytes32(paymentIdBytes32)
                    .create2WalletAddress(create2Address)
                    .build();

            link = oneTimePaymentLinkRepository.save(link);
            log.info("One-time payment link created with code: {} for user: {}, CREATE2: {}", uniqueCode, username, create2Address);

            return ResponseEntity.status(HttpStatus.CREATED).body(buildResponse(link));

        } catch (Exception e) {
            log.error("Error creating one-time payment link for username: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @Override
    public ResponseEntity<OneTimePaymentLinkResponse> getOneTimePaymentLinkByCode(String uniqueCode) {
        try {
            log.info("Getting one-time payment link by code: {}", uniqueCode);

            OneTimePaymentLink link = oneTimePaymentLinkRepository.findByUniqueCode(uniqueCode).orElse(null);
            if (link == null) {
                log.warn("One-time payment link not found: {}", uniqueCode);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.warn("One-time payment link expired: {}", uniqueCode);
                return ResponseEntity.status(HttpStatus.GONE).body(null);
            }

            return ResponseEntity.ok(buildResponse(link));

        } catch (Exception e) {
            log.error("Error getting one-time payment link by code: {}", uniqueCode, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @Override
    public ResponseEntity<List<OneTimePaymentLinkResponse>> getUserOneTimePaymentLinks(String username) {
        try {
            log.info("Getting one-time payment links for user: {}", username);

            Users user = userRepository.getUsersByUsername(username);
            if (user == null) {
                log.error("User not found: {}", username);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            List<OneTimePaymentLink> links = oneTimePaymentLinkRepository.findByCreatorOrderByCreatedAtDesc(user);
            List<OneTimePaymentLinkResponse> responses = links.stream()
                    .map(this::buildResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(responses);

        } catch (Exception e) {
            log.error("Error getting one-time payment links for user: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @Override
    public ResponseEntity<String> cancelOneTimePaymentLink(String username, String uniqueCode) {
        try {
            log.info("Cancelling one-time payment link: {} for user: {}", uniqueCode, username);

            Users user = userRepository.getUsersByUsername(username);
            if (user == null) {
                log.error("User not found: {}", username);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("{\"success\": false, \"error\": \"User not found\"}");
            }

            OneTimePaymentLink link = oneTimePaymentLinkRepository.findByUniqueCode(uniqueCode).orElse(null);
            if (link == null) {
                log.warn("One-time payment link not found: {}", uniqueCode);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("{\"success\": false, \"error\": \"One-time payment link not found\"}");
            }

            if (!link.getCreator().getUsername().equals(username)) {
                log.warn("User {} is not the creator of one-time payment link {}", username, uniqueCode);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body("{\"success\": false, \"error\": \"You are not authorized to cancel this link\"}");
            }

            if (!"CREATED".equals(link.getStatus())) {
                log.warn("One-time payment link {} cannot be cancelled (status: {})", uniqueCode, link.getStatus());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("{\"success\": false, \"error\": \"Only links with status CREATED can be cancelled\"}");
            }

            link.setStatus("CANCELLED");
            oneTimePaymentLinkRepository.save(link);

            log.info("One-time payment link cancelled: {}", uniqueCode);
            return ResponseEntity.ok("{\"success\": true, \"message\": \"One-time payment link cancelled successfully\"}");

        } catch (Exception e) {
            log.error("Error cancelling one-time payment link: {} for user: {}", uniqueCode, username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"success\": false, \"error\": \"Internal server error\"}");
        }
    }

    @Override
    @Transactional
    public ResponseEntity<RefundResponse> refundOneTimePaymentLink(String username, String uniqueCode) {
        try {
            log.info("Refunding one-time payment link: {} for user: {}", uniqueCode, username);

            Users user = userRepository.getUsersByUsername(username);
            if (user == null) {
                log.error("User not found: {}", username);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(RefundResponse.builder().success(false).error("User not found").build());
            }

            OneTimePaymentLink link = oneTimePaymentLinkRepository.findByUniqueCode(uniqueCode).orElse(null);
            if (link == null) {
                log.warn("One-time payment link not found: {}", uniqueCode);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(RefundResponse.builder().success(false).error("One-time payment link not found").build());
            }

            if (!link.getCreator().getUsername().equals(username)) {
                log.warn("User {} is not the creator of one-time payment link {}", username, uniqueCode);
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(RefundResponse.builder().success(false).error("You are not authorized to refund this link").build());
            }

            String status = link.getStatus();
            if (!"PENDING".equals(status) && !"EXPIRED".equals(status) && !"CREATED".equals(status)) {
                log.warn("One-time payment link {} cannot be refunded (status: {})", uniqueCode, status);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(RefundResponse.builder().success(false)
                                .error("Only links with status PENDING, EXPIRED or CREATED can be refunded").build());
            }

            Wallet merchantWallet = walletRepository.findByUsers(link.getCreator());
            if (merchantWallet == null) {
                log.error("Merchant wallet not found for user: {}", username);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(RefundResponse.builder().success(false).error("Merchant wallet not found").build());
            }

            Wallet adminWallet = getAdminWallet();
            if (adminWallet == null) {
                log.error("Admin wallet not configured");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(RefundResponse.builder().success(false).error("Admin wallet not configured").build());
            }

            String txHash = paymentFactoryContractService.refundPayment(
                    link.getPaymentIdBytes32(), merchantWallet.getAddress(), adminWallet);

            if (txHash == null || txHash.startsWith("REFUND-FAILED")) {
                log.error("Refund failed for link: {}, txHash: {}", uniqueCode, txHash);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(RefundResponse.builder().success(false).error("Refund failed. Please try again later.").build());
            }

            link.setStatus("REFUNDED");
            oneTimePaymentLinkRepository.save(link);

            log.info("One-time payment link refunded: {}, txHash: {}", uniqueCode, txHash);
            return ResponseEntity.ok(RefundResponse.builder()
                    .success(true)
                    .uniqueCode(uniqueCode)
                    .refundTxHash(txHash)
                    .refundToAddress(merchantWallet.getAddress())
                    .status("REFUNDED")
                    .build());

        } catch (Exception e) {
            log.error("Error refunding one-time payment link: {} for user: {}", uniqueCode, username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(RefundResponse.builder().success(false).error("Internal server error").build());
        }
    }

    @Override
    @Transactional
    public ResponseEntity<CreateAndPayResponse> createAndPay(String username, CreateAndPayRequest request) {
        try {
            log.info("🔵 create-and-pay: Creating link for user: {}", username);

            CreateOneTimePaymentLinkRequest createRequest = new CreateOneTimePaymentLinkRequest();
            createRequest.setDescription(request.getDescription());
            createRequest.setAmount(request.getAmount());
            createRequest.setCurrency(request.getCurrency());
            createRequest.setExpiresAt(request.getExpiresAt());

            ResponseEntity<OneTimePaymentLinkResponse> createResponse = createOneTimePaymentLink(username, createRequest);

            if (!createResponse.getStatusCode().is2xxSuccessful() || createResponse.getBody() == null) {
                log.error("❌ create-and-pay: Failed to create link");
                return ResponseEntity.status(createResponse.getStatusCode()).build();
            }

            OneTimePaymentLinkResponse linkResponse = createResponse.getBody();
            String uniqueCode = linkResponse.getUniqueCode();
            log.info("✅ create-and-pay: Link created with code: {}", uniqueCode);

            if (request.getPayerPhone() != null || request.getPayerName() != null || request.getPayerEmail() != null) {
                OneTimePaymentLink link = oneTimePaymentLinkRepository.findByUniqueCode(uniqueCode).orElse(null);
                if (link != null) {
                    if (request.getPayerPhone() != null) link.setPayerPhone(request.getPayerPhone());
                    if (request.getPayerName() != null) link.setPayerName(request.getPayerName());
                    if (request.getPayerEmail() != null) link.setPayerEmail(request.getPayerEmail());
                    oneTimePaymentLinkRepository.save(link);
                }
            }

            OneTimePaymentPayRequest payRequest = new OneTimePaymentPayRequest();
            payRequest.setServiceProvider(request.getServiceProvider());
            payRequest.setSourceCurrencyCode(request.getSourceCurrencyCode());
            payRequest.setSourceAmount(request.getSourceAmount());
            payRequest.setCountryCode(request.getCountryCode());

            ResponseEntity<MeldSessionResponse> payResponse = oneTimePaymentMeldService.createPaymentSession(uniqueCode, payRequest);

            CreateAndPayResponse.CreateAndPayResponseBuilder responseBuilder = CreateAndPayResponse.builder()
                    .id(linkResponse.getId())
                    .uniqueCode(linkResponse.getUniqueCode())
                    .description(linkResponse.getDescription())
                    .status("PENDING")
                    .create2WalletAddress(linkResponse.getCreate2WalletAddress())
                    .paymentIdBytes32(linkResponse.getPaymentIdBytes32())
                    .expiresAt(linkResponse.getExpiresAt())
                    .createdAt(linkResponse.getCreatedAt())
                    .payerPhone(request.getPayerPhone())
                    .payerName(request.getPayerName())
                    .payerEmail(request.getPayerEmail());

            if (payResponse.getStatusCode().is2xxSuccessful() && payResponse.getBody() != null) {
                MeldSessionResponse meldSession = payResponse.getBody();
                responseBuilder.meldSessionId(meldSession.getId());
                responseBuilder.meldWidgetUrl(
                        meldSession.getPayRedirectUrl() != null
                                ? meldSession.getPayRedirectUrl()
                                : meldSession.getWidgetUrl());
                log.info("✅ create-and-pay: Meld session created for link: {}", uniqueCode);
            } else {
                log.warn("⚠️ create-and-pay: Meld session creation failed, link created but payment not initiated");
                responseBuilder.status("CREATED");
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(responseBuilder.build());

        } catch (Exception e) {
            log.error("❌ Error in create-and-pay for username: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String generateUniqueCode() {
        final String alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            String code = PinHashUtil.generateRandomString(UNIQUE_CODE_LENGTH);
            code = code.toLowerCase().replaceAll("[^a-z0-9]", "");
            if (code.length() < UNIQUE_CODE_LENGTH) {
                StringBuilder sb = new StringBuilder(code);
                while (sb.length() < UNIQUE_CODE_LENGTH) {
                    int randomIndex = (int) (Math.random() * alphabet.length());
                    sb.append(alphabet.charAt(randomIndex));
                }
                code = sb.toString();
            }
            if (!oneTimePaymentLinkRepository.existsByUniqueCode(code)) {
                return code;
            }
            log.debug("Code {} already exists, retrying... (attempt {}/{})", code, attempt + 1, MAX_RETRIES);
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

    // ★ MODIFIÉ : buildResponse() fait maintenant le lookup du paymentCode + expose sourceAmount/sourceCurrency
        private OneTimePaymentLinkResponse buildResponse(OneTimePaymentLink link) {
        // Lookup du ConditionalPayment associé pour paymentCode + vrai montant USDC
        String paymentCode = null;
        Double sourceAmount = link.getSourceAmount();
        String sourceCurrency = link.getSourceCurrency();

        if (link.getConditionalPaymentId() != null) {
            ConditionalPayment cp = conditionalPaymentRepository
                    .findById(link.getConditionalPaymentId()).orElse(null);
            if (cp != null) {
                paymentCode = cp.getPaymentCode();
                // Le vrai montant USDC reçu est dans le ConditionalPayment
                if (cp.getAmount() != null && cp.getAmount() > 0) {
                    sourceAmount = cp.getAmount();
                }
                if (cp.getCurrency() != null && !cp.getCurrency().isEmpty()) {
                    sourceCurrency = cp.getCurrency();
                }
            }
        }

        return OneTimePaymentLinkResponse.builder()
                .id(link.getId())
                .uniqueCode(link.getUniqueCode())
                .paymentCode(paymentCode)
                .description(link.getDescription())
                .amount(link.getAmount())
                .currency(link.getCurrency())
                .sourceAmount(sourceAmount)
                .sourceCurrency(sourceCurrency)
                .status(link.getStatus())
                .expiresAt(link.getExpiresAt())
                .payerPhone(link.getPayerPhone())
                .payerName(link.getPayerName())
                .payerEmail(link.getPayerEmail())
                .paidAt(link.getPaidAt())
                .creatorUsername(link.getCreator().getUsername())
                .creatorName(buildCreatorName(link.getCreator()))
                .createdAt(link.getCreatedAt())
                .updatedAt(link.getUpdatedAt())
                .create2WalletAddress(link.getCreate2WalletAddress())
                .paymentIdBytes32(link.getPaymentIdBytes32())
                .linkType(link.getLinkType())
                .serviceType(link.getServiceType())
                .serviceStartDate(link.getServiceStartDate())
                .cancellationDeadline(link.getCancellationDeadline())
                .qrCodeUrl(link.getQrCodeUrl())
                .qrCodeToken(link.getQrCodeToken())
                .conditionalPaymentId(link.getConditionalPaymentId())
                .build();
    }

    private String buildCreatorName(Users creator) {
        if (creator == null) return null;
        String first = creator.getFirstname();
        String last = creator.getLastname();
        if (first == null && last == null) return null;
        String name = ((first != null ? first : "") + " " + (last != null ? last : "")).trim();
        return name.isEmpty() ? null : name;
    }

    @Override
    @Transactional
    public ResponseEntity<CreateAndPayResponse> createConditionalAndPay(
            String username, CreateConditionalAndPayRequest request) {
        try {
            log.info("🔵 create-conditional-and-pay: Creating conditional link for user: {}", username);

            CreateConditionalOneTimeLinkRequest conditionalRequest = new CreateConditionalOneTimeLinkRequest();
            conditionalRequest.setDescription(request.getDescription());
            conditionalRequest.setAmount(request.getAmount());
            conditionalRequest.setCurrency(request.getCurrency());
            conditionalRequest.setExpiresAt(request.getExpiresAt());
            conditionalRequest.setServiceType(request.getServiceType());
            conditionalRequest.setServiceStartDate(request.getServiceStartDate());
            conditionalRequest.setCancellationDeadline(request.getCancellationDeadline());

            ResponseEntity<OneTimePaymentLinkResponse> conditionalResponse =
                    createConditionalOneTimePaymentLink(username, conditionalRequest);

            if (!conditionalResponse.getStatusCode().is2xxSuccessful() || conditionalResponse.getBody() == null) {
                log.error("❌ create-conditional-and-pay: Failed to create conditional link");
                return ResponseEntity.status(conditionalResponse.getStatusCode()).build();
            }

            OneTimePaymentLinkResponse linkResponse = conditionalResponse.getBody();
            String uniqueCode = linkResponse.getUniqueCode();
            log.info("✅ create-conditional-and-pay: Conditional link created with code: {}", uniqueCode);

            if (request.getPayerPhone() != null || request.getPayerName() != null || request.getPayerEmail() != null) {
                OneTimePaymentLink link = oneTimePaymentLinkRepository.findByUniqueCode(uniqueCode).orElse(null);
                if (link != null) {
                    if (request.getPayerPhone() != null) link.setPayerPhone(request.getPayerPhone());
                    if (request.getPayerName() != null) link.setPayerName(request.getPayerName());
                    if (request.getPayerEmail() != null) link.setPayerEmail(request.getPayerEmail());
                    oneTimePaymentLinkRepository.save(link);
                }
            }

            OneTimePaymentPayRequest payRequest = new OneTimePaymentPayRequest();
            payRequest.setServiceProvider(request.getServiceProvider());
            payRequest.setSourceCurrencyCode(request.getSourceCurrencyCode());
            payRequest.setSourceAmount(request.getSourceAmount());
            payRequest.setCountryCode(request.getCountryCode());

            ResponseEntity<MeldSessionResponse> payResponse =
                    oneTimePaymentMeldService.createPaymentSession(uniqueCode, payRequest);

            CreateAndPayResponse.CreateAndPayResponseBuilder responseBuilder = CreateAndPayResponse.builder()
                    .id(linkResponse.getId())
                    .uniqueCode(linkResponse.getUniqueCode())
                    .description(linkResponse.getDescription())
                    .status("PENDING")
                    .create2WalletAddress(linkResponse.getCreate2WalletAddress())
                    .paymentIdBytes32(linkResponse.getPaymentIdBytes32())
                    .expiresAt(linkResponse.getExpiresAt())
                    .createdAt(linkResponse.getCreatedAt())
                    .payerPhone(request.getPayerPhone())
                    .payerName(request.getPayerName())
                    .payerEmail(request.getPayerEmail());

            if (payResponse.getStatusCode().is2xxSuccessful() && payResponse.getBody() != null) {
                MeldSessionResponse meldSession = payResponse.getBody();
                responseBuilder.meldSessionId(meldSession.getId());
                responseBuilder.meldWidgetUrl(
                        meldSession.getPayRedirectUrl() != null
                                ? meldSession.getPayRedirectUrl()
                                : meldSession.getWidgetUrl());
                log.info("✅ create-conditional-and-pay: Meld session created for link: {}", uniqueCode);
            } else {
                log.warn("⚠️ create-conditional-and-pay: Meld session creation failed, link created but payment not initiated");
                responseBuilder.status("CREATED");
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(responseBuilder.build());

        } catch (Exception e) {
            log.error("❌ Error in create-conditional-and-pay for username: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    @Transactional
    public ResponseEntity<CreateConditionalAndPayYcResponse> createConditionalAndPayYc(
            String username, CreateConditionalAndPayYcRequest request) {
        try {
            log.info("🔵 create-conditional-and-pay-yc: Creating conditional link for user: {}", username);

            CreateConditionalOneTimeLinkRequest conditionalRequest = new CreateConditionalOneTimeLinkRequest();
            conditionalRequest.setDescription(request.getDescription());
            conditionalRequest.setAmount(request.getAmount());
            conditionalRequest.setCurrency(request.getCurrency());
            conditionalRequest.setExpiresAt(request.getExpiresAt());
            conditionalRequest.setServiceType(request.getServiceType());
            conditionalRequest.setServiceStartDate(request.getServiceStartDate());
            conditionalRequest.setCancellationDeadline(request.getCancellationDeadline());

            ResponseEntity<OneTimePaymentLinkResponse> conditionalResponse =
                    createConditionalOneTimePaymentLink(username, conditionalRequest);

            if (!conditionalResponse.getStatusCode().is2xxSuccessful() || conditionalResponse.getBody() == null) {
                log.error("❌ create-conditional-and-pay-yc: Failed to create conditional link");
                return ResponseEntity.status(conditionalResponse.getStatusCode()).build();
            }

            OneTimePaymentLinkResponse linkResponse = conditionalResponse.getBody();
            String uniqueCode = linkResponse.getUniqueCode();
            log.info("✅ create-conditional-and-pay-yc: Conditional link created with code: {}", uniqueCode);

            OneTimePaymentLink link = oneTimePaymentLinkRepository.findByUniqueCode(uniqueCode).orElse(null);
            if (link != null && (request.getPayerPhone() != null || request.getPayerName() != null || request.getPayerEmail() != null)) {
                if (request.getPayerPhone() != null) link.setPayerPhone(request.getPayerPhone());
                if (request.getPayerName() != null) link.setPayerName(request.getPayerName());
                if (request.getPayerEmail() != null) link.setPayerEmail(request.getPayerEmail());
                oneTimePaymentLinkRepository.save(link);
            }

            Users creator = userRepository.getUsersByUsername(username);
            if (creator == null || creator.getUserId() == null || creator.getUserId().isBlank()) {
                log.error("❌ create-conditional-and-pay-yc: Creator or userId not found for username: {}", username);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            OnRampRequest ycRequest = new OnRampRequest();
            ycRequest.setChannelId(request.getChannelId());
            ycRequest.setAmount(request.getYcAmount());
            ycRequest.setCurrency(request.getYcCurrency());
            ycRequest.setCountry(request.getCountry());
            ycRequest.setReason(request.getReason());
            ycRequest.setForceAccept(true);
            ycRequest.setDirectSettlement(true);
            ycRequest.setRecipient(request.getRecipient());
            ycRequest.setSource(request.getSource());

            ResponseEntity<Object> ycResponse = akuundaYellowCardClientService.createCollectionForPermanentLink(
                    ycRequest, linkResponse.getCreate2WalletAddress(), creator.getUserId());

            CreateConditionalAndPayYcResponse.CreateConditionalAndPayYcResponseBuilder responseBuilder =
                    CreateConditionalAndPayYcResponse.builder()
                            .id(linkResponse.getId())
                            .uniqueCode(linkResponse.getUniqueCode())
                            .description(linkResponse.getDescription())
                            .status("PENDING")
                            .create2WalletAddress(linkResponse.getCreate2WalletAddress())
                            .paymentIdBytes32(linkResponse.getPaymentIdBytes32())
                            .expiresAt(linkResponse.getExpiresAt())
                            .createdAt(linkResponse.getCreatedAt())
                            .payerPhone(request.getPayerPhone())
                            .payerName(request.getPayerName())
                            .payerEmail(request.getPayerEmail());

            if (ycResponse.getStatusCode().is2xxSuccessful() && ycResponse.getBody() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> ycBody = (Map<String, Object>) ycResponse.getBody();
                String ycTransactionId = (String) ycBody.get("transactionId");
                String redirectUrl = null;

                Object ycRaw = ycBody.get("yellowCardResponse");
                if (ycRaw instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> ycRawMap = (Map<String, Object>) ycRaw;
                    redirectUrl = (String) ycRawMap.get("redirectUrl");
                }

                @SuppressWarnings("unchecked")
                Map<String, Object> bankInfo = (Map<String, Object>) ycBody.get("bankInfo");

                String paymentMethod;
                if (redirectUrl != null && !redirectUrl.isBlank()) {
                    paymentMethod = PAYMENT_METHOD_LINK;
                } else if (bankInfo != null && !bankInfo.isEmpty()) {
                    paymentMethod = PAYMENT_METHOD_BANK;
                } else {
                    paymentMethod = PAYMENT_METHOD_PUSH;
                }

                responseBuilder
                        .yellowCardTransactionId(ycTransactionId)
                        .redirectUrl(redirectUrl)
                        .bankInfo(bankInfo)
                        .paymentMethod(paymentMethod);

                if (link != null) {
                    link.setYellowCardTransactionId(ycTransactionId);
                    link.setStatus("PENDING");
                    oneTimePaymentLinkRepository.save(link);
                }

                log.info("✅ create-conditional-and-pay-yc: YellowCard collection created for link: {}, transactionId: {}",
                        uniqueCode, ycTransactionId);
            } else {
                log.warn("⚠️ create-conditional-and-pay-yc: YellowCard collection failed, link created but payment not initiated");
                responseBuilder.status("CREATED");
            }

            return ResponseEntity.status(HttpStatus.CREATED).body(responseBuilder.build());

        } catch (Exception e) {
            log.error("❌ Error in create-conditional-and-pay-yc for username: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    @Transactional
    public ResponseEntity<OneTimePaymentLinkResponse> createConditionalOneTimePaymentLink(
            String username, CreateConditionalOneTimeLinkRequest request) {
        try {
            log.info("Creating conditional one-time payment link for username: {}", username);

            if (request == null) {
                log.error("CreateConditionalOneTimeLinkRequest is null for username: {}", username);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
            }

            Users creator = userRepository.getUsersByUsername(username);
            if (creator == null) {
                log.error("User not found: {}", username);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            String uniqueCode = generateUniqueCode();
            if (uniqueCode == null) {
                log.error("Failed to generate unique code after {} retries", MAX_RETRIES);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }

            LocalDateTime expiresAt = request.getExpiresAt() != null
                    ? request.getExpiresAt()
                    : LocalDateTime.now().plusHours(DEFAULT_EXPIRY_HOURS);

            String paymentIdBytes32 = paymentFactoryContractService.generatePaymentId(uniqueCode);

            Wallet creatorWallet = walletRepository.findByUsers(creator);
            if (creatorWallet == null) {
                log.error("Creator wallet not found for user: {}", username);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            Wallet adminWallet = getAdminWallet();
            if (adminWallet == null) {
                log.error("Admin wallet not configured");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(null);
            }

            long durationSeconds = java.time.Duration.between(LocalDateTime.now(), expiresAt).getSeconds();
            if (durationSeconds <= 0) {
                durationSeconds = DEFAULT_EXPIRY_HOURS * 3600;
            }

            String create2Address = paymentFactoryContractService.createPaymentLink(
                    paymentIdBytes32,
                    adminWallet.getAddress(),
                    MINIMAL_ONCHAIN_AMOUNT_USDC,
                    request.getDescription(),
                    durationSeconds,
                    adminWallet
            );

            if (create2Address == null || create2Address.isEmpty()) {
                log.error("Failed to create conditional payment link on smart contract for code: {}", uniqueCode);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }

            // ★ MODIFIÉ : ajout sourceAmount + sourceCurrency
            OneTimePaymentLink link = OneTimePaymentLink.builder()
                    .uniqueCode(uniqueCode)
                    .creator(creator)
                    .description(request.getDescription())
                    .amount(request.getAmount() != null ? request.getAmount() : 0.0)
                    .currency(request.getCurrency() != null ? request.getCurrency() : "EUR")
                    .sourceAmount(MINIMAL_ONCHAIN_AMOUNT_USDC)
                    .sourceCurrency("USDC")
                    .status("CREATED")
                    .linkType(LINK_TYPE_CONDITIONAL)
                    .serviceType(request.getServiceType())
                    .serviceStartDate(request.getServiceStartDate())
                    .cancellationDeadline(request.getCancellationDeadline())
                    .expiresAt(expiresAt)
                    .paymentIdBytes32(paymentIdBytes32)
                    .create2WalletAddress(create2Address)
                    .build();

            link = oneTimePaymentLinkRepository.save(link);
            log.info("Conditional one-time payment link created with code: {} for user: {}, CREATE2: {}", uniqueCode, username, create2Address);

            return ResponseEntity.status(HttpStatus.CREATED).body(buildResponse(link));

        } catch (Exception e) {
            log.error("Error creating conditional one-time payment link for username: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    @Override
    public ResponseEntity<ConditionalPaymentConfirmationResponse> getConditionalPaymentConfirmation(String uniqueCode) {
        try {
            log.info("Getting conditional payment confirmation for link: {}", uniqueCode);

            OneTimePaymentLink link = oneTimePaymentLinkRepository.findByUniqueCode(uniqueCode).orElse(null);
            if (link == null) {
                log.warn("One-time payment link not found: {}", uniqueCode);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ConditionalPaymentConfirmationResponse.builder()
                                .error("Lien de paiement introuvable")
                                .build());
            }

            if (!"CONDITIONAL".equals(link.getLinkType())) {
                log.warn("Payment link {} is not of type CONDITIONAL", uniqueCode);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ConditionalPaymentConfirmationResponse.builder()
                                .error("Ce lien n'est pas un lien de paiement conditionnel")
                                .build());
            }

            String paymentCode = null;
            if (link.getConditionalPaymentId() != null) {
                ConditionalPayment conditionalPayment = conditionalPaymentRepository
                        .findById(link.getConditionalPaymentId()).orElse(null);
                if (conditionalPayment != null) {
                    paymentCode = conditionalPayment.getPaymentCode();
                }
            }

            String vendorFirstName = link.getCreator() != null ? link.getCreator().getFirstname() : null;
            String vendorLastName = link.getCreator() != null ? link.getCreator().getLastname() : null;

            String confirmationMessage;
            if ("ESCROW_FUNDED".equals(link.getStatus())) {
                confirmationMessage = "Réservation confirmée. Vous avez conditionné votre paiement. Téléchargez Akuunda Pay pour recevoir automatiquement vos fonds en cas d'annulation.";
            } else if ("PAID".equals(link.getStatus())) {
                confirmationMessage = "Paiement effectué ✅";
            } else if ("PENDING".equals(link.getStatus()) || "CREATED".equals(link.getStatus())) {
                confirmationMessage = "En attente de confirmation du paiement...";
            } else {
                confirmationMessage = "Statut : " + link.getStatus();
            }

            return ResponseEntity.ok(ConditionalPaymentConfirmationResponse.builder()
                    .uniqueCode(link.getUniqueCode())
                    .paymentCode(paymentCode)
                    .status(link.getStatus())
                    .description(link.getDescription())
                    .amount(link.getAmount())
                    .currency(link.getCurrency())
                    .serviceType(link.getServiceType())
                    .serviceStartDate(link.getServiceStartDate())
                    .expiresAt(link.getExpiresAt())
                    .qrCodeUrl(link.getQrCodeUrl())
                    .qrCodeToken(link.getQrCodeToken())
                    .vendorFirstName(vendorFirstName)
                    .vendorLastName(vendorLastName)
                    .confirmationMessage(confirmationMessage)
                    .build());

        } catch (Exception e) {
            log.error("Error getting conditional payment confirmation for link: {}", uniqueCode, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ConditionalPaymentConfirmationResponse.builder()
                            .error("Erreur interne du serveur")
                            .build());
        }
    }

    @Override
    // NO @Transactional here — inner repository/service calls each have their own transaction.
    // Adding @Transactional here causes UnexpectedRollbackException when an inner call throws
    // because Spring marks the shared transaction as rollback-only before the outer catch runs.
    public ResponseEntity<OneTimePaymentLinkResponse> recoverStuckLink(String uniqueCode) {
        try {
            log.info("🔧 [{}] Manual recovery requested for stuck link", uniqueCode);

            OneTimePaymentLink link = oneTimePaymentLinkRepository.findByUniqueCode(uniqueCode).orElse(null);
            if (link == null) {
                log.warn("🔧 [{}] Link not found for recovery", uniqueCode);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            if (!"CONDITIONAL".equals(link.getLinkType())) {
                log.warn("🔧 [{}] Link is not CONDITIONAL (type={}), recovery not applicable", uniqueCode, link.getLinkType());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
            }

            // Idempotency check
            String paymentCode = "CP-" + uniqueCode.toUpperCase();
            ConditionalPayment existingPayment = conditionalPaymentRepository.findByPaymentCode(paymentCode).orElse(null);
            if (existingPayment != null && link.getQrCodeToken() != null && !link.getQrCodeToken().isEmpty()
                    && ("ESCROW_FUNDED".equals(link.getStatus()) || "PAID".equals(link.getStatus()))) {

                log.info("🔧 [{}] Link is already recovered", uniqueCode);
                return ResponseEntity.ok(buildResponse(link));
            }

            Wallet vendorWallet = walletRepository.findByUsers(link.getCreator());
            if (vendorWallet == null) {
                log.error("🔧 [{}] Vendor wallet not found", uniqueCode);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }

            Wallet adminWallet = getAdminWallet();
            if (adminWallet == null) {
                log.error("🔧 [{}] Admin wallet not configured", uniqueCode);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(null);
            }

            // Step 1: Create or reuse ConditionalPayment
            ConditionalPayment payment;
            if (existingPayment != null) {
                payment = existingPayment;
                log.info("🔧 [{}] Reusing existing ConditionalPayment: {}", uniqueCode, payment.getId());
            } else {
                try {
                    payment = conditionalPaymentRepository.save(ConditionalPayment.builder()
                            .paymentCode(paymentCode)
                            .client(null)
                            .vendor(link.getCreator())
                            .serviceType(link.getServiceType())
                            .description(link.getDescription())
                            .amount(link.getAmount())
                            .currency(link.getCurrency() != null ? link.getCurrency() : "USDC")
                            .status("pending_condition")
                            .escrowContractAddress(null)
                            .depositTransactionHash(link.getDepositTxHash())
                            .serviceStartDate(link.getServiceStartDate())
                            .cancellationDeadline(link.getCancellationDeadline())
                            .intermediateWallet(adminWallet)
                            .vendorWallet(vendorWallet)
                            .clientWallet(vendorWallet)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build());
                    log.info("🔧 [{}] ConditionalPayment created: {}", uniqueCode, payment.getId());
                } catch (Exception e) {
                    log.error("🔧 [{}] Failed to create ConditionalPayment", uniqueCode, e);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
                }
            }

            // Step 2: Generate QR code
            String qrCodeUrl;
            String qrCodeToken;
            if (link.getQrCodeToken() != null && !link.getQrCodeToken().isEmpty()) {
                qrCodeUrl = link.getQrCodeUrl();
                qrCodeToken = link.getQrCodeToken();
                log.info("🔧 [{}] Reusing existing QR code", uniqueCode);
            } else {
                try {
                    long hoursUntilService = link.getServiceStartDate() != null
                            ? java.time.temporal.ChronoUnit.HOURS.between(LocalDateTime.now(), link.getServiceStartDate())
                            : 720;
                    int qrExpiryHours = (int) Math.max(hoursUntilService + 24, 48);
                    QRCode qrCode = qrCodeService.generateQRCode(payment.getId(), qrExpiryHours);
                    qrCodeUrl = qrCode.getQrCodeUrl();
                    qrCodeToken = qrCode.getToken();
                    log.info("🔧 [{}] QR code generated: token={}", uniqueCode, qrCodeToken);
                } catch (Exception e) {
                    log.error("🔧 [{}] QR code generation failed, saving without QR", uniqueCode, e);
                    // Still update the link with the ConditionalPayment even without QR
                    try {
                        link.setConditionalPaymentId(payment.getId());
                        link.setStatus("ESCROW_FUNDED");
                        link.setUpdatedAt(LocalDateTime.now());
                        oneTimePaymentLinkRepository.save(link);
                    } catch (Exception saveErr) {
                        log.error("🔧 [{}] Failed to save partial recovery", uniqueCode, saveErr);
                    }
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
                }
            }

            // Step 3: Update link with all recovery data
            try {
                link.setStatus("ESCROW_FUNDED");
                link.setUpdatedAt(LocalDateTime.now());
                link.setConditionalPaymentId(payment.getId());
                link.setQrCodeUrl(qrCodeUrl);
                link.setQrCodeToken(qrCodeToken);
                link = oneTimePaymentLinkRepository.save(link);
                log.info("✅ [{}] Manual recovery successful (ESCROW_FUNDED): paymentCode={}, qrToken={}",
                        uniqueCode, paymentCode, qrCodeToken);
            } catch (Exception e) {
                log.error("🔧 [{}] Failed to update link after recovery", uniqueCode, e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
            }

            return ResponseEntity.ok(buildResponse(link));

        } catch (Exception e) {
            log.error("❌ [{}] Error during manual recovery", uniqueCode, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    // =========================================================================
    // 2-STEP YELLOWCARD FLOW
    // =========================================================================

    /**
     * Step 1 — Creates the conditional on-chain escrow link and stores YellowCard
     * payment data on the link entity. Does NOT call YellowCard.
     * Status returned: "CREATED".
     */
    @Override
    @Transactional
    public ResponseEntity<CreateConditionalLinkYcResponse> createConditionalLinkYc(
            String username, CreateConditionalLinkYcRequest request) {
        try {
            log.info("🔵 create-conditional-link-yc: Creating conditional link for user: {}", username);

            CreateConditionalOneTimeLinkRequest conditionalRequest = new CreateConditionalOneTimeLinkRequest();
            conditionalRequest.setDescription(request.getDescription());
            conditionalRequest.setAmount(request.getAmount());
            conditionalRequest.setCurrency(request.getCurrency());
            conditionalRequest.setExpiresAt(request.getExpiresAt());
            conditionalRequest.setServiceType(request.getServiceType());
            conditionalRequest.setServiceStartDate(request.getServiceStartDate());
            conditionalRequest.setCancellationDeadline(request.getCancellationDeadline());

            ResponseEntity<OneTimePaymentLinkResponse> conditionalResponse =
                    createConditionalOneTimePaymentLink(username, conditionalRequest);

            if (!conditionalResponse.getStatusCode().is2xxSuccessful() || conditionalResponse.getBody() == null) {
                log.error("❌ create-conditional-link-yc: Failed to create conditional link");
                return ResponseEntity.status(conditionalResponse.getStatusCode()).build();
            }

            OneTimePaymentLinkResponse linkResponse = conditionalResponse.getBody();
            String uniqueCode = linkResponse.getUniqueCode();
            log.info("✅ create-conditional-link-yc: Conditional link created with code: {}", uniqueCode);

            OneTimePaymentLink link = oneTimePaymentLinkRepository.findByUniqueCode(uniqueCode).orElse(null);
            if (link != null) {
                if (request.getPayerPhone() != null) link.setPayerPhone(request.getPayerPhone());
                if (request.getPayerName() != null) link.setPayerName(request.getPayerName());
                if (request.getPayerEmail() != null) link.setPayerEmail(request.getPayerEmail());
                link.setYcChannelId(request.getChannelId());
                link.setYcCountry(request.getCountry());
                link.setYcCurrency(request.getLocalCurrency());
                link.setYcReason(request.getReason());
                if (request.getRecipient() != null) {
                    link.setYcRecipientJson(objectMapper.writeValueAsString(request.getRecipient()));
                }
                if (request.getSource() != null) {
                    link.setYcSourceJson(objectMapper.writeValueAsString(request.getSource()));
                }
                oneTimePaymentLinkRepository.save(link);
            }

            String create2WalletAddress = linkResponse.getCreate2WalletAddress();
            GenereLinkRequest genereLinkReq = GenereLinkRequest.builder()
                    .recipient(request.getRecipient())
                    .source(request.getSource())
                    .amount(request.getAmount())
                    .currency(request.getLocalCurrency())
                    .country(request.getCountry())
                    .reason(request.getReason() != null ? request.getReason() : "payment")
                    .channelId(request.getChannelId())
                    .settlementWalletAddress(create2WalletAddress)
                    .uniqueCode(uniqueCode)
                    .build();

            GenereLinkResponse genereLinkResp = genereLinkYcService.generateLink(genereLinkReq);

            if (link != null) {
                link.setYcSequenceId(genereLinkResp.getTransactionId());
                link.setYcPaymentUrl(genereLinkResp.getPaymentUrl());
                oneTimePaymentLinkRepository.save(link);
            }

            log.info("✅ create-conditional-link-yc: paymentUrl={}", genereLinkResp.getPaymentUrl());

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    CreateConditionalLinkYcResponse.builder()
                            .id(linkResponse.getId())
                            .uniqueCode(uniqueCode)
                            .description(linkResponse.getDescription())
                            .status("CREATED")
                            .create2WalletAddress(create2WalletAddress)
                            .paymentIdBytes32(linkResponse.getPaymentIdBytes32())
                            .expiresAt(linkResponse.getExpiresAt())
                            .createdAt(linkResponse.getCreatedAt())
                            .payerPhone(request.getPayerPhone())
                            .payerName(request.getPayerName())
                            .payerEmail(request.getPayerEmail())
                            .paymentUrl(genereLinkResp.getPaymentUrl())
                            .build()
            );

        } catch (Exception e) {
            log.error("❌ Error in create-conditional-link-yc for username: {}", username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
