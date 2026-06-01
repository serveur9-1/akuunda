package org.akuunda.akuundawallet.wallet.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.transfert.impl.service.infrastructure.AkunndaTransferClientService;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.wallet.service.PaymentFactoryContractService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Hash;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Implémentation du service d'interface avec le smart contract AkuundaPaymentFactory.
 * Utilise Venly pour les transactions d'écriture et l'API read-contract pour les lectures.
 */
@Slf4j
@Service
public class PaymentFactoryContractServiceImpl implements PaymentFactoryContractService {

    private final AkunndaTransferClientService transferClientService;

    @Value("${akuunda.payment.factory.address:0x4fa9e998dba19a0c4b831259c556a4d99052f0a4}")
    private String factoryAddress;

    @Value("${akuunda.escrow.service.pin:PIN:000000}")
    private String servicePin;

    @Value("${akuunda.escrow.token.address:0x3c499c542cEF5E3811e1192ce70d8cC03d5c3359}")
    private String usdcTokenAddress;

    // URL JSON-RPC HTTP Polygon — utilisée uniquement pour les appels de lecture (balanceOf).
    // Pas d'auth, pas de Venly. Remplace POST /api/contracts/read pour getUsdcBalance().
    @Value("${polygon.rpc.http.url:https://polygon-bor-rpc.publicnode.com}")
    private String polygonHttpRpcUrl;

    private static final String CONTRACT_EXECUTION = "CONTRACT_EXECUTION";
    private static final String SECRET_TYPE = "MATIC";
    private static final long USDC_DECIMALS = 1_000_000L;
    // keccak256("balanceOf(address)") first 4 bytes — ABI selector ERC20
    private static final String BALANCE_OF_SELECTOR = "70a08231";

    // Sélecteurs ABI calculés à l'init — remplacent POST /api/contracts/read
    private static final String IS_PAYMENT_RECEIVED_SEL;
    private static final String GET_WALLET_ADDRESS_SEL;
    private static final String PAYMENTS_SEL;

    static {
        IS_PAYMENT_RECEIVED_SEL = abiSelector("isPaymentReceived(bytes32)");
        GET_WALLET_ADDRESS_SEL  = abiSelector("getWalletAddress(bytes32)");
        PAYMENTS_SEL            = abiSelector("payments(bytes32)");
    }

