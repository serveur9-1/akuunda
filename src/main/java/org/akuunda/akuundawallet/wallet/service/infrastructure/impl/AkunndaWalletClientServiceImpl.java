package org.akuunda.akuundawallet.wallet.service.infrastructure.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.security.HttpClientCall;
import org.akuunda.akuundawallet.wallet.api.dto.external.WalletBalanceResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.WalletCreateResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.WalletGasBalanceResponse;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkunndaWalletClientService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.TokenGenerationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.net.http.HttpResponse;

@Slf4j
@Service
@RequiredArgsConstructor
//@Transactional
public class AkunndaWalletClientServiceImpl implements AkunndaWalletClientService {

    @Value("${venly.url.base}")
    private String walletServerUrlBase;

    private static final String WALLET_URL = "api/wallets";
    private static final String TOKEN_BALANCE = "/balance/tokens";
    private static final String GAS_BALANCE = "/balance";
    private static final String SLASH = "/";
    private static final int CODE_200 = 200;

    private final ObjectMapper objectMapper;

    private final TokenGenerationService tokenGenerationService;

    @Override
    public ResponseEntity<WalletCreateResponse> getWallet(final String walletId,
                                                          final boolean includeBalance) {
        String url = buildUrl(walletId, includeBalance);
        return executeHttpGet(url, WalletCreateResponse.class);
    }

    @Override
    public ResponseEntity<WalletBalanceResponse> getWalletTokens(String walletId) {
        String url = walletServerUrlBase + WALLET_URL + SLASH + walletId + TOKEN_BALANCE;
        return executeHttpPostWithoutSigning(url, WalletBalanceResponse.class);
    }

    @Override
    public ResponseEntity<WalletGasBalanceResponse> getWalletGasBalance(String walletId) {
        String url = walletServerUrlBase + WALLET_URL + SLASH + walletId + GAS_BALANCE;
        return executeHttpGet(url, WalletGasBalanceResponse.class);
    }

    @Override
    public ResponseEntity<WalletCreateResponse> updateWallet(final String walletId,
                                                             final String body) {
        String url = buildUrl(walletId, null);
        return executeHttpPatch(url, body, WalletCreateResponse.class);
    }

    @Override
    public ResponseEntity<WalletCreateResponse> createWallet(final String body,
                                                             final String signingPin) {
        String url = walletServerUrlBase + WALLET_URL;
        return executeHttpPost(url, body, signingPin, WalletCreateResponse.class);
    }

    @Override
    public boolean validWalletAddress(String body) {
        // Implémentation future
        return false;
    }

    private String buildUrl(String walletId, Boolean includeBalance) {
        StringBuilder urlBuilder = new StringBuilder(walletServerUrlBase)
                .append(WALLET_URL)
                .append(SLASH)
                .append(walletId);
        if (includeBalance != null) {
            urlBuilder.append("?includeBalance=").append(includeBalance);
        }
        return urlBuilder.toString();
    }

    private <T> ResponseEntity<T> executeHttpGet(String url, Class<T> responseType) {
        try {
            String apiKey = tokenGenerationService.generateToken().getBody();
            HttpResponse<String> httpResponse = HttpClientCall.httpGet(apiKey, url);
            return processHttpResponse(httpResponse, responseType);
        } catch (IOException | InterruptedException e) {
            log.error("Error during HTTP GET", e);
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    private <T> ResponseEntity<T> executeHttpPatch(String url, String body, Class<T> responseType) {
        try {
            String apiKey = tokenGenerationService.generateToken().getBody();
            HttpResponse<String> httpResponse = HttpClientCall.httpPatch(apiKey, url, body);
            return processHttpResponse(httpResponse, responseType);
        } catch (IOException | InterruptedException e) {
            log.error("Error during HTTP PATCH", e);
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    private <T> ResponseEntity<T> executeHttpPost(String url, String body, String signingPin, Class<T> responseType) {
        try {
            String apiKey = tokenGenerationService.generateToken().getBody();
            HttpResponse<String> httpResponse = HttpClientCall.createWalletPost(body, apiKey, url, signingPin);
            return processHttpResponse(httpResponse, responseType);
        } catch (IOException | InterruptedException e) {
            log.error("Error during HTTP POST", e);
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    private <T> ResponseEntity<T> executeHttpPostWithoutSigning(String url, Class<T> responseType) {
        try {
            String apiKey = tokenGenerationService.generateToken().getBody();
            HttpResponse<String> httpResponse = HttpClientCall.getWalletBalance(apiKey, url);
            return processHttpResponse(httpResponse, responseType);
        } catch (IOException | InterruptedException e) {
            log.error("Error during HTTP POST", e);
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
    }

    private <T> ResponseEntity<T> processHttpResponse(HttpResponse<String> httpResponse, Class<T> responseType) throws IOException {
        if (httpResponse.statusCode() == CODE_200) {
            T response = objectMapper.readValue(httpResponse.body(), responseType);
            return ResponseEntity.ok(response);
        } else {
            log.error("HTTP Error {}: {}", httpResponse.statusCode(), httpResponse.body());
            return new ResponseEntity<>(HttpStatus.EXPECTATION_FAILED);
        }
    }

}
