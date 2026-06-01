package org.akuunda.akuundawallet.backoffice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.akuunda.akuundawallet.common.dto.PushNotificationRequest;
import org.akuunda.akuundawallet.common.enums.NotificationType;
import org.akuunda.akuundawallet.common.service.FcmNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/v1/notifications")
@RequiredArgsConstructor
@Tag(name = "Admin - Notifications", description = "Envoi de notifications push depuis le backoffice")
@SecurityRequirement(name = "bearerAuth")
public class NotificationAdminController {

    private static final Logger log = LoggerFactory.getLogger(NotificationAdminController.class);

    private final FcmNotificationService fcmNotificationService;

    @PostMapping("/send/user")
    @Operation(summary = "Envoyer une notification à un utilisateur")
    public ResponseEntity<Object> sendToUser(@RequestBody SendToUserRequest request) {
        log.info("Admin: notification [{}] -> {}", request.type, request.username);

        PushNotificationRequest notif = new PushNotificationRequest();
        notif.setTitle(request.title);
        notif.setBody(request.body);
        notif.setType(request.type);
        notif.setReferenceId(request.referenceId);

        fcmNotificationService.sendToUser(request.username, notif);
        return ResponseEntity.ok(Map.of("status", "success", "message", "Notification envoyée"));
    }

    @PostMapping("/send/users")
    @Operation(summary = "Envoyer une notification à plusieurs utilisateurs")
    public ResponseEntity<Object> sendToUsers(@RequestBody SendToUsersRequest request) {
        log.info("Admin: notification [{}] -> {} utilisateurs", request.type, request.usernames.size());

        PushNotificationRequest notif = new PushNotificationRequest();
        notif.setTitle(request.title);
        notif.setBody(request.body);
        notif.setType(request.type);

        fcmNotificationService.sendToUsers(request.usernames, notif);
        return ResponseEntity.ok(Map.of("status", "success", "message", "Notifications envoyées"));
    }

    @PostMapping("/send/topic")
    @Operation(summary = "Envoyer une notification à un topic (tous les abonnés)")
    public ResponseEntity<Object> sendToTopic(@RequestBody SendToTopicRequest request) {
        log.info("Admin: notification topic '{}' — {}", request.topic, request.title);

        PushNotificationRequest notif = new PushNotificationRequest();
        notif.setTitle(request.title);
        notif.setBody(request.body);
        notif.setType(request.type);

        fcmNotificationService.sendToTopic(request.topic, notif);
        return ResponseEntity.ok(Map.of("status", "success", "message", "Notification topic envoyée"));
    }

    @PostMapping("/broadcast/new-feature")
    @Operation(summary = "Annoncer une nouvelle fonctionnalité à tous les utilisateurs")
    public ResponseEntity<Object> broadcastNewFeature(@RequestBody BroadcastRequest request) {
        log.info("Broadcast nouvelle fonctionnalité: {}", request.title);

        PushNotificationRequest notif = new PushNotificationRequest();
        notif.setTitle("🆕 " + request.title);
        notif.setBody(request.body);
        notif.setType(NotificationType.NEW_FEATURE);

        fcmNotificationService.sendToTopic("all_users", notif);
        return ResponseEntity.ok(Map.of("status", "success", "message", "Broadcast nouvelle fonctionnalité envoyé"));
    }

    @PostMapping("/broadcast/app-update")
    @Operation(summary = "Annoncer une mise à jour de l'app")
    public ResponseEntity<Object> broadcastAppUpdate(@RequestBody BroadcastRequest request) {
        log.info("Broadcast mise à jour: {}", request.title);

        PushNotificationRequest notif = new PushNotificationRequest();
        notif.setTitle("📲 " + request.title);
        notif.setBody(request.body);
        notif.setType(NotificationType.APP_UPDATE);

        fcmNotificationService.sendToTopic("all_users", notif);
        return ResponseEntity.ok(Map.of("status", "success", "message", "Broadcast mise à jour envoyé"));
    }

    // ── DTOs ────────────────────────────────────────────────────────────────

    public static class SendToUserRequest {
        public String username;
        public String title;
        public String body;
        public NotificationType type;
        public String referenceId;
    }

    public static class SendToUsersRequest {
        public List<String> usernames;
        public String title;
        public String body;
        public NotificationType type;
    }

    public static class SendToTopicRequest {
        public String topic;
        public String title;
        public String body;
        public NotificationType type;
    }

    public static class BroadcastRequest {
        public String title;
        public String body;
    }
}
