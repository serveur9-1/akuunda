package org.akuunda.akuundawallet.wallet.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dao.CheckoutSessionRepository;
import org.akuunda.akuundawallet.wallet.api.dao.CountryCurrencyRepository;
import org.akuunda.akuundawallet.wallet.api.dao.MerchantApiKeyRepository;
import org.akuunda.akuundawallet.wallet.api.dto.CheckoutCountryOption;
import org.akuunda.akuundawallet.wallet.api.dto.CheckoutSessionResponse;
import org.akuunda.akuundawallet.wallet.api.dto.PaymentCreateRequest;
import org.akuunda.akuundawallet.wallet.api.dto.PaymentResponse;
import org.akuunda.akuundawallet.wallet.api.dto.RefundRequest;
import org.akuunda.akuundawallet.wallet.api.entities.CountryCurrency;
import org.akuunda.akuundawallet.wallet.api.entities.CheckoutSession;
import org.akuunda.akuundawallet.wallet.api.entities.MerchantApiKey;
import org.akuunda.akuundawallet.wallet.api.entities.PermanentLink;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.service.PaymentsService;
import org.akuunda.akuundawallet.wallet.service.WebhookService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentsServiceImpl implements PaymentsService {

    private final MerchantApiKeyRepository merchantApiKeyRepository;
    private final CheckoutSessionRepository checkoutSessionRepository;
    private final CountryCurrencyRepository countryCurrencyRepository;
    private final ObjectMapper objectMapper;
    private final WebhookService webhookService;

    @Value("${akuunda.frontend.base-url:https://qr.akuunda-pay.io}")
    private String frontendBaseUrl;

    private static final String CODE_PREFIX = "pay_";
    private static final int CODE_LENGTH = 16;
    private static final long EXPIRY_HOURS = 1;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    private static final String MODE_LIVE = "live";
    private static final String MODE_TEST = "test";

    @Override
    public ResponseEntity<PaymentResponse> createPayment(String apiKey, String idempotencyKey, PaymentCreateRequest request) {
        try {
            MerchantApiKey key = resolveActiveKey(apiKey);
            if (key == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            // Idempotency : si une clé est fournie et qu'on a déjà créé un paiement pour
            // cette clé API + cet idempotencyKey, on renvoie l'existant tel quel.
            String idemKey = normalize(idempotencyKey);
            if (idemKey != null) {
                CheckoutSession existing = checkoutSessionRepository
                        .findByMerchantApiKeyAndIdempotencyKey(key, idemKey)
                        .orElse(null);
                if (existing != null) {
                    log.info("Idempotent replay: returning existing payment {} for key {}", existing.getCheckoutCode(), idemKey);
                    return ResponseEntity.status(HttpStatus.OK).body(toResponse(existing));
                }
            }

            PermanentLink permanentLink = key.getPermanentLink();
            String id = CODE_PREFIX + randomToken(CODE_LENGTH);
            String returnUrl = pick(request.getReturnUrl(), key.getCallbackUrl());
            String cancelUrl = pick(request.getCancelUrl(), key.getCancelUrl());
            String webhookUrl = pick(request.getWebhookUrl(), key.getWebhookUrl());
            String metadataJson = serializeMetadata(request.getMetadata());
            LocalDateTime expiresAt = LocalDateTime.now().plusHours(EXPIRY_HOURS);
            String mode = resolveMode(key);

            String paymentCountry = request.getPaymentCountry() != null
                    ? request.getPaymentCountry().trim().toUpperCase()
                    : null;

            CheckoutSession session = CheckoutSession.builder()
                    .checkoutCode(id)
                    .merchantApiKey(key)
                    .permanentLink(permanentLink)
                    .amount(request.getAmount())
                    .currency(request.getCurrency())
                    .merchantReference(request.getReference())
                    .description(request.getDescription())
                    .callbackUrl(returnUrl)
                    .cancelUrl(cancelUrl)
                    .webhookUrl(webhookUrl)
                    .metadataJson(metadataJson)
                    .status("pending")
                    .webhookSent(false)
                    .expiresAt(expiresAt)
                    .idempotencyKey(idemKey)
                    .mode(mode)
                    .paymentCountry(paymentCountry)
                    .build();

            checkoutSessionRepository.save(session);
            log.info("Payment created: {} (merchant={}, mode={})", id, key.getMerchant().getUsername(), mode);

            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(session));
        } catch (org.springframework.dao.DataIntegrityViolationException dup) {
            // Race condition : un second appel concurrent avec la même clé d'idempotence
            // a gagné la course. On relit et on renvoie l'existant.
            log.warn("Idempotency race detected, retrying lookup");
            MerchantApiKey key = resolveActiveKey(apiKey);
            String idemKey = normalize(idempotencyKey);
            if (key != null && idemKey != null) {
                CheckoutSession existing = checkoutSessionRepository
                        .findByMerchantApiKeyAndIdempotencyKey(key, idemKey)
                        .orElse(null);
                if (existing != null) {
                    return ResponseEntity.ok(toResponse(existing));
                }
            }
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (Exception e) {
            log.error("Erreur création paiement: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public ResponseEntity<PaymentResponse> getPayment(String apiKey, String paymentId) {
        try {
            MerchantApiKey key = resolveActiveKey(apiKey);
            if (key == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            CheckoutSession session = checkoutSessionRepository.findByCheckoutCode(paymentId).orElse(null);
            if (session == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            // Ownership check : la clé doit appartenir au même marchand
            if (!session.getMerchantApiKey().getMerchant().getUserId().equals(key.getMerchant().getUserId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            autoExpire(session);
            return ResponseEntity.ok(toResponse(session));
        } catch (Exception e) {
            log.error("Erreur lecture paiement {}: {}", paymentId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public ResponseEntity<CheckoutSessionResponse> getPublicCheckout(String checkoutCode) {
        try {
            CheckoutSession session = checkoutSessionRepository.findByCheckoutCode(checkoutCode).orElse(null);
            if (session == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            autoExpire(session);

            PermanentLink permanentLink = session.getPermanentLink();
            // Le marchand réel est celui qui possède la clé API, pas permanentLink.creator
            // (qui pointe sur le compte master Akuunda1 utilisé pour l'auth Keycloak).
            var merchant = session.getMerchantApiKey() != null
                    ? session.getMerchantApiKey().getMerchant()
                    : null;
            CheckoutSessionResponse response = CheckoutSessionResponse.builder()
                    .checkoutCode(session.getCheckoutCode())
                    .amount(session.getAmount())
                    .currency(session.getCurrency())
                    .reference(session.getMerchantReference())
                    .description(session.getDescription())
                    .status(session.getStatus())
                    .mode(safeMode(session.getMode()))
                    .merchantSlug(permanentLink != null ? permanentLink.getMerchantSlug() : null)
                    .merchantName(buildMerchantName(merchant))
                    .merchantUsername(merchant != null ? merchant.getUsername() : null)
                    .merchantUserId(merchant != null ? merchant.getUserId() : null)
                    .merchantCountry(merchant != null && merchant.getCountryCurrency() != null
                            ? merchant.getCountryCurrency().getCountryCode()
                            : null)
                    .paymentCountry(session.getPaymentCountry())
                    .callbackUrl(session.getCallbackUrl())
                    .cancelUrl(session.getCancelUrl())
                    .expiresAt(session.getExpiresAt())
                    .createdAt(session.getCreatedAt())
                    .build();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Erreur lecture publique checkout {}: {}", checkoutCode, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public ResponseEntity<PaymentResponse> refundPayment(String apiKey, String paymentId, RefundRequest request) {
        try {
            MerchantApiKey key = resolveActiveKey(apiKey);
            if (key == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            CheckoutSession session = checkoutSessionRepository.findByCheckoutCode(paymentId).orElse(null);
            if (session == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            if (!session.getMerchantApiKey().getMerchant().getUserId().equals(key.getMerchant().getUserId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            String status = session.getStatus();
            if (!"paid".equalsIgnoreCase(status) && !"refunded".equalsIgnoreCase(status)) {
                log.warn("Refund refused for {}: status={}", paymentId, status);
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }

            double maxRefundable = session.getAmount() != null ? session.getAmount() : 0d;
            double alreadyRefunded = session.getRefundedAmount() != null ? session.getRefundedAmount() : 0d;
            double remaining = Math.max(0d, maxRefundable - alreadyRefunded);
            if (remaining <= 0d) {
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }

            double requested = (request != null && request.getAmount() != null) ? request.getAmount() : remaining;
            if (requested <= 0d || requested > remaining) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            double newRefundedTotal = alreadyRefunded + requested;
            session.setRefundedAmount(newRefundedTotal);
            session.setRefundedAt(LocalDateTime.now());
            if (request != null && request.getReason() != null && !request.getReason().isBlank()) {
                session.setRefundReason(request.getReason());
            }
            if (newRefundedTotal >= maxRefundable) {
                session.setStatus("refunded");
            }
            checkoutSessionRepository.save(session);

            // Notification asynchrone côté marchand (best-effort, n'interrompt jamais le flux)
            try {
                webhookService.sendEventWebhook(session, "payment.refunded");
            } catch (Exception webhookError) {
                log.warn("Webhook refund échoué pour {}: {}", paymentId, webhookError.getMessage());
            }

            log.info("Refund OK for {}: amount={} total={}/{}", paymentId, requested, newRefundedTotal, maxRefundable);
            return ResponseEntity.ok(toResponse(session));
        } catch (Exception e) {
            log.error("Erreur remboursement {}: {}", paymentId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public ResponseEntity<PaymentResponse> simulateTestCompletion(String apiKey, String paymentId) {
        try {
            MerchantApiKey key = resolveActiveKey(apiKey);
            if (key == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }
            if (!MODE_TEST.equalsIgnoreCase(safeMode(key.getMode()))) {
                log.warn("Test completion refused: key {} is not in test mode", key.getApiKey());
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            CheckoutSession session = checkoutSessionRepository.findByCheckoutCode(paymentId).orElse(null);
            if (session == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            if (!session.getMerchantApiKey().getMerchant().getUserId().equals(key.getMerchant().getUserId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            if (!MODE_TEST.equalsIgnoreCase(safeMode(session.getMode()))) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            if (!"pending".equalsIgnoreCase(session.getStatus())) {
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }

            session.setStatus("paid");
            session.setPaidAt(LocalDateTime.now());
            checkoutSessionRepository.save(session);

            try {
                webhookService.sendPaymentWebhook(session);
            } catch (Exception webhookError) {
                log.warn("Webhook test completion échoué pour {}: {}", paymentId, webhookError.getMessage());
            }

            log.info("Sandbox: payment {} marqué comme payé", paymentId);
            return ResponseEntity.ok(toResponse(session));
        } catch (Exception e) {
            log.error("Erreur sandbox-complete {}: {}", paymentId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ====== helpers ======

    private MerchantApiKey resolveActiveKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        return merchantApiKeyRepository.findByApiKeyAndIsActiveTrue(apiKey.trim()).orElse(null);
    }

    private void autoExpire(CheckoutSession session) {
        if ("pending".equalsIgnoreCase(session.getStatus())
                && session.getExpiresAt() != null
                && session.getExpiresAt().isBefore(LocalDateTime.now())) {
            session.setStatus("expired");
            checkoutSessionRepository.save(session);
        }
    }

    private PaymentResponse toResponse(CheckoutSession session) {
        return PaymentResponse.builder()
                .id(session.getCheckoutCode())
                .url(frontendBaseUrl + "/checkout/" + session.getCheckoutCode())
                .mode(safeMode(session.getMode()))
                .status(session.getStatus())
                .amount(session.getAmount())
                .currency(session.getCurrency())
                .reference(session.getMerchantReference())
                .description(session.getDescription())
                .metadata(deserializeMetadata(session.getMetadataJson()))
                .expiresAt(session.getExpiresAt())
                .createdAt(session.getCreatedAt())
                .paidAt(session.getPaidAt())
                .refundedAmount(session.getRefundedAmount())
                .refundedAt(session.getRefundedAt())
                .build();
    }

    private String pick(String preferred, String fallback) {
        return (preferred != null && !preferred.isBlank()) ? preferred : fallback;
    }

    private String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String resolveMode(MerchantApiKey key) {
        String mode = safeMode(key.getMode());
        // Fallback de sécurité : un préfixe `sk_test_` force le mode test
        if (key.getApiKey() != null && key.getApiKey().startsWith("sk_test_")) {
            return MODE_TEST;
        }
        return mode;
    }

    private String safeMode(String mode) {
        if (mode == null || mode.isBlank()) return MODE_LIVE;
        return MODE_TEST.equalsIgnoreCase(mode) ? MODE_TEST : MODE_LIVE;
    }

    private String serializeMetadata(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException e) {
            log.warn("Impossible de sérialiser metadata: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, String> deserializeMetadata(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, String>>() { });
        } catch (Exception e) {
            return null;
        }
    }

    private String buildMerchantName(Users merchant) {
        if (merchant == null) return null;
        String firstname = merchant.getFirstname();
        String lastname = merchant.getLastname();
        if (firstname == null || firstname.isEmpty()) return null;
        if (lastname != null && !lastname.isEmpty()) {
            return firstname + " " + lastname.charAt(0) + ".";
        }
        return firstname;
    }

    @Override
    public ResponseEntity<List<CheckoutCountryOption>> getCheckoutCountries(String checkoutCode) {
        try {
            CheckoutSession session = checkoutSessionRepository.findByCheckoutCode(checkoutCode).orElse(null);
            if (session == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();

            String currency = session.getCurrency();
            if (currency == null || currency.isBlank()) return ResponseEntity.ok(List.of());

            // Si le marchand a fixé un pays cible, on retourne uniquement ce pays
            // (sélection automatique côté frontend, zéro interaction pour l'acheteur).
            String fixedCountry = session.getPaymentCountry();

            List<CountryCurrency> rows = countryCurrencyRepository
                    .findCountryCurrencyByCurrencyCode(currency.trim().toUpperCase());

            List<CheckoutCountryOption> options = (rows == null ? List.<CountryCurrency>of() : rows).stream()
                    .filter(CountryCurrency::isActivated)
                    .filter(cc -> fixedCountry == null || fixedCountry.equalsIgnoreCase(cc.getCountryCode()))
                    .map(cc -> CheckoutCountryOption.builder()
                            .code(cc.getCountryCode())
                            .name(cc.getCountryName())
                            .callingCode(cc.getCallingCode())
                            .continentName(cc.getContinentName())
                            .flagUrl(cc.getFlagUrl())
                            .currencyCode(cc.getCurrencyCode())
                            .provider(resolveProvider(cc.getContinentName(), cc.getCurrencyCode()))
                            .build())
                    .sorted(java.util.Comparator.comparing(
                            CheckoutCountryOption::getName,
                            java.util.Comparator.nullsLast(String::compareToIgnoreCase)))
                    .collect(java.util.stream.Collectors.toCollection(java.util.ArrayList::new));

            // No country_currency records for this currency (e.g. EUR/USD) → fall back to
            // the merchant's country and route directly to MELD.
            if (options.isEmpty()) {
                CountryCurrency merchantCC = session.getMerchantApiKey() != null
                        && session.getMerchantApiKey().getMerchant() != null
                        ? session.getMerchantApiKey().getMerchant().getCountryCurrency()
                        : null;
                if (merchantCC != null) {
                    options.add(CheckoutCountryOption.builder()
                            .code(merchantCC.getCountryCode())
                            .name(merchantCC.getCountryName())
                            .callingCode(merchantCC.getCallingCode())
                            .continentName(merchantCC.getContinentName())
                            .flagUrl(merchantCC.getFlagUrl())
                            .currencyCode(currency)
                            .provider("MELD")
                            .build());
                } else {
                    // Pas de pays marchand enregistré — dériver un code pays depuis la devise
                    String defaultCode = defaultCountryForCurrency(currency);
                    options.add(CheckoutCountryOption.builder()
                            .code(defaultCode)
                            .name("International")
                            .currencyCode(currency)
                            .provider("MELD")
                            .build());
                }
            }

            return ResponseEntity.ok(options);
        } catch (Exception e) {
            log.error("Erreur getCheckoutCountries {}: {}", checkoutCode, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /** Devises africaines supportées par YellowCard. */
    private static final java.util.Set<String> YELLOWCARD_CURRENCIES = java.util.Set.of(
            "XOF", "XAF", "GHS", "NGN", "KES", "TZS", "UGX", "ZAR", "ZMW",
            "GNF", "SLL", "RWF", "MZN", "ETB", "MAD", "EGP", "TND"
    );

    /** YellowCard pour les devises africaines (prioritaire sur continentName qui peut être null en DB). */
    private static String resolveProvider(String continentName, String currencyCode) {
        if (currencyCode != null && YELLOWCARD_CURRENCIES.contains(currencyCode.toUpperCase())) return "YELLOWCARD";
        if (continentName != null && continentName.toLowerCase().contains("afric")) return "YELLOWCARD";
        return "MELD";
    }

    /** Retourne un code pays par défaut pour une devise MELD (évite countryCode vide → 400). */
    private static String defaultCountryForCurrency(String currency) {
        if (currency == null) return "US";
        return switch (currency.toUpperCase()) {
            case "EUR" -> "FR";
            case "GBP" -> "GB";
            case "CHF" -> "CH";
            case "SEK" -> "SE";
            case "NOK" -> "NO";
            case "DKK" -> "DK";
            case "CAD" -> "CA";
            case "AUD" -> "AU";
            case "NZD" -> "NZ";
            case "JPY" -> "JP";
            case "INR" -> "IN";
            case "BRL" -> "BR";
            case "MXN" -> "MX";
            case "COP" -> "CO";
            case "ARS" -> "AR";
            case "CLP" -> "CL";
            case "PEN" -> "PE";
            case "TRY" -> "TR";
            case "AED" -> "AE";
            case "SAR" -> "SA";
            case "SGD" -> "SG";
            case "HKD" -> "HK";
            case "KRW" -> "KR";
            case "THB" -> "TH";
            case "IDR" -> "ID";
            case "MYR" -> "MY";
            case "PHP" -> "PH";
            case "VND" -> "VN";
            case "PKR" -> "PK";
            case "BDT" -> "BD";
            default -> "US";
        };
    }

    private static String randomToken(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
