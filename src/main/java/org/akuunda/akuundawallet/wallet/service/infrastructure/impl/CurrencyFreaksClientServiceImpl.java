package org.akuunda.akuundawallet.wallet.service.infrastructure.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dto.external.CurrencyConversionResponse;
import org.akuunda.akuundawallet.wallet.service.infrastructure.CurrencyFreaksClientService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
@RequiredArgsConstructor
@Slf4j
public class CurrencyFreaksClientServiceImpl implements CurrencyFreaksClientService {

    @Value("${currencyfreaks.api.url}")
    private String currencyFreaksApiUrl;

    @Value("${currencyfreaks.api.key}")
    private String currencyFreaksApiKey;

    @Override
    public ResponseEntity<CurrencyConversionResponse> convertCurrency(final String from, final String to,
                                                                      final double amount) {
        log.info("Converting {} {} to {}", amount, from, to);
        log.debug("Converting {} {} to {}", amount, from, to);
        // Vérifier si les paramètres sont valides
        if (from == null || to == null ) {
            log.error("Paramètres invalides pour la conversion de devise");
            return new ResponseEntity<>(HttpStatus.NOT_ACCEPTABLE);
        }
        // Vérifier si l'URL de l'API est configurée
        if (currencyFreaksApiUrl == null || currencyFreaksApiUrl.isEmpty()) {
            log.error("URL de l'API de conversion de devise non configurée");
            return new ResponseEntity<>(HttpStatus.NOT_ACCEPTABLE);
        }
        // Vérifier si la clé API est configurée
        if (currencyFreaksApiKey == null || currencyFreaksApiKey.isEmpty()) {
            log.error("Clé API de conversion de devise non configurée");
            return new ResponseEntity<>(HttpStatus.NOT_ACCEPTABLE);
        }

        if (amount <= 0) {
            log.error("Invalid Amount. Please provide amount in decimal format and greater than 0!");
            return new ResponseEntity<>(HttpStatus.NOT_ACCEPTABLE);
        }


        // Vérifier si la devise de départ et la devise d'arrivée sont différentes
        if (from.equals(to)) {
            log.error("La devise de départ et la devise d'arrivée ne peuvent pas être identiques");
           return new ResponseEntity<>(HttpStatus.NOT_ACCEPTABLE);
        }

        try {
            // Construire l'URL avec les paramètres
            String url = String.format("%s/convert/latest?from=%s&to=%s&amount=%s&apikey=%s",
                    currencyFreaksApiUrl, from, to, amount, currencyFreaksApiKey);

            // Construire la requête HTTP
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .header("accept", "application/json")
                    .build();

            // Exécuter la requête
            HttpClient client = createHttpClient();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Retourner la réponse
            if (response.statusCode() == 200) {
                // Mapper la réponse JSON vers le DTO
                ObjectMapper objectMapper = new ObjectMapper();
                final var mapperValue = objectMapper.readValue(response.body(), CurrencyConversionResponse.class);
                return new ResponseEntity<>(mapperValue, HttpStatus.OK);
            } else {
                log.error("Erreur lors de la conversion de devise : {}", response.body());
                new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
        } catch (Exception e) {
            log.error("Exception lors de la conversion de devise", e);
            return new ResponseEntity<>(HttpStatus.EXPECTATION_FAILED);
        }
        return new ResponseEntity<>(HttpStatus.EXPECTATION_FAILED);
    }

    // 👇 à ajouter en bas de la classe
    public HttpClient createHttpClient() {
        return HttpClient.newHttpClient();
    }

}
