package org.akuunda.akuundawallet.keycloak.impl.service.infrastructure;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

@Validated
public interface WhatsappClientService {

    ResponseEntity<String> SendSimpleSms(final String msg, final String msisdn);

    /**
     * Envoie une notification ESCROW_FUNDED via template WhatsApp approuvé.
     *
     * @param payerPhone   Numéro du payeur au format international (ex: "+337656789")
     * @param amount       Montant formaté avec devise (ex: "13,99 EUR")
     * @param paymentCode  Code unique du paiement conditionnel
     * @param vendorName   Nom du vendeur/prestataire
     * @param serviceDate  Date du service (ex: "25/03/2026")
     * @param qrToken      Token pour le lien QR de suivi
     * @param language     Langue ("fr" ou "en")
     */
    ResponseEntity<String> sendEscrowFundedNotification(
            String payerPhone, String amount, String paymentCode,
            String vendorName, String serviceDate, String qrToken, String language);
}
