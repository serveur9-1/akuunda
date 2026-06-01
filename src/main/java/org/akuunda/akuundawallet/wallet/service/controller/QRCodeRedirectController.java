package org.akuunda.akuundawallet.wallet.service.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.entities.QRCode;
import org.akuunda.akuundawallet.wallet.service.QRCodeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@CrossOrigin("*")
@RequiredArgsConstructor
public class QRCodeRedirectController {

    private final QRCodeService qrCodeService;

    @Value("${akuunda.mobile.deeplink-scheme:akuundapay}")
    private String deeplinkScheme;

    @Value("${akuunda.mobile.fallback-url:https://akuunda-pay.io}")
    private String fallbackUrl;

    /**
     * Intercepte le clic sur le lien QR code reçu par WhatsApp/SMS/email.
     * URL: GET /qr/validate/{token}
     *
     * - Si le token est valide → redirige vers l'app mobile via deep link
     * - Si le token est invalide/expiré → affiche une page d'erreur HTML
     */
    @GetMapping(value = "/qr/validate/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> handleQRCodeLink(@PathVariable String token) {
        log.info("🔵 Clic sur lien QR code: token={}", token);

        QRCode qrCode = qrCodeService.getQRCodeByToken(token);

        if (qrCode == null) {
            log.warn("❌ QR code non trouvé: token={}", token);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(buildErrorPage("QR Code invalide",
                            "Ce QR code n'existe pas ou a été supprimé."));
        }

        if ("expired".equals(qrCode.getStatus())) {
            log.warn("❌ QR code expiré: token={}", token);
            return ResponseEntity.ok(buildErrorPage("QR Code expiré",
                    "Ce QR code a expiré. Veuillez demander un nouveau lien au marchand."));
        }

        if ("scanned".equals(qrCode.getStatus())) {
            log.warn("⚠️ QR code déjà scanné: token={}", token);
            return ResponseEntity.ok(buildErrorPage("QR Code déjà utilisé",
                    "Ce QR code a déjà été scanné et validé."));
        }

        // Deep link vers l'app mobile : akuundapay://qr/validate?token=qr-xxx
        String deepLink = deeplinkScheme + "://qr/validate?token=" + token;
        // Fallback web si l'app n'est pas installée
        String webFallback = fallbackUrl + "/qr/validate?token=" + token;

        log.info("✅ Redirection vers deep link: {}", deepLink);

        String html = buildRedirectPage(token, deepLink, webFallback);
        return ResponseEntity.ok(html);
    }

    private String buildRedirectPage(String token, String deepLink, String webFallback) {
        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Akuunda Pay - Validation QR Code</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        background: linear-gradient(135deg, #4B0056 0%%, #7B1FA2 100%%);
                        min-height: 100vh;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                        color: white;
                    }
                    .card {
                        background: white;
                        border-radius: 24px;
                        padding: 40px 32px;
                        max-width: 400px;
                        width: 90%%;
                        text-align: center;
                        box-shadow: 0 20px 60px rgba(0,0,0,0.3);
                    }
                    .logo { font-size: 48px; margin-bottom: 16px; }
                    h1 { color: #4B0056; font-size: 22px; margin-bottom: 12px; }
                    p { color: #666; font-size: 15px; margin-bottom: 24px; line-height: 1.5; }
                    .btn {
                        display: inline-block;
                        background: #F88809;
                        color: white;
                        padding: 14px 40px;
                        border-radius: 12px;
                        text-decoration: none;
                        font-weight: 600;
                        font-size: 16px;
                        transition: transform 0.2s;
                    }
                    .btn:hover { transform: scale(1.05); }
                    .spinner {
                        border: 3px solid #eee;
                        border-top: 3px solid #F88809;
                        border-radius: 50%%;
                        width: 32px;
                        height: 32px;
                        animation: spin 1s linear infinite;
                        margin: 0 auto 16px;
                    }
                    @keyframes spin { to { transform: rotate(360deg); } }
                    .token { color: #999; font-size: 11px; margin-top: 20px; word-break: break-all; }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="logo">🔐</div>
                    <div class="spinner" id="spinner"></div>
                    <h1>Validation du paiement</h1>
                    <p>Ouverture de l'application Akuunda Pay...</p>
                    <a href="%s" class="btn" id="openApp">Ouvrir l'application</a>
                    <p class="token">Token: %s</p>
                </div>
                <script>
                    // Tenter d'ouvrir l'app automatiquement
                    setTimeout(function() {
                        window.location.href = "%s";
                    }, 500);

                    // Si l'app ne s'ouvre pas après 3 secondes, cacher le spinner
                    setTimeout(function() {
                        var spinner = document.getElementById('spinner');
                        if (spinner) spinner.style.display = 'none';
                    }, 3000);
                </script>
            </body>
            </html>
            """.formatted(deepLink, token, deepLink);
    }

    private String buildErrorPage(String title, String message) {
        return """
            <!DOCTYPE html>
            <html lang="fr">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>Akuunda Pay - %s</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                        background: linear-gradient(135deg, #4B0056 0%%, #7B1FA2 100%%);
                        min-height: 100vh;
                        display: flex;
                        align-items: center;
                        justify-content: center;
                    }
                    .card {
                        background: white;
                        border-radius: 24px;
                        padding: 40px 32px;
                        max-width: 400px;
                        width: 90%%;
                        text-align: center;
                        box-shadow: 0 20px 60px rgba(0,0,0,0.3);
                    }
                    .icon { font-size: 48px; margin-bottom: 16px; }
                    h1 { color: #D32F2F; font-size: 20px; margin-bottom: 12px; }
                    p { color: #666; font-size: 15px; line-height: 1.5; }
                </style>
            </head>
            <body>
                <div class="card">
                    <div class="icon">❌</div>
                    <h1>%s</h1>
                    <p>%s</p>
                </div>
            </body>
            </html>
            """.formatted(title, title, message);
    }
}
