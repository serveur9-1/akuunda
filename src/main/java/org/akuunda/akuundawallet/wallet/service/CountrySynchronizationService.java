package org.akuunda.akuundawallet.wallet.service;

import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * Service de synchronisation des pays depuis l'API REST Countries
 */
public interface CountrySynchronizationService {
    
    /**
     * Synchronise tous les pays depuis l'API REST Countries
     * Met à jour les pays existants et crée les nouveaux pays
     * @return Résultat de la synchronisation avec statistiques
     */
    ResponseEntity<CountrySyncResult> synchronizeAllCountries();
    
    /**
     * Résultat de la synchronisation
     */
    record CountrySyncResult(
            int totalCountries,
            int created,
            int updated,
            int errors,
            List<String> errorMessages
    ) {}
}

