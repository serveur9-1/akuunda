package org.akuunda.akuundawallet.wallet.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.dto.UserDto;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.keycloak.api.service.UserService;
import org.akuunda.akuundawallet.common.service.TransactionNotificationHelper;
import org.akuunda.akuundawallet.transfert.api.dto.external.TransactionResponseDto;
import org.akuunda.akuundawallet.transfert.impl.service.infrastructure.AkunndaTransferClientService;
import org.akuunda.akuundawallet.wallet.api.dao.OperationRepository;
import org.akuunda.akuundawallet.wallet.api.dto.WalletUserDto;
import org.akuunda.akuundawallet.wallet.api.dto.external.*;
import org.akuunda.akuundawallet.wallet.api.entities.Operation;
import org.akuunda.akuundawallet.wallet.api.enums.OperationDesignation;
import org.akuunda.akuundawallet.transfert.impl.service.TransfertService;
import org.akuunda.akuundawallet.wallet.service.AkuundaTransactionService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaGuardarianClientService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaMtPelerinServiceClient;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaYellowCardClientService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.CurrencyFreaksClientService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static java.time.LocalDateTime.now;

@Slf4j
@Service
@RequiredArgsConstructor
public class AkuundaTransactionServiceImpl implements AkuundaTransactionService {
    private static final String MTPELERIN = "MTPELERIN";
    private static final String GUARDIARAN = "GUARDIARAN";
    private static final String YELLOWCARD = "YELLOWCARD";
    private static final String MELD = "MELD";
    private static final String USD_DEVISE = "USD";

    private static final String SECRET_TYPE = "MATIC";
    private static final String TOKEN_TRANSFER = "TOKEN_TRANSFER";
    private static final String tokenAddress = "0x3c499c542cEF5E3811e1192ce70d8cC03d5c3359";

    private final AkuundaYellowCardClientService yellowCardClientService;
    private final AkuundaMtPelerinServiceClient mtPelerinServiceClient;
    private final AkuundaGuardarianClientService guardarianClientService;
    private final UserService userService;
    private final CurrencyFreaksClientService freaksClientService;
    private final AkunndaTransferClientService akunndaTransferClientService;
    private final OperationRepository operationRepository;
    private final TransactionNotificationHelper notificationHelper;
    private final UserRepository userRepository;
    private final TransfertService transfertService;

    @Override
    public ResponseEntity<TransactionStatusResponse> checkTransactionStatus(final String username,
                                                                            final String transactionId,
                                                                            final String transactionType) {
        log.info("Checking transaction status for username {}, transaction type {} and transaction id {}", username, transactionType, transactionId);

        if (transactionType.equalsIgnoreCase(MTPELERIN)) {
            return mtPelerinServiceClient.getTransactionById(transactionId, username);
        } else if (transactionType.equalsIgnoreCase(GUARDIARAN)) {
            return guardarianClientService.getTransactionById(transactionId, username);
        } else if (transactionType.equalsIgnoreCase(YELLOWCARD)) {
            return yellowCardClientService.checkTransactionStatus(transactionId, username);
        }
        return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    }

    @Override
    public ResponseEntity<TransactionHistoryResponse> getAllTransactions(
            final String username,
            final LocalDateTime fromDate,
            final LocalDateTime toDate,
            final Integer skip,
            final Integer limit) {

        log.info("Récupération de toutes les transactions pour utilisateur: {} (from: {}, to: {}, skip: {}, limit: {})",
                username, fromDate, toDate, skip, limit);

        if (username == null || username.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    TransactionHistoryResponse.builder().status("error").message("Username manquant").data(null).build());
        }

