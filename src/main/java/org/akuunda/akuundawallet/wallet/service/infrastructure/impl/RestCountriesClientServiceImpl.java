package org.akuunda.akuundawallet.wallet.service.infrastructure.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dto.external.RestCountryResponse;
import org.akuunda.akuundawallet.wallet.service.infrastructure.RestCountriesClientService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

/**
 * Implémentation du client pour l'API REST Countries
 * Documentation: https://restcountries.com
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RestCountriesClientServiceImpl implements RestCountriesClientService {
    
    private static final String BASE_URL = "https://restcountries.com/v3.1";
    private static final String ALL_COUNTRIES_ENDPOINT = BASE_URL + "/all?fields=name,cca2,currencies,idd,capital,region,subregion,continents,flags";
    private static final String COUNTRY_BY_CODE_ENDPOINT = BASE_URL + "/alpha/";
    
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    
    @Override
    public ResponseEntity<List<RestCountryResponse>> getAllCountries() {
        try {
            log.info("Récupération de tous les pays depuis REST Countries API");
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ALL_COUNTRIES_ENDPOINT))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == HttpStatus.OK.value()) {
                List<RestCountryResponse> countries = objectMapper.readValue(
                        response.body(),
                        new TypeReference<List<RestCountryResponse>>() {}
                );
                
                log.info("✅ {} pays récupérés depuis REST Countries API", countries.size());
                return ResponseEntity.ok(countries);
            } else {
                log.error("❌ Erreur lors de la récupération des pays: HTTP {}", response.statusCode());
                return ResponseEntity.status(response.statusCode()).build();
            }
            
        } catch (Exception e) {
            log.error("❌ Erreur lors de l'appel à l'API REST Countries", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
    
    @Override
    public ResponseEntity<RestCountryResponse> getCountryByCode(String countryCode) {
        try {
            log.info("Récupération du pays {} depuis REST Countries API", countryCode);
            
            String url = COUNTRY_BY_CODE_ENDPOINT + countryCode.toLowerCase();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == HttpStatus.OK.value()) {
                // L'API peut retourner un tableau ou un objet unique
                String body = response.body();
                RestCountryResponse country;
                
                if (body.trim().startsWith("[")) {
                    // C'est un tableau, prendre le premier élément
                    List<RestCountryResponse> countries = objectMapper.readValue(
                            body,
                            new TypeReference<List<RestCountryResponse>>() {}
                    );
                    if (countries == null || countries.isEmpty()) {
                        log.warn("⚠️ Aucun pays trouvé pour le code {}", countryCode);
                        return ResponseEntity.notFound().build();
                    }
                    country = countries.get(0);
                } else {
                    // C'est un objet unique
                    country = objectMapper.readValue(body, RestCountryResponse.class);
                }
                
                log.info("✅ Pays {} récupéré avec succès: {}", countryCode, country.getCountryNameInFrench());
                return ResponseEntity.ok(country);
            } else if (response.statusCode() == HttpStatus.NOT_FOUND.value()) {
                log.warn("⚠️ Pays {} non trouvé dans l'API REST Countries", countryCode);
                return ResponseEntity.notFound().build();
            } else {
                log.error("❌ Erreur lors de la récupération du pays {}: HTTP {}", countryCode, response.statusCode());
                return ResponseEntity.status(response.statusCode()).build();
            }
            
        } catch (Exception e) {
            log.error("❌ Erreur lors de l'appel à l'API REST Countries pour le pays {}", countryCode, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }
}

