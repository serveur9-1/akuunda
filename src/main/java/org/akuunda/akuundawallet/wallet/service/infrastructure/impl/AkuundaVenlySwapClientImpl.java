package org.akuunda.akuundawallet.wallet.service.infrastructure.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dto.TokenPair;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaVenlySwapClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class AkuundaVenlySwapClientImpl implements AkuundaVenlySwapClient {


    @Value("${venly.url.base}")
    private String baseUrl;

    private final ObjectMapper objectMapper;

    /**
     * La première étape consiste à obtenir la liste de toutes les paires de jetons prises en charge.
     * Le résultat varie selon la blockchain sur laquelle le portefeuille ( ) est hébergé.walletId
     * Les paires de jetons seront automatiquement filtrées en fonction de la blockchain hébergeant
     * le portefeuille. Si le portefeuille est sur Ethereum , la liste affichera uniquement les jetons
     * échangeables sur la blockchain Ethereum .
     *
     * @param walletId L'identifiant du portefeuille.
     * @return ResponseEntity contenant les paires de tokens ou une erreur.
     */
    @Override
    public ResponseEntity<List<TokenPair>> retrieveTokenPairs(String walletId) {
        log.info("Retrieving token pairs from wallet {}", walletId);
        log.debug("Retrieving token pairs from wallet {}", walletId);
        try {
            String url = String.format("%s/api/wallets/%s/swaps/pairs", baseUrl, walletId);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .header("Accept", "application/json")
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                List<TokenPair> tokenPairs = objectMapper.readValue(response.body(),
                        objectMapper.getTypeFactory().constructCollectionType(List.class, TokenPair.class));
                return new ResponseEntity<>(tokenPairs, HttpStatus.OK);
            } else {
                log.error("Erreur lors de la récupération des paires de tokens : {}", response.body());
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
        } catch (Exception e) {
            log.error("Exception lors de la récupération des paires de tokens", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Récupère le taux d'échange pour une paire de tokens donnée.
     *
     * @param fromSecretType Blockchain du token source.
     * @param toSecretType Blockchain du token destination.
     * @param fromToken Adresse du token source.
     * @param toToken Adresse du token destination.
     * @param amount Montant à échanger.
     * @param orderType Type de l'ordre (SELL).
     * @return ResponseEntity contenant les informations sur le taux d'échange ou une erreur.
     */
    @Override
    public ResponseEntity<Object> retrieveExchangeRate(String fromSecretType, String toSecretType, String fromToken, String toToken, int amount, String orderType) {
        log.info("Retrieving exchange rate for tokens {} -> {}", fromToken, toToken);
        try {
            String url = String.format("%s/api/swaps/rates?fromSecretType=%s&toSecretType=%s&fromToken=%s&toToken=%s&amount=%d&orderType=%s",
                    baseUrl, fromSecretType, toSecretType, fromToken, toToken, amount, orderType);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .header("Accept", "application/json")
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return new ResponseEntity<>(response.body(), HttpStatus.OK);
            } else {
                log.error("Erreur lors de la récupération du taux d'échange : {}", response.body());
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
        } catch (Exception e) {
            log.error("Exception lors de la récupération du taux d'échange", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Construit les transactions nécessaires pour effectuer un swap.
     *
     * @param walletId L'identifiant du portefeuille source.
     * @param requestBody Le corps de la requête contenant les détails du swap.
     * @return ResponseEntity contenant les transactions nécessaires ou une erreur.
     */
    @Override
    public ResponseEntity<Object> buildSwapTransaction(String walletId, Object requestBody) {
        log.info("Building swap transaction for wallet {}", walletId);
        try {
            String url = String.format("%s/api/wallets/%s/swaps", baseUrl, walletId);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.ofString(new ObjectMapper().writeValueAsString(requestBody)))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return new ResponseEntity<>(response.body(), HttpStatus.OK);
            } else {
                log.error("Erreur lors de la construction de la transaction de swap : {}", response.body());
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
        } catch (Exception e) {
            log.error("Exception lors de la construction de la transaction de swap", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * Exécute une transaction de swap.
     *
     * @param requestBody Le corps de la requête contenant les détails de la transaction.
     * @param signingMethodHeader L'en-tête Signing-Method requis pour l'exécution.
     * @return ResponseEntity contenant le résultat de l'exécution de la transaction ou une erreur.
     */
    @Override
    public ResponseEntity<Object> executeSwapTransaction(Object requestBody, String signingMethodHeader) {
        log.info("Executing swap transaction");
        try {
            String url = String.format("%s/api/transactions/execute", baseUrl);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestBody)))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Signing-Method", signingMethodHeader)
                    .build();

            HttpClient client = HttpClient.newHttpClient();
            HttpResponse<String> response = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                log.info("Transaction executed successfully");
                return new ResponseEntity<>(response.body(), HttpStatus.OK);
            } else {
                log.error("Erreur lors de l'exécution de la transaction : {}", response.body());
                return new ResponseEntity<>(response.body(), HttpStatus.BAD_REQUEST);
            }
        } catch (Exception e) {
            log.error("Exception lors de l'exécution de la transaction", e);
            return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
