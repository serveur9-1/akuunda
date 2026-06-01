package org.akuunda.akuundawallet.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.akuunda.akuundawallet.common.enums.NotificationType;

import java.util.Map;

/**
 * DTO pour envoyer une notification push FCM.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushNotificationRequest {

    /** Titre de la notification (affiché dans la barre de notification). */
    private String title;

    /** Corps du message. */
    private String body;

    /** Type de notification (pour la navigation côté mobile). */
    private NotificationType type;

    /** ID de la ressource associée (transaction ID, escrow ID, etc.). */
    private String referenceId;

    /** Données supplémentaires (key-value) incluses dans le payload data. */
    private Map<String, String> extraData;
}
