package org.akuunda.akuundawallet.wallet.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.keycloak.impl.service.infrastructure.WhatsappClientService;
import org.akuunda.akuundawallet.wallet.service.EmailService;
import org.akuunda.akuundawallet.wallet.service.PaymentEscrowNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentEscrowNotificationServiceImpl implements PaymentEscrowNotificationService {

    private final WhatsappClientService whatsappClientService;
    private final EmailService emailService;

    @Override
    @Async
    public void notifyEscrowFunded(String payerPhone, String payerName, String amount,
                                   String paymentCode, String vendorName, String serviceDate,
                                   String qrToken, String language) {

        log.info("🔔 Notification ESCROW_FUNDED WhatsApp | payeur={} ({}) | montant={} | code={}",
                payerName, payerPhone, amount, paymentCode);

        try {
            ResponseEntity<String> response = whatsappClientService.sendEscrowFundedNotification(
                    payerPhone, amount, paymentCode, vendorName, serviceDate, qrToken, language);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ WhatsApp ESCROW_FUNDED envoyé à {} ({})", payerName, payerPhone);
            } else {
                log.warn("⚠️ WhatsApp ESCROW_FUNDED statut {}: {}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            log.error("❌ Erreur WhatsApp ESCROW_FUNDED: {}", e.getMessage(), e);
        }
    }

    @Override
    @Async
    public void notifyEscrowFundedByEmail(String payerEmail, String payerName, String amount,
                                          String paymentCode, String vendorName, String serviceDate,
                                          String qrCodeUrl, String language) {

        log.info("🔔 Notification ESCROW_FUNDED Email | payeur={} ({}) | montant={} | code={}",
                payerName, payerEmail, amount, paymentCode);

        try {
            String subject = "Paiement sécurisé — " + amount;
            String html = buildEscrowFundedEmailHtml(payerName, amount, paymentCode,
                    vendorName, serviceDate, qrCodeUrl);

            emailService.sendHtmlEmail(payerEmail, subject, html);

            log.info("✅ Email ESCROW_FUNDED envoyé à {} ({})", payerName, payerEmail);

        } catch (Exception e) {
            log.error("❌ Erreur Email ESCROW_FUNDED: {}", e.getMessage(), e);
        }
    }

    /**
     * Template HTML aligné exactement sur le template WhatsApp "escrow_funded_notifications".
     *
     * Texte WhatsApp :
     *   Bonjour,
     *   Vous avez conditionné votre paiement de *{{1}}*
     *   📋 *Référence :* {{2}}
     *   🏢 *Prestataire :* {{3}}
     *   📅 *Date de service :* {{4}}
     *   Présentez votre QR code à votre arrivée pour valider le service.
     *   💡 Téléchargez Akuunda Pay pour recevoir automatiquement vos fonds en cas d'annulation
     */
    private String buildEscrowFundedEmailHtml(String payerName, String amount, String paymentCode,
                                              String vendorName, String serviceDate, String qrCodeUrl) {

        String greeting = (payerName != null && !payerName.isBlank())
                ? "Bonjour " + payerName + ","
                : "Bonjour,";

        return """
                <!DOCTYPE html>
                <html lang="fr">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
                <body style="margin:0;padding:0;background-color:#f4f4f4;font-family:Arial,Helvetica,sans-serif;">
                <table width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f4f4f4;padding:20px 0;">
                <tr><td align="center">
                <table width="600" cellpadding="0" cellspacing="0" style="background-color:#ffffff;border-radius:8px;overflow:hidden;box-shadow:0 2px 8px rgba(0,0,0,0.1);">

                <!-- HEADER -->
                <tr><td style="background-color:#1a1a2e;padding:30px 40px;text-align:center;">
                    <h1 style="color:#ffffff;margin:0;font-size:22px;">Paiement sécurisé — %s</h1>
                </td></tr>

                <!-- BODY — identique au template WhatsApp -->
                <tr><td style="padding:30px 40px;">
                    <p style="font-size:16px;color:#333;margin-top:0;">%s</p>

                    <p style="font-size:16px;color:#333;">
                        Vous avez conditionné votre paiement de <strong>%s</strong>
                    </p>

                    <table width="100%%" cellpadding="12" cellspacing="0" style="background-color:#f8f9fa;border-radius:8px;margin:20px 0;">
                        <tr>
                            <td style="font-size:14px;color:#666;border-bottom:1px solid #e9ecef;">📋 <strong>Référence</strong></td>
                            <td style="font-size:14px;color:#333;border-bottom:1px solid #e9ecef;text-align:right;"><strong>%s</strong></td>
                        </tr>
                        <tr>
                            <td style="font-size:14px;color:#666;border-bottom:1px solid #e9ecef;">🏢 <strong>Prestataire</strong></td>
                            <td style="font-size:14px;color:#333;border-bottom:1px solid #e9ecef;text-align:right;">%s</td>
                        </tr>
                        <tr>
                            <td style="font-size:14px;color:#666;">📅 <strong>Date de service</strong></td>
                            <td style="font-size:14px;color:#333;text-align:right;">%s</td>
                        </tr>
                    </table>

                    <p style="font-size:16px;color:#333;">Présentez votre QR code à votre arrivée pour valider le service.</p>

                    <table width="100%%" cellpadding="0" cellspacing="0" style="margin:25px 0;">
                    <tr><td align="center">
                        <a href="%s" style="display:inline-block;background-color:#25D366;color:#ffffff;text-decoration:none;padding:14px 40px;border-radius:6px;font-size:16px;font-weight:bold;">
                            📱 Voir mon QR code
                        </a>
                    </td></tr>
                    </table>

                    <p style="font-size:14px;color:#888;margin-top:20px;">
                        💡 Téléchargez Akuunda Pay pour recevoir automatiquement vos fonds en cas d'annulation.
                    </p>
                </td></tr>

                <!-- FOOTER -->
                <tr><td style="background-color:#f8f9fa;padding:20px 40px;text-align:center;border-top:1px solid #e9ecef;">
                    <p style="font-size:12px;color:#999;margin:0;">Akuunda Pay — Paiements sécurisés<br>Cet email a été envoyé automatiquement, merci de ne pas y répondre.</p>
                </td></tr>

                </table>
                </td></tr></table>
                </body></html>
                """.formatted(amount, greeting, amount, paymentCode, vendorName, serviceDate, qrCodeUrl);
    }
}
