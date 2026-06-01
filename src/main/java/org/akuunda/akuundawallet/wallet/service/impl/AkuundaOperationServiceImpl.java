package org.akuunda.akuundawallet.wallet.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.transfert.api.dto.OperationUpdateDto;
import org.akuunda.akuundawallet.wallet.api.dao.GuadarianTransactionRepository;
import org.akuunda.akuundawallet.wallet.api.dao.OperationRepository;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.api.dto.OperationDto;
import org.akuunda.akuundawallet.wallet.api.entities.GuardarianTransaction;
import org.akuunda.akuundawallet.wallet.api.entities.Operation;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.wallet.api.enums.OperationDesignation;
import org.akuunda.akuundawallet.wallet.api.service.AkuundaOperationService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.CurrencyFreaksClientService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.akuunda.akuundawallet.common.service.TransactionNotificationHelper;

@Service
@RequiredArgsConstructor
@Slf4j
public class AkuundaOperationServiceImpl implements AkuundaOperationService {

    private final OperationRepository operationRepository;
    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final CurrencyFreaksClientService currencyFreaksClientService;
    private final GuadarianTransactionRepository guadarianTransactionRepository;
    private final TransactionNotificationHelper transactionNotificationHelper;

    @Override
    public ResponseEntity<String> saveOperation(final Operation operation) {
        log.info("Saving operation: {}", operation);

        if (!isValidOperation(operation)) {
            log.error("Invalid operation data: {}", operation);
            return ResponseEntity.badRequest().body("Invalid arguments");
        }

        if (operation.getCreatedAt() == null) operation.setCreatedAt(LocalDateTime.now());
        if (operation.getUpdatedAt() == null) operation.setUpdatedAt(LocalDateTime.now());

        // Auto-résoudre transactionType et provider si absents
        if (operation.getTransactionType() == null || operation.getTransactionType().isBlank()) {
            operation.setTransactionType(
                    OperationDesignation.resolveTransactionType(operation.getDesignation(), operation.getType()));
        }
        if (operation.getProvider() == null || operation.getProvider().isBlank()) {
            operation.setProvider(OperationDesignation.resolveProvider(operation.getDesignation()));
        }

        operationRepository.save(operation);
        return ResponseEntity.ok("Operation saved");
    }

    private boolean isValidOperation(Operation operation) {
        return operation != null
                && operation.getAmount() != null && operation.getAmount() > 0
                && operation.getDevise() != null && !operation.getDevise().isEmpty()
                && operation.getUsername() != null
                && operation.getType() != null
                && operation.getDesignation() != null
                && operation.getOperationHash() != null;
    }

    @Override
    public ResponseEntity<OperationDto> getOperation(final Long operationId) {
        log.info("Retrieving operation with ID: {}", operationId);

        if (operationId == null || operationId <= 0) {
            log.error("Invalid operation ID: {}", operationId);
            return ResponseEntity.badRequest().build();
        }

        return operationRepository.findById(operationId)
                .map(this::mapToDto)
                .map(ResponseEntity::ok)
                .orElseGet(() -> {
                    log.info("Operation not found with ID: {}", operationId);
                    return ResponseEntity.notFound().build();
                });
    }

    @Override
    public ResponseEntity<Page<OperationDto>> getOperationsByUserAndType(
            String username, String type, String page, String size) {

        log.info("Retrieving operations for user: {} with type: {}", username, type);

        if (isNullOrEmpty(username) || isNullOrEmpty(type)) {
            log.error("Invalid username or type: username={}, type={}", username, type);
            return ResponseEntity.badRequest().build();
        }

        int pageNumber = parseOrDefault(page, 0);
        int pageSize = parseOrDefault(size, 100);

        if (pageNumber < 0 || pageSize <= 0) {
            log.error("Invalid page or size: page={}, size={}", pageNumber, pageSize);
            return ResponseEntity.badRequest().build();
        }

        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Operation> operationsPage = operationRepository.findByUsernameAndType(username, type, pageable);

        if (operationsPage.isEmpty()) {
            log.info("No operations found for user: {} with type: {}", username, type);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Page.empty());
        }

