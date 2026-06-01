package org.akuunda.akuundawallet.common.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.service.FcmNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints pour la gestion des tokens FCM côté mobile.
 *
 * L'app Flutter appelle ces endpoints :
 * - POST /api/v1/devices/fcm-token → après login (enregistrer le token)
 * - DELETE /api/v1/devices/fcm-token → au logout (supprimer le token)
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/devices")
@RequiredArgsConstructor
@Tag(name = "Device Token Management", description = "Gestion des tokens FCM pour les notifications push")
@SecurityRequirement(name = "bearerAuth")
public class DeviceTokenController {

    private final FcmNotificationService fcmNotificationService;

    // ── Enregistrer un token ────────────────────────────────────────────────

    @PostMapping("/fcm-token")
    @Operation(
            operationId = "registerFcmToken",
            summary = "Enregistrer un token FCM",
            description = """
                    Enregistre le token FCM d'un device pour recevoir des notifications push.
                    À appeler après chaque login ou quand le token FCM est rafraîchi.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token enregistré avec succès",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {"status": "success", "message": "Token FCM enregistré"}
                                    """))),
            @ApiResponse(responseCode = "400", description = "Requête invalide")
    })
    public ResponseEntity<Object> registerToken(@RequestBody RegisterTokenRequest request) {
        log.info("📱 Enregistrement token FCM pour {} ({}, {})",
                request.getUsername(), request.getDeviceName(), request.getPlatform());

        fcmNotificationService.registerToken(
                request.getUsername(),
                request.getFcmToken(),
                request.getDeviceName(),
                request.getPlatform()
        );

        return ResponseEntity.ok(java.util.Map.of(
                "status", "success",
                "message", "Token FCM enregistré"
        ));
    }

    // ── Supprimer un token ──────────────────────────────────────────────────

    @DeleteMapping("/fcm-token")
    @Operation(
            operationId = "unregisterFcmToken",
            summary = "Supprimer un token FCM",
            description = """
                    Supprime le token FCM d'un device. À appeler au logout.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token supprimé avec succès"),
            @ApiResponse(responseCode = "400", description = "Requête invalide")
    })
    public ResponseEntity<Object> unregisterToken(@RequestBody UnregisterTokenRequest request) {
        log.info("🗑️ Suppression token FCM pour {}", request.getUsername());

        fcmNotificationService.unregisterToken(
                request.getUsername(),
                request.getFcmToken()
        );

        return ResponseEntity.ok(java.util.Map.of(
                "status", "success",
                "message", "Token FCM supprimé"
        ));
    }

    // ── DTOs internes ───────────────���───────────────────────────────────────

    @Data
    public static class RegisterTokenRequest {
        @Schema(description = "Identifiant de l'utilisateur (numéro de téléphone)", example = "0033619268805")
        private String username;

        @Schema(description = "Token FCM du device", example = "dGhpcyBpcyBhIHRlc3QgdG9rZW4...")
        private String fcmToken;

        @Schema(description = "Nom du device", example = "iPhone 15 Pro")
        private String deviceName;

        @Schema(description = "Plateforme", example = "IOS", allowableValues = {"ANDROID", "IOS", "WEB"})
        private String platform;
    }

    @Data
    public static class UnregisterTokenRequest {
        @Schema(description = "Identifiant de l'utilisateur", example = "0033619268805")
        private String username;

        @Schema(description = "Token FCM à supprimer", example = "dGhpcyBpcyBhIHRlc3QgdG9rZW4...")
        private String fcmToken;
    }
}
