package org.akuunda.akuundawallet.wallet.service;

import org.akuunda.akuundawallet.wallet.api.dto.partner.*;

import java.util.List;

/**
 * Service de smart contract modulable pour partenaires.
 *
 * Permet à chaque partenaire de configurer ses propres règles de redistribution
 * sans développement spécifique. Réutilise l'infrastructure escrow existante (Venly).
 */
public interface PartnerContractService {

    /**
     * Crée ou met à jour la configuration d'un contrat partenaire.
     * Valide que la somme des pourcentages des bénéficiaires <= 100%.
     */
    PartnerContractResponse createContract(String partnerUsername, CreatePartnerContractRequest request);

    /**
     * Récupère un contrat par son code.
     */
    PartnerContractResponse getContract(String contractCode);

    /**
     * Liste les contrats actifs d'un partenaire.
     */
    List<PartnerContractResponse> getPartnerContracts(String partnerUsername);

    /**
     * Désactive un contrat (les paiements en cours ne sont pas affectés).
     */
    void deactivateContract(String contractCode, String partnerUsername);

    /**
     * Initie un paiement sous un contrat partenaire.
     * Workflow : Client → Wallet intermédiaire → Smart contract (escrow)
     * Génère un QR code si triggerType = QR_CODE.
     */
    PartnerPaymentResponse initiatePayment(String contractCode, InitiatePartnerPaymentRequest request);

    // ── Modes existants ──────────────────────────────────────────────────────

    /** QR_CODE : scan du QR code par le provider → redistribution */
    PartnerPaymentResponse validateByQrCode(String qrToken, String scannedBy);

    /** MANUAL : validation explicite par un opérateur ou admin → redistribution */
    PartnerPaymentResponse validateManually(String paymentCode, String validatedBy);

    /** WEBHOOK : callback entrant avec signature HMAC → redistribution */
    PartnerPaymentResponse validateByWebhook(String paymentCode, String webhookPayload, String signature);

    // ── Nouveaux modes ───────────────────────────────────────────────────────

    /**
     * REMOTE_CONFIRMATION : le client clique sur le lien/bouton de confirmation
     * reçu par push ou e-mail. Le token est à usage unique.
     */
    PartnerPaymentResponse confirmReceiptByClient(String confirmationToken);

    /**
     * OTP : le provider saisit le code 6 chiffres fourni par le client.
     * Vérifie que le code correspond et n'a pas expiré.
     */
    PartnerPaymentResponse validateByOtp(String paymentCode, String otpCode, String validatedBy);

    /**
     * DUAL_CONFIRMATION – côté provider : le prestataire confirme la livraison.
     * La redistribution se déclenche quand les DEUX parties ont confirmé.
     */
    PartnerPaymentResponse confirmByProvider(String paymentCode, String providerUsername);

    /**
     * DUAL_CONFIRMATION – côté client : le client confirme la réception.
     * La redistribution se déclenche quand les DEUX parties ont confirmé.
     */
    PartnerPaymentResponse confirmByClient(String paymentCode, String clientUsername);

    /**
     * GEOLOCATION : l'app mobile du client envoie ses coordonnées GPS.
     * Vérifie que le client est dans le rayon configuré sur le contrat.
     */
    PartnerPaymentResponse validateByGeolocation(String paymentCode, double latitude, double longitude);

    /**
     * ADMIN_APPROVAL : un admin Akuunda approuve manuellement via le backoffice.
     */
    PartnerPaymentResponse approveByAdmin(String paymentCode, String adminUsername);

    /**
     * Conteste un paiement (modes DISPUTE_WINDOW / TIME_BASED).
     * Bloque la libération automatique et ouvre un dossier de litige.
     */
    PartnerPaymentResponse disputePayment(String paymentCode, String clientUsername, String reason);

    /**
     * DYNAMIC_ASSIGNMENT — Admin assigne les bénéficiaires après réception du paiement.
     * Les USDC sont déjà sécurisés. Une notification est envoyée au client pour confirmer
     * la fin du service une fois le professionnel sur place.
     */
    PartnerPaymentResponse assignBeneficiaries(String paymentCode,
                                               org.akuunda.akuundawallet.wallet.api.dto.partner.AssignPaymentBeneficiariesRequest request);

    /** Annule un paiement et rembourse le client selon les règles du contrat. */
    PartnerPaymentResponse cancelPayment(String paymentCode, String reason);

    /**
     * Rejoue la distribution sur un paiement en échec (fonds toujours dans le smart contract).
     * Réinitialise le statut et retente distribute().
     */
    PartnerPaymentResponse retryDistribution(String paymentCode);

    /**
     * Accorde le RELAYER_ROLE au wallet intermédiaire sur le smart contract.
     * Opération unique à effectuer après le déploiement du contrat.
     */
    String grantRelayerRole();

    /**
     * Récupère le statut d'un paiement.
     */
    PartnerPaymentResponse getPayment(String paymentCode);

    /**
     * Liste les paiements d'un client.
     */
    List<PartnerPaymentResponse> getClientPayments(String clientUsername);

    /**
     * Liste les paiements sous un contrat (pour le partenaire).
     */
    List<PartnerPaymentResponse> getContractPayments(String contractCode, String partnerUsername);
}
