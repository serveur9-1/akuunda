package org.akuunda.akuundawallet.wallet.service;

/**
 * Service responsable de l'envoi des notifications lorsqu'un paiement
 * conditionnel passe au statut ESCROW_FUNDED.
 */
public interface PaymentEscrowNotificationService {

    /**
     * Envoie une notification WhatsApp ESCROW_FUNDED au payeur (client).
     */
    void notifyEscrowFunded(String payerPhone, String payerName, String amount,
                            String paymentCode, String vendorName, String serviceDate,
                            String qrToken, String language);

    /**
     * Envoie une notification Email ESCROW_FUNDED au payeur (client).
     */
    void notifyEscrowFundedByEmail(String payerEmail, String payerName, String amount,
                                   String paymentCode, String vendorName, String serviceDate,
                                   String qrCodeUrl, String language);
}
