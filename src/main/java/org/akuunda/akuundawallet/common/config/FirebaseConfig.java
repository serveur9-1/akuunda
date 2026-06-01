package org.akuunda.akuundawallet.common.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

@Slf4j
@Configuration
public class FirebaseConfig {

    @Value("${firebase.service-account.path:}")
    private String serviceAccountPath;

    @Value("${firebase.project-id}")
    private String projectId;

    @PostConstruct
    public void init() {
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                if (serviceAccountPath == null || serviceAccountPath.isBlank()) {
                    log.error("❌ firebase.service-account.path non défini");
                    log.warn("⚠️ Les notifications push FCM ne seront PAS fonctionnelles.");
                    return;
                }

                log.info("🔑 Chargement Firebase credentials depuis: {}", serviceAccountPath);

                InputStream serviceAccount = new FileInputStream(serviceAccountPath);

                FirebaseOptions options = FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                        .setProjectId(projectId)
                        .build();

                FirebaseApp.initializeApp(options);
                log.info("✅ Firebase Admin SDK initialisé avec succès (projet: {})", projectId);
            }
        } catch (IOException e) {
            log.error("❌ Impossible d'initialiser Firebase Admin SDK: {}", e.getMessage(), e);
            log.warn("⚠️ Les notifications push FCM ne seront PAS fonctionnelles.");
        }
    }

    @Bean
    public FirebaseMessaging firebaseMessaging() {
        if (FirebaseApp.getApps().isEmpty()) {
            log.warn("FirebaseApp non initialisé — FirebaseMessaging sera null");
            return null;
        }
        return FirebaseMessaging.getInstance();
    }
}
