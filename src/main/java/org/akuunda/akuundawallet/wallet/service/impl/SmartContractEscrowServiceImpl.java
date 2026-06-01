package org.akuunda.akuundawallet.wallet.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.transfert.api.dto.external.TransactionResponseDto;
import org.akuunda.akuundawallet.transfert.impl.service.infrastructure.AkunndaTransferClientService;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.wallet.service.SmartContractEscrowService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Implémentation du service escrow via CONTRACT_EXECUTION Venly.
 * Backward-compat: les anciennes méthodes (depositToEscrow, releaseFunds, refundToClient, partialRefund)
 * sont conservées pour ConditionalPaymentServiceImpl et PaymentDepositPollingTask.
 * Nouvelles méthodes: registerConfig, approveAndDeposit, distribute, refund, distributeAndRefund
 * pour AkuundaConditionalPayment (partenaires API).
 */
@Slf4j
@Service
public class SmartContractEscrowServiceImpl implements SmartContractEscrowService {

    private final AkunndaTransferClientService transferClientService;

    @Value("${akuunda.escrow.service.pin:}")
    private String servicePin;

    @Value("${akuunda.escrow.token.address:0x3c499c542cEF5E3811e1192ce70d8cC03d5c3359}")
    private String usdcAddress;

    private static final String SECRET_TYPE        = "MATIC";
    private static final String CONTRACT_EXECUTION = "CONTRACT_EXECUTION";
    private static final long   USDC_DECIMALS      = 1_000_000L;

    public SmartContractEscrowServiceImpl(AkunndaTransferClientService transferClientService) {
        this.transferClientService = transferClientService;
    }

    // =========================================================================
    // MÉTHODES BACKWARD-COMPAT (ConditionalPaymentServiceImpl / PaymentDepositPollingTask)
    // =========================================================================

    @Override
    public String depositToEscrow(String escrowContractAddress, Double amount, Wallet clientWallet,
                                  Wallet intermediateWallet, String smartContractWalletAddress, String clientPin,
                                  String paymentCode, String vendorAddress, Long serviceStartDate) {
        String contractAddress = smartContractWalletAddress != null ? smartContractWalletAddress : escrowContractAddress;
        log.info("depositToEscrow: client={}, contract={}, amount={}, paymentCode={}",
                clientWallet != null ? clientWallet.getAddress() : "N/A", contractAddress, amount, paymentCode);

        if (clientWallet == null || contractAddress == null || contractAddress.length() < 42) {
            log.warn("Wallet client ou adresse smart contract manquante, enregistrement en base uniquement");
            return "ESCROW-DEPOSIT-DB-" + System.currentTimeMillis();
        }

        if (clientPin == null || clientPin.isEmpty()) {
            return "ESCROW-DEPOSIT-DB-" + System.currentTimeMillis();
        }

        if (paymentCode == null || vendorAddress == null || serviceStartDate == null) {
            log.error("Paramètres manquants: paymentCode={}, vendorAddress={}, serviceStartDate={}",
                    paymentCode, vendorAddress, serviceStartDate);
            return "ESCROW-DEPOSIT-ERROR-" + System.currentTimeMillis();
        }

        try {
            String amountInWei = toUsdcWei(amount);
            String step1Body = String.format("""
                {
                  "transactionRequest": {
                    "type": "%s",
                    "walletId": "%s",
                    "to": "%s",
                    "secretType": "%s",
                    "functionName": "approve",
                    "value": 0,
                    "inputs": [
                      {"type": "address", "value": "%s"},
                      {"type": "uint256", "value": "%s"}
                    ],
                    "chainSpecificFields": {"gasLimit": "300000"}
                  }
                }
                """, CONTRACT_EXECUTION, clientWallet.getId(), usdcAddress, SECRET_TYPE, contractAddress, amountInWei);

            var step1Response = transferClientService.executeTransfert(step1Body, clientPin);
            if (step1Response == null || !step1Response.getStatusCode().is2xxSuccessful() || step1Response.getBody() == null) {
                log.error("Échec approve USDC: status={}", step1Response != null ? step1Response.getStatusCode() : "null");
                return "ESCROW-DEPOSIT-FAILED-" + System.currentTimeMillis();
            }

            String step2Body = String.format("""
                {
                  "transactionRequest": {
                    "type": "%s",
                    "walletId": "%s",
                    "to": "%s",
                    "secretType": "%s",
                    "functionName": "deposit",
                    "value": 0,
                    "inputs": [
                      {"type": "string", "value": "%s"},
                      {"type": "address", "value": "%s"},
                      {"type": "uint256", "value": "%s"},
                      {"type": "uint256", "value": "%s"}
                    ],
                    "chainSpecificFields": {"gasLimit": "500000"}
                  }
                }
                """, CONTRACT_EXECUTION, clientWallet.getId(), contractAddress, SECRET_TYPE,
                    paymentCode, vendorAddress, amountInWei, serviceStartDate);

            var step2Response = transferClientService.executeTransfert(step2Body, clientPin);
            if (step2Response == null || !step2Response.getStatusCode().is2xxSuccessful() || step2Response.getBody() == null) {
                log.error("Échec deposit smart contract: status={}", step2Response != null ? step2Response.getStatusCode() : "null");
                return "ESCROW-DEPOSIT-FAILED-" + System.currentTimeMillis();
            }
            String txHash = step2Response.getBody().getResult().getTransactionHash();
            log.info("Deposit OK: txHash={}", txHash);
            return txHash;
        } catch (Exception e) {
            log.error("Erreur depositToEscrow", e);
            return "ESCROW-DEPOSIT-ERROR-" + System.currentTimeMillis();
        }
    }

