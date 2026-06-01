package org.akuunda.akuundawallet.common.enums;

import lombok.Getter;

@Getter
public enum NotificationType {

    // ── Transactions ────────────────────────────────
    TRANSACTION_SUCCESS("transaction_success", "Transaction réussie"),
    TRANSACTION_FAILED("transaction_failed", "Transaction échouée"),
    TRANSACTION_PENDING("transaction_pending", "Transaction en cours"),

    // ── Wallet (dépôt/retrait détecté on-chain) ─────
    WALLET_DEPOSIT("wallet_deposit", "Dépôt détecté"),
    WALLET_WITHDRAWAL("wallet_withdrawal", "Retrait détecté"),

    // ── Paiement QR ─────────────────────────────────
    QR_PAYMENT_RECEIVED("qr_payment_received", "Paiement QR reçu"),

    // ── Escrow (Paiements conditionnels) ────────────
    ESCROW_CREATED("escrow_created", "Escrow créé"),
    ESCROW_FUNDED("escrow_funded", "Escrow financé"),
    ESCROW_RELEASED("escrow_released", "Escrow libéré"),
    ESCROW_REFUNDED("escrow_refunded", "Escrow remboursé"),

    // ── Sécurité ────────────────────────────────────
    SECURITY_ALERT("security_alert", "Alerte de sécurité"),

    // ── App Updates ─────────────────────────────────
    NEW_FEATURE("new_feature", "Nouvelle fonctionnalité"),
    APP_UPDATE("app_update", "Mise à jour disponible"),

    // ── Libération des fonds (modes partenaire) ──────
    PAYMENT_CONFIRMATION_REQUESTED("payment_confirmation_requested", "Confirmation de réception requise"),
    PAYMENT_OTP_SENT("payment_otp_sent", "Code OTP envoyé"),
    PAYMENT_DUAL_CONFIRM_PENDING("payment_dual_confirm_pending", "En attente de confirmation mutuelle"),
    PAYMENT_AUTO_RELEASE_PENDING("payment_auto_release_pending", "Libération automatique programmée"),
    PAYMENT_ADMIN_APPROVAL_NEEDED("payment_admin_approval_needed", "Approbation admin requise"),
    PAYMENT_DISPUTED("payment_disputed", "Paiement contesté"),
    PAYMENT_LOCATION_CONFIRM_NEEDED("payment_location_confirm_needed", "Confirmation de localisation requise"),
    PAYMENT_PROFESSIONAL_ASSIGNMENT_NEEDED("payment_professional_assignment_needed", "Assignation du professionnel requise"),
    PAYMENT_PROFESSIONAL_ASSIGNED("payment_professional_assigned", "Professionnel assigné — confirmation requise");

    private final String type;
    private final String label;

    NotificationType(String type, String label) {
        this.type = type;
        this.label = label;
    }
}
