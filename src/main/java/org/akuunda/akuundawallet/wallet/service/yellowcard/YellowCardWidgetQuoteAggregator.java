package org.akuunda.akuundawallet.wallet.service.yellowcard;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dto.external.YellowCardWidgetQuoteRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Découpe les quotes widget Yellow Card (max 300 000 XOF / tranche) et agrège les résultats.
 * Repli sur le calculateur local si le widget est indisponible pour le canal.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class YellowCardWidgetQuoteAggregator {

    public static final double MAX_QUOTE_CHUNK_LOCAL = 300_000.0;

    private final ObjectMapper objectMapper;

    public ResponseEntity<String> aggregate(
            YellowCardWidgetQuoteRequest request,
            Function<YellowCardWidgetQuoteRequest, ResponseEntity<String>> singleQuoteCaller,
            Function<YellowCardWidgetQuoteRequest, Double> sellRateProvider) {

        Double totalLocal = request.getLocalAmount();
        if (totalLocal == null || totalLocal <= 0) {
            return singleQuoteCaller.apply(request);
        }

        boolean isBuy = "Buy".equalsIgnoreCase(
                request.getTransactionType() != null ? request.getTransactionType().trim() : "Sell");

        List<Double> chunks = splitAmount(totalLocal);
        List<JsonNode> childQuotes = new ArrayList<>();
        Double rateLocal = null;
        double totalNetworkFee = 0;
        double totalServiceFee = 0;
        double totalPartnerFee = 0;
        double totalFiatReceived = 0;
        double totalCryptoReceived = 0;
        double totalConverted = 0;
        String earliestExpire = null;

        for (double chunk : chunks) {
            YellowCardWidgetQuoteRequest chunkReq = copyRequest(request, chunk);
            ResponseEntity<String> response = singleQuoteCaller.apply(chunkReq);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                if (isWidgetFallbackError(response)) {
                    log.warn("Quote widget indisponible ({}), repli calculateur local pour {} {}",
                            response.getStatusCode(), totalLocal, request.getCurrency());
                    return ResponseEntity.ok(buildLocalFallback(request, totalLocal, isBuy, sellRateProvider));
                }
                return response;
            }
            try {
                JsonNode node = objectMapper.readTree(response.getBody());
                childQuotes.add(node);
                if (rateLocal == null && node.has("rateLocal")) {
                    rateLocal = node.get("rateLocal").asDouble();
                }
                totalNetworkFee += readDouble(node, "networkFeeLocal");
                totalServiceFee += readDouble(node, "serviceFeeLocal");
                totalPartnerFee += readDouble(node, "partnerFeeLocal");
                totalFiatReceived += readDouble(node, "fiatReceived");
                totalCryptoReceived += readDouble(node, "cryptoReceived");
                totalConverted += readDouble(node, "convertedAmount");
                String expire = node.has("expireAt") ? node.get("expireAt").asText(null) : null;
                if (expire != null && (earliestExpire == null || expire.compareTo(earliestExpire) < 0)) {
                    earliestExpire = expire;
                }
            } catch (Exception e) {
                log.error("Parse quote enfant échoué: {}", e.getMessage());
                return ResponseEntity.ok(buildLocalFallback(request, totalLocal, isBuy, sellRateProvider));
            }
        }

        double totalFees = totalNetworkFee + totalServiceFee + totalPartnerFee;
        ObjectNode aggregated = objectMapper.createObjectNode();
        aggregated.put("quoteSource", "widget");
        aggregated.put("aggregated", chunks.size() > 1);
        aggregated.put("chunkCount", chunks.size());
        aggregated.put("localAmount", totalLocal);
        aggregated.put("currency", request.getCurrency().trim().toUpperCase());
        aggregated.put("transactionType", isBuy ? "Buy" : "Sell");
        if (rateLocal != null) {
            aggregated.put("rateLocal", rateLocal);
        }
        aggregated.put("networkFeeLocal", round2(totalNetworkFee));
        aggregated.put("serviceFeeLocal", round2(totalServiceFee));
        aggregated.put("partnerFeeLocal", round2(totalPartnerFee));
        aggregated.put("totalFeesLocal", round2(totalFees));
        if (totalFiatReceived > 0) {
            aggregated.put("fiatReceived", round2(totalFiatReceived));
        }
        if (totalCryptoReceived > 0) {
            aggregated.put("cryptoReceived", totalCryptoReceived);
        }
        if (totalConverted > 0) {
            aggregated.put("convertedAmount", totalConverted);
        }
        if (earliestExpire != null) {
            aggregated.put("expireAt", earliestExpire);
        }

        if (isBuy) {
            aggregated.put("totalAmountToPay", round2(totalLocal + totalFees));
            aggregated.put("totalEstimatedReceived", totalCryptoReceived > 0 ? totalCryptoReceived : totalConverted);
        } else {
            aggregated.put("totalAmountToPay", totalLocal);
            aggregated.put("totalEstimatedReceived", totalFiatReceived > 0 ? round2(totalFiatReceived) : round2(totalLocal - totalFees));
        }

        ArrayNode children = aggregated.putArray("childQuotes");
        for (JsonNode child : childQuotes) {
            children.add(child);
        }

        try {
            return ResponseEntity.ok(objectMapper.writeValueAsString(aggregated));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    static List<Double> splitAmount(double total) {
        List<Double> chunks = new ArrayList<>();
        double remaining = total;
        while (remaining > MAX_QUOTE_CHUNK_LOCAL) {
            chunks.add(MAX_QUOTE_CHUNK_LOCAL);
            remaining -= MAX_QUOTE_CHUNK_LOCAL;
        }
        if (remaining > 0) {
            chunks.add(remaining);
        }
        return chunks.isEmpty() ? List.of(total) : chunks;
    }

    private static YellowCardWidgetQuoteRequest copyRequest(YellowCardWidgetQuoteRequest src, double chunkAmount) {
        YellowCardWidgetQuoteRequest copy = new YellowCardWidgetQuoteRequest();
        copy.setCurrency(src.getCurrency());
        copy.setCountry(src.getCountry());
        copy.setChannelId(src.getChannelId());
        copy.setCoin(src.getCoin());
        copy.setNetwork(src.getNetwork());
        copy.setTransactionType(src.getTransactionType());
        copy.setPaymentMethod(src.getPaymentMethod());
        copy.setLocalAmount(chunkAmount);
        copy.setCryptoAmount(null);
        return copy;
    }

    static boolean isWidgetFallbackError(ResponseEntity<String> response) {
        if (response.getStatusCode().value() != 400 && response.getStatusCode().value() != 403) {
            return false;
        }
        String body = response.getBody() != null ? response.getBody().toLowerCase() : "";
        return body.contains("disabled for widget")
                || body.contains("paymentvalidationerror")
                || body.contains("widget use");
    }

    private String buildLocalFallback(
            YellowCardWidgetQuoteRequest request,
            double totalLocal,
            boolean isBuy,
            Function<YellowCardWidgetQuoteRequest, Double> sellRateProvider) {
        String country = request.getCountry() != null ? request.getCountry() : "CI";
        String method = YellowCardLocalFeeCalculator.normalizePaymentMethod(request.getPaymentMethod());
        double fees = YellowCardLocalFeeCalculator.calculateFee(country, method, totalLocal, !isBuy);
        Double rate = sellRateProvider != null ? sellRateProvider.apply(request) : null;

        ObjectNode node = objectMapper.createObjectNode();
        node.put("quoteSource", "local_fallback");
        node.put("aggregated", false);
        node.put("chunkCount", 1);
        node.put("localAmount", totalLocal);
        node.put("currency", request.getCurrency().trim().toUpperCase());
        node.put("transactionType", isBuy ? "Buy" : "Sell");
        node.put("totalFeesLocal", round2(fees));
        node.put("serviceFeeLocal", round2(fees));
        if (rate != null) {
            node.put("rateLocal", rate);
        }
        if (isBuy) {
            node.put("totalAmountToPay", round2(totalLocal + fees));
            node.put("totalEstimatedReceived", totalLocal);
            node.put("cryptoReceived", 0);
        } else {
            node.put("totalAmountToPay", totalLocal);
            node.put("totalEstimatedReceived", round2(totalLocal - fees));
            node.put("fiatReceived", round2(totalLocal - fees));
        }
        node.putArray("childQuotes");
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{}";
        }
    }

    private static double readDouble(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return 0;
        }
        return node.get(field).asDouble(0);
    }

    private static double round2(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
