package org.akuunda.akuundawallet.wallet.api.enums;

public enum BookingStatus {
    PENDING,           // En attente de réponse du prestataire
    ACCEPTED,          // Accepté, paiement conditionnel créé
    REJECTED,          // Refusé par le prestataire
    CANCELLED,         // Annulé par le client
    COMPLETED,         // Service rendu, fonds libérés
    EXPIRED,           // Expiré (pas de réponse)
    DISPUTED           // Litige en cours
}
