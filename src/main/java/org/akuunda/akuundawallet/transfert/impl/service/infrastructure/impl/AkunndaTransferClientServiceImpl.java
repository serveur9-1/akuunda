package org.akuunda.akuundawallet.transfert.impl.service.infrastructure.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.security.HttpClientCall;
import org.akuunda.akuundawallet.transfert.api.dto.command.TransactionCommand;
import org.akuunda.akuundawallet.transfert.api.dto.external.TransactionResponseDto;
import org.akuunda.akuundawallet.transfert.impl.service.infrastructure.AkunndaTransferClientService;
import org.apache.http.client.utils.URIBuilder;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
@Service
public class AkunndaTransferClientServiceImpl implements AkunndaTransferClientService {

    @Value("${venly.url.base}")
    private String venlyServerUrlBase;

    @Value("${venly.url.auth}")
    private String venlyAuthentificationURL;
    @Value("${venly.clientId}")
    private String venlyClientId;

    @Value("${venly.clientSecret}")
    private String venlyClientSecret;

    private static final String TRANSFER_URL = "api/transactions";
    private static final String CONTRACT_URL = "api/contracts";
    private static final String ERC_CONTRACT_URL = "v3/erc1155/contracts";
    private static final String SLASH = "/";
    private static final int CODE_200 = 200;

    private final ObjectMapper objectMapper;

    // Token cache — Venly tokens are valid ~300s; refreshed 30s before expiry.
    private volatile String cachedToken = null;
    private volatile long tokenExpiresAt = 0L;

