package org.akuunda.akuundawallet.wallet.service.infrastructure;

import org.akuunda.akuundawallet.wallet.api.dto.external.RestCountryResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * Service client pour l'API REST Countries (https://restcountries.com)
 */
public interface RestCountriesClientService {
    
    /**
     * Récupère tous les pays depuis l'API REST Countries
     * @return Liste de tous les pays avec leurs informations
     */
    ResponseEntity<List<RestCountryResponse>> getAllCountries();
    
    /**
     * Récupère un pays spécifique par son code ISO (2 lettres)
     * @param countryCode Code ISO du pays (ex: "CD", "CG", "CI")
     * @return Informations du pays
     */
    ResponseEntity<RestCountryResponse> getCountryByCode(String countryCode);
}

