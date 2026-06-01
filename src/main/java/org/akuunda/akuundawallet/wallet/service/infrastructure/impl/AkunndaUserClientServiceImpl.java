package org.akuunda.akuundawallet.wallet.service.infrastructure.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.Exceptions.ErrorResponse;
import org.akuunda.akuundawallet.common.security.HttpClientCall;
import org.akuunda.akuundawallet.keycloak.api.dto.external.ExternalUserCreateResponse;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkunndaUserClientService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.TokenGenerationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.net.http.HttpResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class AkunndaUserClientServiceImpl implements AkunndaUserClientService {

    @Value("${venly.url.base}")
    private String venlyServerUrlBase;

    private static final String USER_URL = "/api/users";
    private static final String SLASH = "/";
    private static final int CODE_200 = 200;

    private final ObjectMapper objectMapper;

    private final TokenGenerationService tokenGenerationService;

    @Override
    public ResponseEntity<ExternalUserCreateResponse> createUSer(final String body) {
        ExternalUserCreateResponse userResponse;
        try {
            // Générer le token d'autorisation
            String apiKey = tokenGenerationService.generateToken().getBody();

            final var httpResponse = HttpClientCall.httpPost(body, apiKey, venlyServerUrlBase + USER_URL);
            final var readValue = getUserResponse(httpResponse);
            if (readValue == null) {
                ErrorResponse errorResponse = objectMapper.readValue(httpResponse.body(), ErrorResponse.class);
                log.error("Error creating user: {}", errorResponse);
                return new ResponseEntity<>(null, HttpStatus.EXPECTATION_FAILED);
            }else {
                log.info("User created successfully: {}", readValue);
                userResponse = objectMapper.readValue(getUserResponse(httpResponse), ExternalUserCreateResponse.class);
                return new ResponseEntity<>(userResponse, HttpStatus.OK);
            }

        } catch (IOException | InterruptedException e) {
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }
    }

    @Override
    public ResponseEntity<ExternalUserCreateResponse> getUserById(final String userId) {
        ExternalUserCreateResponse response;
        try {
            // Générer le token d'autorisation
            String apiKey = tokenGenerationService.generateToken().getBody();

            final var httpResponse = HttpClientCall.httpGet(apiKey, venlyServerUrlBase + USER_URL + SLASH + userId);
            response = objectMapper.readValue(getUserResponse(httpResponse), ExternalUserCreateResponse.class);
        } catch (IOException | InterruptedException e) {
            return new ResponseEntity<>(null, HttpStatus.EXPECTATION_FAILED);
        }
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> createUserSigningMethod(final String userId, final String body) {
        String signingMethod;
        try {
            // Générer le token d'autorisation
            String apiKey = tokenGenerationService.generateToken().getBody();

            final var httpResponse = HttpClientCall.httpGetWithBody(apiKey, venlyServerUrlBase + USER_URL +
                    SLASH + userId, body);
            signingMethod = getUserResponse(httpResponse);
        } catch (IOException | InterruptedException e) {
            return new ResponseEntity<>(null, HttpStatus.EXPECTATION_FAILED);
        }
        return new ResponseEntity<>(signingMethod, HttpStatus.OK);
    }

    private String getUserResponse(HttpResponse<String> httpResponse) throws JsonProcessingException {
        log.debug("Http -> Response -> body : {}", httpResponse.body());

        if (httpResponse.statusCode() == CODE_200) {
            return httpResponse.body();
        } else {
            return null;
        }
    }
}
