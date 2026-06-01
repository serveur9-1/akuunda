package org.akuunda.akuundawallet.wallet.service;

/**
 * Service d'envoi d'emails transactionnels via Microsoft Graph API.
 */
public interface EmailService {

    /**
     * Envoie un email HTML.
     *
     * @param to      Adresse email du destinataire
     * @param subject Sujet de l'email
     * @param html    Contenu HTML de l'email
     */
    void sendHtmlEmail(String to, String subject, String html);
}
