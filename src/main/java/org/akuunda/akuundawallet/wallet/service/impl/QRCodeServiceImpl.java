package org.akuunda.akuundawallet.wallet.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dao.ConditionalPaymentRepository;
import org.akuunda.akuundawallet.wallet.api.dao.QRCodeRepository;
import org.akuunda.akuundawallet.wallet.api.entities.ConditionalPayment;
import org.akuunda.akuundawallet.wallet.api.entities.QRCode;
import org.akuunda.akuundawallet.wallet.service.QRCodeService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
public class QRCodeServiceImpl implements QRCodeService {

    private final QRCodeRepository qrCodeRepository;
    private final ConditionalPaymentRepository conditionalPaymentRepository;

    @Value("${akuunda.qrcode.base-url:https://qr.akuunda-pay.io/qr/validate}")
    private String qrCodeBaseUrl;

    @Value("${akuunda.qrcode.default-expiration-hours:24}")
    private Integer defaultExpirationHours;

    public QRCodeServiceImpl(QRCodeRepository qrCodeRepository, 
                            ConditionalPaymentRepository conditionalPaymentRepository) {
        this.qrCodeRepository = qrCodeRepository;
        this.conditionalPaymentRepository = conditionalPaymentRepository;
    }

    @Override
    @Transactional
    public QRCode generateQRCode(Long conditionalPaymentId, Integer expiresInHours) {
        log.info("🔵 Génération d'un QR code pour le paiement conditionnel: paymentId={}", conditionalPaymentId);

        ConditionalPayment payment = conditionalPaymentRepository.findById(conditionalPaymentId)
                .orElseThrow(() -> new IllegalArgumentException("Paiement conditionnel non trouvé: " + conditionalPaymentId));

        // Générer un token unique
        String token = "qr-" + UUID.randomUUID().toString().replace("-", "");

        // Construire l'URL du QR code
        String qrCodeUrl = qrCodeBaseUrl + "/" + token;

        // Calculer la date d'expiration
        LocalDateTime expiresAt = null;
        if (expiresInHours != null && expiresInHours > 0) {
            expiresAt = LocalDateTime.now().plusHours(expiresInHours);
        } else if (defaultExpirationHours != null && defaultExpirationHours > 0) {
            expiresAt = LocalDateTime.now().plusHours(defaultExpirationHours);
        }

        QRCode qrCode = QRCode.builder()
                .token(token)
                .conditionalPayment(payment)
                .qrCodeUrl(qrCodeUrl)
                .status("generated")
                .expiresAt(expiresAt)
                .build();

        qrCode = qrCodeRepository.save(qrCode);

        log.info("✅ QR code généré avec succès: token={}, url={}", token, qrCodeUrl);
        return qrCode;
    }

    @Override
    @Transactional
    public QRCode validateQRCode(String qrCodeToken, String scannedBy) {
        log.info("🔵 Validation du QR code: token={}, scannedBy={}", qrCodeToken, scannedBy);

        QRCode qrCode = qrCodeRepository.findByToken(qrCodeToken)
                .orElseThrow(() -> new IllegalArgumentException("QR code non trouvé: " + qrCodeToken));

        // Vérifier que le QR code n'a pas déjà été scanné
        if ("scanned".equals(qrCode.getStatus())) {
            throw new IllegalStateException("Ce QR code a déjà été scanné");
        }

        // Vérifier l'expiration
        if (qrCode.getExpiresAt() != null && qrCode.getExpiresAt().isBefore(LocalDateTime.now())) {
            qrCode.setStatus("expired");
            qrCodeRepository.save(qrCode);
            throw new IllegalStateException("Ce QR code a expiré");
        }

        // Marquer comme scanné
        qrCode.setStatus("scanned");
        qrCode.setScannedAt(LocalDateTime.now());
        qrCode.setScannedBy(scannedBy);
        qrCode = qrCodeRepository.save(qrCode);

        log.info("✅ QR code validé avec succès: token={}", qrCodeToken);
        return qrCode;
    }

    @Override
    public QRCode getQRCodeByToken(String token) {
        return qrCodeRepository.findByToken(token).orElse(null);
    }

    @Override
    public QRCode getQRCodeByPaymentId(Long conditionalPaymentId) {
        ConditionalPayment payment = conditionalPaymentRepository.findById(conditionalPaymentId)
                .orElse(null);
        if (payment == null) {
            return null;
        }
        return qrCodeRepository.findByConditionalPayment(payment).orElse(null);
    }
}

