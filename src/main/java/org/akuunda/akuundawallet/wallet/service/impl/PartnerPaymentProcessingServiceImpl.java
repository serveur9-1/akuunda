package org.akuunda.akuundawallet.wallet.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.dto.PushNotificationRequest;
import org.akuunda.akuundawallet.common.enums.NotificationType;
import org.akuunda.akuundawallet.common.service.FcmNotificationService;
import org.akuunda.akuundawallet.wallet.api.dao.PartnerContractPaymentRepository;
import org.akuunda.akuundawallet.wallet.api.entities.PartnerContractPayment;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.wallet.service.PartnerPaymentProcessingService;
import org.akuunda.akuundawallet.wallet.service.PaymentFactoryContractService;
import org.akuunda.akuundawallet.wallet.service.SmartContractEscrowService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerPaymentProcessingServiceImpl implements PartnerPaymentProcessingService {

    private final PartnerContractPaymentRepository paymentRepository;
    private final PaymentFactoryContractService paymentFactoryContractService;
    private final SmartContractEscrowService escrowService;
    private final FcmNotificationService fcmNotificationService;

    @Value("${akuunda.escrow.contract.wallet.address:}")
    private String escrowContractAddress;

    @Value("${akuunda.admin.username:akuunda-admin}")
    private String defaultAdminUsername;

    @Override
    @Transactional
    public void processPayment(PartnerContractPayment payment, Wallet serviceWallet) {
        String onChainPaymentId = payment.getOnChainPaymentId();
        String paymentCode      = payment.getPaymentCode();

        // Étape 1 : vérifier si le client a envoyé ses USDC sur le CREATE2
        boolean received = paymentFactoryContractService.isPaymentReceived(onChainPaymentId);
        if (!received) {
            log.debug("CREATE2 pas encore reçu: paymentCode={}", paymentCode);
            return;
        }

        log.info("USDC reçus sur CREATE2 → transfert vers contrat conditionnel: paymentCode={}", paymentCode);
        payment.setCreate2Status("payment_received");
        paymentRepository.save(payment);

        // Étape 2 : executePayment() → déplace USDC du CREATE2 vers le wallet intermédiaire
        String execTxHash = paymentFactoryContractService.executePayment(onChainPaymentId, serviceWallet);
        if (execTxHash == null || execTxHash.startsWith("PAYMENT-FAILED")) {
            log.error("Échec executePayment: paymentCode={}", paymentCode);
            return;
        }
        log.info("executePayment OK: paymentCode={}, txHash={}", paymentCode, execTxHash);

        // Étape 3a : DYNAMIC_ASSIGNMENT — fonds dans wallet intermédiaire, notifier l'admin
        if ("DYNAMIC_ASSIGNMENT".equals(payment.getContract().getTriggerType())) {
            try {
                fcmNotificationService.sendToUser(defaultAdminUsername,
                        PushNotificationRequest.builder()
                                .title("Assignation requise")
                                .body("Paiement " + paymentCode + " reçu (" + payment.getAmount() + " USDC). "
                                        + "Assignez les professionnels via l'API.")
                                .type(NotificationType.PAYMENT_PROFESSIONAL_ASSIGNMENT_NEEDED)
                                .referenceId(paymentCode)
                                .build());
            } catch (Exception e) {
                log.warn("Échec notification admin DYNAMIC_ASSIGNMENT: paymentCode={}, err={}",
                        paymentCode, e.getMessage());
            }
            log.info("DYNAMIC_ASSIGNMENT: fonds dans wallet intermédiaire, admin notifié: paymentCode={}",
                    paymentCode);
            return;
        }

        // Étape 3b : approve() + deposit() → intermédiaire → AkuundaConditionalPayment (modes standards)
        String depositTxHash = escrowService.approveAndDeposit(
                escrowContractAddress,
                onChainPaymentId,
                payment.getContract().getOnChainConfigId(),
                payment.getAmount(),
                serviceWallet,
                null // servicePin géré dans SmartContractEscrowServiceImpl via @Value
        );

        if (depositTxHash == null || depositTxHash.startsWith("DEPOSIT-FAILED")) {
            log.error("Échec deposit dans contrat conditionnel: paymentCode={}", paymentCode);
            return;
        }

        payment.setDepositTxHash(depositTxHash);
        payment.setEscrowWalletId(escrowContractAddress);
        payment.setCreate2Status("deposited");
        paymentRepository.save(payment);

        log.info("Fonds verrouillés dans AkuundaConditionalPayment: paymentCode={}, txHash={}",
                paymentCode, depositTxHash);
    }
}
