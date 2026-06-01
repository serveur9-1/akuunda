package org.akuunda.akuundawallet.wallet.service;

import org.akuunda.akuundawallet.wallet.api.entities.CheckoutSession;

public interface WebhookService {
    /** Envoie un webhook avec l'événement {@code payment.completed}. */
    void sendPaymentWebhook(CheckoutSession checkoutSession);

    /** Envoie un webhook avec un nom d'événement personnalisé (ex: {@code payment.refunded}). */
    void sendEventWebhook(CheckoutSession checkoutSession, String eventName);
}
