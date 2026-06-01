package org.akuunda.akuundawallet.wallet.service;

import org.akuunda.akuundawallet.wallet.api.dto.CheckoutCountryOption;
import org.akuunda.akuundawallet.wallet.api.dto.CheckoutSessionResponse;
import org.akuunda.akuundawallet.wallet.api.dto.PaymentCreateRequest;
import org.akuunda.akuundawallet.wallet.api.dto.PaymentResponse;
import org.akuunda.akuundawallet.wallet.api.dto.RefundRequest;
import org.springframework.http.ResponseEntity;

import java.util.List;

/**
 * Service côté intégrateur marchand : création et lecture de paiements via clé API.
 */
public interface PaymentsService {

    /**
     * Crée un paiement et retourne l'URL de la page hébergée. Auth par clé API.
     * Le header {@code idempotencyKey} permet de rejouer la même requête sans dupliquer.
     */
    ResponseEntity<PaymentResponse> createPayment(String apiKey, String idempotencyKey, PaymentCreateRequest request);

    /** Récupère un paiement à partir de son id (= checkoutCode). Auth par clé API. */
    ResponseEntity<PaymentResponse> getPayment(String apiKey, String paymentId);

    /** Récupère la vue publique d'un paiement (utilisée par la page de checkout). Sans auth. */
    ResponseEntity<CheckoutSessionResponse> getPublicCheckout(String checkoutCode);

    /**
     * Retourne les pays de paiement disponibles pour ce checkout, avec le provider associé.
     * Basé sur la devise du checkout + les pays activés en base. Sans auth.
     */
    ResponseEntity<List<CheckoutCountryOption>> getCheckoutCountries(String checkoutCode);

    /** Rembourse un paiement (total par défaut, partiel si {@code amount} fourni). */
    ResponseEntity<PaymentResponse> refundPayment(String apiKey, String paymentId, RefundRequest request);

    /** [Sandbox uniquement] Simule la complétion d'un paiement de test. */
    ResponseEntity<PaymentResponse> simulateTestCompletion(String apiKey, String paymentId);
}
