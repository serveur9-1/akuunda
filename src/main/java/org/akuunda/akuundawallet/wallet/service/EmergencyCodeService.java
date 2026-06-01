package org.akuunda.akuundawallet.wallet.service;

import org.springframework.http.ResponseEntity;

public interface EmergencyCodeService {

    /**
     * Définit un Emergency Code pour un utilisateur
     * @param username Le nom d'utilisateur (numéro de téléphone). Utilisé pour récupérer le userId en base de données locale.
     * @param partialEmergencyCode Les 5 caractères que le client choisit pour son Emergency Code
     * @param pincode Le code PIN de l'utilisateur (6 caractères, nécessaire pour authentifier la création chez Venly)
     * @return ResponseEntity avec le résultat de la création
     */
    ResponseEntity<String> defineEmergencyCode(String username, String partialEmergencyCode, String pincode);

    /**
     * Vérifie si l'emergency code fourni correspond à celui stocké pour l'utilisateur
     * @param userId L'ID de l'utilisateur
     * @param emergencyCode Les 5 caractères de l'emergency code fournis par l'utilisateur (partial)
     *                      Les 20 caractères générés seront récupérés depuis la base de données
     *                      Le code complet (25 caractères) sera reconstruit et vérifié chez Venly
     * @return true si l'emergency code est valide, false sinon
     */
    boolean verifyEmergencyCode(String userId, String emergencyCode);

    /**
     * Permet à un utilisateur de changer son Emergency Code en connaissant l'actuel.
     * Utilise le code actuel comme authentification Venly pour mettre à jour le signing method.
     * @param username Le numéro de téléphone de l'utilisateur
     * @param currentPartialEmergencyCode Les 5 caractères actuels connus de l'utilisateur
     * @param newPartialEmergencyCode Les 5 nouveaux caractères choisis par l'utilisateur
     * @return ResponseEntity avec le résultat
     */
    ResponseEntity<String> changeEmergencyCode(String username, String currentPartialEmergencyCode, String newPartialEmergencyCode);

    /**
     * Vérifie si les 5 caractères partiels fournis correspondent au code de secours de l'utilisateur.
     */
    boolean verifyPartialEmergencyCode(String username, String partialEmergencyCode);

    /**
     * L'utilisateur a une ligne {@code user_emergency_codes} en base.
     */
    boolean hasEmergencyCodeConfigured(String username);

    /**
     * Indique si l'utilisateur doit migrer vers le code = 5 derniers chiffres du téléphone.
     * Pas de migration si le compte a été créé le 20/05/2026 ou après, ou si le code de secours
     * a été défini/modifié à partir de cette date ({@code user_emergency_codes.created_at}).
     * Les comptes antérieurs avec un code non mis à jour depuis cette date doivent migrer.
     */
    boolean requiresPhoneFormatEmergencyCodeUpdate(String username);

    /** Valeur du flag {@code uses_phone_last5} en base ({@code false} si absent ou utilisateur inconnu). */
    boolean isUsesPhoneLast5FlagSet(String username);

    /** {@code true} si le compte est résolu en base (username, mobile, chiffres). */
    boolean isMigrationStatusUserFound(String username);

    /**
     * Code déjà = 5 derniers chiffres du téléphone (vérification Venly) : marque {@code uses_phone_last5=true}.
     */
    ResponseEntity<String> acknowledgePhoneFormatEmergencyCode(String username);
}
