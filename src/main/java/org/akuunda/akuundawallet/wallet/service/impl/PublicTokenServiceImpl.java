package org.akuunda.akuundawallet.wallet.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.keycloak.api.dto.AuthenticationResponse;
import org.akuunda.akuundawallet.wallet.api.dto.PublicTokenResponse;
import org.akuunda.akuundawallet.wallet.service.PublicTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

@Slf4j
@Service
public class PublicTokenServiceImpl implements PublicTokenService {

    @Value("${akuunda.public.auth.username}")
    private String publicUsername;

    @Value("${akuunda.public.auth.password}")
    private String publicPassword;

    @Value("${keycloak.clientId}")
    private String clientId;

    @Value("${keycloak.clientSecret}")
    private String clientSecret;

    @Value("${keycloak.token.generate.url}")
    private String tokenUrl;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final int EXPIRY_SAFETY_MARGIN_SECONDS = 60;

    private volatile PublicTokenResponse cachedResponse;
    private volatile Instant tokenExpiryTime;

    @Override
    public synchronized ResponseEntity<PublicTokenResponse> generatePublicToken() {
        if (isCachedTokenValid()) {
            log.debug("Returning cached public token");
            return ResponseEntity.ok(cachedResponse);
        }

        try {
            String requestBody = String.format(
                    "username=%s&password=%s&grant_type=password&client_id=%s&client_secret=%s",
                    URLEncoder.encode(publicUsername, StandardCharsets.UTF_8),
                    URLEncoder.encode(publicPassword, StandardCharsets.UTF_8),
                    URLEncoder.encode(clientId, StandardCharsets.UTF_8),
                    URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
            );

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(tokenUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                AuthenticationResponse authResponse = OBJECT_MAPPER.readValue(response.body(), AuthenticationResponse.class);

                int expiresIn = authResponse.getExpires_in() != null ? authResponse.getExpires_in() : 0;
                cachedResponse = PublicTokenResponse.builder()
                        .accessToken(authResponse.getAccess_token())
                        .expiresIn(expiresIn)
                        .tokenType(authResponse.getToken_type())
                        .build();
                tokenExpiryTime = Instant.now().plusSeconds(expiresIn - EXPIRY_SAFETY_MARGIN_SECONDS);

                log.info("Successfully generated new public token");
                return ResponseEntity.ok(cachedResponse);
            } else {
                log.error("Failed to generate public token: status={}, body={}", response.statusCode(), response.body());
                return ResponseEntity.status(response.statusCode()).build();
            }
        } catch (Exception e) {
            log.error("Exception while generating public token", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private boolean isCachedTokenValid() {
        return cachedResponse != null && tokenExpiryTime != null && Instant.now().isBefore(tokenExpiryTime);
    }
}
