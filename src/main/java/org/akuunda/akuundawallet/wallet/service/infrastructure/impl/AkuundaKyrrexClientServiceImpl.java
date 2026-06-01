package org.akuunda.akuundawallet.wallet.service.infrastructure.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.keycloak.api.service.UserService;
import org.akuunda.akuundawallet.wallet.api.dao.KyrrexTransactionRepository;
import org.akuunda.akuundawallet.wallet.api.dao.KyrrexUserCredentialRepository;
import org.akuunda.akuundawallet.wallet.api.entities.KyrrexUserCredential;
import org.akuunda.akuundawallet.wallet.api.dao.OperationRepository;
import org.akuunda.akuundawallet.wallet.api.dto.external.*;
import org.akuunda.akuundawallet.wallet.api.entities.KyrrexTransaction;
import org.akuunda.akuundawallet.wallet.api.entities.KyrrexTransactionType;
import org.akuunda.akuundawallet.wallet.api.entities.Operation;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaKyrrexClientService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.CredentialEncryptionService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.KyrrexCredentialMissingException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import org.springframework.core.ParameterizedTypeReference;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implémentation unique de {@link AkuundaKyrrexClientService}.
 * <p>69 méthodes — couverture complète Kyrrex Malta Business API — 2026-04-18.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AkuundaKyrrexClientServiceImpl implements AkuundaKyrrexClientService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final KyrrexTransactionRepository kyrrexTransactionRepository;
    private final KyrrexUserCredentialRepository kyrrexUserCredentialRepository;
    private final OperationRepository operationRepository;
    private final ObjectMapper objectMapper;
    private final CredentialEncryptionService credentialEncryptionService;

    @Value("${kyrrex.api.base-url:https://my.kyrrex.mt}")
    private String baseUrl;

    @Value("${kyrrex.api.auth-token:}")
    private String authToken;

    @Value("${kyrrex.api.access-key:}")
    private String businessAccessKey;

    @Value("${kyrrex.api.secret-key:}")
    private String businessSecretKey;

    @Value("${kyrrex.api.business-username:}")
    private String businessUsername;

    private volatile List<KyrrexCountryResponse> cachedRegistrationCountries = List.of();

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private static final long DEFAULT_SESSION_EXPIRY_SECONDS = 3600;

    // Immutable session snapshot held atomically via volatile reference
    private record SessionKeys(String accessKey, String secretKey, Instant expireAt) {
        boolean isValid() {
            return accessKey != null && expireAt != null && Instant.now().isBefore(expireAt);
        }
    }

    private volatile SessionKeys currentSession;

    // ══════════════════════════════════════════════════════════════
    //  STARTUP VALIDATION
    // ══════════════════════════════════════════════════════════════

    @PostConstruct
    public void validateCredentials() {
        if (authToken == null || authToken.isBlank()) {
            log.warn("⚠️ [KYRREX] La propriété 'kyrrex.api.auth-token' est vide ou non définie. Les appels Kyrrex échoueront.");
        }
        if (businessAccessKey == null || businessAccessKey.isBlank()) {
            log.warn("⚠️ [KYRREX] La propriété 'kyrrex.api.access-key' est vide ou non définie. Les sessions business Kyrrex échoueront.");
        }
        if (businessSecretKey == null || businessSecretKey.isBlank()) {
            log.warn("⚠️ [KYRREX] La propriété 'kyrrex.api.secret-key' est vide ou non définie. Les sessions business Kyrrex échoueront.");
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  SESSION MANAGEMENT (business-level)
    // ══════════════════════════════════════════════════════════════

    private synchronized void ensureBusinessSession() {
        if (currentSession != null && currentSession.isValid()) {
            return;
        }

        // If a business-username is configured, use its member credentials
        if (businessUsername != null && !businessUsername.isBlank()) {
            try {
                log.info("🔑 Obtention session Kyrrex business via credentials membre (username={})...", businessUsername);
                KyrrexUserCredential cred = withDecryptedKeys(kyrrexUserCredentialRepository.findByUsername(businessUsername)
                        .orElseThrow(() -> new RuntimeException("Aucun credential Kyrrex trouvé pour le business-username: " + businessUsername)));

                String path = "/api/v1/business/sessions";
                String body = "{}";
                String signature = generateHmacSignature("POST", path, body, cred.getSecretKey());
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + path))
                        .header("Content-Type", "application/json")
                        .header("Auth-Token", authToken)
                        .header("APIKey", cred.getAccessKey())
                        .header("APISign", signature)
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    KyrrexSessionResponse session = objectMapper.readValue(response.body(), KyrrexSessionResponse.class);
                    Instant expireAt = session.getExpireAt() != null
                            ? session.getExpireAt()
                            : Instant.now().plusSeconds(DEFAULT_SESSION_EXPIRY_SECONDS);
                    currentSession = new SessionKeys(session.getAccessKey(), session.getSecretKey(), expireAt);
                    log.info("✅ Session Kyrrex business obtenue via member credentials, expiration: {}", expireAt);
                    return;
                } else {
                    log.error("❌ Échec session via member credentials pour {}: {} {}", businessUsername, response.statusCode(), response.body());
                }
            } catch (Exception e) {
                log.error("❌ Erreur session via member credentials: {}", e.getMessage(), e);
            }
        }

        // Fallback: use businessAccessKey/businessSecretKey (legacy behaviour)
        try {
            log.info("🔑 Obtention d'une nouvelle session Kyrrex business (credentials statiques)...");
            String path = "/api/v1/business/sessions";
            String body = "{}";
            String signature = generateHmacSignature("POST", path, body, businessSecretKey);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .header("Auth-Token", authToken)
                    .header("APIKey", businessAccessKey)
                    .header("APISign", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                KyrrexSessionResponse session = objectMapper.readValue(response.body(), KyrrexSessionResponse.class);
                Instant expireAt = session.getExpireAt() != null
                        ? session.getExpireAt()
                        : Instant.now().plusSeconds(DEFAULT_SESSION_EXPIRY_SECONDS);
                currentSession = new SessionKeys(session.getAccessKey(), session.getSecretKey(), expireAt);
                log.info("✅ Session Kyrrex business obtenue via static credentials, expiration: {}", expireAt);
            } else {
                log.error("❌ Échec obtention session Kyrrex business: {} {}", response.statusCode(), response.body());
                throw new RuntimeException("Échec obtention session Kyrrex business: " + response.statusCode());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ Erreur obtention session Kyrrex business: {}", e.getMessage(), e);
            throw new RuntimeException("Erreur obtention session Kyrrex business", e);
        }
    }

    private SessionKeys ensureUserSession(String username) {
        KyrrexUserCredential cred = requireActiveCredential(username);
        if (cred.getSessionAccessKey() != null && cred.getSessionExpireAt() != null
                && Instant.now().isBefore(cred.getSessionExpireAt())) {
            return new SessionKeys(cred.getSessionAccessKey(), cred.getSessionSecretKey(), cred.getSessionExpireAt());
        }
        try {
            log.info("🔑 Obtention d'une nouvelle session Kyrrex pour l'utilisateur: {} (credentials={})",
                    username, cred.getUsername());
            String path = "/api/v1/business/sessions";
            String body = "{}";
            String signature = generateHmacSignature("POST", path, body, cred.getSecretKey());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .header("Auth-Token", authToken)
                    .header("APIKey", cred.getAccessKey())
                    .header("APISign", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                KyrrexSessionResponse session = objectMapper.readValue(response.body(), KyrrexSessionResponse.class);
                Instant expireAt = session.getExpireAt() != null
                        ? session.getExpireAt()
                        : Instant.now().plusSeconds(DEFAULT_SESSION_EXPIRY_SECONDS);
                cred.setSessionAccessKey(session.getAccessKey());
                cred.setSessionSecretKey(session.getSecretKey());
                cred.setSessionExpireAt(expireAt);
                saveCredential(cred);
                log.info("✅ Session Kyrrex obtenue pour {} (credentials={}), expiration: {}",
                        username, cred.getUsername(), expireAt);
                return new SessionKeys(session.getAccessKey(), session.getSecretKey(), expireAt);
            } else {
                log.error("❌ Échec obtention session Kyrrex pour {} (credentials={}): {} {}",
                        username, cred.getUsername(), response.statusCode(), response.body());
                throw new RuntimeException("Échec obtention session Kyrrex pour " + username + ": " + response.statusCode());
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ Erreur obtention session Kyrrex pour {} (credentials={}): {}",
                    username, cred.getUsername(), e.getMessage(), e);
            throw new RuntimeException("Erreur obtention session Kyrrex pour " + username, e);
        }
    }

    private KyrrexUserCredential requireActiveCredential(String requestedUsername) {
        String credentialUsername = resolveCredentialUsername(requestedUsername);
        Optional<KyrrexUserCredential> active =
                kyrrexUserCredentialRepository.findByUsernameAndRevokedAtIsNull(credentialUsername);
        if (active.isPresent()) {
            return withDecryptedKeys(active.get());
        }
        Optional<KyrrexUserCredential> any =
                kyrrexUserCredentialRepository.findByUsername(credentialUsername);
        boolean revoked = any.isPresent() && any.get().getRevokedAt() != null;
        throw new KyrrexCredentialMissingException(requestedUsername, credentialUsername, revoked);
    }

    boolean hasActiveCredential(String requestedUsername) {
        try {
            requireActiveCredential(requestedUsername);
            return true;
        } catch (KyrrexCredentialMissingException e) {
            return false;
        }
    }

    private String resolveCredentialUsername(String requestedUsername) {
        if (requestedUsername == null || requestedUsername.isBlank()) {
            return requestedUsername;
        }
        if (kyrrexUserCredentialRepository.findByUsernameAndRevokedAtIsNull(requestedUsername).isPresent()) {
            return requestedUsername;
        }

        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        candidates.add(requestedUsername.trim());
        userRepository.findFirstByUsernameOrderByCreatedAtAsc(requestedUsername.trim()).ifPresent(u -> {
            if (u.getUserId() != null && !u.getUserId().isBlank()) candidates.add(u.getUserId().trim());
            if (u.getUsername() != null && !u.getUsername().isBlank()) candidates.add(u.getUsername().trim());
            if (u.getMobilePhone() != null && !u.getMobilePhone().isBlank()) candidates.add(u.getMobilePhone().trim());
        });
        userRepository.findFirstByMobilePhoneOrderByCreatedAtAsc(requestedUsername.trim()).ifPresent(u -> {
            if (u.getUserId() != null && !u.getUserId().isBlank()) candidates.add(u.getUserId().trim());
            if (u.getUsername() != null && !u.getUsername().isBlank()) candidates.add(u.getUsername().trim());
            if (u.getMobilePhone() != null && !u.getMobilePhone().isBlank()) candidates.add(u.getMobilePhone().trim());
        });
        userRepository.findFirstByEmailOrderByCreatedAtAsc(requestedUsername.trim()).ifPresent(u -> {
            if (u.getUserId() != null && !u.getUserId().isBlank()) candidates.add(u.getUserId().trim());
            if (u.getUsername() != null && !u.getUsername().isBlank()) candidates.add(u.getUsername().trim());
            if (u.getMobilePhone() != null && !u.getMobilePhone().isBlank()) candidates.add(u.getMobilePhone().trim());
        });

        for (String candidate : candidates) {
            if (kyrrexUserCredentialRepository.findByUsernameAndRevokedAtIsNull(candidate).isPresent()) {
                if (!candidate.equals(requestedUsername)) {
                    log.info("🔁 Kyrrex credential lookup remapped {} -> {}", requestedUsername, candidate);
                }
                return candidate;
            }
        }
        return requestedUsername;
    }

    // ══════════════════════════════════════════════════════════════
    //  HMAC-SHA256 SIGNATURE (Convention A)
    // ══════════════════════════════════════════════════════════════

    private String generateHmacSignature(String method, String pathWithQuery, String body, String signingKey) {
        try {
            String pathOnly = pathWithQuery;
            String sortedParams;

            if (method.equalsIgnoreCase("GET")) {
                int qIdx = pathWithQuery.indexOf('?');
                if (qIdx >= 0) {
                    pathOnly = pathWithQuery.substring(0, qIdx);
                    sortedParams = buildSortedGetParams(pathWithQuery.substring(qIdx + 1));
                } else {
                    sortedParams = "";
                }
            } else {
                sortedParams = buildSortedPostParams(body);
            }

            String payload = method.toUpperCase() + "|" + pathOnly + "|" + sortedParams;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] rawHmac = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(rawHmac);
        } catch (Exception e) {
            log.error("❌ Erreur génération signature HMAC: {}", e.getMessage(), e);
            throw new RuntimeException("Erreur génération signature HMAC", e);
        }
    }

    private String buildSortedGetParams(String queryString) {
        if (queryString == null || queryString.isEmpty()) {
            return "";
        }
        Map<String, String> params = new TreeMap<>();
        for (String pair : queryString.split("&")) {
            String[] kv = pair.split("=", 2);
            String key = kv[0];
            if ("access_key".equals(key) || "nonce".equals(key)) {
                continue;
            }
            String value = kv.length == 2 ? kv[1] : "";
            params.put(key, value);
        }
        return params.entrySet().stream()
                .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                .collect(Collectors.joining("&"));
    }

    private String buildSortedPostParams(String body) {
        if (body == null || body.isEmpty() || "{}".equals(body.trim())) {
            return "";
        }
        try {
            Map<String, Object> bodyMap = objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
            Map<String, String> flattened = new TreeMap<>();
            flattenObject("", bodyMap, flattened);
            return flattened.entrySet().stream()
                    .map(e -> urlEncode(e.getKey()) + "=" + urlEncode(e.getValue()))
                    .collect(Collectors.joining("&"));
        } catch (Exception e) {
            log.warn("⚠️ Impossible de parser le body JSON pour la signature: {}", e.getMessage());
            return "";
        }
    }

    @SuppressWarnings("unchecked")
    private void flattenObject(String prefix, Map<String, Object> map, Map<String, String> result) {
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String key = prefix.isEmpty() ? entry.getKey() : prefix + "_" + entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Map) {
                flattenObject(key, (Map<String, Object>) value, result);
            } else if (value != null) {
                result.put(key, value.toString());
            }
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    // ══════════════════════════════════════════════════════════════
    //  HTTP REQUEST BUILDERS
    // ══════════════════════════════════════════════════════════════

    private HttpRequest.Builder buildRequest(String method, String path, String body) {
        ensureBusinessSession();
        SessionKeys session = currentSession;
        String signature = generateHmacSignature(method, path, body, session.secretKey());
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .header("Auth-Token", authToken)
                .header("APIKey", session.accessKey())
                .header("APISign", signature);
    }

    private HttpRequest.Builder buildUserRequest(String method, String path, String body, String username) {
        SessionKeys session = ensureUserSession(username);
        String signature = generateHmacSignature(method, path, body, session.secretKey());
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .header("Auth-Token", authToken)
                .header("APIKey", session.accessKey())
                .header("APISign", signature);
    }

    private HttpRequest.Builder buildAuthTokenRequest(String path) {
        String signature = generateHmacSignature("GET", path, "", businessSecretKey);
        return HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .header("Auth-Token", authToken)
                .header("APIKey", businessAccessKey)
                .header("APISign", signature);
    }

    private String buildErrorResponse(String message) {
        String safe = message != null ? message.replace("\"", "'") : "Internal error";
        return "{\"error\":\"" + safe + "\"}";
    }

    private <T> ResponseEntity<T> credentialMissingResponse(KyrrexCredentialMissingException ex) {
        @SuppressWarnings("unchecked")
        ResponseEntity<T> resp = (ResponseEntity<T>) ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(buildErrorResponse(ex.getMessage()));
        return resp;
    }

    private boolean isCredentialMissing(Throwable e) {
        if (e instanceof KyrrexCredentialMissingException) {
            return true;
        }
        String msg = e.getMessage();
        return msg != null && msg.contains("Aucun credential Kyrrex trouvé");
    }

    // ══════════════════════════════════════════════════════════════
    //  GENERIC HTTP EXECUTORS (business-session)
    // ══════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private <T> ResponseEntity<T> executeGet(String path, Class<T> clazz) {
        try {
            log.info("📋 GET {}", path);
            HttpRequest request = buildRequest("GET", path, "").GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("✅ GET {} → {}", path, response.statusCode());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return ResponseEntity.ok(objectMapper.readValue(response.body(), clazz));
            }
            log.error("❌ GET {} returned {}: {}", path, response.statusCode(), response.body());
            return (ResponseEntity<T>) ResponseEntity.status(response.statusCode()).body(response.body());
        } catch (Exception e) {
            log.error("❌ Erreur GET {}: {}", path, e.getMessage(), e);
            return (ResponseEntity<T>) ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse(e.getMessage()));
        }
    }

    private ResponseEntity<String> executeGetRaw(String path) {
        try {
            log.info("📋 GET {}", path);
            HttpRequest request = buildRequest("GET", path, "").GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("✅ GET {} → {}", path, response.statusCode());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return ResponseEntity.ok(response.body());
            }
            return ResponseEntity.status(response.statusCode()).body(response.body());
        } catch (Exception e) {
            log.error("❌ Erreur GET {}: {}", path, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse(e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private <T> ResponseEntity<T> executePost(String path, Object bodyObj, Class<T> clazz) {
        try {
            String bodyJson = bodyObj != null ? objectMapper.writeValueAsString(bodyObj) : "{}";
            log.info("🚀 POST {} body={}", path, bodyJson);
            HttpRequest request = buildRequest("POST", path, bodyJson)
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("✅ POST {} → {}", path, response.statusCode());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return ResponseEntity.ok(objectMapper.readValue(response.body(), clazz));
            }
            log.error("❌ POST {} returned {}: {}", path, response.statusCode(), response.body());
            return (ResponseEntity<T>) ResponseEntity.status(response.statusCode()).body(response.body());
        } catch (Exception e) {
            log.error("❌ Erreur POST {}: {}", path, e.getMessage(), e);
            return (ResponseEntity<T>) ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse(e.getMessage()));
        }
    }

    private ResponseEntity<String> executePostRaw(String path, Object bodyObj) {
        try {
            String bodyJson = bodyObj != null ? objectMapper.writeValueAsString(bodyObj) : "{}";
            log.info("🚀 POST {} body={}", path, bodyJson);
            HttpRequest request = buildRequest("POST", path, bodyJson)
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("✅ POST {} → {}", path, response.statusCode());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return ResponseEntity.ok(response.body());
            }
            return ResponseEntity.status(response.statusCode()).body(response.body());
        } catch (Exception e) {
            log.error("❌ Erreur POST {}: {}", path, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse(e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private <T> ResponseEntity<List<T>> executeGetList(String path, Class<T> elementClass) {
        try {
            log.info("📋 GET list {}", path);
            HttpRequest request = buildRequest("GET", path, "").GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("✅ GET list {} → {}", path, response.statusCode());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                List<T> list = parseListResponse(response.body(), elementClass);
                return ResponseEntity.ok(list);
            }
            log.error("❌ GET list {} returned {}: {}", path, response.statusCode(), response.body());
            return (ResponseEntity<List<T>>) (Object) ResponseEntity.status(response.statusCode()).body(response.body());
        } catch (Exception e) {
            log.error("❌ Erreur GET list {}: {}", path, e.getMessage(), e);
            return (ResponseEntity<List<T>>) (Object) ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse(e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private <T> ResponseEntity<List<T>> executeGetListWithAuthToken(String path, Class<T> elementClass) {
        try {
            log.info("📋 GET list (Auth-Token) {}", path);
            HttpRequest request = buildAuthTokenRequest(path).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("✅ GET list {} → {}", path, response.statusCode());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                List<T> list = parseListResponse(response.body(), elementClass);
                return ResponseEntity.ok(list);
            }
            log.error("❌ GET list (Auth-Token) {} returned {}: {}", path, response.statusCode(), response.body());
            return (ResponseEntity<List<T>>) (Object) ResponseEntity.status(response.statusCode()).body(response.body());
        } catch (Exception e) {
            log.error("❌ Erreur GET list (Auth-Token) {}: {}", path, e.getMessage(), e);
            return (ResponseEntity<List<T>>) (Object) ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse(e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  GENERIC HTTP EXECUTORS (user-session)
    // ══════════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private <T> ResponseEntity<T> executeUserPost(String path, Object bodyObj, Class<T> clazz, String username) {
        try {
            String bodyJson = bodyObj != null ? objectMapper.writeValueAsString(bodyObj) : "{}";
            log.info("🚀 POST {} body={} (user={})", path, bodyJson, username);
            HttpRequest request = buildUserRequest("POST", path, bodyJson, username)
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("✅ POST {} → {} (user={})", path, response.statusCode(), username);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return ResponseEntity.ok(objectMapper.readValue(response.body(), clazz));
            }
            log.error("❌ POST {} returned {}: {}", path, response.statusCode(), response.body());
            return (ResponseEntity<T>) ResponseEntity.status(response.statusCode()).body(response.body());
        } catch (Exception e) {
            log.error("❌ Erreur POST {} (user={}): {}", path, username, e.getMessage(), e);
            return (ResponseEntity<T>) ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse(e.getMessage()));
        }
    }

    private ResponseEntity<String> executeUserPostRaw(String path, Object bodyObj, String username) {
        try {
            String bodyJson = bodyObj != null ? objectMapper.writeValueAsString(bodyObj) : "{}";
            log.info("🚀 POST {} body={} (user={})", path, bodyJson, username);
            HttpRequest request = buildUserRequest("POST", path, bodyJson, username)
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("✅ POST {} → {} (user={})", path, response.statusCode(), username);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return ResponseEntity.ok(response.body());
            }
            return ResponseEntity.status(response.statusCode()).body(response.body());
        } catch (Exception e) {
            log.error("❌ Erreur POST {} (user={}): {}", path, username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse(e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private <T> ResponseEntity<T> executeUserGet(String path, Class<T> clazz, String username) {
        try {
            log.info("📋 GET {} (user={})", path, username);
            HttpRequest request = buildUserRequest("GET", path, "", username).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("✅ GET {} → {} (user={})", path, response.statusCode(), username);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return ResponseEntity.ok(objectMapper.readValue(response.body(), clazz));
            }
            log.error("❌ GET {} returned {}: {}", path, response.statusCode(), response.body());
            return (ResponseEntity<T>) ResponseEntity.status(response.statusCode()).body(response.body());
        } catch (Exception e) {
            if (isCredentialMissing(e)) {
                return e instanceof KyrrexCredentialMissingException k
                        ? credentialMissingResponse(k)
                        : (ResponseEntity<T>) ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(buildErrorResponse(e.getMessage()));
            }
            log.error("❌ Erreur GET {} (user={}): {}", path, username, e.getMessage(), e);
            return (ResponseEntity<T>) ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse(e.getMessage()));
        }
    }

    private ResponseEntity<String> executeUserGetRaw(String path, String username) {
        try {
            log.info("📋 GET {} (user={})", path, username);
            HttpRequest request = buildUserRequest("GET", path, "", username).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("✅ GET {} → {} (user={})", path, response.statusCode(), username);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return ResponseEntity.ok(response.body());
            }
            return ResponseEntity.status(response.statusCode()).body(response.body());
        } catch (Exception e) {
            if (isCredentialMissing(e)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(buildErrorResponse(e.getMessage()));
            }
            log.error("❌ Erreur GET {} (user={}): {}", path, username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse(e.getMessage()));
        }
    }

    @SuppressWarnings("unchecked")
    private <T> ResponseEntity<List<T>> executeUserGetList(String path, Class<T> elementClass, String username) {
        try {
            log.info("📋 GET list {} (user={})", path, username);
            HttpRequest request = buildUserRequest("GET", path, "", username).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("✅ GET list {} → {} (user={})", path, response.statusCode(), username);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                List<T> list = parseListResponse(response.body(), elementClass);
                return ResponseEntity.ok(list);
            }
            log.error("❌ GET list {} returned {}: {}", path, response.statusCode(), response.body());
            return (ResponseEntity<List<T>>) (Object) ResponseEntity.status(response.statusCode()).body(response.body());
        } catch (Exception e) {
            if (isCredentialMissing(e)) {
                return (ResponseEntity<List<T>>) (Object) ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(buildErrorResponse(e.getMessage()));
            }
            log.error("❌ Erreur GET list {} (user={}): {}", path, username, e.getMessage(), e);
            return (ResponseEntity<List<T>>) (Object) ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse(e.getMessage()));
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  JSON LIST PARSER
    // ══════════════════════════════════════════════════════════════

    private <T> List<T> parseListResponse(String responseBody, Class<T> elementClass) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);

        if (root.isArray()) {
            return objectMapper.readValue(responseBody,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, elementClass));
        }

        if (root.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (field.getValue().isArray()) {
                    log.debug("📦 Response wrappée détectée, extraction du champ '{}' comme tableau", field.getKey());
                    return objectMapper.readValue(field.getValue().toString(),
                            objectMapper.getTypeFactory().constructCollectionType(List.class, elementClass));
                }
            }
            log.warn("⚠️ Aucun champ tableau trouvé dans la réponse wrappée, encapsulation de l'objet dans une liste");
            T singleObject = objectMapper.treeToValue(root, elementClass);
            return new ArrayList<>(List.of(singleObject));
        }

        return objectMapper.readValue(responseBody,
                objectMapper.getTypeFactory().constructCollectionType(List.class, elementClass));
    }

    // ══════════════════════════════════════════════════════════════
    //  SECTION 1 : INSCRIPTION & SESSION (5 méthodes)
    // ══════════════════════════════════════════════════════════════

    @Override
    public boolean hasActiveKyrrexCredentials(String username) {
        return hasActiveCredential(username);
    }

    @Override
    public List<KyrrexCountryResponse> listCountriesForRegistration() {
        return loadRegistrationCountries();
    }

    @Override
    public int resolveSignupCountryId(String username, Integer requestedCountryId) {
        List<KyrrexCountryResponse> countries = loadRegistrationCountries();
        if (requestedCountryId != null && isAllowedCountryId(countries, requestedCountryId)) {
            return requestedCountryId;
        }
        Optional<Users> user = findAkuundaUser(username);
        String iso = user.map(this::guessIsoCountryCode).orElse(null);
        if (iso != null) {
            Optional<Integer> fromIso = countries.stream()
                    .filter(c -> c.getCode() != null && iso.equalsIgnoreCase(c.getCode().trim()))
                    .map(KyrrexCountryResponse::getId)
                    .findFirst();
            if (fromIso.isPresent()) {
                log.info("🌍 country_id Kyrrex {} pour {} (ISO {})", fromIso.get(), username, iso);
                return fromIso.get();
            }
        }
        if (!countries.isEmpty() && countries.get(0).getId() != null) {
            int first = countries.get(0).getId();
            log.warn("⚠️ Fallback premier pays Kyrrex id={} pour {}", first, username);
            return first;
        }
        throw new IllegalStateException(
                "Catalogue pays Kyrrex indisponible — impossible de résoudre country_id pour " + username);
    }

    @Override
    public ResponseEntity<KyrrexMemberSignUpResponse> ensureKyrrexMemberRegistered(
            String username, KyrrexMemberSignUpRequest request) {
        if (hasActiveCredential(username)) {
            return ResponseEntity.ok(new KyrrexMemberSignUpResponse());
        }
        KyrrexMemberSignUpRequest normalized = normalizeSignUpRequest(username, request);
        if (normalized.getEmail() == null || normalized.getEmail().isBlank()) {
            throw new KyrrexCredentialMissingException(username, username, false);
        }
        ResponseEntity<KyrrexMemberSignUpResponse> registered = registerMember(username, normalized);
        if (hasActiveCredential(username)) {
            return registered;
        }
        log.warn("⚠️ Inscription Kyrrex n'a pas créé de credentials locaux pour {} (HTTP {})",
                username, registered.getStatusCode().value());
        throw new KyrrexCredentialMissingException(username, username, false);
    }

    @Override
    public ResponseEntity<KyrrexMemberSignUpResponse> registerMember(String username, KyrrexMemberSignUpRequest request) {
        log.info("📝 Inscription du membre Kyrrex pour l'utilisateur: {}", username);
        KyrrexMemberSignUpRequest normalized = normalizeSignUpRequest(username, request);
        log.info("📝 Sign-up Kyrrex country_id={} type={} email={}",
                normalized.getCountryId(), normalized.getType(), normalized.getEmail());
        try {
            String bodyJson = objectMapper.writeValueAsString(normalized);
            String path = "/api/v1/business/members/sign-up";
            String signature = generateHmacSignature("POST", path, bodyJson, businessSecretKey);
            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .header("Auth-Token", authToken)
                    .header("APIKey", businessAccessKey)
                    .header("APISign", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                    .build();
            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            log.info("✅ POST {} → {}", path, response.statusCode());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                KyrrexMemberSignUpResponse signUpResponse = objectMapper.readValue(response.body(), KyrrexMemberSignUpResponse.class);
                KyrrexUserCredential cred = kyrrexUserCredentialRepository.findByUsername(username)
                        .orElseGet(() -> KyrrexUserCredential.builder().username(username).build());
                cred.setKyrrexMemberUid(signUpResponse.getUid());
                cred.setAccessKey(signUpResponse.getAccessKey());
                cred.setSecretKey(signUpResponse.getSecretKey());
                cred.setRevokedAt(null);
                cred.clearSession();
                saveCredential(cred);
                log.info("✅ Membre Kyrrex inscrit, uid={}", signUpResponse.getUid());
                return ResponseEntity.ok(signUpResponse);
            }
            log.error("❌ Échec inscription membre Kyrrex: {} {}", response.statusCode(), response.body());
            return ResponseEntity.status(response.statusCode())
                    .body(parseSignUpResponseBody(response.body()));
        } catch (Exception e) {
            log.error("❌ Erreur inscription membre Kyrrex: {}", e.getMessage(), e);
            KyrrexMemberSignUpResponse err = new KyrrexMemberSignUpResponse();
            err.setStatus(e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
        }
    }

    private KyrrexMemberSignUpResponse parseSignUpResponseBody(String raw) {
        if (raw == null || raw.isBlank()) {
            return new KyrrexMemberSignUpResponse();
        }
        try {
            return objectMapper.readValue(raw, KyrrexMemberSignUpResponse.class);
        } catch (Exception ignored) {
            KyrrexMemberSignUpResponse fallback = new KyrrexMemberSignUpResponse();
            fallback.setStatus(raw);
            return fallback;
        }
    }

    private KyrrexMemberSignUpRequest normalizeSignUpRequest(String username, KyrrexMemberSignUpRequest request) {
        Optional<Users> user = findAkuundaUser(username);
        String email = request != null && request.getEmail() != null && !request.getEmail().isBlank()
                ? request.getEmail().trim()
                : user.map(Users::getEmail).map(String::trim).orElse(null);
        String type = request != null && request.getType() != null && !request.getType().isBlank()
                ? request.getType().trim()
                : guessMemberType(user.orElse(null));
        Integer requestedCountry = request != null ? request.getCountryId() : null;
        int countryId = resolveSignupCountryId(username, requestedCountry);
        return new KyrrexMemberSignUpRequest(email, type, countryId);
    }

    private Optional<Users> findAkuundaUser(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        String u = username.trim();
        Optional<Users> byUsername = userRepository.findFirstByUsernameOrderByCreatedAtAsc(u);
        if (byUsername.isPresent()) {
            return byUsername;
        }
        return userRepository.findFirstByMobilePhoneOrderByCreatedAtAsc(u);
    }

    private String guessMemberType(Users user) {
        if (user == null || user.getAccountType() == null) {
            return "personal";
        }
        String at = user.getAccountType().toLowerCase(Locale.ROOT);
        if (at.contains("business") || at.contains("merchant") || at.contains("pro")
                || at.contains("entreprise") || at.contains("company")) {
            return "business";
        }
        return "personal";
    }

    private String guessIsoCountryCode(Users user) {
        if (user.getCountryCurrency() != null && user.getCountryCurrency().getCountryCode() != null
                && !user.getCountryCurrency().getCountryCode().isBlank()) {
            return user.getCountryCurrency().getCountryCode().trim().toUpperCase(Locale.ROOT);
        }
        return guessIsoFromPhone(user.getMobilePhone());
    }

    private static String guessIsoFromPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return null;
        }
        String digits = phone.replaceAll("\\D", "");
        if (digits.startsWith("00")) {
            digits = digits.substring(2);
        }
        record Prefix(String prefix, String iso) {}
        Prefix[] prefixes = {
                new Prefix("33", "FR"),
                new Prefix("225", "CI"),
                new Prefix("221", "SN"),
                new Prefix("226", "BF"),
                new Prefix("228", "TG"),
                new Prefix("229", "BJ"),
                new Prefix("237", "CM"),
                new Prefix("241", "GA"),
                new Prefix("242", "CG"),
                new Prefix("243", "CD"),
                new Prefix("234", "NG"),
                new Prefix("233", "GH"),
                new Prefix("212", "MA"),
                new Prefix("213", "DZ"),
                new Prefix("216", "TN"),
                new Prefix("49", "DE"),
                new Prefix("44", "GB"),
                new Prefix("1", "US"),
        };
        for (Prefix p : prefixes) {
            if (digits.startsWith(p.prefix())) {
                return p.iso();
            }
        }
        return null;
    }

    private List<KyrrexCountryResponse> loadRegistrationCountries() {
        if (!cachedRegistrationCountries.isEmpty()) {
            return cachedRegistrationCountries;
        }
        synchronized (this) {
            if (!cachedRegistrationCountries.isEmpty()) {
                return cachedRegistrationCountries;
            }
            ResponseEntity<List<KyrrexCountryResponse>> resp =
                    executeGetList("/api/v1/business/tools/countries", KyrrexCountryResponse.class);
            if (resp.getStatusCode().is2xxSuccessful() && resp.getBody() != null) {
                cachedRegistrationCountries = List.copyOf(resp.getBody());
                log.info("🌍 Catalogue Kyrrex chargé: {} pays", cachedRegistrationCountries.size());
            } else {
                log.warn("⚠️ Impossible de charger le catalogue pays Kyrrex: HTTP {}",
                        resp.getStatusCode().value());
            }
            return cachedRegistrationCountries;
        }
    }

    private static boolean isAllowedCountryId(List<KyrrexCountryResponse> countries, Integer id) {
        if (id == null || countries == null || countries.isEmpty()) {
            return false;
        }
        return countries.stream().anyMatch(c -> id.equals(c.getId()));
    }

    @Override
    public ResponseEntity<KyrrexSessionResponse> createSession(String username) {
        log.info("🔑 Création session Kyrrex pour: {}", username);
        SessionKeys session = ensureUserSession(username);
        KyrrexSessionResponse resp = new KyrrexSessionResponse();
        resp.setAccessKey(session.accessKey());
        resp.setExpireAt(session.expireAt());
        return ResponseEntity.ok(resp);
    }

    @Override
    public ResponseEntity<KyrrexSessionLoginResponse> loginSession(String username) {
        log.info("🔑 Création session Kyrrex pour l'utilisateur: {}", username);
        KyrrexUserCredential cred = requireActiveCredential(username);
        try {
            String path = "/api/v1/business/sessions";
            String body = "{}";
            String signature = generateHmacSignature("POST", path, body, cred.getSecretKey());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .header("Auth-Token", authToken)
                    .header("APIKey", cred.getAccessKey())
                    .header("APISign", signature)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("✅ POST {} → {} (user={})", path, response.statusCode(), username);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                KyrrexSessionLoginResponse sessionResponse = objectMapper.readValue(response.body(), KyrrexSessionLoginResponse.class);
                Instant expireAt = sessionResponse.getExpireAt() != null
                        ? Instant.parse(sessionResponse.getExpireAt())
                        : Instant.now().plusSeconds(DEFAULT_SESSION_EXPIRY_SECONDS);
                cred.setSessionAccessKey(sessionResponse.getAccessKey());
                cred.setSessionSecretKey(sessionResponse.getSecretKey());
                cred.setSessionExpireAt(expireAt);
                saveCredential(cred);
                log.info("✅ Session Kyrrex créée pour {}, expiration: {}", username, expireAt);
                return ResponseEntity.status(response.statusCode()).body(sessionResponse);
            }
            log.error("❌ Échec création session Kyrrex pour {}: {} {}", username, response.statusCode(), response.body());
            return ResponseEntity.status(response.statusCode()).build();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ Erreur création session Kyrrex pour {}: {}", username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public ResponseEntity<Boolean> logoutSession(String username) {
        log.info("🔒 Suppression session Kyrrex pour l'utilisateur: {}", username);
        KyrrexUserCredential cred = requireActiveCredential(username);
        if (cred.getSessionAccessKey() == null) {
            log.warn("⚠️ Aucune session active à supprimer pour l'utilisateur: {}", username);
            return ResponseEntity.ok(false);
        }
        try {
            String path = "/api/v1/business/sessions";
            String body = "";
            String signature = generateHmacSignature("DELETE", path, body, cred.getSessionSecretKey());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .header("Auth-Token", authToken)
                    .header("APIKey", cred.getSessionAccessKey())
                    .header("APISign", signature)
                    .DELETE()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("✅ DELETE {} → {} (user={})", path, response.statusCode(), username);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                cred.setSessionAccessKey(null);
                cred.setSessionSecretKey(null);
                cred.setSessionExpireAt(null);
                saveCredential(cred);
                log.info("✅ Session Kyrrex supprimée pour {}", username);
                return ResponseEntity.ok(true);
            }
            log.error("❌ Échec suppression session Kyrrex pour {}: {} {}", username, response.statusCode(), response.body());
            return ResponseEntity.status(response.statusCode()).body(false);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ Erreur suppression session Kyrrex pour {}: {}", username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(false);
        }
    }

    @Override
    public ResponseEntity<KyrrexSessionListResponse> listSessions(String username, Integer page, Integer perPage) {
        log.info("📋 Liste des sessions Kyrrex pour l'utilisateur: {}", username);
        KyrrexUserCredential cred = requireActiveCredential(username);
        if (cred.getSessionAccessKey() == null) {
            log.warn("⚠️ Aucune session active pour l'utilisateur: {}", username);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        try {
            StringBuilder pathBuilder = new StringBuilder("/api/v1/business/sessions");
            if (page != null || perPage != null) {
                pathBuilder.append("?");
                if (page != null) {
                    pathBuilder.append("page=").append(page);
                    if (perPage != null) pathBuilder.append("&");
                }
                if (perPage != null) {
                    pathBuilder.append("per_page=").append(perPage);
                }
            }
            String path = pathBuilder.toString();
            String signature = generateHmacSignature("GET", path, "", cred.getSessionSecretKey());
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .header("Content-Type", "application/json")
                    .header("Auth-Token", authToken)
                    .header("APIKey", cred.getSessionAccessKey())
                    .header("APISign", signature)
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("✅ GET {} → {} (user={})", path, response.statusCode(), username);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return ResponseEntity.ok(objectMapper.readValue(response.body(), KyrrexSessionListResponse.class));
            }
            log.error("❌ GET {} returned {}: {}", path, response.statusCode(), response.body());
            return ResponseEntity.status(response.statusCode()).build();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("❌ Erreur liste sessions Kyrrex pour {}: {}", username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  SECTION 2 : MEMBERS (3 méthodes — NOUVELLES)
    // ══════════════════════════════════════════════════════════════

    @Override
    public ResponseEntity<KyrrexMemberInfoResponse> getMemberInfo(String username) {
        log.info("👤 Récupération infos membre Kyrrex pour: {}", username);
        return executeUserGet("/api/v1/business/members/me", KyrrexMemberInfoResponse.class, username);
    }

    @Override
    public ResponseEntity<KyrrexMemberTotalBalanceResponse> getMemberTotalBalance(String username, String outputAsset) {
        log.info("💰 Récupération solde total membre Kyrrex pour: {}", username);
        String path = "/api/v1/business/members/total_balance";
        if (outputAsset != null && !outputAsset.isBlank()) path += "?output_asset=" + urlEncode(outputAsset);
        return executeUserGet(path, KyrrexMemberTotalBalanceResponse.class, username);
    }

    @Override
    public ResponseEntity<List<KyrrexMemberAccountResponse>> getMemberAccounts(String username) {
        log.info("📋 Récupération comptes membre Kyrrex pour: {}", username);
        return executeUserGetList("/api/v1/business/members/accounts", KyrrexMemberAccountResponse.class, username);
    }

    // ══════════════════════════════════════════════════════════════
    //  SECTION 3 : KYC / KYB (8 méthodes — 1 NOUVELLE: getKycStatus)
    // ══════════════════════════════════════════════════════════════

    @Override
    public ResponseEntity<KyrrexKycTokenResponse> generateKycToken(String username) {
        log.info("🔐 Génération token KYC pour: {}", username);
        return executeUserPost("/api/v1/business/kyc/generate_token", null, KyrrexKycTokenResponse.class, username);
    }

    @Override
    public ResponseEntity<KyrrexKycTokenResponse> generateKycSharedToken(String username) {
        log.info("🔐 Génération shared token KYC pour: {}", username);
        return executeUserPost("/api/v1/business/kyc/shared_token", null, KyrrexKycTokenResponse.class, username);
    }

    @Override
    public ResponseEntity<KyrrexKycWebLinkResponse> generateKycWebLink(String username) {
        log.info("🔗 Génération web link KYC pour: {}", username);
        return executeUserPost("/api/v1/business/kyc/generate_web_link", null, KyrrexKycWebLinkResponse.class, username);
    }

    @Override
    public ResponseEntity<List<KyrrexKycLevelsResponse>> getKycLevels(String username) {
        log.info("📋 Récupération niveaux KYC pour: {}", username);
        return executeUserGetList("/api/v1/business/kyc/levels", KyrrexKycLevelsResponse.class, username);
    }

    /** NOUVEAU — GET /kyc/status/{customerId} */
    @Override
    public ResponseEntity<KyrrexKycStatusResponse> getKycStatus(String username, String customerId) {
        log.info("🔍 Récupération statut KYC pour customerId={} (user={})", customerId, username);
        return executeUserGet("/api/v1/business/kyc/status/" + urlEncode(customerId), KyrrexKycStatusResponse.class, username);
    }

    @Override
    public ResponseEntity<KyrrexKybTokenResponse> generateKybToken(String username) {
        log.info("🔐 Génération token KYB pour: {}", username);
        return executeUserPost("/api/v1/business/kyb/generate_token", null, KyrrexKybTokenResponse.class, username);
    }

    @Override
    public ResponseEntity<KyrrexKybWebLinkResponse> generateKybWebLink(String username) {
        log.info("🔗 Génération web link KYB pour: {}", username);
        return executeUserPost("/api/v1/business/kyb/generate_web_link", null, KyrrexKybWebLinkResponse.class, username);
    }

    @Override
    public ResponseEntity<KyrrexLegalEntityInfoResponse> getLegalEntityInfo(String username) {
        log.info("📋 Récupération infos entité légale pour: {}", username);
        return executeUserGet("/api/v1/business/kyb/legal_entity_info", KyrrexLegalEntityInfoResponse.class, username);
    }

    // ══════════════════════════════════════════════════════════════
    //  SECTION 4 : TOOLS (5 méthodes — 3 NOUVELLES)
    // ══════════════════════════════════════════════════════════════

    @Override
    public ResponseEntity<List<KyrrexCountryResponse>> getCountries(String username) {
        log.info("🌍 Récupération des pays Kyrrex (user={})", username);
        return executeUserGetList("/api/v1/business/tools/countries", KyrrexCountryResponse.class, username);
    }

    /** NOUVEAU — GET /tools/countries_codes */
    @Override
    public ResponseEntity<List<KyrrexCountryCodeResponse>> getCountriesCodes(String username) {
        log.info("🌍 Récupération des codes pays Kyrrex (user={})", username);
        return executeUserGetList("/api/v1/business/tools/countries_codes", KyrrexCountryCodeResponse.class, username);
    }

    @Override
    public ResponseEntity<List<KyrrexIdentificationDocumentResponse>> getIdentificationDocuments(String username) {
        log.info("📄 Récupération des documents d'identification Kyrrex (user={})", username);
        return executeUserGetList("/api/v1/business/tools/identification_documents", KyrrexIdentificationDocumentResponse.class, username);
    }

    /** NOUVEAU — GET /tools/vasps */
    @Override
    public ResponseEntity<List<KyrrexVaspResponse>> getVasps(String username) {
        log.info("🏢 Récupération des VASPs Kyrrex (user={})", username);
        return executeUserGetList("/api/v1/business/tools/vasps", KyrrexVaspResponse.class, username);
    }

    /** NOUVEAU — GET /tools/timestamp */
    @Override
    public ResponseEntity<KyrrexTimestampResponse> getTimestamp(String username) {
        log.info("⏱️ Récupération timestamp serveur Kyrrex (user={})", username);
        return executeUserGet("/api/v1/business/tools/timestamp", KyrrexTimestampResponse.class, username);
    }

    // ══════════════════════════════════════════════════════════════
    //  SECTION 5 : BALANCES & ASSETS (3 méthodes)
    // ══════════════════════════════════════════════════════════════

    @Override
    public ResponseEntity<List<KyrrexBalanceResponse>> getBalances(String username) {
        log.info("💰 Récupération des soldes Kyrrex (user={})", username);
        return executeUserGetList("/api/v1/business/balances", KyrrexBalanceResponse.class, username);
    }

    @Override
    public ResponseEntity<KyrrexPaginatedAssetsResponse> getAssets(String username, Boolean activeDeposit, Boolean activeWithdrawal, Integer page, Integer perPage) {
        StringBuilder pathBuilder = new StringBuilder("/api/v1/business/assets?");
        if (activeDeposit != null) {
            pathBuilder.append("active_deposit=").append(activeDeposit).append("&");
        }
        if (activeWithdrawal != null) {
            pathBuilder.append("active_withdrawal=").append(activeWithdrawal).append("&");
        }
        if (page != null) {
            pathBuilder.append("page=").append(page).append("&");
        }
        if (perPage != null) {
            pathBuilder.append("per_page=").append(perPage).append("&");
        }
        String path = pathBuilder.toString().replaceAll("[&?]+$", "");
        log.info("📋 Récupération des assets Kyrrex (user={}, activeDeposit={}, activeWithdrawal={}, page={}, perPage={})",
                username, activeDeposit, activeWithdrawal, page, perPage);
        return executeUserGet(path, KyrrexPaginatedAssetsResponse.class, username);
    }

    @Override
    public ResponseEntity<String> getCurrencySettings(String username) {
        log.info("⚙️ Récupération currency settings Kyrrex (user={})", username);
        return executeUserGetRaw("/api/v1/business/settings/currencies", username);
    }

    // ══════════════════════════════════════════════════════════════
    //  SECTION 6 : CUSTOMER & PROVIDER (6 méthodes)
    // ══════════════════════════════════════════════════════════════

    @Override
    public ResponseEntity<KyrrexCustomerResponse> getCustomerDetails(String username) {
        log.info("📋 Récupération des détails du client Kyrrex pour: {}", username);
        return executeUserGet("/api/v1/business/customer", KyrrexCustomerResponse.class, username);
    }

    @Override
    public ResponseEntity<KyrrexCustomerResponse> getCustomerStatus(String username, String providerId) {
        log.info("🔍 Statut client chez provider: {} (user={})", providerId, username);
        return executeUserGet("/api/v1/business/fiat/providers/" + urlEncode(providerId) + "/customers", KyrrexCustomerResponse.class, username);
    }

    @Override
    public ResponseEntity<KyrrexCustomerResponse> registerCustomerAtProvider(String username, String providerId) {
        log.info("👤 Enregistrement client chez provider: {} (user={})", providerId, username);
        return executeUserPost("/api/v1/business/fiat/" + urlEncode(providerId) + "/customers", null, KyrrexCustomerResponse.class, username);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ResponseEntity<List<KyrrexProviderResponse>> getFiatProviders(String username) {
        log.info("📋 Récupération des providers fiat Kyrrex (user={})", username);
        try {
            HttpRequest request = buildUserRequest("GET", "/api/v1/business/fiat/providers/deposits", "", username).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("✅ GET /api/v1/business/fiat/providers/deposits → {} (user={})", response.statusCode(), username);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                List<KyrrexProviderResponse> providers = flattenFiatProviders(response.body());
                return ResponseEntity.ok(providers);
            }
            log.error("❌ GET providers returned {}: {}", response.statusCode(), response.body());
            return (ResponseEntity<List<KyrrexProviderResponse>>) (Object) ResponseEntity.status(response.statusCode()).body(response.body());
        } catch (Exception e) {
            log.error("❌ Erreur GET providers (user={}): {}", username, e.getMessage(), e);
            return (ResponseEntity<List<KyrrexProviderResponse>>) (Object) ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse(e.getMessage()));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ResponseEntity<List<KyrrexProviderResponse>> getFiatDepositProviders(String username) {
        log.info("📋 Récupération des providers fiat dépôt Kyrrex (user={})", username);
        try {
            HttpRequest request = buildUserRequest("GET", "/api/v1/business/fiat/providers/deposits", "", username).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("✅ GET /api/v1/business/fiat/providers/deposits → {} (user={})", response.statusCode(), username);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                List<KyrrexProviderResponse> providers = flattenFiatProviders(response.body());
                return ResponseEntity.ok(providers);
            }
            log.error("❌ GET fiat deposit providers returned {}: {}", response.statusCode(), response.body());
            return (ResponseEntity<List<KyrrexProviderResponse>>) (Object) ResponseEntity.status(response.statusCode()).body(response.body());
        } catch (Exception e) {
            log.error("❌ Erreur GET fiat deposit providers (user={}): {}", username, e.getMessage(), e);
            return (ResponseEntity<List<KyrrexProviderResponse>>) (Object) ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse(e.getMessage()));
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public ResponseEntity<List<KyrrexProviderResponse>> getFiatWithdrawalProviders(String username) {
        log.info("📋 Récupération des providers fiat retrait Kyrrex (user={})", username);
        try {
            HttpRequest request = buildUserRequest("GET", "/api/v1/business/fiat/providers/withdrawals", "", username).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.info("✅ GET /api/v1/business/fiat/providers/withdrawals → {} (user={})", response.statusCode(), username);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                List<KyrrexProviderResponse> providers = flattenFiatProviders(response.body());
                return ResponseEntity.ok(providers);
            }
            log.error("❌ GET fiat withdrawal providers returned {}: {}", response.statusCode(), response.body());
            return (ResponseEntity<List<KyrrexProviderResponse>>) (Object) ResponseEntity.status(response.statusCode()).body(response.body());
        } catch (Exception e) {
            log.error("❌ Erreur GET fiat withdrawal providers (user={}): {}", username, e.getMessage(), e);
            return (ResponseEntity<List<KyrrexProviderResponse>>) (Object) ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse(e.getMessage()));
        }
    }

    private List<KyrrexProviderResponse> flattenFiatProviders(String responseBody) throws Exception {
        List<KyrrexProviderResponse> result = new ArrayList<>();
        JsonNode root = objectMapper.readTree(responseBody);
        Iterator<Map.Entry<String, JsonNode>> categories = root.fields();
        while (categories.hasNext()) {
            Map.Entry<String, JsonNode> categoryEntry = categories.next();
            String category = categoryEntry.getKey();
            JsonNode categoryNode = categoryEntry.getValue();
            if (categoryNode.isObject()) {
                Iterator<Map.Entry<String, JsonNode>> instruments = categoryNode.fields();
                while (instruments.hasNext()) {
                    Map.Entry<String, JsonNode> instrumentEntry = instruments.next();
                    String instrument = instrumentEntry.getKey();
                    JsonNode providerArray = instrumentEntry.getValue();
                    if (providerArray.isArray()) {
                        for (JsonNode providerNode : providerArray) {
                            KyrrexProviderResponse provider = objectMapper.treeToValue(providerNode, KyrrexProviderResponse.class);
                            provider.setCategory(category);
                            provider.setInstrument(instrument);
                            result.add(provider);
                        }
                    }
                }
            }
        }
        return result;
    }

    // ══════════════════════════════════════════════════════════════
    //  SECTION 7 : INSTRUMENTS — CARTES & VIREMENTS (4 méthodes)
    // ══════════════════════════════════════════════════════════════

    @Override
    public ResponseEntity<KyrrexCardInstrumentResponse> createCardInstrument(String username, String providerId, KyrrexCardInstrumentRequest request) {
        log.info("💳 Création instrument carte chez provider: {} (user={})", providerId, username);
        return executeUserPost("/api/v1/business/fiat/providers/" + urlEncode(providerId) + "/instruments/cards",
                request, KyrrexCardInstrumentResponse.class, username);
    }

    @Override
    public ResponseEntity<List<KyrrexCardInstrumentResponse>> getCardInstruments(String username, String providerId, String instrument) {
        log.info("💳 Liste instruments carte chez provider: {}, instrument: {} (user={})", providerId, instrument, username);
        String path = "/api/v1/business/fiat/providers/" + urlEncode(providerId) + "/instruments/cards?instrument=" + urlEncode(instrument);
        return executeUserGetList(path, KyrrexCardInstrumentResponse.class, username);
    }

    @Override
    public ResponseEntity<String> createBankTransferInstrument(String username, String providerId, Object body) {
        log.info("🏦 Création instrument virement chez provider: {} (user={})", providerId, username);
        return executeUserPostRaw("/api/v1/business/fiat/providers/" + urlEncode(providerId) + "/instruments/bank_transfers", body, username);
    }

    @Override
    public ResponseEntity<String> getBankTransferInstrumentDetails(String username, String providerId, String instrumentId) {
        String path = "/api/v1/business/fiat/providers/" + urlEncode(providerId) + "/instruments/bank_transfers?instrument=" + urlEncode(instrumentId);
        log.info("🏦 Détails instrument virement SEPA: provider={}, instrument={} (user={})", providerId, instrumentId, username);
        return executeUserGetRaw(path, username);
    }

    // ══════════════════════════════════════════════════════════════
    //  SECTION 8 : FIAT DEPOSIT (6 méthodes — 2 NOUVELLES)
    // ══════════════════════════════════════════════════════════════

    @Override
    public ResponseEntity<KyrrexFiatDepositResponse> createFiatDeposit(String username, KyrrexFiatDepositRequest request) {
        log.info("💳 Dépôt fiat via carte pour utilisateur: {}", username);
        try {
            if (request.getRedirectUrl() == null || request.getRedirectUrl().isBlank()) {
                request.setRedirectUrl("https://akuunda-pay.io/");
            }

            ResponseEntity<KyrrexFiatDepositResponse> response = executeUserPost(
                    "/api/v1/business/fiat/deposits", request, KyrrexFiatDepositResponse.class, username);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                KyrrexFiatDepositResponse body = response.getBody();
                KyrrexTransaction tx = KyrrexTransaction.builder()
                        .kyrrexId(body.getUuid())
                        .username(username)
                        .type(KyrrexTransactionType.ADVANCED_EXCHANGE)
                        .status("INITIATED")
                        .fiatAmount(request.getAmount())
                        .redirectUrl(body.getFrameUrl())
                        .providerId(request.getProviderId())
                        .build();
                kyrrexTransactionRepository.save(tx);
                Operation op = buildOperation(username, body.getUuid(), "KYRREX_ON_RAMP", "CREDIT",
                        request.getAmount() != null ? request.getAmount().doubleValue() : null,
                        null, null, null);
                operationRepository.save(op);
                log.info("✅ Dépôt fiat créé, uuid={}, frameUrl={}", body.getUuid(), body.getFrameUrl());
            }
            return response;
        } catch (Exception e) {
            log.error("❌ Erreur dépôt fiat: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /** NOUVEAU — POST /deposits/fiat/generate_link */
    @Override
    public ResponseEntity<KyrrexFiatDepositLinkResponse> generateFiatDepositLink(String username, KyrrexFiatDepositLinkRequest request) {
        log.info("🔗 Génération lien dépôt fiat pour: {} (amount={}, currency={})",
                username, request.getAmount(), request.getCurrency());
        return executeUserPost("/api/v1/business/fiat/deposits/generate_link",
                request, KyrrexFiatDepositLinkResponse.class, username);
    }

    @Override
    public ResponseEntity<String> getFiatDepositFees(String username, String instrument, String providerId) {
        String path = "/api/v1/business/fiat/deposits/fees?instrument=" + urlEncode(instrument) + "&provider_id=" + urlEncode(providerId);
        log.info("💰 Récupération des frais de dépôt fiat: instrument={}, provider={} (user={})", instrument, providerId, username);
        return executeUserGetRaw(path, username);
    }

    @Override
    public ResponseEntity<String> getFiatDepositFeeEstimate(String username, String providerId, String instrument, String amount) {
        String path = "/api/v1/business/fiat/deposits/fees/estimate?provider_id=" + urlEncode(providerId)
                + "&instrument=" + urlEncode(instrument) + "&amount=" + urlEncode(amount);
        log.info("💰 Estimation frais dépôt fiat: provider={}, instrument={}, amount={} (user={})", providerId, instrument, amount, username);
        return executeUserGetRaw(path, username);
    }

    @Override
    public ResponseEntity<String> getFiatDepositHistory(String username) {
        log.info("📋 Historique des dépôts fiat (user={})", username);
        return executeUserGetRaw("/api/v1/business/fiat/deposits", username);
    }

    /** NOUVEAU — GET /deposits/fiat/{depositId} */
    @Override
    public ResponseEntity<KyrrexFiatDepositDetailResponse> getFiatDepositById(String username, String depositId) {
        log.info("🔍 Détail dépôt fiat id={} (user={})", depositId, username);
        return executeUserGet("/api/v1/business/fiat/deposits/" + urlEncode(depositId),
                KyrrexFiatDepositDetailResponse.class, username);
    }

    // ══════════════════════════════════════════════════════════════
    //  SECTION 9 : FIAT WITHDRAWAL (6 méthodes — 2 NOUVELLES)
    // ══════════════════════════════════════════════════════════════

    @Override
    public ResponseEntity<KyrrexFiatWithdrawalResponse> executeFiatWithdrawal(String username, KyrrexFiatWithdrawalRequest request) {
        log.info("💸 Retrait fiat pour: {}", username);
        try {
            ResponseEntity<KyrrexFiatWithdrawalResponse> response = executeUserPost(
                    "/api/v1/business/fiat/withdrawals", request, KyrrexFiatWithdrawalResponse.class, username);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                KyrrexFiatWithdrawalResponse body = response.getBody();
                KyrrexTransaction tx = KyrrexTransaction.builder()
                        .kyrrexId(body.getId())
                        .username(username)
                        .type(KyrrexTransactionType.FIAT_WITHDRAWAL)
                        .status(body.getStatus())
                        .fiatCurrency(body.getCurrency())
                        .providerId(request.getProviderId())
                        .paymentMethod(request.getPayoutMethod())
                        .instrument(request.getInstrument())
                        .build();
                kyrrexTransactionRepository.save(tx);
                Operation op = buildOperation(username, body.getId(), "KYRREX_OFF_RAMP", "DEBIT",
                        request.getAmount() != null ? request.getAmount().doubleValue() : null,
                        request.getCurrency(), null, null);
                operationRepository.save(op);
                log.info("✅ Retrait fiat créé, id={}", body.getId());
            }
            return response;
        } catch (Exception e) {
            log.error("❌ Erreur retrait fiat: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /** NOUVEAU — POST /withdrawals/fiat/bank_details */
    @Override
    public ResponseEntity<KyrrexFiatWithdrawalBankDetailsResponse> createFiatWithdrawalBankDetails(
            String username, KyrrexFiatWithdrawalBankDetailsRequest request) {
        log.info("🏦 Retrait fiat par virement bancaire pour: {} (amount={}, currency={})",
                username, request.getAmount(), request.getCurrency());
        try {
            ResponseEntity<KyrrexFiatWithdrawalBankDetailsResponse> response = executeUserPost(
                    "/api/v1/business/fiat/withdrawals/bank_details",
                    request, KyrrexFiatWithdrawalBankDetailsResponse.class, username);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                KyrrexFiatWithdrawalBankDetailsResponse body = response.getBody();
                KyrrexTransaction tx = KyrrexTransaction.builder()
                        .kyrrexId(body.getUid())
                        .username(username)
                        .type(KyrrexTransactionType.FIAT_WITHDRAWAL)
                        .status(body.getStatus())
                        .fiatCurrency(body.getCurrency())
                        .paymentMethod("BANK_TRANSFER")
                        .build();
                kyrrexTransactionRepository.save(tx);
                Operation op = buildOperation(username, body.getUid(), "KYRREX_OFF_RAMP", "DEBIT",
                        request.getAmount() != null ? request.getAmount().doubleValue() : null,
                        request.getCurrency(), null, null);
                operationRepository.save(op);
                log.info("✅ Retrait fiat bank_details créé, id={}", body.getUid());
            }
            return response;
        } catch (Exception e) {
            log.error("❌ Erreur retrait fiat bank_details: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /** NOUVEAU — POST /withdrawals/fiat/card */
    @Override
    public ResponseEntity<KyrrexFiatWithdrawalCardResponse> createFiatWithdrawalCard(
            String username, KyrrexFiatWithdrawalCardRequest request) {
        log.info("💳 Retrait fiat par carte pour: {} (amount={}, currency={})",
                username, request.getAmount(), request.getCurrency());
        try {
            ResponseEntity<KyrrexFiatWithdrawalCardResponse> response = executeUserPost(
                    "/api/v1/business/fiat/withdrawals/card",
                    request, KyrrexFiatWithdrawalCardResponse.class, username);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                KyrrexFiatWithdrawalCardResponse body = response.getBody();
                KyrrexTransaction tx = KyrrexTransaction.builder()
                        .kyrrexId(body.getUid())
                        .username(username)
                        .type(KyrrexTransactionType.FIAT_WITHDRAWAL)
                        .status(body.getStatus())
                        .fiatCurrency(body.getCurrency())
                        .paymentMethod("CARD")
                        .build();
                kyrrexTransactionRepository.save(tx);
                Operation op = buildOperation(username, body.getUid(), "KYRREX_OFF_RAMP", "DEBIT",
                        request.getAmount() != null ? request.getAmount().doubleValue() : null,
                        request.getCurrency(), null, null);
                operationRepository.save(op);
                log.info("✅ Retrait fiat card créé, id={}", body.getUid());
            }
            return response;
        } catch (Exception e) {
            log.error("❌ Erreur retrait fiat card: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public ResponseEntity<KyrrexWithdrawalFeeEstimateResponse> getWithdrawalFeeEstimate(String username, String instrument, String amount, String providerId) {
        String path = "/api/v1/business/fiat/withdrawals/fees/estimate?instrument=" + urlEncode(instrument)
                + "&amount=" + urlEncode(amount) + "&provider_id=" + urlEncode(providerId);
        log.info("💰 Estimation frais retrait: instrument={}, amount={}, provider={} (user={})", instrument, amount, providerId, username);
        return executeUserGet(path, KyrrexWithdrawalFeeEstimateResponse.class, username);
    }

    @Override
    public ResponseEntity<String> getFiatWithdrawalFees(String username, String instrument, String providerId) {
        String path = "/api/v1/business/fiat/withdrawals/fees?instrument=" + urlEncode(instrument) + "&provider_id=" + urlEncode(providerId);
        log.info("💰 Récupération frais de retrait fiat: instrument={}, provider={} (user={})", instrument, providerId, username);
        return executeUserGetRaw(path, username);
    }

    @Override
    public ResponseEntity<String> getFiatWithdrawalHistory(String username) {
        log.info("📋 Historique des retraits fiat (user={})", username);
        return executeUserGetRaw("/api/v1/business/fiat/withdrawals", username);
    }

    // ══════════════════════════════════════════════════════════════
    //  SECTION 10 : CRYPTO DEPOSIT (5 méthodes — 2 NOUVELLES)
    // ══════════════════════════════════════════════════════════════

    @Override
    public ResponseEntity<String> createDepositAddress(String username, String dchain, String name) {
        log.info("📬 Création adresse dépôt: dchain={}, name={} (user={})", dchain, name, username);
        KyrrexDepositAddressRequest req = new KyrrexDepositAddressRequest(dchain, name);
        return executeUserPostRaw("/api/v1/business/deposit_addresses", req, username);
    }

    /** NOUVEAU — POST /deposits/crypto/generate_link */
    @Override
    public ResponseEntity<KyrrexCryptoDepositLinkResponse> generateCryptoDepositLink(
            String username, KyrrexCryptoDepositLinkRequest request) {
        log.info("🔗 Génération lien dépôt crypto pour: {} (currency={}, network={})",
                username, request.getCurrency(), request.getNetwork());
        return executeUserPost("/api/v1/business/deposits/crypto/generate_link",
                request, KyrrexCryptoDepositLinkResponse.class, username);
    }

    @Override
    public ResponseEntity<String> getDepositAddresses(String username) {
        log.info("📋 Liste adresses dépôt (user={})", username);
        return executeUserGetRaw("/api/v1/business/deposit_addresses", username);
    }

    @Override
    public ResponseEntity<String> getCryptoDeposits(String username) {
        log.info("📋 Historique dépôts crypto (user={})", username);
        return executeUserGetRaw("/api/v1/business/deposits", username);
    }

    /** NOUVEAU — GET /deposits/crypto/{depositId} */
    @Override
    public ResponseEntity<KyrrexCryptoDepositDetailResponse> getCryptoDepositById(String username, String depositId) {
        log.info("🔍 Détail dépôt crypto id={} (user={})", depositId, username);
        return executeUserGet("/api/v1/business/deposits/" + urlEncode(depositId),
                KyrrexCryptoDepositDetailResponse.class, username);
    }

    // ══════════════════════════════════════════════════════════════
    //  SECTION 11 : CRYPTO WITHDRAWAL (4 méthodes — 2 NOUVELLES)
    // ══════════════════════════════════════════════════════════════

    @Override
    public ResponseEntity<String> executeCryptoWithdrawal(String username, String currency, BigDecimal amount, String requisiteId) {
        log.info("💸 Retrait crypto {} {} pour: {}", amount, currency, username);
        try {
            KyrrexCryptoWithdrawalRequest req = new KyrrexCryptoWithdrawalRequest(currency, amount, requisiteId);
            ResponseEntity<String> response = executeUserPostRaw("/api/v1/business/withdrawals", req, username);
            if (response.getStatusCode().is2xxSuccessful()) {
                String withdrawalUid = null;
                try {
                    JsonNode node = objectMapper.readTree(response.getBody());
                    if (node.hasNonNull("uid")) {
                        withdrawalUid = node.get("uid").asText();
                    } else if (node.hasNonNull("id")) {
                        withdrawalUid = node.get("id").asText();
                    } else if (node.has("data") && node.get("data").hasNonNull("uid")) {
                        withdrawalUid = node.get("data").get("uid").asText();
                    }
                } catch (Exception ignored) {
                    // keep null uid; webhook-driven update still possible by sequence
                }
                KyrrexTransaction tx = KyrrexTransaction.builder()
                        .kyrrexId(withdrawalUid)
                        .username(username)
                        .type(KyrrexTransactionType.CRYPTO_WITHDRAWAL)
                        .status("INITIATED")
                        .cryptoAsset(currency)
                        .cryptoAmount(amount)
                        .sequenceId(UUID.randomUUID().toString())
                        .build();
                kyrrexTransactionRepository.save(tx);
            }
            return response;
        } catch (Exception e) {
            log.error("❌ Erreur retrait crypto: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /** NOUVEAU — POST /withdrawals/crypto/validate_address */
    @Override
    public ResponseEntity<KyrrexCryptoAddressValidationResponse> validateCryptoWithdrawalAddress(
            String username, KyrrexCryptoAddressValidationRequest request) {
        log.info("✅ Validation adresse crypto: address={}, network={}, dchain={} (user={})",
                request.getAddress(), request.getNetwork(), request.getDchain(), username);
        return executeUserPost("/api/v1/business/withdrawals/crypto/validate_address",
                request, KyrrexCryptoAddressValidationResponse.class, username);
    }

    @Override
    public ResponseEntity<String> getCryptoWithdrawalHistory(String username) {
        log.info("📋 Historique retraits crypto (user={})", username);
        return executeUserGetRaw("/api/v1/business/withdrawals", username);
    }

    /** NOUVEAU — GET /withdrawals/crypto/{withdrawalId} */
    @Override
    public ResponseEntity<KyrrexCryptoWithdrawalDetailResponse> getCryptoWithdrawalById(String username, String withdrawalId) {
        log.info("🔍 Détail retrait crypto id={} (user={})", withdrawalId, username);
        return executeUserGet("/api/v1/business/withdrawals/" + urlEncode(withdrawalId),
                KyrrexCryptoWithdrawalDetailResponse.class, username);
    }

    // ══════════════════════════════════════════════════════════════
    //  SECTION 12 : REQUISITES (4 méthodes — 2 NOUVELLES)
    // ══════════════════════════════════════════════════════════════

    @Override
    public ResponseEntity<String> createRequisite(String username, String currency, String address, String network, String label) {
        log.info("📝 Création réquisite: {} adresse {} (user={})", currency, address, username);
        KyrrexRequisiteRequest req = new KyrrexRequisiteRequest(currency, address, label);
        return executeUserPostRaw("/api/v1/business/requisites", req, username);
    }

    @Override
    public ResponseEntity<String> getRequisites(String username) {
        log.info("📋 Liste des réquisites (user={})", username);
        return executeUserGetRaw("/api/v1/business/requisites", username);
    }

    /** NOUVEAU — GET /requisites/deposit */
    @Override
    public ResponseEntity<KyrrexDepositRequisitesResponse> getDepositRequisites(String username, String currency, String network) {
        StringBuilder pathBuilder = new StringBuilder("/api/v1/business/requisites/deposit?");
        if (currency != null) {
            pathBuilder.append("currency=").append(urlEncode(currency)).append("&");
        }
        if (network != null) {
            pathBuilder.append("network=").append(urlEncode(network)).append("&");
        }
        String path = pathBuilder.toString().replaceAll("[&?]+$", "");
        log.info("📋 Prérequis dépôt: currency={}, network={} (user={})", currency, network, username);
        return executeUserGet(path, KyrrexDepositRequisitesResponse.class, username);
    }

    /** NOUVEAU — GET /requisites/withdrawal */
    @Override
    public ResponseEntity<KyrrexWithdrawalRequisitesResponse> getWithdrawalRequisites(String username, String currency, String network) {
        StringBuilder pathBuilder = new StringBuilder("/api/v1/business/requisites/withdrawal?");
        if (currency != null) {
            pathBuilder.append("currency=").append(urlEncode(currency)).append("&");
        }
        if (network != null) {
            pathBuilder.append("network=").append(urlEncode(network)).append("&");
        }
        String path = pathBuilder.toString().replaceAll("[&?]+$", "");
        log.info("📋 Prérequis retrait: currency={}, network={} (user={})", currency, network, username);
        return executeUserGet(path, KyrrexWithdrawalRequisitesResponse.class, username);
    }

    // ══════════════════════════════════════════════════════════════
    //  SECTION 13 : SWAPS (1 méthode — NOUVELLE)
    // ══════════════════════════════════════════════════════════════

    /** NOUVEAU — POST /swaps */
    @Override
    public ResponseEntity<KyrrexSwapResponse> createSwap(String username, KyrrexSwapRequest request) {
        log.info("🔄 Création swap pour: {} (from={}, to={}, amount={})",
                username, request.getInputAsset(), request.getOutputAsset(), request.getInputAmount());
        try {
            ResponseEntity<KyrrexSwapResponse> response = executeUserPost(
                    "/api/v1/business/swaps", request, KyrrexSwapResponse.class, username);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                KyrrexSwapResponse body = response.getBody();
                KyrrexTransaction tx = KyrrexTransaction.builder()
                        .kyrrexId(body.getUid())
                        .username(username)
                        .type(KyrrexTransactionType.EXCHANGE)
                        .status(body.getStatus())
                        .fiatCurrency(request.getInputAsset())
                        .cryptoAsset(request.getOutputAsset())
                        .fiatAmount(body.getInputAmount())
                        .cryptoAmount(body.getOutputAmount())
                        .rate(body.getRate())
                        .sequenceId(UUID.randomUUID().toString())
                        .build();
                kyrrexTransactionRepository.save(tx);
                Operation op = buildOperation(username, body.getUid(), "KYRREX_SWAP", "DEBIT",
                        body.getInputAmount() != null ? body.getInputAmount().doubleValue() : null,
                        request.getInputAsset(),
                        body.getOutputAmount() != null ? body.getOutputAmount().doubleValue() : null,
                        request.getOutputAsset());
                operationRepository.save(op);
                log.info("✅ Swap créé, id={}, rate={}", body.getUid(), body.getRate());
            }
            return response;
        } catch (Exception e) {
            log.error("❌ Erreur swap: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  SECTION 14 : EXCHANGE SIMPLE (2 méthodes)
    // ══════════════════════════════════════════════════════════════

    @Override
    public ResponseEntity<KyrrexExchangeEstimateResponse> getExchangeEstimate(
            String username, String inputAsset, String outputAsset, BigDecimal amount, String providerId) {
        String path = "/api/v1/business/exchanges/estimate?input_asset=" + urlEncode(inputAsset)
                + "&output_asset=" + urlEncode(outputAsset) + "&amount=" + amount + "&provider_id=" + urlEncode(providerId);
        log.info("📊 Estimation swap: {} → {}, amount={} (user={})", inputAsset, outputAsset, amount, username);
        return executeUserGet(path, KyrrexExchangeEstimateResponse.class, username);
    }

    @Override
    public ResponseEntity<String> executeExchange(String username, KyrrexExchangeRequest request) {
        log.info("🔄 Exécution exchange pour: {}", username);
        try {
            ResponseEntity<String> response = executeUserPostRaw("/api/v1/business/exchanges", request, username);
            if (response.getStatusCode().is2xxSuccessful()) {
                KyrrexTransaction tx = KyrrexTransaction.builder()
                        .username(username)
                        .type(KyrrexTransactionType.EXCHANGE)
                        .status("INITIATED")
                        .sequenceId(UUID.randomUUID().toString())
                        .build();
                kyrrexTransactionRepository.save(tx);
            }
            return response;
        } catch (Exception e) {
            log.error("❌ Erreur exchange: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  SECTION 15 : EXCHANGE AVANCÉ (3 méthodes)
    // ══════════════════════════════════════════════════════════════

    @Override
    public ResponseEntity<KyrrexAdvancedExchangeEstimateResponse> getAdvancedExchangeEstimate(
            String username, String fiatCurrency, String cryptoAsset, BigDecimal fiatAmount, String providerId) {
        String path = "/api/v1/business/advanced_exchanges/estimate?fiat_currency=" + urlEncode(fiatCurrency)
                + "&crypto_asset=" + urlEncode(cryptoAsset) + "&fiat_amount=" + fiatAmount + "&provider_id=" + urlEncode(providerId);
        log.info("📊 Estimation advanced exchange: fiat={}, crypto={}, amount={} (user={})", fiatCurrency, cryptoAsset, fiatAmount, username);
        return executeUserGet(path, KyrrexAdvancedExchangeEstimateResponse.class, username);
    }

    @Override
    public ResponseEntity<KyrrexAdvancedExchangeResponse> executeAdvancedExchange(String username, KyrrexAdvancedExchangeRequest request) {
        log.info("🚀 Exécution advanced exchange pour: {}", username);
        try {
            ResponseEntity<KyrrexAdvancedExchangeResponse> response = executeUserPost(
                    "/api/v1/business/advanced_exchanges", request, KyrrexAdvancedExchangeResponse.class, username);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                KyrrexAdvancedExchangeResponse body = response.getBody();
                String resolvedUrl = body.getRedirectUrl() != null ? body.getRedirectUrl() : body.getFrameUrl();
                KyrrexTransaction tx = KyrrexTransaction.builder()
                        .kyrrexId(body.getUid())
                        .username(username)
                        .type(KyrrexTransactionType.ADVANCED_EXCHANGE)
                        .status(body.getStatus())
                        .fiatCurrency(body.getFiatCurrency())
                        .cryptoAsset(body.getCryptoAsset())
                        .fiatAmount(body.getFiatAmount())
                        .cryptoAmount(body.getCryptoAmount())
                        .rate(body.getRate())
                        .fee(body.getFee())
                        .providerId(request.getProviderId())
                        .paymentMethod(request.getPaymentMethod())
                        .redirectUrl(resolvedUrl)
                        .build();
                kyrrexTransactionRepository.save(tx);
                Operation op = buildOperation(username, body.getUid(), "KYRREX_ON_RAMP", "CREDIT",
                        body.getFiatAmount() != null ? body.getFiatAmount().doubleValue() : null,
                        body.getFiatCurrency(),
                        body.getCryptoAmount() != null ? body.getCryptoAmount().doubleValue() : null,
                        body.getCryptoAsset());
                operationRepository.save(op);
                log.info("✅ Advanced exchange créé, uid={}, url={}", body.getUid(), resolvedUrl);
            }
            return response;
        } catch (Exception e) {
            log.error("❌ Erreur advanced exchange: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public ResponseEntity<String> getAdvancedExchangeHistory(String username) {
        log.info("📋 Historique advanced exchanges (user={})", username);
        return executeUserGetRaw("/api/v1/business/advanced_exchanges", username);
    }

    // ══════════════════════════════════════════════════════════════
    //  SECTION 16 : MARCHÉS (3 méthodes)
    // ══════════════════════════════════════════════════════════════

    @Override
    public ResponseEntity<List<KyrrexMarketResponse>> getFiatMarkets(String username, int page, int perPage) {
        String path = "/api/v1/business/markets?page=" + page + "&per_page=" + perPage + "&type=fiat";
        log.info("📈 Marchés fiat Kyrrex (page={}, perPage={}, user={})", page, perPage, username);
        return executeUserGetList(path, KyrrexMarketResponse.class, username);
    }

    @Override
    public ResponseEntity<KyrrexMarketResponse> getMarketInfo(String username, String marketId) {
        log.info("📈 Info marché: {} (user={})", marketId, username);
        return executeUserGet("/api/v1/business/markets/" + urlEncode(marketId), KyrrexMarketResponse.class, username);
    }

    @Override
    public ResponseEntity<String> getMarketSettings(String username) {
        log.info("⚙️ Récupération market settings (user={})", username);
        return executeUserGetRaw("/api/v1/business/settings/markets", username);
    }

    // ══════════════════════════════════════════════════════════════
    //  SECTION 17 : TRANSACTIONS (1 méthode)
    // ══════════════════════════════════════════════════════════════

    @Override
    public ResponseEntity<List<SimpleTransactionResponse>> getSimpleTransactions(String username) {
        log.info("📋 Transactions simplifiées pour: {}", username);
        try {
            List<KyrrexTransaction> transactions = kyrrexTransactionRepository.findByUsernameOrderByCreatedAtDesc(username);
            List<SimpleTransactionResponse> result = transactions.stream()
                    .map(tx -> SimpleTransactionResponse.builder()
                            .id(tx.getKyrrexId() != null ? tx.getKyrrexId() : String.valueOf(tx.getId()))
                            .status(mapStatus(tx.getStatus()))
                            .date(tx.getCreatedAt() != null ? tx.getCreatedAt() : null)
                            .amount(tx.getFiatAmount() != null ? tx.getFiatAmount().doubleValue()
                                    : tx.getCryptoAmount() != null ? tx.getCryptoAmount().doubleValue() : null)
                            .currency(tx.getFiatCurrency() != null ? tx.getFiatCurrency() : tx.getCryptoAsset())
                            .operator("KYRREX")
                            .type(tx.getType() != null ? tx.getType().name() : null)
                            .build())
                    .toList();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("❌ Erreur récupération transactions: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    // ══════════════════════════════════════════════════════════════
    //  SECTION 18 : CREDENTIAL MANAGEMENT (3 méthodes)
    // ══════════════════════════════════════════════════════════════

    /** Persiste en base avec chiffrement AES-256-GCM (pas de properties). */
    private void saveCredential(KyrrexUserCredential cred) {
        cred.setAccessKey(encryptAtRest(cred.getAccessKey()));
        cred.setSecretKey(encryptAtRest(cred.getSecretKey()));
        if (cred.getSessionAccessKey() != null) {
            cred.setSessionAccessKey(encryptAtRest(cred.getSessionAccessKey()));
        }
        if (cred.getSessionSecretKey() != null) {
            cred.setSessionSecretKey(encryptAtRest(cred.getSessionSecretKey()));
        }
        kyrrexUserCredentialRepository.save(cred);
    }

    private KyrrexUserCredential withDecryptedKeys(KyrrexUserCredential cred) {
        cred.setAccessKey(decryptAtRest(cred.getAccessKey()));
        cred.setSecretKey(decryptAtRest(cred.getSecretKey()));
        if (cred.getSessionAccessKey() != null) {
            cred.setSessionAccessKey(decryptAtRest(cred.getSessionAccessKey()));
        }
        if (cred.getSessionSecretKey() != null) {
            cred.setSessionSecretKey(decryptAtRest(cred.getSessionSecretKey()));
        }
        return cred;
    }

    private String encryptAtRest(String plaintext) {
        if (plaintext == null || plaintext.isBlank() || isEncryptedAtRest(plaintext)) {
            return plaintext;
        }
        return credentialEncryptionService.encrypt(plaintext);
    }

    private String decryptAtRest(String stored) {
        if (stored == null || stored.isBlank() || !isEncryptedAtRest(stored)) {
            return stored;
        }
        return credentialEncryptionService.decrypt(stored);
    }

    private boolean isEncryptedAtRest(String value) {
        try {
            credentialEncryptionService.decrypt(value);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public ResponseEntity<Map<String, String>> storeUserCredentials(String username, String accessKey, String secretKey) {
        return importMemberCredentials(username, null, accessKey, secretKey);
    }

    @Override
    public ResponseEntity<Map<String, String>> importMemberCredentials(
            String username, String uid, String accessKey, String secretKey) {
        log.info("🔑 Import credentials Kyrrex pour: {} (uid={})", username, uid);
        KyrrexUserCredential cred = kyrrexUserCredentialRepository.findByUsername(username)
                .orElseGet(() -> KyrrexUserCredential.builder().username(username).build());
        if (uid != null && !uid.isBlank()) {
            cred.setKyrrexMemberUid(uid.trim());
        }
        cred.setAccessKey(accessKey);
        cred.setSecretKey(secretKey);
        cred.setRevokedAt(null);
        cred.clearSession();
        saveCredential(cred);
        log.info("✅ Credentials importés en base (chiffrés) pour {} (uid={})", username, cred.getKyrrexMemberUid());
        return ResponseEntity.ok(Map.of(
                "status", "imported",
                "username", username,
                "uid", cred.getKyrrexMemberUid() != null ? cred.getKyrrexMemberUid() : ""));
    }

    @Override
    public ResponseEntity<Map<String, String>> getUserCredentialStatus(String username) {
        log.info("🔍 Statut credentials Kyrrex pour: {}", username);
        return kyrrexUserCredentialRepository.findByUsername(username)
                .map(cred -> ResponseEntity.ok(Map.of(
                        "username", username,
                        "active", String.valueOf(cred.isActive()),
                        "hasSession", String.valueOf(cred.hasActiveSession()),
                        "kycVerified", String.valueOf(cred.isKycVerified())
                )))
                .orElseGet(() -> ResponseEntity.ok(Map.of("username", username, "active", "false")));
    }

    @Override
    public ResponseEntity<Void> revokeUserCredentials(String username) {
        log.info("🔒 Révocation credentials Kyrrex pour: {}", username);
        kyrrexUserCredentialRepository.findByUsername(username).ifPresent(cred -> {
            cred.setRevokedAt(java.time.Instant.now());
            cred.clearSession();
            saveCredential(cred);
            log.info("✅ Credentials révoqués pour {}", username);
        });
        return ResponseEntity.noContent().build();
    }

    // ══════════════════════════════════════════════════════════════
    //  HELPERS PRIVÉS
    // ══════════════════════════════════════════════════════════════

    private Operation buildOperation(String username, String hash, String designation, String type,
                                     Double fiatAmount, String fiatCurrency,
                                     Double cryptoAmount, String cryptoCurrency) {
        Operation op = new Operation();
        op.setUsername(username);
        op.setOperationHash(hash != null ? hash : UUID.randomUUID().toString());
        op.setDesignation(designation);
        op.setType(type);
        op.setStatus("INITIATED");
        op.setAmount(fiatAmount);
        op.setDevise(fiatCurrency);
        op.setConvertedAmount(fiatAmount);
        op.setProviderAmount(cryptoAmount);
        op.setProviderDevise(cryptoCurrency);
        op.setProvider("Kyrrex");
        op.setTransactionType("CREDIT".equals(type) ? "Dépôt" : "Retrait");
        op.setCreatedAt(java.time.LocalDateTime.now());
        op.setUpdatedAt(java.time.LocalDateTime.now());
        return op;
    }

    private String mapStatus(String kyrrexStatus) {
        if (kyrrexStatus == null) return "unknown";
        return switch (kyrrexStatus.toLowerCase()) {
            case "created", "initiated" -> "INITIATED";
            case "aml_processing", "processing", "wait", "postponed" -> "PENDING";
            case "done" -> "SUCCESS";
            case "frozen", "rejected", "fail", "cancel" -> "FAILED";
            default -> kyrrexStatus;
        };
    }
}