    @Override
    public String depositToEscrowStep2Only(String escrowContractAddress, Double amount, Wallet intermediateWallet,
                                           String paymentCode, String vendorAddress, Long serviceStartDate,
                                           Wallet clientWallet, String clientPin) {
        log.info("depositToEscrowStep2Only (retry): client={}, contract={}, paymentCode={}",
                clientWallet != null ? clientWallet.getAddress() : "N/A", escrowContractAddress, paymentCode);

        if (clientWallet == null || escrowContractAddress == null || clientPin == null || clientPin.isEmpty()) {
            log.error("Paramètres manquants pour depositToEscrowStep2Only");
            return "ESCROW-DEPOSIT-FAILED-" + System.currentTimeMillis();
        }

        try {
            String amountInWei = toUsdcWei(amount);
            String step1Body = String.format("""
                {
                  "transactionRequest": {
                    "type": "%s",
                    "walletId": "%s",
                    "to": "%s",
                    "secretType": "%s",
                    "functionName": "approve",
                    "value": 0,
                    "inputs": [
                      {"type": "address", "value": "%s"},
                      {"type": "uint256", "value": "%s"}
                    ],
                    "chainSpecificFields": {"gasLimit": "300000"}
                  }
                }
                """, CONTRACT_EXECUTION, clientWallet.getId(), usdcAddress, SECRET_TYPE, escrowContractAddress, amountInWei);

            var step1Response = transferClientService.executeTransfert(step1Body, clientPin);
            if (step1Response == null || !step1Response.getStatusCode().is2xxSuccessful() || step1Response.getBody() == null) {
                log.error("Échec approve (retry): status={}", step1Response != null ? step1Response.getStatusCode() : "null");
                return "ESCROW-DEPOSIT-FAILED-" + System.currentTimeMillis();
            }

            String step2Body = String.format("""
                {
                  "transactionRequest": {
                    "type": "%s",
                    "walletId": "%s",
                    "to": "%s",
                    "secretType": "%s",
                    "functionName": "deposit",
                    "value": 0,
                    "inputs": [
                      {"type": "string", "value": "%s"},
                      {"type": "address", "value": "%s"},
                      {"type": "uint256", "value": "%s"},
                      {"type": "uint256", "value": "%s"}
                    ],
                    "chainSpecificFields": {"gasLimit": "500000"}
                  }
                }
                """, CONTRACT_EXECUTION, clientWallet.getId(), escrowContractAddress, SECRET_TYPE,
                    paymentCode, vendorAddress, amountInWei, serviceStartDate);

            var step2Response = transferClientService.executeTransfert(step2Body, clientPin);
            if (step2Response == null || !step2Response.getStatusCode().is2xxSuccessful() || step2Response.getBody() == null) {
                log.error("Échec deposit (retry): status={}", step2Response != null ? step2Response.getStatusCode() : "null");
                return "ESCROW-DEPOSIT-FAILED-" + System.currentTimeMillis();
            }
            String txHash = step2Response.getBody().getResult().getTransactionHash();
            log.info("Deposit (retry) OK: txHash={}", txHash);
            return txHash;
        } catch (Exception e) {
            log.error("Erreur depositToEscrowStep2Only", e);
            return "ESCROW-DEPOSIT-ERROR-" + System.currentTimeMillis();
        }
    }

