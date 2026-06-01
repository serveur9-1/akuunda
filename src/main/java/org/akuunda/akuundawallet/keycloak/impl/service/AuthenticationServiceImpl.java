package org.akuunda.akuundawallet.keycloak.impl.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.keycloak.api.dto.AuthenticationRequest;
import org.akuunda.akuundawallet.keycloak.api.dto.AuthenticationResponse;
import org.akuunda.akuundawallet.keycloak.api.service.AuthenticationService;
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

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthenticationServiceImpl implements AuthenticationService {

    @Value("${keycloak.token.generate.url}")
    private String authenticationUrl;

    @Override
    public ResponseEntity<AuthenticationResponse> generateToken(
            AuthenticationRequest loginRequest, String realmName) {
        try {
            // Construire les paramètres encodés
            String requestBody = String.format(
                    "username=%s&password=%s&grant_type=%s&client_id=%s&client_secret=%s",
                    URLEncoder.encode(loginRequest.getUsername(), StandardCharsets.UTF_8),
                    URLEncoder.encode(loginRequest.getPassword(), StandardCharsets.UTF_8),
                    URLEncoder.encode(loginRequest.getGrant_type(), StandardCharsets.UTF_8),
                    URLEncoder.encode(loginRequest.getClient_id(), StandardCharsets.UTF_8),
                    URLEncoder.encode(loginRequest.getClient_secret(), StandardCharsets.UTF_8)
            );

            // Construire la requête HTTP
            HttpClient httpClient = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(authenticationUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            // Envoyer la requête
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // Vérifier si la réponse est OK
            if (response.statusCode() == 200) {
                // Mapper la réponse JSON dans AuthenticationResponse
                ObjectMapper objectMapper = new ObjectMapper();
                AuthenticationResponse authResponse = objectMapper.readValue(response.body(), AuthenticationResponse.class);

                return ResponseEntity.ok(authResponse);
            } else {
                log.error("Erreur lors de l'appel au service d'authentification : {}, body : {}", response.statusCode(), response.body());
                return ResponseEntity.status(response.statusCode()).build();
            }
        } catch (Exception e) {
            log.error("Exception lors de l'appel au service d'authentification", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}
