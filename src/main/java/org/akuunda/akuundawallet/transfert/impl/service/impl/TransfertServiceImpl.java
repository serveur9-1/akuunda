package org.akuunda.akuundawallet.transfert.impl.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.keycloak.api.service.UserHelperService;
import org.akuunda.akuundawallet.transfert.api.dto.external.TransactionResponseDto;
import org.akuunda.akuundawallet.transfert.impl.service.TransfertService;
import org.akuunda.akuundawallet.transfert.impl.service.infrastructure.AkunndaTransferClientService;
import org.akuunda.akuundawallet.wallet.api.dao.OperationRepository;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.api.entities.Operation;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.wallet.api.enums.OperationDesignation;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkunndaWalletClientService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

import static java.time.LocalDateTime.now;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransfertServiceImpl implements TransfertService {

    private static final String SECRET_TYPE = "MATIC";
    private static final String GAS_TRANSFER = "GAS_TRANSFER";
    private static final String TOKEN_TRANSFER = "TOKEN_TRANSFER";
    private static final String tokenAddress = "0x3c499c542cEF5E3811e1192ce70d8cC03d5c3359";

    private final AkunndaTransferClientService akunndaTransferClientService;
    private final WalletRepository walletRepository;
    private final OperationRepository operationRepository;
    private final UserHelperService userHelperService;
    private final AkunndaWalletClientService walletClientService;

    // Configuration auto-recharge gas (Akuunda Funding Wallet)
    @Value("${akuunda.intermediate.wallet.id}")
    private String sponsorWalletId;

    @Value("${akuunda.escrow.service.pin}")
    private String sponsorWalletPin;

    @Value("${akuunda.gas.minimum-required:0.02}")
    private double minimumGasRequired;

    @Value("${akuunda.gas.refill-amount:0.05}")
    private double gasRefillAmount;

    // ═══════════════════════════════════════════════════════════════
    // GAS TRANSFER — Interne système, NON MODIFIÉ
    // ═══════════════════════════════════════════════════════════════

    @Override
    public ResponseEntity<String> executeGasTransfert(String senderUsername, final String reiceverUsername,
                                                      final Double amount, final Double gasAmount,
                                                      final String headerPin, final String devise) {

        if (senderUsername == null || senderUsername.isEmpty() ||
            reiceverUsername == null || reiceverUsername.isEmpty() ||
                gasAmount == null || gasAmount <= 0 ||
            headerPin == null || headerPin.isEmpty()) {
            log.error("Invalid input parameters for transfer");
            return ResponseEntity.badRequest().body("Invalid input parameters");
        }

        log.info("Initiating transfer from wallet {} to {} of amount {}", senderUsername,
                reiceverUsername, amount);

        Users senderUser = userHelperService.getUserEntity(senderUsername);
        Users receiverUser = userHelperService.getUserEntity(reiceverUsername);
        if (senderUser == null) {
            return ResponseEntity.badRequest()
                    .body("erreur dans la recuperation de senderUser");
        }

        if (receiverUser == null) {
            return ResponseEntity.badRequest()
                    .body("erreur dans la recuperation de receiverUser");
        }

        List<Wallet> senderWallets = userHelperService.getWalletsByUser(senderUser);
        List<Wallet> receiverWallets = userHelperService.getWalletsByUser(receiverUser);

        if (senderWallets == null || senderWallets.isEmpty()) {
            log.error("Sender wallet not found for user: {}", senderUsername);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Sender wallet not found");
        }

        if (receiverWallets == null || receiverWallets.isEmpty()) {
            log.error("Receiver wallet not found for user: {}", reiceverUsername);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Receiver wallet not found");
        }

        Wallet walletSender = senderWallets.get(0);
        Wallet walletReceiver = receiverWallets.get(0);

        if (walletSender == null || walletSender.getId() == null) {
            log.error("Sender wallet not found for user: {}", senderUsername);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Sender wallet not found");
        }

        if (walletReceiver == null || walletReceiver.getId() == null) {
            log.error("Receiver wallet not found for user: {}", reiceverUsername);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Receiver wallet not found");
        }

        if (walletSender.getBalance() < amount) {
            log.error("Insufficient balance in sender's wallet: {}", senderUsername);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Insufficient balance");
        }

        // ═══════════════════════════════════════════════════════════════
        // 🔋 VÉRIFICATION ET RECHARGE AUTOMATIQUE DU GAS
        // Même mécanisme que pour l'off-ramp — nécessaire aussi pour les
        // transferts internes qui passent par Venly (transaction on-chain).
        // ═══════════════════════════════════════════════════════════════
        ResponseEntity<String> gasCheck = verifyAndRefillGasIfNeeded(walletSender, senderUsername, amount);
        if (!gasCheck.getStatusCode().is2xxSuccessful()) {
            return gasCheck;
        }

        String requestBody = String.format("""
            {
              "transactionRequest": {
                "walletId": "%s",
                "to": "%s",
                "value": %s,
                "secretType": "%s",
                "type": "%s"
              }
            }
            """, walletSender.getId(), walletReceiver.getAddress(), gasAmount,
                SECRET_TYPE, GAS_TRANSFER);

        final var response = akunndaTransferClientService.executeTransfert(requestBody, headerPin);

        if (response != null) {
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Transfert executed successfully: {}", response.getBody());

                TransactionResponseDto transactionResponseDto = response.getBody();
                assert transactionResponseDto != null;
                String transactionHash = transactionResponseDto.getResult().getTransactionHash();

                final var senderDeviseAfter = walletSender.getBalance() - amount;
                final var receiverDeviseAfter = walletReceiver.getBalance() + amount;

                updateWalletBalances(walletSender, senderDeviseAfter);
                updateWalletBalances(walletReceiver, receiverDeviseAfter);

                // Ne pas polluer l'historique client avec les seuls grants gas (activation / refill visible).
                if (amount != null && amount > 0) {
                    saveOperationLegacy("DEBIT", "TRANSFERT", senderUsername,
                            amount, gasAmount, devise, transactionHash);
                    saveOperationLegacy("CREDIT", "TRANSFERT", reiceverUsername,
                            amount, gasAmount, devise, transactionHash);
                } else {
                    log.info("⛽ Transfert gas seul ({} {}) — non enregistré dans operations",
                            gasAmount, devise);
                }

                return new ResponseEntity<>("Succes", HttpStatus.OK);
            } else {
                log.error("Failed to execute transfert: {}", response.getStatusCode());
                return ResponseEntity.status(response.getStatusCode()).body("Transfert failed");
            }
        } else {
            log.error("Response from transfer client is null");
            return new ResponseEntity<>("Transfert failed due to null response",
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // ON-RAMP TOKEN TRANSFER — AVEC AUTO-RECHARGE GAS
    // ═══════════════════════════════════════════════════════════════

    @Override
    public ResponseEntity<String> executeOnRampTokenTransfert(Users userSender, String reicevedAdress,
                                                              Double amount, Double GasAmount,
                                                              String headerPin, String devise) {
        if (userSender == null || reicevedAdress == null || reicevedAdress.isEmpty() ||
                amount == null || amount <= 0 ||
                headerPin == null || headerPin.isEmpty()) {
            log.error("Invalid input parameters for transfer");
            return ResponseEntity.badRequest().body("Invalid input parameters");
        }

        final var walletSender = walletRepository.findWalletByUsers(userSender).get(0);

        log.info("🔄 Exécution du transfert Venly:");
        log.info("   - User: {} (userId: {})", userSender.getUsername(), userSender.getUserId());
        log.info("   - Wallet Sender ID: {}", walletSender.getId());
        log.info("   - Wallet Sender Balance: {} USDC", walletSender.getBalance());
        log.info("   - Destination Address: {}", reicevedAdress);
        log.info("   - Amount: {} USDC", amount);

        double tokenBalance = walletSender.getBalance();
        if (tokenBalance < amount) {
            log.warn("❌ Solde insuffisant pour {}: {} < {}", userSender.getUsername(), tokenBalance, amount);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Solde insuffisant pour effectuer cette opération.");
        }

        // ═══════════════════════════════════════════════════════════════
        // 🔋 VÉRIFICATION ET RECHARGE AUTOMATIQUE DU GAS (après validation du solde)
        // ═══════════════════════════════════════════════════════════════
        ResponseEntity<String> gasCheck = verifyAndRefillGasIfNeeded(walletSender, userSender.getUsername(), amount);
        if (!gasCheck.getStatusCode().is2xxSuccessful()) {
            return gasCheck;
        }

        // ═══════════════════════════════════════════════════════════════
        // EXÉCUTION DU TRANSFERT
        // ═══════════════════════════════════════════════════════════════
        String requestBody = String.format("""
            {
              "transactionRequest": {
                "walletId": "%s",
                "to": "%s",
                "value": %s,
                "secretType": "%s",
                "type": "%s",
                "tokenAddress": "%s"
              }
            }
            """, walletSender.getId(), reicevedAdress, amount, SECRET_TYPE, TOKEN_TRANSFER, tokenAddress);

        log.info("📤 Request body envoyé à Venly: {}", requestBody);

        final var response = akunndaTransferClientService.executeTransfert(requestBody, headerPin);

        if (response != null) {
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("✅ Transfert executed successfully: {}", response.getBody());

                TransactionResponseDto transactionResponseDto = response.getBody();
                assert transactionResponseDto != null;
                String transactionHash = transactionResponseDto.getResult().getTransactionHash();

                final var senderDeviseAfter = walletSender.getBalance() - amount;
                walletSender.setBalance(senderDeviseAfter);
                walletRepository.saveAndFlush(walletSender);

                // Étape technique d'un off-ramp / retrait : le destinataire est une adresse
                // on-chain hors Akuunda (provider YellowCard / Meld / Guardarian / ...).
                // On la tague comme EXTERNAL_TRANSFER_SENT pour que les listings users
                // (wallet, transactions pro) la masquent — la valeur métier est déjà
                // représentée par l'opération "Retrait" provenant du provider.
                saveOperationHarmonized(OperationDesignation.EXTERNAL_TRANSFER_SENT,
                        userSender.getUsername(), amount, devise,
                        amount, "USDC", transactionHash);

                return new ResponseEntity<>("Succes", HttpStatus.OK);

            } else {
                log.error("❌ Échec du transfert Venly - Status: {}, Body: {}",
                        response.getStatusCode(), response.getBody());
                String errorMessage = "Echec transfert";
                if (response.getBody() != null) {
                    errorMessage += " - " + response.getBody().toString();
                }
                return new ResponseEntity<>(errorMessage, response.getStatusCode());
            }
        } else {
            log.error("Response from transfer client is null");
            return new ResponseEntity<>("Transfert failed", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // GAS REFILL FROM SPONSOR
    // ═══════════════════════════════════════════════════════════════

    @Override
    public ResponseEntity<String> executeGasRefillFromSponsor(String sponsorWalletId,
                                                               String destinationAddress,
                                                               Double amount,
                                                               String sponsorPin) {

        if (sponsorWalletId == null || sponsorWalletId.isEmpty() ||
            destinationAddress == null || destinationAddress.isEmpty() ||
            amount == null || amount <= 0 ||
            sponsorPin == null || sponsorPin.isEmpty()) {
            log.error("❌ Paramètres invalides pour le refill de gas");
            return ResponseEntity.badRequest().body("Invalid input parameters for gas refill");
        }

        log.info("🔄 Gas refill: {} POL → {}", amount, destinationAddress);

        String requestBody = String.format("""
            {
              "transactionRequest": {
                "walletId": "%s",
                "to": "%s",
                "value": %s,
                "secretType": "%s",
                "type": "%s"
              }
            }
            """, sponsorWalletId, destinationAddress, amount, SECRET_TYPE, GAS_TRANSFER);

        final var response = akunndaTransferClientService.executeTransfert(requestBody, sponsorPin);

        if (response != null && response.getStatusCode().is2xxSuccessful()) {
            log.info("✅ Gas refill réussi: {} POL → {}", amount, destinationAddress);
            return new ResponseEntity<>("Success", HttpStatus.OK);
        } else {
            String errorDetails = response != null ? response.getStatusCode().toString() : "null response";
            log.error("❌ Gas refill échoué: {}", errorDetails);
            return new ResponseEntity<>("Gas refill failed: " + errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // ═══════════════════════════════════════════════════════════════
    // VÉRIFICATION ET RECHARGE AUTOMATIQUE DU GAS
    // ═══════════════════════════════════════════════════════════════

    /**
     * Vérifie le gas et recharge automatiquement si nécessaire.
     * Transparent pour l'utilisateur et tous les providers.
     */
    @Override
    public ResponseEntity<String> verifyAndRefillGasForUsername(String username) {
        return verifyAndRefillGasForUsername(username, null);
    }

    @Override
    public ResponseEntity<String> verifyAndRefillGasForUsername(String username, Double operationAmount) {
        Wallet wallet = walletRepository.findByUsersUsername(username);
        if (wallet == null) {
            log.error("❌ Wallet introuvable pour username: {}", username);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Wallet introuvable pour: " + username);
        }
        return verifyAndRefillGasIfNeeded(wallet, username, operationAmount);
    }

    @Override
    public ResponseEntity<String> verifyAndRefillGasIfNeeded(Wallet wallet, String username) {
        return verifyAndRefillGasIfNeeded(wallet, username, null);
    }

    @Override
    public ResponseEntity<String> verifyAndRefillGasIfNeeded(
            Wallet wallet, String username, Double operationAmount) {
        if (operationAmount == null || operationAmount <= 0) {
            double gasOnly = fetchRealGasBalance(wallet);
            log.info("🔍 Gas {} (pas de recharge auto sans opération): {} POL", username, gasOnly);
            if (gasOnly >= minimumGasRequired) {
                return ResponseEntity.ok("Gas OK");
            }
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    "Solde de gas insuffisant. Une opération avec solde disponible est requise avant recharge.");
        }

        double tokenBalance = wallet.getBalance();
        if (tokenBalance < operationAmount) {
            log.warn("❌ Solde insuffisant pour {}: {} < {} — pas de recharge MATIC", username, tokenBalance, operationAmount);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Solde insuffisant pour effectuer cette opération.");
        }

        // 1️⃣ Récupérer le solde RÉEL depuis Venly
        double gasBalance = fetchRealGasBalance(wallet);
        log.info("🔍 Gas balance pour {}: {} POL (min requis: {} POL)", username, gasBalance, minimumGasRequired);

        // 2️⃣ Si suffisant → OK
        if (gasBalance >= minimumGasRequired) {
            log.info("✅ Gas OK: {} POL", gasBalance);
            return ResponseEntity.ok("Gas OK");
        }

        // 3️⃣ Solde opération validé + gas insuffisant → recharge sponsor
        log.warn("⚠️ Gas insuffisant: {} POL < {} POL - Recharge (opération {} validée)...",
                gasBalance, minimumGasRequired, operationAmount);

        // Vérifier que le sponsor est configuré
        if (sponsorWalletId == null || sponsorWalletId.isEmpty() ||
            sponsorWalletPin == null || sponsorWalletPin.isEmpty()) {
            log.error("❌ Wallet sponsor non configuré");
            return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED)
                    .body("Solde de gas insuffisant: " + gasBalance + " POL, requis: " + minimumGasRequired + " POL");
        }

        // 4️⃣ Effectuer la recharge
        log.info("🔄 Recharge: {} POL → {}", gasRefillAmount, wallet.getAddress());
        var refillResponse = executeGasRefillFromSponsor(
                sponsorWalletId,
                wallet.getAddress(),
                gasRefillAmount,
                sponsorWalletPin
        );

        if (!refillResponse.getStatusCode().is2xxSuccessful()) {
            log.error("❌ Recharge gas échouée");
            return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED)
                    .body("Recharge gas échouée. Veuillez réessayer.");
        }

        // 5️⃣ Attendre confirmation blockchain
        log.info("⏳ Attente confirmation blockchain (10s)...");
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // 6️⃣ Vérifier nouveau solde
        gasBalance = fetchRealGasBalance(wallet);
        log.info("📊 Solde après recharge: {} POL", gasBalance);

        if (gasBalance < minimumGasRequired) {
            // Attendre encore
            log.info("⏳ Attente supplémentaire (5s)...");
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            gasBalance = fetchRealGasBalance(wallet);

            if (gasBalance < minimumGasRequired) {
                return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED)
                        .body("Gas en cours de traitement. Veuillez réessayer dans quelques secondes.");
            }
        }

        log.info("✅ Gas rechargé: {} POL", gasBalance);
        return ResponseEntity.ok("Gas rechargé");
    }

    /**
     * Récupère le solde gas RÉEL depuis Venly (pas le cache BD)
     */
    private double fetchRealGasBalance(Wallet wallet) {
        try {
            var response = walletClientService.getWalletGasBalance(wallet.getId());

            if (response.getStatusCode().is2xxSuccessful() &&
                response.getBody() != null &&
                response.getBody().getResult() != null) {

                var result = response.getBody().getResult();
                double balance = result.getGasBalance() > 0 ? result.getGasBalance() : result.getBalance();

                // Mettre à jour le cache BD
                wallet.setGasBalance(balance);
                walletRepository.save(wallet);

                return balance;
            }
        } catch (Exception e) {
            log.error("❌ Erreur fetch gas balance: {}", e.getMessage());
        }

        return wallet.getGasBalance(); // Fallback cache
    }

    // ═══════════════════════════════════════════════════════════════
    // SAVE OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    private void saveOperationLegacy(String type, String designation, String username,
                                     Double convertedAmount, Double amount,
                                     String devise, String operationHash) {
        Operation operation = new Operation();
        operation.setConvertedAmount(convertedAmount);
        operation.setAmount(amount);
        operation.setDevise(devise);
        operation.setDesignation(designation);
        operation.setCreatedAt(now());
        operation.setUpdatedAt(now());
        operation.setStatus("VALIDEE");
        operation.setUsername(username);
        operation.setType(type);
        operation.setOperationHash(operationHash);
        operationRepository.save(operation);
    }

    private void saveOperationHarmonized(OperationDesignation designation, String username,
                                         Double amount, String devise,
                                         Double providerAmount, String providerDevise,
                                         String operationHash) {
        Operation operation = new Operation();
        operation.setDesignation(designation.name());
        operation.setTransactionType(designation.getTransactionType());
        operation.setType(designation.getType());
        operation.setProvider(designation.getProvider());
        operation.setAmount(amount);
        operation.setDevise(devise);
        operation.setProviderAmount(providerAmount);
        operation.setProviderDevise(providerDevise);
        operation.setConvertedAmount(providerAmount);
        operation.setUsername(username);
        operation.setStatus("VALIDEE");
        operation.setCreatedAt(now());
        operation.setUpdatedAt(now());
        operation.setOperationHash(operationHash);
        operationRepository.save(operation);
    }

    private void updateWalletBalances(Wallet wallet, Double newBalance) {
        wallet.setBalance(newBalance);
        walletRepository.saveAndFlush(wallet);
    }
}
