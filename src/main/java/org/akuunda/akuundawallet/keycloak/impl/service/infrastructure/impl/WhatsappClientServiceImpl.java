package org.akuunda.akuundawallet.keycloak.impl.service.infrastructure.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.keycloak.impl.service.infrastructure.WhatsappClientService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Slf4j
@Service
@RequiredArgsConstructor
public class WhatsappClientServiceImpl implements WhatsappClientService {

    @Value("${whatsapp.url.base}")
    private String whatsappUrlBase;

    @Value("${whatsapp.token}")
    private String whatsappToken;

    @Value("${whatsapp.expediteur}")
    private String whatsappExpediteur;

    @Override
    public ResponseEntity<String> SendSimpleSms(String msg, String msisdn) {
        String whatsappResponse;

        String messageBody = getBody(msg, msisdn);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(whatsappUrlBase + whatsappExpediteur + "/messages"))
                    .header("Authorization", "Bearer " + whatsappToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(messageBody))
                    .build();
            HttpClient http = HttpClient.newHttpClient();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            final var statusCode = response.statusCode();
            whatsappResponse = response.body();

            if (statusCode != 200) {
                return new ResponseEntity<>(whatsappResponse, HttpStatusCode.valueOf(statusCode));
            }

        } catch (URISyntaxException | IOException | InterruptedException e) {
            return new ResponseEntity<>("Failed to send Whatsapp SMS. Error: " + e, HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>(whatsappResponse, HttpStatus.OK);
    }

    @Override
    public ResponseEntity<String> sendEscrowFundedNotification(
            String payerPhone, String amount, String paymentCode,
            String vendorName, String serviceDate, String qrToken, String language) {

        String whatsappResponse;
        String messageBody = buildEscrowFundedBody(payerPhone, amount, paymentCode,
                vendorName, serviceDate, qrToken, language);

        log.info("📤 Envoi notification ESCROW_FUNDED WhatsApp au {} | montant={} | code={} | vendeur={}",
                payerPhone, amount, paymentCode, vendorName);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(new URI(whatsappUrlBase + whatsappExpediteur + "/messages"))
                    .header("Authorization", "Bearer " + whatsappToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(messageBody))
                    .build();
            HttpClient http = HttpClient.newHttpClient();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            final var statusCode = response.statusCode();
            whatsappResponse = response.body();

            if (statusCode != 200) {
                log.error("❌ Échec envoi notification ESCROW_FUNDED WhatsApp. Status: {} | Réponse: {}",
                        statusCode, whatsappResponse);
                return new ResponseEntity<>(whatsappResponse, HttpStatusCode.valueOf(statusCode));
            }

            log.info("✅ Notification ESCROW_FUNDED WhatsApp envoyée avec succès au {}", payerPhone);

        } catch (URISyntaxException | IOException | InterruptedException e) {
            log.error("❌ Exception lors de l'envoi notification ESCROW_FUNDED WhatsApp: {}", e.getMessage(), e);
            return new ResponseEntity<>("Failed to send Escrow Funded WhatsApp notification. Error: " + e,
                    HttpStatus.BAD_REQUEST);
        }
        return new ResponseEntity<>(whatsappResponse, HttpStatus.OK);
    }

    // ==================== PRIVATE HELPERS ====================

    private static String getBody(String otp, String msisdn) {

        String messagingProduct = "whatsapp";
        String to = msisdn.substring(2);
        String templateName = "verification_short_code_fr";
        String languageCode = "fr";

        String jsonTemplate = """
        {
           "messaging_product": "%s",
           "to": "%s",
           "type": "template",
           "template": {
               "name": "%s",
               "language": {
                   "code": "%s"
               },
               "components": [
                   {
                       "type": "body",
                       "parameters": [
                           {
                               "type": "text",
                               "text": "%s"
                           }
                       ]
                   },
                   {
                       "type": "button",
                       "sub_type": "url",
                       "index": 0,
                       "parameters": [
                           {
                               "type": "text",
                               "text": "%s"
                           }
                       ]
                   }
               ]
           }
        }
        """;

        return String.format(jsonTemplate, messagingProduct, to, templateName, languageCode, otp, otp);
    }

    /**
     * Normalise le numéro de téléphone du payeur pour l'API WhatsApp Meta.
     * Formats acceptés en entrée : "+337656789", "00337656789", "337656789"
     * Format de sortie : "337656789" (sans "+" ni "00" devant)
     */
    private static String normalizePhoneNumber(String payerPhone) {
        if (payerPhone == null) return "";
        String cleaned = payerPhone.trim();
        if (cleaned.startsWith("+")) {
            return cleaned.substring(1); // "+337656789" → "337656789"
        }
        if (cleaned.startsWith("00")) {
            return cleaned.substring(2); // "00337656789" → "337656789"
        }
        return cleaned; // déjà au bon format
    }

    private static String buildEscrowFundedBody(
            String payerPhone, String amount, String paymentCode,
            String vendorName, String serviceDate, String qrToken, String language) {

        String to = normalizePhoneNumber(payerPhone);
        String langCode = (language != null && language.startsWith("en")) ? "en" : "fr";

        String jsonTemplate = """
        {
           "messaging_product": "whatsapp",
           "to": "%s",
           "type": "template",
           "template": {
               "name": "escrow_funded_notifications",
               "language": {
                   "code": "%s"
               },
               "components": [
                   {
                       "type": "header",
                       "parameters": [
                           {
                               "type": "text",
                               "text": "%s"
                           }
                       ]
                   },
                   {
                       "type": "body",
                       "parameters": [
                           {
                               "type": "text",
                               "text": "%s"
                           },
                           {
                               "type": "text",
                               "text": "%s"
                           },
                           {
                               "type": "text",
                               "text": "%s"
                           },
                           {
                               "type": "text",
                               "text": "%s"
                           }
                       ]
                   },
                   {
                       "type": "button",
                       "sub_type": "url",
                       "index": 0,
                       "parameters": [
                           {
                               "type": "text",
                               "text": "%s"
                           }
                       ]
                   }
               ]
           }
        }
        """;

        return String.format(jsonTemplate,
                to,              // to  (ex: "337656789")
                langCode,        // language code ("fr" ou "en")
                amount,          // header {{1}}  (ex: "13,99 EUR")
                amount,          // body {{1}}
                paymentCode,     // body {{2}}
                vendorName,      // body {{3}}
                serviceDate,     // body {{4}}
                qrToken          // button[0] {{1}}
        );
    }
}
