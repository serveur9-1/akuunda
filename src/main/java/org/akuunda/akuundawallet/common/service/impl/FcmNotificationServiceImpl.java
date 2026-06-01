package org.akuunda.akuundawallet.common.service.impl;

import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.dto.PushNotificationRequest;
import org.akuunda.akuundawallet.common.entity.UserDeviceToken;
import org.akuunda.akuundawallet.common.repository.UserDeviceTokenRepository;
import org.akuunda.akuundawallet.common.service.FcmNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class FcmNotificationServiceImpl implements FcmNotificationService {

    private final UserDeviceTokenRepository deviceTokenRepository;
    private FirebaseMessaging firebaseMessaging;

    public FcmNotificationServiceImpl(UserDeviceTokenRepository deviceTokenRepository) {
        this.deviceTokenRepository = deviceTokenRepository;
    }

    @Autowired(required = false)
    public void setFirebaseMessaging(FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
    }

    // ── Envoi à un utilisateur ──────────────────────────────────────────────

    @Override
    @Async
    @Transactional
    public void sendToUser(String username, PushNotificationRequest request) {
        if (firebaseMessaging == null) {
            log.warn("⚠️ FirebaseMessaging non disponible — notification ignorée pour {}", username);
            return;
        }

        List<UserDeviceToken> tokens = deviceTokenRepository.findByUsername(username);
        if (tokens.isEmpty()) {
            log.info("Aucun token FCM pour l'utilisateur {} — notification non envoyée", username);
            return;
        }

        List<String> fcmTokens = tokens.stream()
                .map(UserDeviceToken::getFcmToken)
                .collect(Collectors.toList());

        sendToTokens(fcmTokens, request);
        log.info("🔔 Notification [{}] envoyée à {} ({} device(s))",
                request.getType(), username, fcmTokens.size());
    }

    // ── Envoi à plusieurs utilisateurs ──────────────────────────────────────

    @Override
    @Async
    @Transactional
    public void sendToUsers(List<String> usernames, PushNotificationRequest request) {
        if (firebaseMessaging == null) {
            log.warn("⚠️ FirebaseMessaging non disponible — notification de masse ignorée");
            return;
        }

        List<UserDeviceToken> tokens = deviceTokenRepository.findByUsernameIn(usernames);
        if (tokens.isEmpty()) {
            log.info("Aucun token FCM pour les {} utilisateurs", usernames.size());
            return;
        }

        List<String> fcmTokens = tokens.stream()
                .map(UserDeviceToken::getFcmToken)
                .collect(Collectors.toList());

        sendToTokens(fcmTokens, request);
        log.info("🔔 Notification [{}] envoyée à {} utilisateurs ({} device(s))",
                request.getType(), usernames.size(), fcmTokens.size());
    }

    // ── Envoi par topic ─────────────────────────────────────────────────────

    @Override
    @Async
    public void sendToTopic(String topic, PushNotificationRequest request) {
        if (firebaseMessaging == null) {
            log.warn("⚠️ FirebaseMessaging non disponible — notification topic ignorée");
            return;
        }

        try {
            Message message = Message.builder()
                    .setTopic(topic)
                    .setNotification(Notification.builder()
                            .setTitle(request.getTitle())
                            .setBody(request.getBody())
                            .build())
                    .putAllData(buildDataPayload(request))
                    .setAndroidConfig(buildAndroidConfig())
                    .setApnsConfig(buildApnsConfig())
                    .build();

            String response = firebaseMessaging.send(message);
            log.info("✅ Notification topic '{}' envoyée: {}", topic, response);

        } catch (FirebaseMessagingException e) {
            log.error("❌ Erreur envoi notification topic '{}': {}", topic, e.getMessage(), e);
        }
    }

    // ── Gestion des tokens ──────────────────────────────────────────────────

    @Override
    @Transactional
    public void registerToken(String username, String fcmToken, String deviceName, String platform) {
        Optional<UserDeviceToken> existing = deviceTokenRepository
                .findByUsernameAndFcmToken(username, fcmToken);

        if (existing.isPresent()) {
            UserDeviceToken token = existing.get();
            token.setDeviceName(deviceName);
            token.setPlatform(platform);
            deviceTokenRepository.save(token);
            log.info("🔄 Token FCM mis à jour pour {} ({})", username, deviceName);
        } else {
            UserDeviceToken token = UserDeviceToken.builder()
                    .username(username)
                    .fcmToken(fcmToken)
                    .deviceName(deviceName)
                    .platform(platform)
                    .build();
            deviceTokenRepository.save(token);
            log.info("✅ Token FCM enregistré pour {} ({}, {})", username, deviceName, platform);
        }
    }

    @Override
    @Transactional
    public void unregisterToken(String username, String fcmToken) {
        deviceTokenRepository.deleteByUsernameAndFcmToken(username, fcmToken);
        log.info("🗑️ Token FCM supprimé pour {}", username);
    }

    // ── Méthodes privées ────────────────────────────────────────────────────

    private void sendToTokens(List<String> fcmTokens, PushNotificationRequest request) {
        Map<String, String> dataPayload = buildDataPayload(request);

        List<List<String>> batches = partition(fcmTokens, 500);

        for (List<String> batch : batches) {
            List<Message> messages = batch.stream()
                    .map(token -> Message.builder()
                            .setToken(token)
                            .setNotification(Notification.builder()
                                    .setTitle(request.getTitle())
                                    .setBody(request.getBody())
                                    .build())
                            .putAllData(dataPayload)
                            .setAndroidConfig(buildAndroidConfig())
                            .setApnsConfig(buildApnsConfig())
                            .build())
                    .collect(Collectors.toList());

            try {
                BatchResponse response = firebaseMessaging.sendEach(messages);

                if (response.getFailureCount() > 0) {
                    handleFailedTokens(batch, response);
                }

                log.info("📤 Batch FCM: {}/{} succès",
                        response.getSuccessCount(), batch.size());

            } catch (FirebaseMessagingException e) {
                log.error("❌ Erreur batch FCM: {}", e.getMessage(), e);
            }
        }
    }

    private void handleFailedTokens(List<String> tokens, BatchResponse response) {
        List<SendResponse> responses = response.getResponses();
        List<String> invalidTokens = new ArrayList<>();

        for (int i = 0; i < responses.size(); i++) {
            SendResponse sendResponse = responses.get(i);
            if (!sendResponse.isSuccessful()) {
                FirebaseMessagingException exception = sendResponse.getException();
                if (exception != null) {
                    MessagingErrorCode errorCode = exception.getMessagingErrorCode();
                    if (errorCode == MessagingErrorCode.UNREGISTERED ||
                            errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
                        invalidTokens.add(tokens.get(i));
                    } else {
                        log.warn("⚠️ Erreur FCM token index {}: {} ({})",
                                i, errorCode, exception.getMessage());
                    }
                }
            }
        }

        if (!invalidTokens.isEmpty()) {
            log.info("🗑️ Suppression de {} token(s) FCM invalide(s)", invalidTokens.size());
            invalidTokens.forEach(deviceTokenRepository::deleteByFcmToken);
        }
    }

    private Map<String, String> buildDataPayload(PushNotificationRequest request) {
        Map<String, String> data = new HashMap<>();
        if (request.getType() != null) {
            data.put("type", request.getType().getType());
        }
        if (request.getReferenceId() != null) {
            data.put("id", request.getReferenceId());
        }
        if (request.getExtraData() != null) {
            data.putAll(request.getExtraData());
        }
        data.put("timestamp", String.valueOf(System.currentTimeMillis()));
        return data;
    }

    private AndroidConfig buildAndroidConfig() {
        return AndroidConfig.builder()
                .setPriority(AndroidConfig.Priority.HIGH)
                .setNotification(AndroidNotification.builder()
                        .setChannelId("akuunda_pay_high_importance")
                        .setSound("default")
                        .build())
                .build();
    }

    private ApnsConfig buildApnsConfig() {
        return ApnsConfig.builder()
                .setAps(Aps.builder()
                        .setBadge(1)
                        .setSound("default")
                        .build())
                .putHeader("apns-priority", "10")
                .build();
    }

    private <T> List<List<T>> partition(List<T> list, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        for (int i = 0; i < list.size(); i += batchSize) {
            batches.add(list.subList(i, Math.min(i + batchSize, list.size())));
        }
        return batches;
    }
}