        try {
            List<SimpleTransactionResponse> allTransactions = new ArrayList<>();

            // Guardarian
            try {
                ResponseEntity<List<SimpleTransactionResponse>> guardarianResponse =
                        guardarianClientService.getSimpleTransactions(username, fromDate, toDate, 0, 1000);
                if (guardarianResponse.getStatusCode().is2xxSuccessful() && guardarianResponse.getBody() != null) {
                    allTransactions.addAll(guardarianResponse.getBody());
                }
            } catch (Exception e) {
                log.warn("Erreur transactions Guardarian: {}", e.getMessage());
            }

            // YellowCard
            try {
                ResponseEntity<List<SimpleTransactionResponse>> yellowCardResponse =
                        yellowCardClientService.getSimpleTransactions(username, fromDate, toDate, 0, 1000);
                if (yellowCardResponse.getStatusCode().is2xxSuccessful() && yellowCardResponse.getBody() != null) {
                    allTransactions.addAll(yellowCardResponse.getBody());
                }
            } catch (Exception e) {
                log.warn("Erreur transactions YellowCard: {}", e.getMessage());
            }

            // MT Pelerin
            try {
                ResponseEntity<List<SimpleTransactionResponse>> mtPelerinResponse =
                        mtPelerinServiceClient.getSimpleMerchantTransactions(fromDate, toDate, 0, 1000, username);
                if (mtPelerinResponse.getStatusCode().is2xxSuccessful() && mtPelerinResponse.getBody() != null) {
                    allTransactions.addAll(mtPelerinResponse.getBody());
                }
            } catch (Exception e) {
                log.error("Erreur transactions MT Pelerin: {}", e.getMessage(), e);
            }

            List<SimpleTransactionResponse> sortedTransactions = allTransactions.stream()
                    .filter(tx -> tx != null)
                    .sorted(Comparator.comparing(SimpleTransactionResponse::getDate).reversed())
                    .collect(Collectors.toList());

            int skipValue = (skip != null && skip >= 0) ? skip : 0;
            int limitValue = (limit != null && limit > 0) ? limit : 100;
            int startIndex = Math.min(skipValue, sortedTransactions.size());
            int endIndex = Math.min(startIndex + limitValue, sortedTransactions.size());

            List<SimpleTransactionResponse> paginatedTransactions = sortedTransactions.subList(startIndex, endIndex);

            return ResponseEntity.ok(TransactionHistoryResponse.builder()
                    .status("success")
                    .message("Transactions récupérées avec succès")
                    .data(paginatedTransactions)
                    .build());

        } catch (Exception e) {
            log.error("Erreur historique unifié pour {}: {}", username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    TransactionHistoryResponse.builder().status("error")
                            .message("Erreur lors de la récupération de l'historique des transactions").data(null).build());
        }
    }

    @Override
    public ResponseEntity<String> executeTransfertCpteACpte(AkuundaTransfertRequest request) {
        log.info("Transfert request received: {}", request);

        // 1. Validation
        if (request.getCibleUsername() == null || request.getSourceUsername() == null
                || request.getAmount() == null || request.getDevise() == null || request.getHeaderPin() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid transfer request: missing required fields");
        }

        if (request.getCibleUsername().equals(request.getSourceUsername())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid transfer request: source and target usernames cannot be the same");
        }

        if (request.getAmount() <= 0) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid transfer request: amount must be positive");
        }

        // 2. Vérifier utilisateurs
        final var sourceResponse = userService.getUser(request.getSourceUsername());
        final var cibleResponse = userService.getUser(request.getCibleUsername());

        if (!sourceResponse.getStatusCode().is2xxSuccessful() || !cibleResponse.getStatusCode().is2xxSuccessful()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid transfer request: source or target user not found");
        }

        final var sourceUser = sourceResponse.getBody();
        final var cibleUser = cibleResponse.getBody();

        if (sourceUser == null || cibleUser == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid transfer request: source or target user not found");
        }

        UserDto sourceUserDto = sourceUser.data();
        UserDto cibleUserDto = cibleUser.data();

        WalletUserDto sourceWallet = sourceUserDto.getWallets().isEmpty() ? null : sourceUserDto.getWallets().get(0);
        WalletUserDto cibleWallet = cibleUserDto.getWallets().isEmpty() ? null : cibleUserDto.getWallets().get(0);

        if (sourceWallet == null || cibleWallet == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid transfer request: source or target wallet not found");
        }

        // 3. Convertir
        final var conversionResponse = freaksClientService.convertCurrency(request.getDevise(), USD_DEVISE, request.getAmount());

        if (!conversionResponse.getStatusCode().is2xxSuccessful() || conversionResponse.getBody() == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Currency conversion failed");
        }

        CurrencyConversionResponse conversionResult = conversionResponse.getBody();
        String amountConverted = conversionResult.getConvertedAmount();

        if (sourceWallet.getUserBalance() < Double.parseDouble(amountConverted)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Insufficient balance in source wallet");
        }

        // 4. Vérification et recharge automatique du gas (POL) si insuffisant
        ResponseEntity<String> gasCheck = transfertService.verifyAndRefillGasForUsername(
                request.getSourceUsername(), Double.parseDouble(amountConverted));
        if (!gasCheck.getStatusCode().is2xxSuccessful()) {
            return ResponseEntity.status(gasCheck.getStatusCode()).body(gasCheck.getBody());
        }

        // 5. Exécuter le transfert Venly
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
            """, sourceWallet.getId(), cibleWallet.getWalletAddress(), amountConverted,
                SECRET_TYPE, TOKEN_TRANSFER, tokenAddress);

        final var response = akunndaTransferClientService.executeTransfert(requestBody, request.getHeaderPin());

        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to execute transfer via Venly");
        }

        TransactionResponseDto transactionResponseDto = response.getBody();
        assert transactionResponseDto != null;
        String transactionHash = transactionResponseDto.getResult().getTransactionHash();
        Double usdcAmount = Double.valueOf(amountConverted);

        // ✅ CORRIGÉ : Résoudre les noms d'affichage pour la contrepartie
        String sourceDisplayName = resolveDisplayName(request.getSourceUsername());
        String cibleDisplayName = resolveDisplayName(request.getCibleUsername());

        // 5. ✅ CORRIGÉ : Enregistrer avec counterpart
        createAndSaveOperation(
                OperationDesignation.INTERNAL_TRANSFER_SENT,
                request.getSourceUsername(),
                request.getAmount(),
                request.getDevise(),
                usdcAmount,
                "USDC",
                transactionHash,
                request.getCibleUsername(),
                cibleDisplayName
        );

        createAndSaveOperation(
                OperationDesignation.INTERNAL_TRANSFER_RECEIVED,
                request.getCibleUsername(),
                request.getAmount(),
                request.getDevise(),
                usdcAmount,
                "USDC",
                transactionHash,
                request.getSourceUsername(),
                sourceDisplayName
        );

        // 6. Notifications
        notificationHelper.notifyTransferSent(request.getSourceUsername(),
                request.getCibleUsername(),
                request.getAmount() + " " + request.getDevise(), transactionHash);

        notificationHelper.notifyTransferReceived(request.getCibleUsername(),
                request.getSourceUsername(),
                request.getAmount() + " " + request.getDevise(), transactionHash);

        return new ResponseEntity<>("Transfert exécuté avec succès", HttpStatus.OK);
    }

    /**
     * ✅ CORRIGÉ : Méthode harmonisée avec counterpart (username + displayName).
     */
    private void createAndSaveOperation(OperationDesignation designation,
                                        String username,
                                        Double amount,
                                        String devise,
                                        Double providerAmount,
                                        String providerDevise,
                                        String operationHash,
                                        String counterpartUsername,
                                        String counterpartDisplayName) {
        Operation operation = new Operation();
        operation.setDesignation(designation.name());
        operation.setTransactionType(designation.getTransactionType());
        operation.setType(designation.getType());
        operation.setProvider(designation.getProvider());
        operation.setAmount(amount);
        operation.setDevise(devise);
        operation.setProviderAmount(providerAmount);
        operation.setProviderDevise(providerDevise);
        operation.setConvertedAmount(providerAmount); // Legacy compat
        operation.setUsername(username);
        operation.setStatus("VALIDEE");
        operation.setCreatedAt(now());
        operation.setUpdatedAt(now());
        operation.setOperationHash(operationHash);
        operation.setCounterpartUsername(counterpartUsername);
        operation.setCounterpartDisplayName(counterpartDisplayName);
        operationRepository.save(operation);
    }

    /**
     * ✅ AJOUTÉ : Résout le nom complet d'un utilisateur pour l'affichage.
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
}
