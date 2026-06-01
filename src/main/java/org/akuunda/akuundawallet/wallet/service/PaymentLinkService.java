package org.akuunda.akuundawallet.wallet.service;

import org.akuunda.akuundawallet.wallet.api.dto.CreatePaymentLinkRequest;
import org.akuunda.akuundawallet.wallet.api.dto.PaymentLinkInfoResponse;
import org.akuunda.akuundawallet.wallet.api.dto.PaymentLinkResponse;
import org.akuunda.akuundawallet.wallet.api.dto.ProcessPaymentRequest;
import org.akuunda.akuundawallet.wallet.api.dto.WebPaymentRequest;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface PaymentLinkService {

    /**
     * Crée un nouveau lien de paiement pour un utilisateur
     * @param username Username du créateur du lien
     * @param request Détails du lien à créer
     * @return Réponse avec les détails du lien créé
     */
    ResponseEntity<PaymentLinkResponse> createPaymentLink(String username, CreatePaymentLinkRequest request);

    /**
     * Récupère un lien de paiement par son code unique
     * @param uniqueCode Code unique du lien
     * @return Réponse avec les détails du lien
     */
    ResponseEntity<PaymentLinkResponse> getPaymentLinkByCode(String uniqueCode);

    /**
     * Récupère les informations publiques d'un lien de paiement (pour l'interface web, sans authentification)
     * @param uniqueCode Code unique du lien
     * @return Réponse avec les informations publiques du lien
     */
    ResponseEntity<PaymentLinkInfoResponse> getPaymentLinkInfoForWeb(String uniqueCode);

    /**
     * Récupère tous les liens de paiement d'un utilisateur
     * @param username Username du créateur
     * @return Liste des liens de paiement
     */
    ResponseEntity<List<PaymentLinkResponse>> getUserPaymentLinks(String username);

    /**
     * Désactive un lien de paiement
     * @param username Username du créateur
     * @param uniqueCode Code unique du lien
     * @return Réponse de confirmation
     */
    ResponseEntity<String> deactivatePaymentLink(String username, String uniqueCode);

    /**
     * Traite un paiement via un lien de paiement (pour utilisateurs authentifiés)
     * @param request Détails du paiement
     * @return Réponse de confirmation
     */
    ResponseEntity<String> processPayment(ProcessPaymentRequest request);

    /**
     * Traite un paiement via l'interface web (sans authentification, via mobile money)
     * @param request Détails du paiement web
     * @return Réponse de confirmation
     */
    ResponseEntity<String> processWebPayment(WebPaymentRequest request);
}

