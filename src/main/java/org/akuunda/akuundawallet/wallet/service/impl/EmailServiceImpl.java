package org.akuunda.akuundawallet.wallet.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.service.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Envoi d'emails via Microsoft Graph API (OAuth2 client_credentials).
 * Ne nécessite PAS spring-boot-starter-mail ni SMTP.
 */
@Slf4j
@Service
public class EmailServiceImpl implements EmailService {

    @Value("${azure.mail.tenant-id}")
    private String tenantId;

    @Value("${azure.mail.client-id}")
    private String clientId;

    @Value("${azure.mail.client-secret}")
    private String clientSecret;

    @Value("${azure.mail.from}")
    private String fromEmail;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Override
    public void sendHtmlEmail(String to, String subject, String html) {
        try {
            // 1. Obtenir un token OAuth2
            String accessToken = getAccessToken();

            // 2. Envoyer l'email via Graph API
            String jsonBody = buildSendMailJson(to, subject, html);

            String graphUrl = "https://graph.microsoft.com/v1.0/users/" + fromEmail + "/sendMail";

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(graphUrl))
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 202 || response.statusCode() == 200) {
                log.info("✅ Email envoyé via Microsoft Graph à {}", to);
            } else {
                log.error("❌ Échec envoi email via Graph. Status: {} | Body: {}", response.statusCode(), response.body());
                throw new RuntimeException("Graph API sendMail failed: HTTP " + response.statusCode());
            }

        } catch (Exception e) {
            log.error("❌ Erreur envoi email à {}: {}", to, e.getMessage(), e);
            throw new RuntimeException("Échec envoi email via Microsoft Graph: " + e.getMessage(), e);
        }
    }

    /**
     * Obtient un token OAuth2 via client_credentials flow.
     */
    private String getAccessToken() throws Exception {
        String tokenUrl = "https://login.microsoftonline.com/" + tenantId + "/oauth2/v2.0/token";

        String formBody = "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
                + "&scope=" + URLEncoder.encode("https://graph.microsoft.com/.default", StandardCharsets.UTF_8)
                + "&grant_type=client_credentials";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(formBody))
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            log.error("❌ Échec obtention token OAuth2. Status: {} | Body: {}", response.statusCode(), response.body());
            throw new RuntimeException("OAuth2 token request failed: HTTP " + response.statusCode());
        }

        // Extraire access_token du JSON (sans dépendance externe)
        String body = response.body();
        int start = body.indexOf("\"access_token\":\"") + 16;
        int end = body.indexOf("\"", start);
        String token = body.substring(start, end);

        log.debug("🔑 Token OAuth2 obtenu ({}... caractères)", token.length());
        return token;
    }

    /**
     * Construit le JSON pour l'API sendMail de Microsoft Graph.
     */
    private String buildSendMailJson(String to, String subject, String htmlContent) {
        // Escape les caractères spéciaux JSON dans le HTML
        String escapedHtml = htmlContent
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");

        String escapedSubject = subject
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");

        return """
                {
                    "message": {
                        "subject": "%s",
                        "body": {
                            "contentType": "HTML",
                            "content": "%s"
                        },
                        "toRecipients": [
                            {
                                "emailAddress": {
                                    "address": "%s"
                                }
                            }
                        ]
                    },
                    "saveToSentItems": "false"
                }
                """.formatted(escapedSubject, escapedHtml, to);
    }
}
