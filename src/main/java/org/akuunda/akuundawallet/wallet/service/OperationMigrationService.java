package org.akuunda.akuundawallet.wallet.service;

import org.akuunda.akuundawallet.wallet.api.dto.MigrationResponseDto;
import org.springframework.http.ResponseEntity;

/**
 * Service de migration pour corriger les opérations existantes en base de données.
 * 
 * Ce service corrige :
 * 1. Les opérations Guardarian avec des montants null (amount, convertedAmount)
 * 2. Les statuts non normalisés
 * 3. Les dates manquantes (createdAt, updatedAt)
 * 4. Les données incorrectes (username, designation, status)
 */
public interface OperationMigrationService {

    /**
     * Migre toutes les opérations en corrigeant les données incorrectes.
     * 
     * @return ResponseEntity contenant le résultat de la migration avec les statistiques en JSON
     */
    ResponseEntity<MigrationResponseDto> migrateAllOperations();
}