    @Override
    public String releaseFunds(String escrowContractAddress, Double amount, String vendorWalletAddress,
                               String paymentCode, Wallet intermediateWallet) {
        return releaseFunds(escrowContractAddress, amount, vendorWalletAddress, paymentCode, intermediateWallet, null);
    }

    @Override
    public String releaseFunds(String escrowContractAddress, Double amount, String vendorWalletAddress,
                               String paymentCode, Wallet intermediateWallet, Wallet escrowWallet) {
        log.info("releaseFunds: paymentCode={}, contract={}", paymentCode, escrowContractAddress);

        if (intermediateWallet == null || escrowContractAddress == null || paymentCode == null) {
            log.error("Paramètres manquants pour releaseFunds");
            return "ESCROW-RELEASE-FAILED-" + System.currentTimeMillis();
        }

        try {
            String requestBody = String.format("""
                {
                  "transactionRequest": {
                    "type": "%s",
                    "walletId": "%s",
                    "to": "%s",
                    "secretType": "%s",
                    "functionName": "release",
                    "value": 0,
                    "inputs": [
                      {"type": "string", "value": "%s"}
                    ],
                    "chainSpecificFields": {"gasLimit": "300000"}
                  }
                }
                """, CONTRACT_EXECUTION, intermediateWallet.getId(), escrowContractAddress, SECRET_TYPE, paymentCode);

            var response = transferClientService.executeTransfert(requestBody, servicePin);
            if (response != null && response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                TransactionResponseDto tx = response.getBody();
                log.info("releaseFunds OK: txHash={}", tx.getResult().getTransactionHash());
                return tx.getResult().getTransactionHash();
            }
            log.error("releaseFunds KO: status={}", response != null ? response.getStatusCode() : "null");
            return "ESCROW-RELEASE-FAILED-" + System.currentTimeMillis();
        } catch (Exception e) {
            log.error("Erreur releaseFunds", e);
            return "ESCROW-RELEASE-ERROR-" + System.currentTimeMillis();
        }
    }

    @Override
    public String refundToClient(String escrowContractAddress, Double amount, String clientWalletAddress,
                                 String paymentCode, Wallet intermediateWallet) {
        return refundToClient(escrowContractAddress, amount, clientWalletAddress, paymentCode, intermediateWallet, null);
    }

    @Override
    public String refundToClient(String escrowContractAddress, Double amount, String clientWalletAddress,
                                 String paymentCode, Wallet intermediateWallet, Wallet escrowWallet) {
        log.info("refundToClient: paymentCode={}, contract={}", paymentCode, escrowContractAddress);

        if (intermediateWallet == null || escrowContractAddress == null || paymentCode == null) {
            log.error("Paramètres manquants pour refundToClient");
            return "ESCROW-REFUND-FAILED-" + System.currentTimeMillis();
        }

        try {
            String requestBody = String.format("""
                {
                  "transactionRequest": {
                    "type": "%s",
                    "walletId": "%s",
                    "to": "%s",
                    "secretType": "%s",
                    "functionName": "refund",
                    "value": 0,
                    "inputs": [
                      {"type": "string", "value": "%s"}
                    ],
                    "chainSpecificFields": {"gasLimit": "300000"}
                  }
                }
                """, CONTRACT_EXECUTION, intermediateWallet.getId(), escrowContractAddress, SECRET_TYPE, paymentCode);

            var response = transferClientService.executeTransfert(requestBody, servicePin);
            if (response != null && response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                TransactionResponseDto tx = response.getBody();
                log.info("refundToClient OK: txHash={}", tx.getResult().getTransactionHash());
                return tx.getResult().getTransactionHash();
            }
            log.error("refundToClient KO: status={}", response != null ? response.getStatusCode() : "null");
            return "ESCROW-REFUND-FAILED-" + System.currentTimeMillis();
        } catch (Exception e) {
            log.error("Erreur refundToClient", e);
            return "ESCROW-REFUND-ERROR-" + System.currentTimeMillis();
        }
    }

