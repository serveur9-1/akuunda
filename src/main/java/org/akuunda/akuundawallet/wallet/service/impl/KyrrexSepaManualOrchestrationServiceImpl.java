package org.akuunda.akuundawallet.wallet.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.wallet.api.dao.KyrrexSepaIbanStateRepository;
import org.akuunda.akuundawallet.wallet.api.dao.KyrrexUserCredentialRepository;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.api.dto.external.KyrrexCryptoAddressValidationRequest;
import org.akuunda.akuundawallet.wallet.api.dto.external.KyrrexExchangeRequest;
import org.akuunda.akuundawallet.wallet.api.dto.external.KyrrexSepaOrchestrationRequest;
import org.akuunda.akuundawallet.wallet.api.entities.KyrrexSepaIbanState;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.wallet.service.KyrrexSepaManualOrchestrationService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaKyrrexClientService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KyrrexSepaManualOrchestrationServiceImpl implements KyrrexSepaManualOrchestrationService {

    private final AkuundaKyrrexClientService kyrrexClientService;
    private final KyrrexSepaIbanStateRepository ibanStateRepository;
    private final WalletRepository walletRepository;
    private final KyrrexUserCredentialRepository kyrrexUserCredentialRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @Value("${akuunda.kyrrex.settlement.target-asset:USDC}")
    private String targetAsset;

    @Value("${akuunda.kyrrex.settlement.target-network:Polygon}")
    private String targetNetwork;

    /// Kyrrex documente les instruments en minuscules (ex. cjeur/**sepa**).
    private static final String DEFAULT_SEPA_INSTRUMENT = "sepa";

    @Override
    public Map<String, Object> openOrRefreshIban(String username, KyrrexSepaOrchestrationRequest request) {
        if (request == null) {
            request = new KyrrexSepaOrchestrationRequest();
        }
        try {
            return openOrRefreshIban0(username, request);
        } catch (DataAccessException e) {
            log.error("❌ [KYRREX-SEPA] Erreur base (migration kyrrex_sepa_iban_state appliquée ?) user={}", username, e);
            Throwable cause = e.getMostSpecificCause();
            String dbMsg = (cause != null && cause.getMessage() != null) ? cause.getMessage() : e.getMessage();
            return Map.of(
                    "success", false,
                    "status", "DB_ERROR",
                    "message", "Erreur base de données: " + (dbMsg != null ? dbMsg : "unknown")
            );
        } catch (Exception e) {
            log.error("❌ [KYRREX-SEPA] openOrRefreshIban user={}", username, e);
            return Map.of(
                    "success", false,
                    "status", "ERROR",
                    "message", e.getMessage() != null ? e.getMessage() : "SEPA_IBAN_OPEN_FAILED"
            );
        }
    }

    private Map<String, Object> openOrRefreshIban0(String pathUsername, KyrrexSepaOrchestrationRequest request) {
        String localUserKey = pathUsername != null ? pathUsername.trim() : "";
        if (localUserKey.isEmpty()) {
            return Map.of("success", false, "message", "username is required");
        }
        if (request == null) {
            request = new KyrrexSepaOrchestrationRequest();
        }
        String kyrrexCredentialUser = resolveKyrrexCredentialUsername(localUserKey);

        String providerId = safe(request.getProviderId());
        String instrument = safe(request.getInstrument(), DEFAULT_SEPA_INSTRUMENT);

        if (providerId == null) {
            return Map.of("success", false, "message", "providerId is required");
        }

        if (request.getInstrumentRegistrationBody() != null) {
            ResponseEntity<String> created = kyrrexClientService.createBankTransferInstrument(
                    kyrrexCredentialUser, providerId, request.getInstrumentRegistrationBody());
            if (!isOk(created)) {
                return persistAndFail(localUserKey, providerId, instrument, created, "CREATE_INSTRUMENT");
            }
        }

        ResponseEntity<String> details = kyrrexClientService.getBankTransferInstrumentDetails(
                kyrrexCredentialUser, providerId, safe(request.getInstrumentId(), instrument)
        );
        if (!isOk(details)) {
            return persistAndFail(localUserKey, providerId, instrument, details, "GET_INSTRUMENT");
        }

        String raw = details.getBody();
        String iban = extractIban(raw);
        String instrumentId = extractAny(raw, "instrument", "instrument_id", "uid", "id");
        String status = (iban != null && !iban.isBlank()) ? "ACTIVE" : "CLOSED";

        persistIbanState(localUserKey, providerId, instrument, instrumentId, iban, status, raw);

        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("status", status);
        result.put("iban", iban);
        result.put("instrumentId", instrumentId);
        result.put("providerId", providerId);
        result.put("instrument", instrument);
        return result;
    }

    /**
     * L'app mobile envoie souvent le téléphone dans le path alors que {@link org.akuunda.akuundawallet.wallet.api.entities.KyrrexUserCredential}
     * est stocké sous le sub JWT ou le username base — on aligne sur le premier candidat ayant des credentials actifs.
     */
    private String resolveKyrrexCredentialUsername(String pathUsername) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(pathUsername);
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwtAuth) {
            String sub = jwtAuth.getName();
            if (sub != null && !sub.isBlank()) {
                candidates.add(sub);
                userRepository.findById(sub).ifPresent(u -> {
                    if (u.getUsername() != null && !u.getUsername().isBlank()) {
                        candidates.add(u.getUsername().trim());
                    }
                    if (u.getMobilePhone() != null && !u.getMobilePhone().isBlank()) {
                        candidates.add(u.getMobilePhone().trim());
                    }
                });
            }
        }
        for (String c : candidates) {
            if (c == null || c.isBlank()) {
                continue;
            }
            if (kyrrexUserCredentialRepository.findByUsernameAndRevokedAtIsNull(c).isPresent()) {
                if (!c.equals(pathUsername)) {
                    log.info("[KYRREX-SEPA] credential username résolu: {} (chemin API était {})", c, pathUsername);
                }
                return c;
            }
        }
        return pathUsername;
    }

    private Map<String, Object> persistAndFail(
            String username,
            String providerId,
            String instrument,
            ResponseEntity<String> resp,
            String step) {
        String raw = resp != null ? resp.getBody() : null;
        persistIbanState(username, providerId, instrument, null, null, "CLOSED", raw);
        Map<String, Object> m = new HashMap<>();
        m.put("success", false);
        m.put("status", "KYRREX_HTTP_ERROR");
        m.put("step", step);
        m.put("providerId", providerId);
        m.put("instrument", instrument);
        m.put("message", buildKyrrexErrorMessage(resp));
        m.put("iban", null);
        return m;
    }

    private String buildKyrrexErrorMessage(ResponseEntity<String> resp) {
        if (resp == null) {
            return "Réponse Kyrrex vide";
        }
        String body = resp.getBody() != null ? resp.getBody() : "";
        if (body.length() > 900) {
            body = body.substring(0, 900) + "…";
        }
        return "Kyrrex API HTTP " + resp.getStatusCode() + ": " + body;
    }

    private boolean isOk(ResponseEntity<String> r) {
        return r != null && r.getStatusCode() != null && r.getStatusCode().is2xxSuccessful();
    }

    private void persistIbanState(
            String username,
            String providerId,
            String instrument,
            String instrumentId,
            String iban,
            String status,
            String raw) {
        KyrrexSepaIbanState state = ibanStateRepository
                .findTopByUsernameAndProviderIdAndInstrumentOrderByUpdatedAtDesc(username, providerId, instrument)
                .orElseGet(KyrrexSepaIbanState::new);
        state.setUsername(username);
        state.setProviderId(providerId);
        state.setInstrument(instrument);
        state.setInstrumentId(instrumentId);
        state.setIban(iban);
        state.setStatus(status);
        state.setRawResponse(raw);
        state.setUpdatedAt(Instant.now());
        ibanStateRepository.save(state);
    }

    @Override
    public Map<String, Object> orchestrate(String username, KyrrexSepaOrchestrationRequest request) {
        if (request == null) {
            request = new KyrrexSepaOrchestrationRequest();
        }
        String localUserKey = username != null ? username.trim() : "";
        if (localUserKey.isEmpty()) {
            return Map.of("success", false, "message", "username is required");
        }
        String kyrrexUser = resolveKyrrexCredentialUsername(localUserKey);
        Map<String, Object> ibanData = openOrRefreshIban(username, request);
        if (Boolean.FALSE.equals(ibanData.get("success"))) {
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("step", "OPEN_IBAN");
            err.put("status", ibanData.get("status"));
            err.put("message", ibanData.get("message") != null ? ibanData.get("message") : "IBAN open failed");
            err.put("iban", ibanData.get("iban"));
            return err;
        }
        if (!"ACTIVE".equals(ibanData.get("status"))) {
            return Map.of(
                    "success", false,
                    "step", "OPEN_IBAN",
                    "status", "IBAN_CLOSED",
                    "message", "IBAN is CLOSED. Open a new IBAN before continuing."
            );
        }

        String fiatHistoryRaw = safeBody(kyrrexClientService.getFiatDepositHistory(kyrrexUser));
        JsonNode doneDeposit = findLatestDoneFiatDeposit(fiatHistoryRaw);
        if (doneDeposit == null) {
            return Map.of(
                    "success", true,
                    "step", "WAITING_DEPOSIT",
                    "status", "PENDING",
                    "message", "No completed SEPA deposit detected yet.",
                    "iban", ibanData.get("iban")
            );
        }

        String inputAsset = text(doneDeposit, "currency");
        BigDecimal amount = request.getExchangeAmount();
        if (amount == null) {
            amount = decimal(doneDeposit, "amount");
        }
        if (inputAsset == null || amount == null || amount.signum() <= 0) {
            return Map.of("success", false, "step", "EXCHANGE", "message", "Unable to resolve deposit currency/amount.");
        }

        String output = safe(request.getOutputAsset(), targetAsset);
        KyrrexExchangeRequest exchangeRequest = new KyrrexExchangeRequest(inputAsset, output, amount);
        ResponseEntity<String> exchangeResp = kyrrexClientService.executeExchange(kyrrexUser, exchangeRequest);
        if (exchangeResp == null || !exchangeResp.getStatusCode().is2xxSuccessful()) {
            return Map.of("success", false, "step", "EXCHANGE", "message", "Exchange request failed.");
        }

        Wallet wallet = walletRepository.findByUsersUsername(localUserKey);
        if (wallet == null) {
            wallet = walletRepository.findByUsersUsername(kyrrexUser);
        }
        if (wallet == null || wallet.getAddress() == null || wallet.getAddress().isBlank()) {
            return Map.of("success", false, "step", "WITHDRAWAL", "message", "User wallet address not found.");
        }

        kyrrexClientService.validateCryptoWithdrawalAddress(
                kyrrexUser,
                new KyrrexCryptoAddressValidationRequest(wallet.getAddress(), targetNetwork, targetNetwork)
        );

        String requisiteId = resolveRequisite(kyrrexUser, wallet.getAddress(), output);
        if (requisiteId == null) {
            return Map.of("success", false, "step", "WITHDRAWAL", "message", "Unable to resolve requisiteId.");
        }

        ResponseEntity<String> wd = kyrrexClientService.executeCryptoWithdrawal(kyrrexUser, output, amount, requisiteId);
        String wdId = extractAny(safeBody(wd), "uid", "id");
        return Map.of(
                "success", true,
                "step", "WITHDRAWAL_INITIATED",
                "depositStatus", "DONE",
                "exchangeStatus", "DONE",
                "withdrawalId", wdId,
                "requisiteId", requisiteId,
                "targetWallet", wallet.getAddress()
        );
    }

    private JsonNode findLatestDoneFiatDeposit(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode items = root.has("items") ? root.get("items") : root;
            if (items != null && items.isArray()) {
                for (JsonNode n : items) {
                    String st = text(n, "status");
                    if (st != null && (st.equalsIgnoreCase("done") || st.equalsIgnoreCase("success"))) {
                        return n;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Unable to parse fiat history: {}", e.getMessage());
        }
        return null;
    }

    private String resolveRequisite(String username, String address, String currency) {
        String listRaw = safeBody(kyrrexClientService.getRequisites(username));
        String fromList = findRequisiteIdByAddress(listRaw, address);
        if (fromList != null) return fromList;
        kyrrexClientService.createRequisite(username, currency.toLowerCase(Locale.ROOT), address, targetNetwork, "Akuunda wallet");
        String refreshed = safeBody(kyrrexClientService.getRequisites(username));
        return findRequisiteIdByAddress(refreshed, address);
    }

    private String findRequisiteIdByAddress(String raw, String address) {
        if (raw == null) return null;
        try {
            JsonNode root = objectMapper.readTree(raw);
            JsonNode items = root.has("items") ? root.get("items") : root;
            if (items != null && items.isArray()) {
                for (JsonNode n : items) {
                    if (address.equalsIgnoreCase(text(n, "address"))) {
                        String id = text(n, "uid");
                        if (id == null) id = text(n, "id");
                        if (id != null) return id;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String extractIban(String raw) {
        String iban = extractAny(raw, "iban", "IBAN");
        if (iban != null) return iban;
        return extractAny(raw, "account", "account_number");
    }

    private String extractAny(String raw, String... keys) {
        if (raw == null || raw.isBlank()) return null;
        try {
            JsonNode root = objectMapper.readTree(raw);
            return extractAny(root, keys);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String extractAny(JsonNode node, String... keys) {
        if (node == null) return null;
        for (String k : keys) {
            JsonNode v = node.get(k);
            if (v != null && !v.isNull() && !v.asText().isBlank()) return v.asText();
        }
        if (node.has("items") && node.get("items").isArray() && node.get("items").size() > 0) {
            return extractAny(node.get("items").get(0), keys);
        }
        if (node.has("data")) {
            return extractAny(node.get("data"), keys);
        }
        return null;
    }

    private String text(JsonNode n, String key) {
        JsonNode v = n.get(key);
        return (v == null || v.isNull() || v.asText().isBlank()) ? null : v.asText();
    }

    private BigDecimal decimal(JsonNode n, String key) {
        try {
            String s = text(n, key);
            return s == null ? null : new BigDecimal(s);
        } catch (Exception e) {
            return null;
        }
    }

    private String safe(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private String safe(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private String safeBody(ResponseEntity<String> response) {
        return response == null ? null : response.getBody();
    }
}
