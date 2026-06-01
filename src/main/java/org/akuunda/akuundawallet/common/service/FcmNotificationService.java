package org.akuunda.akuundawallet.common.service;

import org.akuunda.akuundawallet.common.dto.PushNotificationRequest;

import java.util.List;

/**
 * Service d'envoi de notifications push via Firebase Cloud Messaging.
 */
public interface FcmNotificationService {

    /**
     * Envoyer une notification push à un utilisateur (tous ses devices).
     *
     * @param username Le numéro de téléphone / identifiant de l'utilisateur
     * @param request  Les détails de la notification
     */
    void sendToUser(String username, PushNotificationRequest request);

    /**
     * Envoyer une notification push à plusieurs utilisateurs.
     *
     * @param usernames Liste des identifiants
     * @param request   Les détails de la notification
     */
    void sendToUsers(List<String> usernames, PushNotificationRequest request);

    /**
     * Envoyer une notification à un topic FCM (ex: "promotions", "maintenance").
     *
     * @param topic   Le nom du topic
     * @param request Les détails de la notification
     */
    void sendToTopic(String topic, PushNotificationRequest request);

    /**
     * Enregistrer le token FCM d'un device.
     *
     * @param username   Identifiant de l'utilisateur
     * @param fcmToken   Token FCM du device
     * @param deviceName Nom du device (optionnel)
     * @param platform   Plateforme : ANDROID, IOS, WEB (optionnel)
     */
    void registerToken(String username, String fcmToken, String deviceName, String platform);

    /**
     * Supprimer le token FCM d'un device (déconnexion).
     *
     * @param username Identifiant de l'utilisateur
     * @param fcmToken Token FCM à supprimer
     */
    void unregisterToken(String username, String fcmToken);
}