    @Override
    public String partialRefund(String escrowContractAddress, Double vendorAmount, Double clientAmount,
                                String vendorWalletAddress, String clientWalletAddress,
                                String paymentCode, Wallet intermediateWallet) {
        return partialRefund(escrowContractAddress, vendorAmount, clientAmount, vendorWalletAddress,
                clientWalletAddress, paymentCode, intermediateWallet, null);
    }

    @Override
    public String partialRefund(String escrowContractAddress, Double vendorAmount, Double clientAmount,
                                String vendorWalletAddress, String clientWalletAddress,
                                String paymentCode, Wallet intermediateWallet, Wallet escrowWallet) {
        log.info("partialRefund: paymentCode={}, vendorAmount={}, clientAmount={}", paymentCode, vendorAmount, clientAmount);

        if (intermediateWallet == null || escrowContractAddress == null || paymentCode == null) {
            log.error("Paramètres manquants pour partialRefund");
            return "ESCROW-PARTIAL-REFUND-FAILED-" + System.currentTimeMillis();
        }

        try {
            String requestBody = String.format("""
                {
                  "transactionRequest": {
                    "type": "%s",
                    "walletId": "%s",
                    "to": "%s",
                    "secretType": "%s",
                    "functionName": "partialRefund",
                    "value": 0,
                    "inputs": [
                      {"type": "string", "value": "%s"},
                      {"type": "uint256", "value": "%s"},
                      {"type": "uint256", "value": "%s"}
                    ],
                    "chainSpecificFields": {"gasLimit": "500000"}
                  }
                }
                """, CONTRACT_EXECUTION, intermediateWallet.getId(), escrowContractAddress, SECRET_TYPE,
                    paymentCode, toUsdcWei(vendorAmount), toUsdcWei(clientAmount));

            var response = transferClientService.executeTransfert(requestBody, servicePin);
            if (response != null && response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                TransactionResponseDto tx = response.getBody();
                log.info("partialRefund OK: txHash={}", tx.getResult().getTransactionHash());
                return tx.getResult().getTransactionHash();
            }
            log.error("partialRefund KO: status={}", response != null ? response.getStatusCode() : "null");
            return "ESCROW-PARTIAL-REFUND-FAILED-" + System.currentTimeMillis();
        } catch (Exception e) {
            log.error("Erreur partialRefund", e);
            return "ESCROW-PARTIAL-REFUND-ERROR-" + System.currentTimeMillis();
        }
    }

    // =========================================================================
    // MÉTHODES AKUUNDACONDITIONALPAYMENT (partenaires API)
    // =========================================================================

    @Override
    public String registerConfig(String contractAddress, String configId,
                                  List<String> beneficiaryAddrs, List<Integer> sharesBps,
                                  Wallet adminWallet) {
        log.info("registerConfig: contract={}, configId={}, beneficiaries={}",
                contractAddress, configId, beneficiaryAddrs.size());

        if (!isContractConfigured(contractAddress)) return null;

        String addrsJson  = toJsonArray(beneficiaryAddrs.stream()
                .map(a -> "\"" + a + "\"").collect(Collectors.joining(",")));
        String sharesJson = toJsonArray(sharesBps.stream()
                .map(s -> "\"" + s + "\"").collect(Collectors.joining(",")));

        String body = """
                {
                  "transactionRequest": {
                    "type": "%s",
                    "walletId": "%s",
                    "to": "%s",
                    "secretType": "%s",
                    "functionName": "registerConfig",
                    "value": 0,
                    "inputs": [
                      {"type": "bytes32",   "value": "%s"},
                      {"type": "address[]", "value": %s},
                      {"type": "uint16[]",  "value": %s}
                    ],
                    "chainSpecificFields": {"gasLimit": "500000"}
                  }
                }
                """.formatted(CONTRACT_EXECUTION, adminWallet.getId(), contractAddress,
                SECRET_TYPE, configId, addrsJson, sharesJson);

        return execute(body, servicePin, "registerConfig");
    }

    @Override
    public String grantRole(String contractAddress, String roleBytes32, String account, Wallet adminWallet) {
        log.info("grantRole: contract={}, role={}, account={}", contractAddress, roleBytes32, account);
        if (!isContractConfigured(contractAddress)) return null;

        String body = """
                {
                  "transactionRequest": {
                    "type": "%s",
                    "walletId": "%s",
                    "to": "%s",
                    "secretType": "%s",
                    "functionName": "grantRole",
                    "value": 0,
                    "inputs": [
                      {"type": "bytes32", "value": "%s"},
                      {"type": "address", "value": "%s"}
                    ],
                    "chainSpecificFields": {"gasLimit": "100000"}
                  }
                }
                """.formatted(CONTRACT_EXECUTION, adminWallet.getId(), contractAddress,
                SECRET_TYPE, roleBytes32, account);

        return execute(body, servicePin, "grantRole");
    }