    public AkunndaTransferClientServiceImpl(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String getAllTransaction(final TransactionCommand command) {
        try {
            String apiKey = getToken();

            URIBuilder uriBuilder = new URIBuilder(venlyServerUrlBase + TRANSFER_URL);
            uriBuilder.addParameter("statuses", command.getStatuses());
            uriBuilder.addParameter("userId", command.getUserId());
            uriBuilder.addParameter("includeUsers", String.valueOf(command.isIncludeUsers()));
            uriBuilder.addParameter("page", String.valueOf(command.getPage()));
            uriBuilder.addParameter("size", String.valueOf(command.getSize()));
            uriBuilder.addParameter("sortOn", command.getSortOn());
            uriBuilder.addParameter("sortOrder", command.getSortOrder());

            final var httpResponse = HttpClientCall.httpGet(apiKey, uriBuilder.build().toString());
            return getTransferResponse(httpResponse);
        } catch (IOException | InterruptedException | URISyntaxException e) {
            log.error("Error fetching transactions: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public String executeNftTransfer(final String body) {

        try {
            String apiKey = getToken();

            final var httpResponse = HttpClientCall.httpPost(apiKey, venlyServerUrlBase + TRANSFER_URL +
                    SLASH + "execute", body);
            return getTransferResponse(httpResponse);
        } catch (IOException | InterruptedException e) {
           log.error("Error execute transactions: {}", e.getMessage());
              return null;
        }
    }

    @Override
    public ResponseEntity<TransactionResponseDto> executeTransfert(final String body, final String headerPin) {
        try {
            String apiKeyToken = getToken();

            final var httpResponse = HttpClientCall.executeTransfertPost( venlyServerUrlBase + TRANSFER_URL +
                    SLASH + "execute", body, headerPin, apiKeyToken);
            
            // ⚠️ CRITIQUE : Logger la réponse complète de Venly pour diagnostic
            log.info("📥 Réponse Venly Transfer - Status: {}, Body: {}", 
                    httpResponse.statusCode(), 
                    httpResponse.body());
            
            if (httpResponse.statusCode() == CODE_200) {
                return ResponseEntity.ok(objectMapper.readValue(httpResponse.body(), TransactionResponseDto.class));
            } else {
                // Retourner la réponse avec le body pour avoir plus de détails
                log.error("❌ Erreur Venly Transfer - Status: {}, Body: {}", 
                        httpResponse.statusCode(), 
                        httpResponse.body());
                return ResponseEntity.status(httpResponse.statusCode())
                        .body(null); // Le body sera géré dans TransfertServiceImpl
            }
        } catch (IOException | InterruptedException e) {
            return new ResponseEntity<>(null, HttpStatus.EXPECTATION_FAILED);
        }
    }

    @Override
    public String cancelTransaction(final String body) {
        try {
            String apiKey = getToken();

            final var httpResponse = HttpClientCall.httpPost(body, apiKey, venlyServerUrlBase + TRANSFER_URL +
                    SLASH + "cancel");
            return getTransferResponse(httpResponse);
        } catch (IOException | InterruptedException e) {
            log.error("Error cancel transactions: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public String readContract(final String body) {
        try {
            String apiKey = getToken();
            if (apiKey == null) {
                log.error("❌ Failed to get Venly token for readContract");
                return null;
            }
            log.debug("📤 readContract request: {}", body);
            final var httpResponse = HttpClientCall.httpPost(body, apiKey, venlyServerUrlBase + CONTRACT_URL +
                    SLASH + "read");
            log.debug("📥 readContract response - Status: {}, Body: {}", httpResponse.statusCode(), httpResponse.body());
            return getTransferResponse(httpResponse);
        } catch (IOException | InterruptedException e) {
            log.error("❌ Error read contracts: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    public String createContract(final String body) {
        try {
            String apiKey = getToken();

            final var httpResponse = HttpClientCall.httpPost(body, apiKey, venlyServerUrlBase + ERC_CONTRACT_URL
                    + "/deployments");
            return getTransferResponse(httpResponse);
        } catch (IOException | InterruptedException e) {
            log.error("Error create contracts: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public String checkContractStatus(final String deployment_id) {
        try {
            String apiKey = getToken();

            final var httpResponse = HttpClientCall.httpGet(apiKey, venlyServerUrlBase + ERC_CONTRACT_URL
                    + "/deployments" + SLASH + deployment_id);
            return getTransferResponse(httpResponse);
        } catch (IOException | InterruptedException e) {
            log.error("Error check contract status: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public String getContractByChainAndAddress(final String chain, String contractAddress) {
        try {
            String apiKey = getToken();

            final var httpResponse = HttpClientCall.httpGet(apiKey, venlyServerUrlBase + ERC_CONTRACT_URL
                    + SLASH + chain + SLASH + contractAddress);
            return getTransferResponse(httpResponse);
        } catch (IOException | InterruptedException e) {
            log.error("Error get contract: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public String deleteContract(final String chain, final String contractAddress) {
        try {
            String apiKey = getToken();

            final var httpResponse = HttpClientCall.httpDelete(apiKey, venlyServerUrlBase + ERC_CONTRACT_URL
                    + SLASH + chain + SLASH + contractAddress);
            return getTransferResponse(httpResponse);
        } catch (IOException | InterruptedException e) {
            log.error("Error deleting contract: {}", e.getMessage());
            return null;
        }
    }

    private String getTransferResponse(HttpResponse<String> httpResponse) throws JsonProcessingException {
        if (httpResponse.statusCode() == CODE_200) {
            return httpResponse.body();
        } else {
            log.error("❌ Venly API error - Status: {}, Body: {}", httpResponse.statusCode(), httpResponse.body());
            return null;
        }
    }

    private synchronized String getToken() throws IOException, InterruptedException {
        if (cachedToken != null && System.currentTimeMillis() < tokenExpiresAt) {
            return cachedToken;
        }
        HttpResponse<String> response = getHttpResponse();
        if (response.statusCode() != CODE_200) {
            log.error("❌ Error fetching Venly token - Status: {}, Body: {}", response.statusCode(), response.body());
            return null;
        }
        JSONObject json = new JSONObject(response.body());
        cachedToken = json.getString("access_token");
        long expiresIn = json.optLong("expires_in", 300L);
        tokenExpiresAt = System.currentTimeMillis() + (expiresIn - 30L) * 1000L;
        log.debug("🔑 Venly token refreshed, expires in {}s", expiresIn);
        return cachedToken;
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
