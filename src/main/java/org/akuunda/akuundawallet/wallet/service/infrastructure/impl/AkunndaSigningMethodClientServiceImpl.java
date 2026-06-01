package org.akuunda.akuundawallet.wallet.service.infrastructure.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.security.HttpClientCall;
import org.akuunda.akuundawallet.keycloak.api.dto.external.ExternalSigningMethod;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkunndaSigningMethodClientService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkunndaUserClientService;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class AkunndaSigningMethodClientServiceImpl implements AkunndaSigningMethodClientService {

    @Value("${venly.url.base}")
    private String venlyServerUrlBase;

    @Value("${venly.url.auth}")
    private String venlyAuthentificationURL;
    @Value("${venly.clientId}")
    private String venlyClientId;

    @Value("${venly.clientSecret}")
    private String venlyClientSecret;

    private static final String USER_URL = "/api/users";

    private static final String SLASH = "/";
    private static final int CODE_200 = 200;

    private static final String SIGNING_METHODS = "/signing-methods";

    private final AkunndaUserClientService akunndaUserClientService;

    @Override
    public ResponseEntity<String> createUserPinSigningMethod(final String userId, final String body) {
        try {
            log.info("Creating PIN signing method for userId: {}", userId);
            
            // Get Venly access token
            HttpResponse<String> tokenResponse = HttpClientCall
                    .getAuthenticationToken(venlyAuthentificationURL, venlyClientId, venlyClientSecret);
            
            if (tokenResponse.statusCode() != CODE_200) {
                log.error("Failed to get Venly token. Status: {}, Body: {}", tokenResponse.statusCode(), tokenResponse.body());
                return new ResponseEntity<>("Failed to authenticate with Venly: " + tokenResponse.body(), 
                        HttpStatus.valueOf(tokenResponse.statusCode()));
            }
            
            String tokenBody = tokenResponse.body();
            if (tokenBody == null || tokenBody.isEmpty()) {
                log.error("Venly token response body is empty");
                return new ResponseEntity<>("Venly token response is empty", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            
            JSONObject tokenJson = new JSONObject(tokenBody);
            String apiKey = tokenJson.getString("access_token");
            log.debug("Successfully obtained Venly access token");
            
            // Create PIN signing method
            final var httpResponse = HttpClientCall.httpPost(body, apiKey, venlyServerUrlBase + USER_URL +
                    SLASH + userId + SIGNING_METHODS);
            
            log.debug("Venly response status: {}, body: {}", httpResponse.statusCode(), httpResponse.body());
            
            if (httpResponse.statusCode() == CODE_200) {
                return ResponseEntity.status(CODE_200).body(httpResponse.body());
            } else {
                log.error("Failed to create PIN signing method. Status: {}, Body: {}", 
                        httpResponse.statusCode(), httpResponse.body());
                return new ResponseEntity<>("Venly error: " + httpResponse.body(), 
                        HttpStatus.valueOf(httpResponse.statusCode()));
            }
        } catch (IOException | InterruptedException e) {
            log.error("Exception while creating PIN signing method", e);
            return new ResponseEntity<>("Internal error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (org.json.JSONException e) {
            log.error("Failed to parse Venly token response", e);
            return new ResponseEntity<>("Failed to parse Venly response: " + e.getMessage(), 
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<List<ExternalSigningMethod>> getAllSigningMethods(final String userId) {

        final var userResponse = akunndaUserClientService.getUserById(userId);

        if (Objects.requireNonNull(userResponse.getBody()).isSuccess()) {
            final var signingList = userResponse.getBody().getResult().getSigningMethods();
            return new ResponseEntity<>(signingList, userResponse.getStatusCode());
        } else {
            return new ResponseEntity<>(List.of(), HttpStatus.EXPECTATION_FAILED);
        }
    }

    @Override
    public ResponseEntity<String> createUserEmergencyCodeSigningMethod(final String userId, final String body, final String pinSigningMethodHeader) {
        try {
            log.info("Creating emergency code signing method for userId: {} with PIN authentication", userId);
            
            // Vérifier les paramètres
            if (userId == null || userId.isEmpty()) {
                log.error("userId is null or empty");
                return new ResponseEntity<>("userId cannot be null or empty", HttpStatus.BAD_REQUEST);
            }
            if (body == null || body.isEmpty()) {
                log.error("Request body is null or empty");
                return new ResponseEntity<>("Request body cannot be null or empty", HttpStatus.BAD_REQUEST);
            }
            if (pinSigningMethodHeader == null || pinSigningMethodHeader.isEmpty()) {
                log.error("PIN signing method header is null or empty");
                return new ResponseEntity<>("PIN signing method header cannot be null or empty", HttpStatus.BAD_REQUEST);
            }
            
            log.debug("Request body: {}", body);
            log.debug("PIN signing method header: {}", pinSigningMethodHeader);
            
            // Get Venly access token
            HttpResponse<String> tokenResponse = HttpClientCall
                    .getAuthenticationToken(venlyAuthentificationURL, venlyClientId, venlyClientSecret);
            
            if (tokenResponse.statusCode() != CODE_200) {
                log.error("Failed to get Venly token. Status: {}, Body: {}", tokenResponse.statusCode(), tokenResponse.body());
                return new ResponseEntity<>("Failed to authenticate with Venly: " + tokenResponse.body(), 
                        HttpStatus.valueOf(tokenResponse.statusCode()));
            }
            
            String tokenBody = tokenResponse.body();
            if (tokenBody == null || tokenBody.isEmpty()) {
                log.error("Venly token response body is empty");
                return new ResponseEntity<>("Venly token response is empty", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            
            JSONObject tokenJson = new JSONObject(tokenBody);
            String apiKey = tokenJson.getString("access_token");
            if (apiKey == null || apiKey.isEmpty()) {
                log.error("Venly access token is null or empty");
                return new ResponseEntity<>("Venly access token is empty", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            log.debug("Successfully obtained Venly access token");
            
            // Create emergency code signing method with PIN authentication
            // Utilise httpPostWithSigning pour inclure le header Signing-Method avec le PIN
            String url = venlyServerUrlBase + USER_URL + SLASH + userId + SIGNING_METHODS;
            log.debug("Calling Venly API at URL: {}", url);
            final var httpResponse = HttpClientCall.httpPostWithSigning(body, apiKey, url, pinSigningMethodHeader);
            
            log.debug("Venly response status: {}, body: {}", httpResponse.statusCode(), httpResponse.body());
            
            if (httpResponse.statusCode() == CODE_200) {
                return ResponseEntity.status(CODE_200).body(httpResponse.body());
            } else {
                log.error("Failed to create emergency code signing method. Status: {}, Body: {}", 
                        httpResponse.statusCode(), httpResponse.body());
                return new ResponseEntity<>("Venly error: " + httpResponse.body(), 
                        HttpStatus.valueOf(httpResponse.statusCode()));
            }
        } catch (IllegalArgumentException e) {
            log.error("Invalid argument while creating emergency code signing method: {}", e.getMessage(), e);
            return new ResponseEntity<>("Invalid argument: " + e.getMessage(), HttpStatus.BAD_REQUEST);
        } catch (IOException | InterruptedException e) {
            log.error("Exception while creating emergency code signing method", e);
            return new ResponseEntity<>("Internal error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (org.json.JSONException e) {
            log.error("Failed to parse Venly token response", e);
            return new ResponseEntity<>("Failed to parse Venly response: " + e.getMessage(), 
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<String> updatePinSigningMethod(final String userId, final String signingMethodId, final String body, final String emergencyCodeSigningMethodHeader) {
        try {
            log.info("Updating PIN signing method for userId: {}, signingMethodId: {} with emergency code", userId, signingMethodId);
            
            // Get Venly access token
            HttpResponse<String> tokenResponse = HttpClientCall
                    .getAuthenticationToken(venlyAuthentificationURL, venlyClientId, venlyClientSecret);
            
            if (tokenResponse.statusCode() != CODE_200) {
                log.error("Failed to get Venly token. Status: {}, Body: {}", tokenResponse.statusCode(), tokenResponse.body());
                return new ResponseEntity<>("Failed to authenticate with Venly: " + tokenResponse.body(), 
                        HttpStatus.valueOf(tokenResponse.statusCode()));
            }
            
            String tokenBody = tokenResponse.body();
            if (tokenBody == null || tokenBody.isEmpty()) {
                log.error("Venly token response body is empty");
                return new ResponseEntity<>("Venly token response is empty", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            
            JSONObject tokenJson = new JSONObject(tokenBody);
            String apiKey = tokenJson.getString("access_token");
            log.debug("Successfully obtained Venly access token");
            
            // Update PIN signing method using PUT with emergency code authentication
            // Format header: Signing-Method: emergencyCodeSigningMethodId:emergencyCodeValue
            String url = venlyServerUrlBase + USER_URL + SLASH + userId + SIGNING_METHODS + SLASH + signingMethodId;
            log.info("Updating PIN at URL: {}", url);
            log.info("Using Signing-Method header: {}", emergencyCodeSigningMethodHeader);
            log.info("Request body: {}", body);
            
            final var httpResponse = HttpClientCall.httpPutWithSigning(apiKey, url, body, emergencyCodeSigningMethodHeader);
            
            log.info("Venly response status: {}, body: {}", httpResponse.statusCode(), httpResponse.body());
            
            if (httpResponse.statusCode() == CODE_200) {
                return ResponseEntity.status(CODE_200).body(httpResponse.body());
            } else {
                log.error("Failed to update PIN signing method. Status: {}, Body: {}", 
                        httpResponse.statusCode(), httpResponse.body());
                return new ResponseEntity<>("Venly error: " + httpResponse.body(), 
                        HttpStatus.valueOf(httpResponse.statusCode()));
            }
        } catch (IOException | InterruptedException e) {
            log.error("Exception while updating PIN signing method", e);
            return new ResponseEntity<>("Internal error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (org.json.JSONException e) {
            log.error("Failed to parse Venly token response", e);
            return new ResponseEntity<>("Failed to parse Venly response: " + e.getMessage(), 
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<String> updateEmergencyCodeSigningMethod(final String userId, final String signingMethodId, final String body, final String currentEmergencyCodeHeader) {
        try {
            log.info("Updating emergency code signing method for userId: {}, signingMethodId: {}", userId, signingMethodId);

            HttpResponse<String> tokenResponse = HttpClientCall
                    .getAuthenticationToken(venlyAuthentificationURL, venlyClientId, venlyClientSecret);

            if (tokenResponse.statusCode() != CODE_200) {
                log.error("Failed to get Venly token. Status: {}", tokenResponse.statusCode());
                return new ResponseEntity<>("Failed to authenticate with Venly: " + tokenResponse.body(),
                        HttpStatus.valueOf(tokenResponse.statusCode()));
            }

            String tokenBody = tokenResponse.body();
            if (tokenBody == null || tokenBody.isEmpty()) {
                return new ResponseEntity<>("Venly token response is empty", HttpStatus.INTERNAL_SERVER_ERROR);
            }

            JSONObject tokenJson = new JSONObject(tokenBody);
            String apiKey = tokenJson.getString("access_token");

            String url = venlyServerUrlBase + USER_URL + SLASH + userId + SIGNING_METHODS + SLASH + signingMethodId;
            log.info("Updating emergency code at URL: {}", url);

            final var httpResponse = HttpClientCall.httpPutWithSigning(apiKey, url, body, currentEmergencyCodeHeader);

            log.info("Venly response status: {}, body: {}", httpResponse.statusCode(), httpResponse.body());

            if (httpResponse.statusCode() == CODE_200) {
                return ResponseEntity.status(CODE_200).body(httpResponse.body());
            } else {
                log.error("Failed to update emergency code. Status: {}, Body: {}", httpResponse.statusCode(), httpResponse.body());
                return new ResponseEntity<>("Venly error: " + httpResponse.body(),
                        HttpStatus.valueOf(httpResponse.statusCode()));
            }
        } catch (IOException | InterruptedException e) {
            log.error("Exception while updating emergency code signing method", e);
            return new ResponseEntity<>("Internal error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (org.json.JSONException e) {
            log.error("Failed to parse Venly token response", e);
            return new ResponseEntity<>("Failed to parse Venly response: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public ResponseEntity<String> createUserOtherSigningMethod(final String userId, final String body, final String pin) {
        try {
            log.info("Creating other signing method for userId: {}", userId);
            
            // Get Venly access token
            HttpResponse<String> tokenResponse = HttpClientCall
                    .getAuthenticationToken(venlyAuthentificationURL, venlyClientId, venlyClientSecret);
            
            if (tokenResponse.statusCode() != CODE_200) {
                log.error("Failed to get Venly token. Status: {}, Body: {}", tokenResponse.statusCode(), tokenResponse.body());
                return new ResponseEntity<>("Failed to authenticate with Venly: " + tokenResponse.body(), 
                        HttpStatus.valueOf(tokenResponse.statusCode()));
            }
            
            String tokenBody = tokenResponse.body();
            if (tokenBody == null || tokenBody.isEmpty()) {
                log.error("Venly token response body is empty");
                return new ResponseEntity<>("Venly token response is empty", HttpStatus.INTERNAL_SERVER_ERROR);
            }
            
            JSONObject tokenJson = new JSONObject(tokenBody);
            String apiKey = tokenJson.getString("access_token");
            log.debug("Successfully obtained Venly access token");
            
            // Create signing method with PIN authentication
            final var httpResponse = HttpClientCall.httpPostWithSigning(body, apiKey, venlyServerUrlBase + USER_URL +
                    SLASH + userId + SIGNING_METHODS, pin);
            
            log.debug("Venly response status: {}, body: {}", httpResponse.statusCode(), httpResponse.body());
            
            if (httpResponse.statusCode() == CODE_200) {
                return ResponseEntity.status(CODE_200).body(httpResponse.body());
            } else if (httpResponse.statusCode() == 400 && httpResponse.body() != null
                    && httpResponse.body().contains("biometric.exists")) {
                // Biometric signing method already registered for this device — treat as success
                log.info("Biometric signing method already exists for userId={}, treating as success", userId);
                return ResponseEntity.status(CODE_200).body(httpResponse.body());
            } else {
                log.error("Failed to create signing method. Status: {}, Body: {}",
                        httpResponse.statusCode(), httpResponse.body());
                return new ResponseEntity<>("Venly error: " + httpResponse.body(),
                        HttpStatus.valueOf(httpResponse.statusCode()));
            }
        } catch (IOException | InterruptedException e) {
            log.error("Exception while creating signing method", e);
            return new ResponseEntity<>("Internal error: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        } catch (org.json.JSONException e) {
            log.error("Failed to parse Venly token response", e);
            return new ResponseEntity<>("Failed to parse Venly response: " + e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}
