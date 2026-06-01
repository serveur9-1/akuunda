package org.akuunda.akuundawallet.wallet.service;

import org.springframework.http.ResponseEntity;

/**
 * Service de migration pour mettre à jour les pincodes des anciens comptes
 * qui n'ont pas passé par le nouveau mécanisme de hash.
 */
public interface PinMigrationService {

    /**
     * Migre tous les utilisateurs en mettant à jour leur pincode à "111111"
     * dans la base de données (avec le nouveau mécanisme de hash) et dans Venly.
     * 
     * @return ResponseEntity contenant le résultat de la migration
     */
    ResponseEntity<String> migrateAllUsersPincode();
}

