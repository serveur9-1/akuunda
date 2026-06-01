package org.akuunda.akuundawallet.backoffice.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.akuunda.akuundawallet.backoffice.dto.ApiSuccess;
import org.akuunda.akuundawallet.backoffice.service.BackofficeProMerchantResolver;
import org.akuunda.akuundawallet.wallet.api.dto.external.AkuundaTransfertRequest;
import org.akuunda.akuundawallet.wallet.api.dto.external.OffRampRequest;
import org.akuunda.akuundawallet.wallet.api.dto.FeesCalculationRequest;
import org.akuunda.akuundawallet.wallet.api.dto.FeesCalculationResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.YellowCardWidgetQuoteRequest;
import org.akuunda.akuundawallet.wallet.service.AkuundaTransactionService;
import org.akuunda.akuundawallet.wallet.service.FeesCalculationService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaYellowCardClientService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Proxies paiements Pro (JWT → username marchand) vers les services wallet internes
 * (Yellow Card, transfert compte-à-compte), comme sur l'app mobile.
 */
@RestController
@RequestMapping(path = "/api/v1/pro/payments", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Backoffice - Pro Payments")
@RequiredArgsConstructor
public class BackofficeProPaymentsController {

    private final BackofficeProMerchantResolver merchantResolver;
    private final AkuundaYellowCardClientService yellowCardClientService;
    private final AkuundaTransactionService transactionService;
    private final FeesCalculationService feesCalculationService;
    private final ObjectMapper objectMapper;

    @GetMapping("/channels")
    @Operation(summary = "Canaux Yellow Card par pays (proxy mobile)")
    public ResponseEntity<ApiSuccess<Object>> channels(
            @RequestParam(required = false) String country) {
        String countryCode = normalizeCountry(country);
        return wrapJsonBody(yellowCardClientService.getChannels(countryCode));
    }

    @GetMapping("/networks")
    @Operation(summary = "Réseaux MoMo Yellow Card par pays (proxy mobile)")
    public ResponseEntity<ApiSuccess<Object>> networks(
            @RequestParam(required = false) String country) {
        String countryCode = normalizeCountry(country);
        return wrapJsonBody(yellowCardClientService.getNetworks(countryCode));
    }

    @GetMapping("/exchange-rates")
    @Operation(summary = "Taux Yellow Card pour une devise")
    public ResponseEntity<ApiSuccess<Object>> exchangeRates(
            @RequestParam(required = false) String currency) {
        String code = currency != null && !currency.isBlank() ? currency.trim().toUpperCase() : "XOF";
        return wrapJsonBody(yellowCardClientService.getRates(code));
    }

    @PostMapping("/quote")
    @Operation(summary = "Quote Yellow Card widget (taux et frais off-ramp Sell)")
    public ResponseEntity<ApiSuccess<Object>> quote(@RequestBody Map<String, Object> body) {
        YellowCardWidgetQuoteRequest req = new YellowCardWidgetQuoteRequest();
        req.setCurrency(requiredString(body, "currency"));
        req.setChannelId(requiredString(body, "channelId"));
        req.setCountry(body.get("country") != null ? String.valueOf(body.get("country")) : null);
        req.setTransactionType(body.get("transactionType") != null
                ? String.valueOf(body.get("transactionType")) : "Sell");
        if (body.get("localAmount") != null) {
            req.setLocalAmount(requiredDouble(body, "localAmount"));
        } else if (body.get("amount") != null) {
            req.setLocalAmount(requiredDouble(body, "amount"));
        }
        if (body.get("cryptoAmount") != null) {
            req.setCryptoAmount(requiredDouble(body, "cryptoAmount"));
        }
        if (body.get("coin") != null) {
            req.setCoin(String.valueOf(body.get("coin")));
        }
        if (body.get("network") != null) {
            req.setNetwork(String.valueOf(body.get("network")));
        }
        if (body.get("paymentMethod") != null) {
            req.setPaymentMethod(String.valueOf(body.get("paymentMethod")));
        }
        return wrapJsonBody(yellowCardClientService.getWidgetQuote(req));
    }

    @GetMapping("/fees")
    @Operation(summary = "Frais off-ramp Yellow Card")
    public ResponseEntity<ApiSuccess<FeesCalculationResponse>> fees(
            @RequestParam double amount,
            @RequestParam String currency,
            @RequestParam String country,
            @RequestParam(required = false) String channelId) {
        FeesCalculationRequest req = new FeesCalculationRequest();
        req.setAmount(amount);
        req.setCurrency(currency);
        req.setCountryCode(normalizeCountry(country));
        req.setOperator("yellowcard");
        ResponseEntity<FeesCalculationResponse> res = feesCalculationService.calculateOffRampFees(req);
        if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Calcul des frais indisponible");
        }
        return ResponseEntity.ok(ApiSuccess.of(res.getBody()));
    }

    @PostMapping("/off-ramp")
    @Operation(summary = "Retrait / transfert externe Yellow Card (proxy off-ramp)")
    public ResponseEntity<ApiSuccess<Object>> offRamp(@RequestBody Map<String, Object> body) {
        String username = merchantResolver.resolveWalletUsername();
        String headerPin = extractHeaderPin(body);
        OffRampRequest request = objectMapper.convertValue(stripPinFields(body), OffRampRequest.class);
        ResponseEntity<Object> upstream = yellowCardClientService.createOffRampPaiements(request, headerPin, username);
        if (!upstream.getStatusCode().is2xxSuccessful()) {
            return ResponseEntity.status(upstream.getStatusCode())
                    .body(ApiSuccess.of(upstream.getBody() != null ? upstream.getBody() : Map.of()));
        }
        return ResponseEntity.ok(ApiSuccess.of(upstream.getBody()));
    }

    @PostMapping("/send")
    @Operation(summary = "Transfert interne wallet-to-wallet (proxy transactions/transfert/execute)")
    public ResponseEntity<ApiSuccess<Map<String, Object>>> send(@RequestBody Map<String, Object> body) {
        String sourceUsername = merchantResolver.resolveWalletUsername();
        AkuundaTransfertRequest request = new AkuundaTransfertRequest();
        request.setSourceUsername(sourceUsername);
        request.setCibleUsername(requiredString(body, "cibleUsername"));
        request.setAmount(requiredDouble(body, "amount"));
        request.setDevise(requiredString(body, "devise"));
        request.setHeaderPin(extractHeaderPin(body));

        ResponseEntity<String> upstream = transactionService.executeTransfertCpteACpte(request);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", upstream.getStatusCode().is2xxSuccessful() ? "success" : "error");
        result.put("message", upstream.getBody() != null ? upstream.getBody() : "");
        if (!upstream.getStatusCode().is2xxSuccessful()) {
            return ResponseEntity.status(upstream.getStatusCode()).body(ApiSuccess.of(result));
        }
        return ResponseEntity.ok(ApiSuccess.of(result));
    }

    private ResponseEntity<ApiSuccess<Object>> wrapJsonBody(ResponseEntity<String> upstream) {
        if (!upstream.getStatusCode().is2xxSuccessful()) {
            throw new ResponseStatusException(
                    HttpStatus.valueOf(upstream.getStatusCode().value()),
                    upstream.getBody() != null ? upstream.getBody() : "Erreur Yellow Card");
        }
        return ResponseEntity.ok(ApiSuccess.of(parseJsonBody(upstream.getBody())));
    }

    private Object parseJsonBody(String body) {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(body, Object.class);
        } catch (Exception e) {
            try {
                return objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
            } catch (Exception e2) {
                return Map.of("raw", body);
            }
        }
    }

    private static String normalizeCountry(String country) {
        if (country == null || country.isBlank()) {
            return "CI";
        }
        return country.trim().toUpperCase();
    }

    private static String extractHeaderPin(Map<String, Object> body) {
        Object pin = body.get("headerPin");
        if (pin == null) {
            pin = body.get("pin");
        }
        if (pin == null || String.valueOf(pin).isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "headerPin requis");
        }
        return String.valueOf(pin);
    }

    private static Map<String, Object> stripPinFields(Map<String, Object> body) {
        Map<String, Object> copy = new LinkedHashMap<>(body);
        copy.remove("headerPin");
        copy.remove("pin");
        return copy;
    }

    private static String requiredString(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " requis");
        }
        return String.valueOf(v);
    }

    private static Double requiredDouble(Map<String, Object> body, String key) {
        Object v = body.get(key);
        if (v == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " requis");
        }
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(v));
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, key + " invalide");
        }
    }
}