    private static String abiSelector(String sig) {
        byte[] h = Hash.sha3(sig.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return String.format("%02x%02x%02x%02x", h[0] & 0xff, h[1] & 0xff, h[2] & 0xff, h[3] & 0xff);
    }

    public PaymentFactoryContractServiceImpl(AkunndaTransferClientService transferClientService) {
        this.transferClientService = transferClientService;
    }

    @Override
    public String createPaymentLink(String paymentId, String merchantAddress, Double amountInUSDC,
                                    String description, Long durationInSeconds, Wallet signerWallet) {
        log.info("🔵 createPaymentLink: paymentId={}, merchant={}, amount={}", paymentId, merchantAddress, amountInUSDC);

        if (signerWallet == null || paymentId == null || merchantAddress == null) {
            log.error("❌ Paramètres manquants pour createPaymentLink");
            return null;
        }

        try {
            String amountInWei = toUsdcWei(amountInUSDC);
            String body = String.format("""
                {
                  "transactionRequest": {
                    "type": "%s",
                    "walletId": "%s",
                    "to": "%s",
                    "secretType": "%s",
                    "functionName": "createPaymentLink",
                    "value": 0,
                    "inputs": [
                      {"type": "bytes32", "value": "%s"},
                      {"type": "address", "value": "%s"},
                      {"type": "uint256", "value": "%s"},
                      {"type": "string", "value": "%s"},
                      {"type": "uint256", "value": "%s"}
                    ],
                    "chainSpecificFields": {"gasLimit": "500000"}
                  }
                }
                """, CONTRACT_EXECUTION, signerWallet.getId(), factoryAddress, SECRET_TYPE,
                    paymentId, merchantAddress, amountInWei, description, durationInSeconds);

            log.info("📤 Appel Factory.createPaymentLink() via Venly: walletId={}", signerWallet.getId());
            var response = transferClientService.executeTransfert(body, servicePin);

            if (response != null && response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String txHash = response.getBody().getResult().getTransactionHash();
                log.info("✅ createPaymentLink OK: txHash={}", txHash);
                // Récupérer l'adresse CREATE2 depuis la Factory
                return getWalletAddress(paymentId);
            } else {
                log.error("❌ Échec createPaymentLink: status={}",
                        response != null ? response.getStatusCode() : "null");
                return null;
            }
        } catch (Exception e) {
            log.error("❌ Erreur lors du createPaymentLink", e);
            return null;
        }
    }

    @Override
    public String executePayment(String paymentId, Wallet signerWallet) {
        log.info("🟢 executePayment: paymentId={}", paymentId);

        if (signerWallet == null || paymentId == null) {
            log.error("❌ Paramètres manquants pour executePayment");
            return "PAYMENT-FAILED-" + System.currentTimeMillis();
        }

        try {
            String body = String.format("""
                {
                  "transactionRequest": {
                    "type": "%s",
                    "walletId": "%s",
                    "to": "%s",
                    "secretType": "%s",
                    "functionName": "executePayment",
                    "value": 0,
                    "inputs": [
                      {"type": "bytes32", "value": "%s"}
                    ],
                    "chainSpecificFields": {"gasLimit": "300000"}
                  }
                }
                """, CONTRACT_EXECUTION, signerWallet.getId(), factoryAddress, SECRET_TYPE, paymentId);

            log.info("📤 Appel Factory.executePayment() via Venly: walletId={}", signerWallet.getId());
            var response = transferClientService.executeTransfert(body, servicePin);

            if (response != null && response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                var result = response.getBody().getResult();
                String txHash = result != null ? result.getTransactionHash() : null;
                if (result == null || !result.isOnChainSuccess()) {
                    log.error("❌ executePayment REVERTED on-chain: txHash={}, status={}",
                            txHash, result != null ? result.getStatus() : "null");
                    return "PAYMENT-FAILED-" + System.currentTimeMillis();
                }
                log.info("✅ executePayment OK: txHash={}", txHash);
                return txHash;
            } else {
                log.error("❌ Échec executePayment: status={}",
                        response != null ? response.getStatusCode() : "null");
                return "PAYMENT-FAILED-" + System.currentTimeMillis();
            }
        } catch (Exception e) {
            log.error("❌ Erreur lors du executePayment", e);
            return "PAYMENT-FAILED-" + System.currentTimeMillis();
        }
    }

    // ── eth_call direct Polygon — remplace POST /api/contracts/read ─────────

    private String ethCallRpc(String to, String callData) {
        try {
            String jsonBody = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_call\",\"params\":[{\"to\":\"" +
                    to + "\",\"data\":\"" + callData + "\"},\"latest\"],\"id\":1}";
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(polygonHttpRpcUrl))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();
            java.net.http.HttpResponse<String> resp = java.net.http.HttpClient.newHttpClient()
                    .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                String hex = new JSONObject(resp.body()).optString("result", "0x");
                if (hex != null && hex.length() > 2) return hex.startsWith("0x") ? hex.substring(2) : hex;
            }
        } catch (Exception e) {
            log.error("❌ ethCallRpc error for {}", to, e);
        }
        return null;
    }

    private static String encodeBytes32(String paymentId) {
        String hex = paymentId.startsWith("0x") ? paymentId.substring(2) : paymentId;
        // bytes32 = droit, mais keccak256 → toujours 64 hex chars
        if (hex.length() >= 64) return hex.substring(0, 64);
        StringBuilder sb = new StringBuilder(hex);
        while (sb.length() < 64) sb.append('0');
        return sb.toString();
    }

    @Override
    public boolean isPaymentReceived(String paymentId) {
        log.debug("🔍 isPaymentReceived RPC: paymentId={}", paymentId);
        try {
            String hex = ethCallRpc(factoryAddress, "0x" + IS_PAYMENT_RECEIVED_SEL + encodeBytes32(paymentId));
            if (hex == null) return false;
            // bool ABI = 32 bytes, dernier bit = 1 si true
            return new java.math.BigInteger(hex, 16).compareTo(java.math.BigInteger.ZERO) != 0;
        } catch (Exception e) {
            log.error("❌ isPaymentReceived RPC error", e);
            return false;
        }
    }

    @Override
    public String getWalletAddress(String paymentId) {
        log.debug("🔍 getWalletAddress RPC: paymentId={}", paymentId);
        try {
            String hex = ethCallRpc(factoryAddress, "0x" + GET_WALLET_ADDRESS_SEL + encodeBytes32(paymentId));
            if (hex == null || hex.length() < 40) return null;
            // address = 20 bytes dans les 32 derniers → prendre les 40 derniers chars
            String addr = "0x" + hex.substring(hex.length() - 40);
            return addr.equalsIgnoreCase("0x0000000000000000000000000000000000000000") ? null : addr;
        } catch (Exception e) {
            log.error("❌ getWalletAddress RPC error", e);
            return null;
        }
    }

    @Override
    public PaymentInfo getPayment(String paymentId) {
        log.debug("🔍 getPayment RPC: paymentId={}", paymentId);
        try {
            String hex = ethCallRpc(factoryAddress, "0x" + PAYMENTS_SEL + encodeBytes32(paymentId));
            if (hex == null || hex.length() < 384) return null;

            // Tuple ABI: (address merchant, uint256 amount, uint256 expiration, address walletAddress, uint8 status, string description)
            // Chaque slot = 64 hex chars (32 bytes)
            String merchant     = "0x" + hex.substring(24, 64);          // slot 0 → address (20 bytes)
            double amount       = new java.math.BigInteger(hex.substring(64, 128), 16).doubleValue() / USDC_DECIMALS;
            long   expiration   = new java.math.BigInteger(hex.substring(128, 192), 16).longValue();
            String walletAddress = "0x" + hex.substring(216, 256);       // slot 3 → address
            int    status       = new java.math.BigInteger(hex.substring(256, 320), 16).intValue();
            // slot 4 (320-383) = offset vers la string (en bytes depuis début du tuple)
            int strByteOffset   = new java.math.BigInteger(hex.substring(320, 384), 16).intValue();
            String description  = "";
            try {
                int hexOffset = strByteOffset * 2; // bytes → hex chars
                int strLen    = new java.math.BigInteger(hex.substring(hexOffset, hexOffset + 64), 16).intValue();
                if (strLen > 0 && hex.length() >= hexOffset + 64 + strLen * 2) {
                    byte[] strBytes = new byte[strLen];
                    String strHex = hex.substring(hexOffset + 64, hexOffset + 64 + strLen * 2);
                    for (int i = 0; i < strLen; i++)
                        strBytes[i] = (byte) Integer.parseInt(strHex.substring(i * 2, i * 2 + 2), 16);
                    description = new String(strBytes, java.nio.charset.StandardCharsets.UTF_8);
                }
            } catch (Exception e) {
                log.warn("⚠️ getPayment: impossible de parser la description", e);
            }
            return new PaymentInfo(merchant, amount, expiration, walletAddress, status, description);
        } catch (Exception e) {
            log.error("❌ getPayment RPC error", e);
            return null;
        }
    }

    @Override
    public String refundPayment(String paymentId, String refundToAddress, Wallet signerWallet) {
        log.info("🟡 refundPayment: paymentId={}, refundTo={}", paymentId, refundToAddress);

        if (signerWallet == null || paymentId == null) {
            log.error("❌ Paramètres manquants pour refundPayment");
            return "REFUND-FAILED-" + System.currentTimeMillis();
        }

        try {
            String body = String.format("""
                {
                  "transactionRequest": {
                    "type": "%s",
                    "walletId": "%s",
                    "to": "%s",
                    "secretType": "%s",
                    "functionName": "refundPayment",
                    "value": 0,
                    "inputs": [
                      {"type": "bytes32", "value": "%s"},
                      {"type": "address", "value": "%s"}
                    ],
                    "chainSpecificFields": {"gasLimit": "300000"}
                  }
                }
                """, CONTRACT_EXECUTION, signerWallet.getId(), factoryAddress, SECRET_TYPE,
                    paymentId, refundToAddress);

            log.info("📤 Appel Factory.refundPayment() via Venly: walletId={}", signerWallet.getId());
            var response = transferClientService.executeTransfert(body, servicePin);

            if (response != null && response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                var result = response.getBody().getResult();
                String txHash = result != null ? result.getTransactionHash() : null;
                if (result == null || !result.isOnChainSuccess()) {
                    log.error("❌ refundPayment REVERTED on-chain: txHash={}, status={}",
                            txHash, result != null ? result.getStatus() : "null");
                    return "REFUND-FAILED-" + System.currentTimeMillis();
                }
                log.info("✅ refundPayment OK: txHash={}", txHash);
                return txHash;
            } else {
                log.error("❌ Échec refundPayment: status={}",
                        response != null ? response.getStatusCode() : "null");
                return "REFUND-FAILED-" + System.currentTimeMillis();
            }
        } catch (Exception e) {
            log.error("❌ Erreur lors du refundPayment", e);
            return "REFUND-FAILED-" + System.currentTimeMillis();
        }
    }

    @Override
    public Double getUsdcBalance(String walletAddress) {
        log.debug("🔍 getUsdcBalance (RPC direct): address={}", walletAddress);
        try {
            // ABI encode : selector balanceOf(address) + adresse paddée sur 32 bytes
            String paddedAddr = "000000000000000000000000" + walletAddress.replace("0x", "").toLowerCase();
            String data = "0x" + BALANCE_OF_SELECTOR + paddedAddr;

            String jsonBody = "{\"jsonrpc\":\"2.0\",\"method\":\"eth_call\",\"params\":[{\"to\":\"" +
                    usdcTokenAddress + "\",\"data\":\"" + data + "\"},\"latest\"],\"id\":1}";

            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(polygonHttpRpcUrl))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(jsonBody))
                    .build();

            java.net.http.HttpResponse<String> response = java.net.http.HttpClient.newHttpClient()
                    .send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JSONObject json = new JSONObject(response.body());
                String hex = json.optString("result", "0x");
                if (!hex.equals("0x") && hex.length() > 2) {
                    java.math.BigInteger raw = new java.math.BigInteger(hex.replace("0x", ""), 16);
                    return raw.doubleValue() / USDC_DECIMALS;
                }
                return 0.0;
            }
            log.warn("⚠️ getUsdcBalance RPC status={}", response.statusCode());
            return null;
        } catch (Exception e) {
            log.error("❌ Error getting USDC balance via RPC", e);
            return null;
        }
    }

    @Override
    public String generatePaymentId(String uniqueCode) {
        return Hash.sha3String(uniqueCode);
    }

    private String toUsdcWei(Double amount) {
        return BigDecimal.valueOf(amount)
                .multiply(BigDecimal.valueOf(USDC_DECIMALS))
                .setScale(0, RoundingMode.HALF_UP)
                .toBigInteger().toString();
    }
}