        Users userForType = userRepository.getUsersByUsername(username);
        final String userLocalCurrencyForType = (userForType != null && userForType.getCountryCurrency() != null)
                ? userForType.getCountryCurrency().getCurrencyCode()
                : null;

        List<OperationDto> filteredDtos = operationsPage.getContent().stream()
                .filter(op -> !OperationDesignation.isHiddenSystemTransfer(
                        op.getDesignation(), op.getCounterpartUsername(), op.getAmount()))
                .map(op -> mapToDto(op, userLocalCurrencyForType))
                .collect(java.util.stream.Collectors.toList());

        long hiddenCount = operationsPage.getContent().size() - filteredDtos.size();
        Page<OperationDto> dtoPage = new org.springframework.data.domain.PageImpl<>(
                filteredDtos,
                pageable,
                operationsPage.getTotalElements() - hiddenCount
        );

        log.info("Found {} operations for user: {} with type: {} (hidden {} system transfers)",
                dtoPage.getTotalElements(), username, type, hiddenCount);
        return ResponseEntity.ok(dtoPage);
    }

    @Override
    public ResponseEntity<Page<OperationDto>> getAllOperationsByUser(final String username, final String page, final String size) {
        log.info("Retrieving all operations for user: {}", username);

        if (isNullOrEmpty(username)) {
            log.error("Invalid username: {}", username);
            return ResponseEntity.badRequest().build();
        }

        int pageNumber = parseOrDefault(page, 0);
        int pageSize = parseOrDefault(size, 100);

        if (pageNumber < 0 || pageSize <= 0) {
            log.error("Invalid page or size: page={}, size={}", pageNumber, pageSize);
            return ResponseEntity.badRequest().build();
        }

        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Operation> operationsPage = operationRepository.findByUsername(username, pageable);

        Users userForAll = userRepository.getUsersByUsername(username);
        final String userLocalCurrencyForAll = (userForAll != null && userForAll.getCountryCurrency() != null)
                ? userForAll.getCountryCurrency().getCurrencyCode()
                : null;

        List<OperationDto> filteredDtos = operationsPage.getContent().stream()
                .filter(op -> {
                    if (!username.equals(op.getUsername())) {
                        log.warn("⚠️ [SECURITE] opération {} appartient à {} mais demandée par {}",
                                op.getId(), op.getUsername(), username);
                        return false;
                    }
                    // Exclure les anciens TRANSFERT en crypto
                    if ("TRANSFERT".equalsIgnoreCase(op.getDesignation())) {
                        if (isCryptoCurrency(op.getDevise())) {
                            return false;
                        }
                    }
                    // Exclure les transferts techniques (off-ramp → wallet externe) :
                    // la valeur métier est déjà représentée par l'opération "Retrait" du provider.
                    if (OperationDesignation.isHiddenSystemTransfer(
                            op.getDesignation(), op.getCounterpartUsername(), op.getAmount())) {
                        return false;
                    }
                    return true;
                })
                .map(op -> mapToDto(op, userLocalCurrencyForAll))
                .collect(java.util.stream.Collectors.toList());

        Page<OperationDto> dtoPage = new org.springframework.data.domain.PageImpl<>(
                filteredDtos,
                pageable,
                operationsPage.getTotalElements() - (operationsPage.getContent().size() - filteredDtos.size())
        );

        log.info("Found {} operations for user: {}", dtoPage.getTotalElements(), username);
        return ResponseEntity.ok(dtoPage);
    }

    @Override
    public ResponseEntity<String> updateOperation(final OperationUpdateDto dto) {
        log.info("Updating operation for user: {}, hash={}", dto.getUsername(), dto.getOperationHash());

        Operation operation = operationRepository.findByOperationHash(dto.getOperationHash());
        if (operation == null) {
            log.error("No operation found for given hash: {}", dto.getOperationHash());
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Aucune opération trouvée pour les critères spécifiés.");
        }

        updateOperationFields(operation, dto);
        operationRepository.save(operation);

        Users user = userRepository.getUsersByUsername(operation.getUsername());
        if (user == null) {
            log.error("User not found: {}", operation.getUsername());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Utilisateur non trouvé");
        }

        Wallet wallet = walletRepository.findWalletByUsers(user).stream().findFirst().orElse(null);
        if (wallet == null) {
            log.error("Wallet not found for user: {}", operation.getUsername());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Wallet non trouvé");
        }

        updateWalletBalance(wallet, operation);
        walletRepository.save(wallet);

        return ResponseEntity.ok("Opération mise à jour avec succès pour l'utilisateur : " + operation.getUsername());
    }

    private void updateOperationFields(Operation operation, OperationUpdateDto dto) {
        if (!Objects.equals(dto.getAmount(), operation.getAmount())) {
            operation.setAmount(dto.getAmount());
        }
        if (!Objects.equals(dto.getConvertedAmount(), operation.getConvertedAmount())) {
            operation.setConvertedAmount(dto.getConvertedAmount());
        }
        if (!Objects.equals(dto.getDevise(), operation.getDevise())) {
            operation.setDevise(dto.getDevise());
        }
        if (dto.getStatus() != null) {
            operation.setStatus(dto.getStatus());
        }
        operation.setUpdatedAt(LocalDateTime.now());
    }

    private void updateWalletBalance(Wallet wallet, Operation operation) {
        double amount = operation.getAmount() != null ? operation.getAmount() : 0.0;
        double converted = operation.getConvertedAmount() != null ? operation.getConvertedAmount() : 0.0;

        switch (operation.getType().toUpperCase()) {
            case "CREDIT" -> {
                wallet.setBalance(wallet.getBalance() + amount);
                wallet.setDeviseBalance(wallet.getDeviseBalance() + converted);
            }
            case "DEBIT" -> {
                wallet.setBalance(wallet.getBalance() - amount);
                wallet.setDeviseBalance(wallet.getDeviseBalance() - converted);
            }
            default -> {
                log.error("Invalid operation type: {}", operation.getType());
                throw new IllegalArgumentException("Type d'opération invalide");
            }
        }

        // 🔔 Notification push centralisée — mise à jour du solde
        try {
            String username = wallet.getUsers() != null ? wallet.getUsers().getUsername() : null;
            if (username != null) {
                String label = "CREDIT".equalsIgnoreCase(operation.getType()) ? "Fonds reçus" : "Envoi confirmé";
                String amountInfo = operation.getProviderAmount() + " " + operation.getProviderDevise()
                        + " (" + operation.getAmount() + " " + operation.getDevise() + ")";
                transactionNotificationHelper.notifyTransactionSuccess(
                        username,
                        label + " : " + amountInfo,
                        operation.getOperationHash()
                );
            }
        } catch (Exception e) {
            log.warn("⚠️ Échec notification wallet update: {}", e.getMessage());
        }

    }
    // ═══════════════════════════════════════════════════════════════════════
    // mapToDto — REFACTORISÉ
    // ═══════════════════════════════════════════════════════════════════════

    private OperationDto mapToDto(Operation operation) {
        Users user = userRepository.getUsersByUsername(operation.getUsername());
        String userLocalCurrency = null;
        if (user != null && user.getCountryCurrency() != null && user.getCountryCurrency().getCurrencyCode() != null) {
            userLocalCurrency = user.getCountryCurrency().getCurrencyCode();
        }
        return mapToDto(operation, userLocalCurrency);
    }

    private OperationDto mapToDto(Operation operation, String userLocalCurrency) {
        OperationDto dto = new OperationDto();
        dto.setId(operation.getId());
        dto.setUsername(operation.getUsername());
        dto.setDesignation(operation.getDesignation());
        dto.setType(operation.getType());
        dto.setCreatedAt(operation.getCreatedAt());
        dto.setUpdatedAt(operation.getUpdatedAt());
        dto.setOperationHash(operation.getOperationHash());

        // ── Statut normalisé ──
        dto.setStatus(normalizeStatus(operation.getStatus()));

        // ── Original (brut de la BDD) ──
        dto.setOriginalAmount(operation.getAmount());
        dto.setOriginalDevise(operation.getDevise());

        // ── Provider (USDC) ──
        dto.setProviderAmount(operation.getProviderAmount());
        dto.setProviderDevise(operation.getProviderDevise());

        // ── Contrepartie ──
        dto.setCounterpartDisplayName(operation.getCounterpartDisplayName());

        // ── TransactionType & Provider (résolution) ──
        if (operation.getTransactionType() != null && !operation.getTransactionType().isBlank()) {
            dto.setTransactionType(operation.getTransactionType());
        } else {
            dto.setTransactionType(
                    OperationDesignation.resolveTransactionType(operation.getDesignation(), operation.getType()));
        }

        if (operation.getProvider() != null && !operation.getProvider().isBlank()) {
            dto.setProvider(operation.getProvider());
        } else {
            dto.setProvider(OperationDesignation.resolveProvider(operation.getDesignation()));
        }

        // ── Montant affiché (converti en devise locale) ──
        Double displayAmount = resolveDisplayAmount(operation, userLocalCurrency);
        dto.setAmount(displayAmount);
        dto.setConvertedAmount(displayAmount);

        // ── Devise affichée ──
        dto.setDevise(userLocalCurrency != null ? userLocalCurrency : operation.getDevise());

        return dto;
    }

    /**
     * Normalise le statut pour l'affichage.
     * Harmonise les variantes (valide → VALIDEE, etc.)
     */
    private String normalizeStatus(String status) {
        if (status == null) return "PENDING";
        return switch (status.toUpperCase()) {
            case "VALIDEE", "VALIDE", "COMPLETED", "SETTLED", "SUCCESS" -> "VALIDEE";
            case "ECHOUEE", "FAILED", "DECLINED", "ERROR" -> "ECHOUEE";
            case "EN_COURS", "PROCESSING", "SETTLING" -> "EN_COURS";
            case "PENDING", "PENDING_CONDITION" -> "PENDING";
            case "REMBOURSEE", "REFUNDED" -> "REMBOURSEE";
            case "EXPIREE", "EXPIRED" -> "EXPIREE";
            default -> status;
        };
    }

    /**
     * Résout le montant à afficher en devise locale de l'utilisateur.
     * Priorité :
     * 1. Si amount + devise existent et que la devise == devise locale → utiliser directement
     * 2. Si amount + devise existent et devise != devise locale → convertir
     * 3. Si providerAmount + providerDevise existent → convertir depuis USDC
     * 4. Fallback sur convertedAmount de la BDD
     * 5. Fallback 0.0
     */
    private Double resolveDisplayAmount(Operation operation, String userLocalCurrency) {
        // CAS 1 & 2 : amount en devise locale connue
        if (operation.getAmount() != null && operation.getDevise() != null && userLocalCurrency != null) {
            if (userLocalCurrency.equalsIgnoreCase(operation.getDevise())) {
                return operation.getAmount();
            }
            // Convertir
            Double converted = convertCurrency(operation.getDevise(), userLocalCurrency, operation.getAmount());
            if (converted != null) return converted;
        }

        // CAS 3 : providerAmount (USDC) → devise locale
        if (operation.getProviderAmount() != null && operation.getProviderDevise() != null && userLocalCurrency != null) {
            Double converted = convertCurrency(operation.getProviderDevise(), userLocalCurrency, operation.getProviderAmount());
            if (converted != null) return converted;
        }

        // CAS 4 : Legacy — convertedAmount + recherche devise Guardarian
        if (operation.getConvertedAmount() != null && userLocalCurrency != null) {
            String sourceCurrency = resolveSourceCurrencyForLegacy(operation);
            if (sourceCurrency != null && !userLocalCurrency.equalsIgnoreCase(sourceCurrency)) {
                Double converted = convertCurrency(sourceCurrency, userLocalCurrency, operation.getConvertedAmount());
                if (converted != null) return converted;
            }
            return operation.getConvertedAmount();
        }

        // CAS 5 : amount brut
        if (operation.getAmount() != null) return operation.getAmount();

        return 0.0;
    }

    /**
     * Pour les anciennes opérations Guardarian, retrouve la devise source depuis GuardarianTransaction.
     */
    private String resolveSourceCurrencyForLegacy(Operation operation) {
        String designation = operation.getDesignation() != null ? operation.getDesignation().toUpperCase() : "";

        if (designation.contains("GUADARIAN") || designation.contains("GUARDARIAN")) {
            try {
                if (operation.getOperationHash() != null) {
                    Long externalTransactionId = Long.parseLong(operation.getOperationHash());
                    var txOpt = guadarianTransactionRepository.findByExternalTransactionId(externalTransactionId);
                    if (txOpt.isPresent()) {
                        GuardarianTransaction tx = txOpt.get();
                        if (designation.contains("ONRAMP")) {
                            return tx.getFromCurrency() != null ? tx.getFromCurrency() : "EUR";
                        } else {
                            return tx.getToCurrency() != null ? tx.getToCurrency() : "EUR";
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("⚠️ Erreur récupération devise Guardarian pour opération {}: {}", operation.getId(), e.getMessage());
            }
            return "EUR"; // Fallback
        }

        return operation.getDevise();
    }

    /**
     * Appel API de conversion. Retourne null si la conversion échoue.
     */
    private Double convertCurrency(String from, String to, Double amount) {
        if (from == null || to == null || amount == null || from.equalsIgnoreCase(to)) return amount;
        try {
            var response = currencyFreaksClientService.convertCurrency(from, to, amount);
            if (response != null && response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String str = response.getBody().getConvertedAmount();
                if (str != null && !str.isBlank()) {
                    return Double.parseDouble(str);
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ Conversion {} → {} échouée pour montant {}: {}", from, to, amount, e.getMessage());
        }
        return null;
    }

    private boolean isNullOrEmpty(String value) {
        return value == null || value.trim().isEmpty();
    }

    private int parseOrDefault(String value, int defaultValue) {
        try {
            return (value != null && !value.isEmpty()) ? Integer.parseInt(value) : defaultValue;
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static final Set<String> KNOWN_CRYPTOS = new HashSet<>(Arrays.asList(
            "USDC", "USDT", "BTC", "ETH", "MATIC", "BNB", "SOL", "XRP",
            "ADA", "DOT", "DOGE", "LTC", "BCH", "TRX", "AVAX", "LINK",
            "UNI", "ATOM", "WETH", "WBTC", "DAI", "BUSD", "SHIB", "AAVE",
            "COMP", "MKR", "SUSHI", "YFI", "CRV", "SNX", "FTM", "ALGO",
            "NEAR", "APT", "SUI", "ARB", "OP"
    ));

    private static final Set<String> CRYPTO_NETWORK_SUFFIXES = new HashSet<>(Arrays.asList(
            "_POLYGON", "_BASE", "_ARBITRUM", "_OPTIMISM", "_BSC"
    ));

    private boolean isCryptoCurrency(String currency) {
        if (currency == null || currency.trim().isEmpty()) return false;
        String upper = currency.trim().toUpperCase();
        if (KNOWN_CRYPTOS.contains(upper)) return true;
        int idx = upper.indexOf('_');
        if (idx > 0) {
            if (KNOWN_CRYPTOS.contains(upper.substring(0, idx))) return true;
            for (String suffix : CRYPTO_NETWORK_SUFFIXES) {
                if (upper.endsWith(suffix)) return true;
            }
        }
        return false;
    }
}
