package org.akuunda.akuundawallet.keycloak.impl.service.infrastructure.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.dto.external.sumsub.AccessToken;
import org.akuunda.akuundawallet.keycloak.impl.service.infrastructure.SumsubClientService;
import org.apache.commons.codec.binary.Hex;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Slf4j
public class SumsubClientServiceImpl implements SumsubClientService {

    @Value("${sumsub.url.base}")
    private String sumsubUrlBase;

    @Value("${sumsub.app.token}")
    private String sumsubToken;

    @Value("${sumsub.secret.key}")
    private String sumsubSecretKey;

    private final UserRepository userRepository;

    @Override
    public ResponseEntity<AccessToken> getAccessToken(String username, String levelName) {

        String sumsubResponse;

        // get user with username
        final var sumsubUser = userRepository.getUsersByUsername(username);
        if (sumsubUser == null) {
            log.info("User not found with username " + username + " in sumsub");
            log.debug("User not found with username " + username + " in sumsub");
            return new ResponseEntity<>(null, HttpStatus.NOT_FOUND);
        }
        final var externalUserId = sumsubUser.getUserId();
        String url = sumsubUrlBase + "/resources/accessTokens?userId=" + URLEncoder.encode(externalUserId, StandardCharsets.UTF_8)
                + "&levelName=" + URLEncoder.encode(levelName, StandardCharsets.UTF_8);

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .header("X-App-Token", sumsubToken)
                    .header("X-App-Access-Ts", String.valueOf(Instant.now().getEpochSecond()))
                    .header("X-App-Access-Sig", createMethodSignature())
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            sumsubResponse = response.body();

            if (response.statusCode() != 200) {
                log.info("Failed to get access token. HTTP status code: " + response.statusCode());
                log.debug("Failed to get access token. HTTP status code: " + response.statusCode());
                return new ResponseEntity<>(null, HttpStatus.valueOf(response.statusCode()));
            }

        } catch (NoSuchAlgorithmException | InvalidKeyException | IOException | InterruptedException e) {
            log.info("Failed to get access token. HTTP status code: " + e.getMessage());
            log.debug("Failed to get access token. HTTP status code: " + e.getMessage());
            return new ResponseEntity<>(null, HttpStatus.BAD_REQUEST);
        }

        return new ResponseEntity<>(new AccessToken(externalUserId, sumsubResponse), HttpStatus.OK);
    }

    private String createMethodSignature() throws NoSuchAlgorithmException, InvalidKeyException {
        long ts = Instant.now().getEpochSecond();
        Mac hmacSha256 = Mac.getInstance("HmacSHA256");
        hmacSha256.init(new SecretKeySpec(sumsubSecretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        hmacSha256.update((ts + "POST" + "/resources/accessTokens").getBytes(StandardCharsets.UTF_8));
        byte[] bytes = hmacSha256.doFinal();
        return Hex.encodeHexString(bytes);
    }
}
