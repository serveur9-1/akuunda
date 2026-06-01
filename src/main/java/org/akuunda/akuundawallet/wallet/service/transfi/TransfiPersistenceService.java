package org.akuunda.akuundawallet.wallet.service.transfi;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dao.TransfiIbanRecordRepository;
import org.akuunda.akuundawallet.wallet.api.dao.TransfiOrderRecordRepository;
import org.akuunda.akuundawallet.wallet.api.dao.TransfiUserMappingRepository;
import org.akuunda.akuundawallet.wallet.api.dto.external.transfi.TransfiCreateIbanRequest;
import org.akuunda.akuundawallet.wallet.api.dto.external.transfi.TransfiCreateOrderRequest;
import org.akuunda.akuundawallet.wallet.api.entities.TransfiIbanRecord;
import org.akuunda.akuundawallet.wallet.api.entities.TransfiOrderRecord;
import org.akuunda.akuundawallet.wallet.api.entities.TransfiUserMapping;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Persists TransFi POST results into the local {@code transfi_users / transfi_orders /
 * transfi_ibans} tables when an Akuunda {@code username} was attached to the request.
 * Tolerant to both flat and wrapped TransFi responses (e.g. {@code {status, data: {...}}}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransfiPersistenceService {

    private final TransfiUserMappingRepository userMappingRepository;
    private final TransfiOrderRecordRepository orderRepository;
    private final TransfiIbanRecordRepository ibanRepository;

    public void saveUserMapping(String username, String userType, Object responseBody) {
        if (isBlank(username)) return;
        Map<String, Object> data = unwrap(responseBody);
        if (data == null) return;
        String transfiUserId = asString(data.get("userId"));
        if (isBlank(transfiUserId)) {
            log.warn("TransFi user mapping skipped — no userId in response for username={}", username);
            return;
        }
        String trimmed = username.trim();
        TransfiUserMapping mapping = userMappingRepository.findByUsername(trimmed)
                .orElseGet(() -> TransfiUserMapping.builder().username(trimmed).build());
        mapping.setTransfiUserId(transfiUserId);
        mapping.setUserType(normalizeUserType(userType));
        String kyc = asString(data.get("kycStatus"));
        if (!isBlank(kyc)) mapping.setKycStatus(kyc);
        String kyb = asString(data.get("kybStatus"));
        if (!isBlank(kyb)) mapping.setKybStatus(kyb);
        try {
            userMappingRepository.save(mapping);
            log.info("TransFi user mapping saved: username={} transfiUserId={} type={}",
                    trimmed, transfiUserId, mapping.getUserType());
        } catch (Exception ex) {
            log.warn("Failed to save TransFi user mapping username={}: {}", trimmed, ex.getMessage());
        }
    }

    public void saveOrder(TransfiCreateOrderRequest request, Object responseBody) {
        if (request == null || isBlank(request.getUsername())) return;
        Map<String, Object> data = unwrap(responseBody);
        if (data == null) return;
        String orderId = asString(data.get("orderId"));
        if (isBlank(orderId)) {
            log.warn("TransFi order persistence skipped — no orderId in response for username={}",
                    request.getUsername());
            return;
        }
        String trimmed = request.getUsername().trim();
        TransfiOrderRecord record = orderRepository.findByOrderId(orderId)
                .orElseGet(() -> TransfiOrderRecord.builder().orderId(orderId).username(trimmed).build());
        record.setUsername(trimmed);
        if (!isBlank(request.getUserId())) record.setTransfiUserId(request.getUserId());
        if (!isBlank(request.getOrderType())) record.setOrderType(request.getOrderType());
        if (!isBlank(request.getPurposeCode())) record.setPurposeCode(request.getPurposeCode());

        String status = asString(data.get("status"));
        if (!isBlank(status)) record.setStatus(status);

        String payUrl = asString(data.get("payUrl"));
        if (isBlank(payUrl)) payUrl = asString(data.get("paymentUrl"));
        if (!isBlank(payUrl)) record.setPaymentUrl(payUrl);

        // Source / Destination snapshot — both from response if present, else from request.
        applyAmountsFromMaps(record,
                asMap(data.get("source")), asMap(data.get("destination")),
                request);

        Object fees = data.get("fees");
        if (fees instanceof Map<?, ?> feeMap) {
            Object procFee = feeMap.get("processingFee");
            if (procFee != null) {
                BigDecimal amount = toBigDecimal(procFee);
                if (amount != null) record.setFeeAmount(amount);
            }
            String feeCurrency = asString(feeMap.get("feeCurrency"));
            if (!isBlank(feeCurrency)) record.setFeeCurrency(feeCurrency);
        }

        try {
            orderRepository.save(record);
            log.info("TransFi order persisted: orderId={} username={} type={}",
                    orderId, trimmed, record.getOrderType());
        } catch (Exception ex) {
            log.warn("Failed to persist TransFi order orderId={}: {}", orderId, ex.getMessage());
        }
    }

    public void saveIban(TransfiCreateIbanRequest request, Object responseBody) {
        if (request == null || isBlank(request.getUsername())) return;
        Map<String, Object> data = unwrap(responseBody);
        if (data == null) return;
        String resolvedIbanId = asString(data.get("ibanId"));
        if (isBlank(resolvedIbanId)) resolvedIbanId = asString(data.get("id"));
        if (isBlank(resolvedIbanId)) {
            log.warn("TransFi IBAN persistence skipped — no ibanId in response for username={}",
                    request.getUsername());
            return;
        }
        final String ibanId = resolvedIbanId;
        final String trimmed = request.getUsername().trim();
        TransfiIbanRecord record = ibanRepository.findByIbanId(ibanId)
                .orElseGet(() -> TransfiIbanRecord.builder().ibanId(ibanId).username(trimmed).build());
        record.setUsername(trimmed);
        record.setCurrency(firstNonBlank(asString(data.get("currency")), request.getCurrency()));
        record.setIban(asString(data.get("iban")));
        record.setBic(asString(data.get("bic")));
        record.setBankName(asString(data.get("bankName")));
        record.setAccountHolderName(asString(data.get("accountHolderName")));
        record.setStatus(asString(data.get("status")));
        if (request.getCustomer() != null) {
            String userId = asString(request.getCustomer().get("userId"));
            if (!isBlank(userId)) record.setTransfiUserId(userId);
        }
        try {
            ibanRepository.save(record);
            log.info("TransFi IBAN persisted: ibanId={} username={} currency={}",
                    ibanId, trimmed, record.getCurrency());
        } catch (Exception ex) {
            log.warn("Failed to persist TransFi IBAN ibanId={}: {}", ibanId, ex.getMessage());
        }
    }

    public Optional<TransfiUserMapping> findByUsername(String username) {
        if (isBlank(username)) return Optional.empty();
        return userMappingRepository.findByUsername(username.trim());
    }

    public Optional<String> resolveTransfiUserId(String username) {
        return findByUsername(username).map(TransfiUserMapping::getTransfiUserId);
    }

    public Optional<String> resolveUsername(String transfiUserId) {
        if (isBlank(transfiUserId)) return Optional.empty();
        return userMappingRepository.findByTransfiUserId(transfiUserId.trim())
                .map(TransfiUserMapping::getUsername);
    }

    // ── Helpers ─────────────────────────────────────────────────────

    /**
     * TransFi wraps most successful responses as {@code {status, data: {...}}}. Some endpoints return
     * the payload flat. This method always returns the inner data map so callers don't have to care.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrap(Object responseBody) {
        if (!(responseBody instanceof Map<?, ?> map)) return null;
        Object data = map.get("data");
        if (data instanceof Map<?, ?> inner) return (Map<String, Object>) inner;
        return (Map<String, Object>) map;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : null;
    }

    private static String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String firstNonBlank(String a, String b) {
        return isBlank(a) ? b : a;
    }

    private static String normalizeUserType(String userType) {
        if (isBlank(userType)) return "individual";
        return userType.trim().toLowerCase(Locale.ROOT);
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        try {
            if (value instanceof Number n) return new BigDecimal(n.toString());
            return new BigDecimal(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private void applyAmountsFromMaps(TransfiOrderRecord record,
            Map<String, Object> source, Map<String, Object> destination,
            TransfiCreateOrderRequest request) {
        if (source != null) {
            record.setSourceCurrency(asString(source.get("currency")));
            BigDecimal amount = toBigDecimal(source.get("amount"));
            if (amount != null) record.setSourceAmount(amount);
        } else if (request.getSource() != null) {
            record.setSourceCurrency(request.getSource().getCurrency());
            if (request.getSource().getAmount() != null) {
                record.setSourceAmount(BigDecimal.valueOf(request.getSource().getAmount()));
            }
        }
        if (destination != null) {
            record.setDestinationCurrency(asString(destination.get("currency")));
            BigDecimal amount = toBigDecimal(destination.get("amount"));
            if (amount != null) record.setDestinationAmount(amount);
        } else if (request.getDestination() != null) {
            record.setDestinationCurrency(request.getDestination().getCurrency());
            if (request.getDestination().getAmount() != null) {
                record.setDestinationAmount(BigDecimal.valueOf(request.getDestination().getAmount()));
            }
        }
    }
}
