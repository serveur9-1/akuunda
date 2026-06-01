package org.akuunda.akuundawallet.wallet.service;

import org.akuunda.akuundawallet.wallet.api.entities.QRCode;

/**
 * Service pour générer et gérer les QR codes de validation.
 */
public interface QRCodeService {

    /**
     * Génère un QR code pour un paiement conditionnel.
     * 
     * @param conditionalPaymentId ID du paiement conditionnel
     * @param expiresInHours Nombre d'heures avant expiration (null = pas d'expiration)
     * @return QRCode généré
     */
    QRCode generateQRCode(Long conditionalPaymentId, Integer expiresInHours);

    /**
     * Valide un QR code en le scannant.
     * 
     * @param qrCodeToken Token unique du QR code
     * @param scannedBy Username de la personne qui scanne
     * @return QRCode validé
     */
    QRCode validateQRCode(String qrCodeToken, String scannedBy);

    /**
     * Récupère un QR code par son token.
     * 
     * @param token Token du QR code
     * @return QRCode ou null si non trouvé
     */
    QRCode getQRCodeByToken(String token);

    /**
     * Récupère le QR code associé à un paiement conditionnel.
     * 
     * @param conditionalPaymentId ID du paiement conditionnel
     * @return QRCode ou null si non trouvé
     */
    QRCode getQRCodeByPaymentId(Long conditionalPaymentId);
}

