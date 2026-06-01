package org.akuunda.akuundawallet.wallet.service;

import org.akuunda.akuundawallet.wallet.api.dto.CancelConditionalPaymentRequest;
import org.akuunda.akuundawallet.wallet.api.dto.ConditionalPaymentRequest;
import org.akuunda.akuundawallet.wallet.api.dto.ConditionalPaymentResponse;
import org.akuunda.akuundawallet.wallet.api.dto.QRCodeValidationRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Service principal pour gérer les paiements conditionnels (escrow via smart contract).
 */
@Validated
public interface ConditionalPaymentService {

    /**
     * Crée un nouveau paiement conditionnel.
     * Le montant est débité du wallet du client et déposé dans le smart contract.
     * 
     * @param clientUsername Username du client
     * @param request Données du paiement conditionnel
     * @return ConditionalPaymentResponse
     */
    ResponseEntity<ConditionalPaymentResponse> createConditionalPayment(String clientUsername, ConditionalPaymentRequest request);

    /**
     * Valide un paiement conditionnel en scannant le QR code.
     * Change le statut à "condition_validated" et libère les fonds vers le vendeur.
     * 
     * @param request Requête de validation du QR code
     * @return ConditionalPaymentResponse
     */
    ResponseEntity<ConditionalPaymentResponse> validateQRCode(QRCodeValidationRequest request);

    /**
     * Annule un paiement conditionnel.
     * Applique les règles de remboursement (total ou partiel selon les règles).
     * 
     * @param paymentCode Code du paiement conditionnel
     * @param request Requête d'annulation
     * @return ConditionalPaymentResponse
     */
    ResponseEntity<ConditionalPaymentResponse> cancelPayment(String paymentCode, CancelConditionalPaymentRequest request);

    /**
     * Récupère un paiement conditionnel par son code.
     * 
     * @param paymentCode Code du paiement
     * @return ConditionalPaymentResponse
     */
    ResponseEntity<ConditionalPaymentResponse> getPaymentByCode(String paymentCode);

    /**
     * Récupère tous les paiements conditionnels d'un utilisateur (client ou vendeur).
     * 
     * @param username Username de l'utilisateur
     * @return Liste des paiements conditionnels
     */
    ResponseEntity<List<ConditionalPaymentResponse>> getUserPayments(String username);

    /**
     * Libère manuellement les fonds d'un paiement conditionnel validé.
     * (Utile si la libération automatique a échoué)
     * 
     * @param paymentCode Code du paiement
     * @return ConditionalPaymentResponse
     */
    ResponseEntity<ConditionalPaymentResponse> releaseFunds(String paymentCode);

    /**
     * Exécute le dépôt blockchain (Client → Wallet intermédiaire → Smart contract).
     * À appeler après la création du paiement, quand le client confirme avec son PIN.
     * 
     * @param paymentId ID du paiement conditionnel (retourné à la création)
     * @param clientPin PIN du client pour signer le transfert Venly
     * @return ConditionalPaymentResponse avec le vrai depositTransactionHash si succès
     */
    ResponseEntity<ConditionalPaymentResponse> executeDeposit(Long paymentId, String clientPin);
}