    @Override
    public String deactivateConfig(String contractAddress, String configId, Wallet adminWallet) {
        log.info("deactivateConfig: contract={}, configId={}", contractAddress, configId);

        if (!isContractConfigured(contractAddress)) return null;

        String body = """
                {
                  "transactionRequest": {
                    "type": "%s",
                    "walletId": "%s",
                    "to": "%s",
                    "secretType": "%s",
                    "functionName": "deactivateConfig",
                    "value": 0,
                    "inputs": [
                      {"type": "bytes32", "value": "%s"}
                    ],
                    "chainSpecificFields": {"gasLimit": "100000"}
                  }
                }
                """.formatted(CONTRACT_EXECUTION, adminWallet.getId(), contractAddress,
                SECRET_TYPE, configId);

        return execute(body, servicePin, "deactivateConfig");
    }

    @Override
    public String approveAndDeposit(String contractAddress, String paymentId, String configId,
                                     Double amount, Wallet clientWallet, String clientPin) {
        log.info("approveAndDeposit: contract={}, paymentId={}, amount={}", contractAddress, paymentId, amount);

        if (!isContractConfigured(contractAddress)) {
            return "DEPOSIT-NO-CONTRACT-" + System.currentTimeMillis();
        }

        String amountWei = toUsdcWei(amount);
        String pin = (clientPin != null && !clientPin.isBlank()) ? clientPin : servicePin;

        String approveBody = """
                {
                  "transactionRequest": {
                    "type": "%s",
                    "walletId": "%s",
                    "to": "%s",
                    "secretType": "%s",
                    "functionName": "approve",
                    "value": 0,
                    "inputs": [
                      {"type": "address", "value": "%s"},
                      {"type": "uint256", "value": "%s"}
                    ],
                    "chainSpecificFields": {"gasLimit": "100000"}
                  }
                }
                """.formatted(CONTRACT_EXECUTION, clientWallet.getId(), usdcAddress,
                SECRET_TYPE, contractAddress, amountWei);

        String approveTx = execute(approveBody, pin, "approve(USDC)");
        if (approveTx == null) {
            log.error("Échec approve USDC: paymentId={}", paymentId);
            return "DEPOSIT-APPROVE-FAILED-" + System.currentTimeMillis();
        }

        String depositBody = """
                {
                  "transactionRequest": {
                    "type": "%s",
                    "walletId": "%s",
                    "to": "%s",
                    "secretType": "%s",
                    "functionName": "deposit",
                    "value": 0,
                    "inputs": [
                      {"type": "bytes32", "value": "%s"},
                      {"type": "bytes32", "value": "%s"},
                      {"type": "uint256", "value": "%s"}
                    ],
                    "chainSpecificFields": {"gasLimit": "200000"}
                  }
                }
                """.formatted(CONTRACT_EXECUTION, clientWallet.getId(), contractAddress,
                SECRET_TYPE, paymentId, configId, amountWei);

        String depositTx = execute(depositBody, pin, "deposit");
        if (depositTx == null) {
            return "DEPOSIT-FAILED-" + System.currentTimeMillis();
        }

        log.info("approveAndDeposit OK: paymentId={}, txHash={}", paymentId, depositTx);
        return depositTx;
    }

    @Override
    public String distribute(String contractAddress, String paymentId, Wallet relayerWallet) {
        log.info("distribute: contract={}, paymentId={}", contractAddress, paymentId);
        if (!isContractConfigured(contractAddress)) return "DISTRIBUTE-NO-CONTRACT-" + System.currentTimeMillis();

        String body = """
                {
                  "transactionRequest": {
                    "type": "%s",
                    "walletId": "%s",
                    "to": "%s",
                    "secretType": "%s",
                    "functionName": "distribute",
                    "value": 0,
                    "inputs": [{"type": "bytes32", "value": "%s"}],
                    "chainSpecificFields": {"gasLimit": "400000"}
                  }
                }
                """.formatted(CONTRACT_EXECUTION, relayerWallet.getId(), contractAddress, SECRET_TYPE, paymentId);

        return execute(body, servicePin, "distribute");
    }

