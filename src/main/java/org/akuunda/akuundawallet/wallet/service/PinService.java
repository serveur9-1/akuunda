package org.akuunda.akuundawallet.wallet.service;

import org.springframework.http.ResponseEntity;

public interface PinService {

    ResponseEntity<String> definePin(String userId, String pincode);

    /**
     * Stocke le PIN de manière sécurisée en base locale pour un PIN qui existe déjà chez Venly.
     * Utilise la même sécurité que definePin (génération de strings, hash PBKDF2).
     * À utiliser lors de la création d'un utilisateur où le PIN a déjà été créé chez Venly.
     * 
     * @param userId L'ID de l'utilisateur Venly
     * @param pincode Le PIN à stocker en base locale
     * @return ResponseEntity avec status OK si le PIN est stocké avec succès
     */
    ResponseEntity<String> storePinSecurelyForExistingUser(String userId, String pincode);

    /**
     * Réinitialise le PIN en utilisant l'emergency code
     * @param username Le nom d'utilisateur (numéro de téléphone). Utilisé pour récupérer le userId en base de données locale.
     *                 Cet endpoint est utilisé hors session, donc l'utilisateur n'a pas son userId, seulement son username.
     * @param emergencyCode Les 5 caractères de l'emergency code que l'utilisateur a mémorisés.
     *                     Les 20 caractères générés seront récupérés depuis la base de données.
     * @param newPincode Le nouveau PIN (6 caractères)
     * @return La réponse de Venly après mise à jour du PIN
     */
    ResponseEntity<String> resetPinWithEmergencyCode(String username, String emergencyCode, String newPincode);

    /**
     * Vérifie si un PIN fourni est correct pour un utilisateur.
     * Utilisé pour déverrouiller l'application mobile.
     * 
     * @param username Le nom d'utilisateur (numéro de téléphone). Utilisé pour récupérer le userId en base de données locale.
     * @param pincode Le PIN à vérifier
     * @return ResponseEntity avec success=true si le PIN est correct, success=false sinon
     */
    ResponseEntity<String> verifyPin(String username, String pincode);
}


