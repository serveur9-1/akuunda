package org.akuunda.akuundawallet.wallet.service.infrastructure.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.service.infrastructure.TokenGenerationService;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class TokenGenerationServiceImpl implements TokenGenerationService {

    @Value("${venly.url.base}")
    private String venlyServerUrlBase;

    @Value("${venly.url.auth}")
    private String venlyAuthentificationURL;
    @Value("${venly.clientId}")
    private String venlyClientId;

    @Value("${venly.clientSecret}")
    private String venlyClientSecret;

    private static final int CODE_200 = 200;


    @Override
    public ResponseEntity<String> generateToken() {
        HttpResponse<String> response;
        try {
            response = getHttpResponse();
        } catch (IOException | InterruptedException e) {
           return new ResponseEntity<>(e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }

        if (response.statusCode() != CODE_200) {
           return new ResponseEntity<>(response.body(), HttpStatus.valueOf(response.statusCode()));
        }
        if (response.body() == null) {
            return new ResponseEntity<>("response body is null", HttpStatus.INTERNAL_SERVER_ERROR);
        }

        JSONObject jsonResponse = new JSONObject(response.body());
        String accessToken = jsonResponse.getString("access_token");
        return new ResponseEntity<>(accessToken, HttpStatus.OK);
    }

    private  HttpResponse<String> getHttpResponse() throws IOException, InterruptedException {
        String body = "grant_type=client_credentials&client_secret=" + venlyClientSecret + "&client_id=" + venlyClientId;
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(venlyAuthentificationURL))
                .header("accept", "application/json")
                .header("content-type", "application/x-www-form-urlencoded")
                .method("POST", HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
        return response;
    }
}