    @Override
    public String refund(String contractAddress, String paymentId, Wallet relayerWallet) {
        log.info("refund(new): contract={}, paymentId={}", contractAddress, paymentId);
        if (!isContractConfigured(contractAddress)) return "REFUND-NO-CONTRACT-" + System.currentTimeMillis();

        String body = """
                {
                  "transactionRequest": {
                    "type": "%s",
                    "walletId": "%s",
                    "to": "%s",
                    "secretType": "%s",
                    "functionName": "refund",
                    "value": 0,
                    "inputs": [{"type": "bytes32", "value": "%s"}],
                    "chainSpecificFields": {"gasLimit": "150000"}
                  }
                }
                """.formatted(CONTRACT_EXECUTION, relayerWallet.getId(), contractAddress, SECRET_TYPE, paymentId);

        return execute(body, servicePin, "refund");
    }

    @Override
    public String distributeAndRefund(String contractAddress, String paymentId,
                                       int penaltyBps, Wallet relayerWallet) {
        log.info("distributeAndRefund: contract={}, paymentId={}, penaltyBps={}", contractAddress, paymentId, penaltyBps);
        if (!isContractConfigured(contractAddress)) return "DISTRIBUTE-REFUND-NO-CONTRACT-" + System.currentTimeMillis();

        String body = """
                {
                  "transactionRequest": {
                    "type": "%s",
                    "walletId": "%s",
                    "to": "%s",
                    "secretType": "%s",
                    "functionName": "distributeAndRefund",
                    "value": 0,
                    "inputs": [
                      {"type": "bytes32", "value": "%s"},
                      {"type": "uint16",  "value": "%d"}
                    ],
                    "chainSpecificFields": {"gasLimit": "400000"}
                  }
                }
                """.formatted(CONTRACT_EXECUTION, relayerWallet.getId(), contractAddress,
                SECRET_TYPE, paymentId, penaltyBps);

        return execute(body, servicePin, "distributeAndRefund");
    }

    // =========================================================================
    // UTILITAIRES
    // =========================================================================

    private String execute(String body, String pin, String operation) {
        try {
            var response = transferClientService.executeTransfert(body, pin);
            if (response == null || !response.getStatusCode().is2xxSuccessful()) {
                log.error("{} KO: status={} body={}", operation,
                        response != null ? response.getStatusCode() : "null",
                        response != null ? response.getBody() : "null");
                return null;
            }
            var responseBody = response.getBody();
            if (responseBody == null || responseBody.getResult() == null) {
                log.error("{} KO: body ou result null — response={}", operation, responseBody);
                return null;
            }
            String txHash = responseBody.getResult().getTransactionHash();
            log.info("{} OK: txHash={}", operation, txHash);
            return txHash;
        } catch (Exception e) {
            log.error("{} erreur: {}", operation, e.getMessage(), e);
            return null;
        }
    }

    private boolean isContractConfigured(String address) {
        if (address == null || address.isBlank()) {
            log.warn("Adresse du smart contract non configurée (akuunda.escrow.contract.wallet.address)");
            return false;
        }
        return true;
    }

    private String toUsdcWei(Double amount) {
        return BigDecimal.valueOf(amount)
                .multiply(BigDecimal.valueOf(USDC_DECIMALS))
                .setScale(0, RoundingMode.HALF_UP)
                .toBigInteger().toString();
    }

    private String toJsonArray(String items) {
        return "[" + items + "]";
    }

    @Override
    public String transferUsdc(String toAddress, Double amount, Wallet fromWallet) {
        log.info("transferUsdc direct: to={}, amount={}", toAddress, amount);
        if (toAddress == null || toAddress.isBlank() || fromWallet == null || amount == null || amount <= 0) {
            log.error("transferUsdc: paramètres invalides");
            return null;
        }
        String amountWei = toUsdcWei(amount);
        String body = """
                {
                  "transactionRequest": {
                    "type": "TOKEN_TRANSFER",
                    "walletId": "%s",
                    "to": "%s",
                    "secretType": "%s",
                    "tokenAddress": "%s",
                    "value": %s
                  }
                }
                """.formatted(fromWallet.getId(), toAddress, SECRET_TYPE, usdcAddress, amountWei);
        return execute(body, servicePin, "transferUsdc");
    }
}
