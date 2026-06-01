package org.akuunda.akuundawallet.common.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.dto.PushNotificationRequest;
import org.akuunda.akuundawallet.common.enums.NotificationType;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Helper pour envoyer des notifications push métier.
 * Les messages sont envoyés dans la langue préférée de l'utilisateur (fr/en).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TransactionNotificationHelper {

    private final FcmNotificationService fcmNotificationService;
    private final UserRepository userRepository;

    // ── Résolution de la langue ──────────────────────────────��──────────────

    /**
     * Récupère la langue préférée de l'utilisateur. Retourne "fr" par défaut.
     */
    private String resolveLanguage(String username) {
        try {
            Users user = userRepository.getUsersByUsername(username);
            if (user != null && user.getPreferredLanguage() != null
                    && !user.getPreferredLanguage().isBlank()) {
                return user.getPreferredLanguage().toLowerCase();
            }
        } catch (Exception e) {
            log.warn("⚠️ Impossible de résoudre la langue pour {}: {}", username, e.getMessage());
        }
        return "fr";
    }

    /**
     * Récupère le nom complet d'un utilisateur pour l'affichage dans les notifications.
     * Retourne le username si le nom n'est pas trouvé.
     */
    private String resolveDisplayName(String username) {
        try {
            Users user = userRepository.getUsersByUsername(username);
            if (user != null) {
                String firstName = user.getFirstname() != null ? user.getFirstname() : "";
                String lastName = user.getLastname() != null ? user.getLastname() : "";
                String fullName = (firstName + " " + lastName).trim();
                if (!fullName.isEmpty()) {
                    return fullName;
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ Impossible de résoudre le nom pour {}: {}", username, e.getMessage());
        }
        return username;
    }

    private boolean isFrench(String lang) {
        return "fr".equals(lang);
    }

    // ── Transactions ────────────────────────────────────────────────────────

    /**
     * Notification : transaction réussie (générique).
     */
    public void notifyTransactionSuccess(String username, String amount, String transactionId) {
        String lang = resolveLanguage(username);
        fcmNotificationService.sendToUser(username,
                PushNotificationRequest.builder()
                        .title(isFrench(lang)
                                ? "✅ Transaction réussie"
                                : "✅ Successful transaction")
                        .body(isFrench(lang)
                                ? "Votre transaction de " + amount + " a été effectuée avec succès."
                                : "Your transaction of " + amount + " has been completed successfully.")
                        .type(NotificationType.TRANSACTION_SUCCESS)
                        .referenceId(transactionId)
                        .extraData(Map.of("amount", amount))
                        .build()
        );
    }

    /**
     * Notification : transfert envoyé (pour l'envoyeur).
     */
    public void notifyTransferSent(String senderUsername, String receiverUsername,
                                    String amount, String transactionId) {
        String lang = resolveLanguage(senderUsername);
        String receiverName = resolveDisplayName(receiverUsername);
        fcmNotificationService.sendToUser(senderUsername,
                PushNotificationRequest.builder()
                        .title(isFrench(lang)
                                ? "✅ Transfert envoyé"
                                : "✅ Transfer sent")
                        .body(isFrench(lang)
                                ? "Votre transfert de " + amount + " vers " + receiverName + " a été effectué avec succès."
                                : "Your transfer of " + amount + " to " + receiverName + " has been completed successfully.")
                        .type(NotificationType.TRANSACTION_SUCCESS)
                        .referenceId(transactionId)
                        .extraData(Map.of("amount", amount, "receiver", receiverName))
                        .build()
        );
    }

    /**
     * Notification : transfert reçu (pour le receveur).
     */
    public void notifyTransferReceived(String receiverUsername, String senderUsername,
                                        String amount, String transactionId) {
        String lang = resolveLanguage(receiverUsername);
        String senderName = resolveDisplayName(senderUsername);
        fcmNotificationService.sendToUser(receiverUsername,
                PushNotificationRequest.builder()
                        .title(isFrench(lang)
                                ? "💰 Transfert reçu"
                                : "💰 Transfer received")
                        .body(isFrench(lang)
                                ? "Vous avez reçu " + amount + " de " + senderName + "."
                                : "You received " + amount + " from " + senderName + ".")
                        .type(NotificationType.TRANSACTION_SUCCESS)
                        .referenceId(transactionId)
                        .extraData(Map.of("amount", amount, "sender", senderName))
                        .build()
        );
    }

    /**
     * Notification : transaction échouée.
     */
    public void notifyTransactionFailed(String username, String amount, String transactionId, String reason) {
        String lang = resolveLanguage(username);
        fcmNotificationService.sendToUser(username,
                PushNotificationRequest.builder()
                        .title(isFrench(lang)
                                ? "❌ Transaction échouée"
                                : "❌ Transaction failed")
                        .body(isFrench(lang)
                                ? "Votre transaction de " + amount + " a échoué. " + reason
                                : "Your transaction of " + amount + " has failed. " + reason)
                        .type(NotificationType.TRANSACTION_FAILED)
                        .referenceId(transactionId)
                        .extraData(Map.of("amount", amount, "reason", reason))
                        .build()
        );
    }

    /**
     * Notification : transaction en cours (pending).
     */
    public void notifyTransactionPending(String username, String amount, String transactionId) {
        String lang = resolveLanguage(username);
        fcmNotificationService.sendToUser(username,
                PushNotificationRequest.builder()
                        .title(isFrench(lang)
                                ? "⏳ Transaction en cours"
                                : "⏳ Transaction pending")
                        .body(isFrench(lang)
                                ? "Votre transaction de " + amount + " est en cours de traitement."
                                : "Your transaction of " + amount + " is being processed.")
                        .type(NotificationType.TRANSACTION_PENDING)
                        .referenceId(transactionId)
                        .extraData(Map.of("amount", amount))
                        .build()
        );
    }

    // ── Paiement QR ─────────────────────────────────────────────────────────

    /**
     * Notification : paiement QR reçu (le vendeur a scanné le QR du client).
     */
    public void notifyQrPaymentReceived(String username, String amount, String payerName, String transactionId) {
        String lang = resolveLanguage(username);
        fcmNotificationService.sendToUser(username,
                PushNotificationRequest.builder()
                        .title(isFrench(lang)
                                ? "💰 Paiement QR reçu"
                                : "💰 QR payment received")
                        .body(isFrench(lang)
                                ? "Vous avez reçu " + amount + " de " + payerName
                                : "You received " + amount + " from " + payerName)
                        .type(NotificationType.QR_PAYMENT_RECEIVED)
                        .referenceId(transactionId)
                        .extraData(Map.of("amount", amount, "payer", payerName))
                        .build()
        );
    }

    // ── Escrow (Paiements conditionnels) ─────────────────────────────────

    /**
     * Notification : escrow créé.
     */
    public void notifyEscrowCreated(String username, String amount, String escrowId, String vendorName) {
        String lang = resolveLanguage(username);
        fcmNotificationService.sendToUser(username,
                PushNotificationRequest.builder()
                        .title(isFrench(lang)
                                ? "📋 Paiement conditionnel créé"
                                : "📋 Conditional payment created")
                        .body(isFrench(lang)
                                ? "Escrow de " + amount + " créé pour " + vendorName
                                : "Escrow of " + amount + " created for " + vendorName)
                        .type(NotificationType.ESCROW_CREATED)
                        .referenceId(escrowId)
                        .extraData(Map.of("amount", amount, "vendor", vendorName))
                        .build()
        );
    }

    /**
     * Notification : escrow financé — pour le CLIENT (payeur).
     */
    public void notifyEscrowFunded(String username, String amount, String escrowId, String vendorName) {
        String lang = resolveLanguage(username);
        fcmNotificationService.sendToUser(username,
                PushNotificationRequest.builder()
                        .title(isFrench(lang)
                                ? "🔒 Fonds sécurisés"
                                : "🔒 Funds secured")
                        .body(isFrench(lang)
                                ? amount + " sécurisés pour " + vendorName + ". Présentez votre QR code."
                                : amount + " secured for " + vendorName + ". Present your QR code.")
                        .type(NotificationType.ESCROW_FUNDED)
                        .referenceId(escrowId)
                        .extraData(Map.of("amount", amount, "vendor", vendorName))
                        .build()
        );
    }

    /**
     * Notification : escrow financé — pour le VENDOR (demandeur/prestataire).
     */
    public void notifyVendorEscrowFunded(String vendorUsername, String amount, String escrowId, String clientName) {
        String lang = resolveLanguage(vendorUsername);
        fcmNotificationService.sendToUser(vendorUsername,
                PushNotificationRequest.builder()
                        .title(isFrench(lang)
                                ? "💰 Paiement reçu — Fonds conditionnés"
                                : "💰 Payment received — Funds held")
                        .body(isFrench(lang)
                                ? clientName + " a effectué un paiement de " + amount
                                    + ". Les fonds sont conditionnés en attente de validation."
                                : clientName + " made a payment of " + amount
                                    + ". Funds are held pending validation.")
                        .type(NotificationType.ESCROW_FUNDED)
                        .referenceId(escrowId)
                        .extraData(Map.of("amount", amount, "client", clientName))
                        .build()
        );
    }

    /**
     * Notification : escrow libéré (service validé, fonds transférés au vendeur).
     */
    public void notifyEscrowReleased(String username, String amount, String escrowId) {
        String lang = resolveLanguage(username);
        fcmNotificationService.sendToUser(username,
                PushNotificationRequest.builder()
                        .title(isFrench(lang)
                                ? "✅ Paiement libéré"
                                : "✅ Payment released")
                        .body(isFrench(lang)
                                ? "Le paiement de " + amount + " a été libéré au prestataire."
                                : "The payment of " + amount + " has been released to the provider.")
                        .type(NotificationType.ESCROW_RELEASED)
                        .referenceId(escrowId)
                        .extraData(Map.of("amount", amount))
                        .build()
        );
    }

    /**
     * Notification : escrow remboursé.
     */
    public void notifyEscrowRefunded(String username, String amount, String escrowId) {
        String lang = resolveLanguage(username);
        fcmNotificationService.sendToUser(username,
                PushNotificationRequest.builder()
                        .title(isFrench(lang)
                                ? "💸 Remboursement effectué"
                                : "💸 Refund completed")
                        .body(isFrench(lang)
                                ? "Vous avez été remboursé de " + amount + "."
                                : "You have been refunded " + amount + ".")
                        .type(NotificationType.ESCROW_REFUNDED)
                        .referenceId(escrowId)
                        .extraData(Map.of("amount", amount))
                        .build()
        );
    }

    // ── Sécurité ────────────────────────────────────────────────────────────

    /**
     * Notification : alerte de sécurité (connexion suspecte, etc.).
     */
    public void notifySecurityAlert(String username, String alertMessage) {
        String lang = resolveLanguage(username);
        fcmNotificationService.sendToUser(username,
                PushNotificationRequest.builder()
                        .title(isFrench(lang)
                                ? "🔐 Alerte de sécurité"
                                : "🔐 Security alert")
                        .body(alertMessage)
                        .type(NotificationType.SECURITY_ALERT)
                        .build()
        );
    }
        // ── Wallet Deposit / Withdrawal (détection on-chain) ────────────────────

    /**
     * Notification : dépôt détecté sur le wallet de l'utilisateur.
     */
    public void notifyWalletDeposit(String username, String amount, String currency) {
        String lang = resolveLanguage(username);
        fcmNotificationService.sendToUser(username,
                PushNotificationRequest.builder()
                        .title(isFrench(lang)
                                ? "💰 Dépôt reçu"
                                : "💰 Deposit received")
                        .body(isFrench(lang)
                                ? "Votre wallet a reçu " + amount + " " + currency + "."
                                : "Your wallet received " + amount + " " + currency + ".")
                        .type(NotificationType.WALLET_DEPOSIT)
                        .extraData(Map.of("amount", amount, "currency", currency))
                        .build()
        );
    }

    /**
     * Notification : retrait détecté sur le wallet de l'utilisateur.
     */
    public void notifyWalletWithdrawal(String username, String amount, String currency) {
        String lang = resolveLanguage(username);
        fcmNotificationService.sendToUser(username,
                PushNotificationRequest.builder()
                        .title(isFrench(lang)
                                ? "📤 Retrait effectué"
                                : "📤 Withdrawal detected")
                        .body(isFrench(lang)
                                ? amount + " " + currency + " ont été retirés de votre wallet."
                                : amount + " " + currency + " were withdrawn from your wallet.")
                        .type(NotificationType.WALLET_WITHDRAWAL)
                        .extraData(Map.of("amount", amount, "currency", currency))
                        .build()
        );
    }
}
