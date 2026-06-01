package org.akuunda.akuundawallet.backoffice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.akuunda.akuundawallet.backoffice.dto.ApiSuccess;
import org.akuunda.akuundawallet.backoffice.dto.PaginatedResponse;
import org.akuunda.akuundawallet.backoffice.repository.BackofficeUserRepository;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.dao.OperationRepository;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.wallet.api.enums.OperationDesignation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Portefeuille marchand (Espace Pro) — soldes et historique liés au compte Akuunda
 * résolu via l'email du JWT (users.username ou backoffice_user.wallet_username).
 */
@RestController
@RequestMapping(path = "/api/v1/pro/wallet", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Backoffice - Pro Wallet")
@RequiredArgsConstructor
public class BackofficeProWalletController {

    private static final Set<String> PENDING_STATUSES = Set.of(
            "NEW", "EN_COURS", "EN ATTENTE", "PENDING", "PENDING_CONDITION",
            "PROCESSING", "INITIALIZED", "INITIATED", "WAITING"
    );

    private static final Set<String> SUCCESS_STATUSES = Set.of(
            "VALIDEE", "VALID", "VALIDATED", "SUCCESS", "SUCESS", "SUCCEEDED",
            "COMPLETED", "COMPLETE", "DONE", "CONFIRMED", "APPROUVE", "APPROVED",
            "PAID", "OK", "REUSSI", "REUSSIE", "Complétée"
    );

    private static final Set<String> USD_PEGGED = Set.of(
            "USDC", "USDT", "USDP", "DAI", "BUSD", "TUSD", "FDUSD", "PYUSD", "USDD"
    );

    private static final Set<String> FIAT_WHITELIST = Set.of(
            "EUR", "USD", "GBP", "CHF", "CAD", "AUD", "JPY", "CNY",
            "XOF", "XAF", "MAD", "DZD", "TND", "EGP",
            "NGN", "GHS", "KES", "UGX", "TZS", "ZAR", "RWF",
            "INR", "AED", "SAR", "TRY", "BRL", "MXN"
    );

    private final WalletRepository walletRepository;
    private final OperationRepository operationRepository;
    private final UserRepository userRepository;
    private final BackofficeUserRepository backofficeUserRepository;

    @GetMapping("/balance")
    @Operation(summary = "Solde du wallet marchand (devise locale + solde USD)")
    public ResponseEntity<ApiSuccess<Map<String, Object>>> getBalance() {
        String username = resolveWalletUsername();
        Wallet wallet = walletRepository.findByUsersUsername(username);

        String fiatCode = "XOF";
        double fiatBalance = 0d;
        double usdcBalance = 0d;
        if (wallet != null) {
            usdcBalance = wallet.getBalance();
            if (wallet.getCurrency() != null && wallet.getCurrency().getCurrencyCode() != null) {
                String raw = wallet.getCurrency().getCurrencyCode();
                fiatCode = normalizeFiat(raw);
                if (fiatCode == null) fiatCode = "USD";
            }
            fiatBalance = wallet.getDeviseBalance() != 0d ? wallet.getDeviseBalance() : 0d;
        }

        List<org.akuunda.akuundawallet.wallet.api.entities.Operation> ops = loadVisibleOperations(username);
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        double pendingIn = 0d;
        double pendingOut = 0d;
        double monthIn = 0d;
        double monthOut = 0d;
        long monthOps = 0L;

        for (org.akuunda.akuundawallet.wallet.api.entities.Operation o : ops) {
            if (o.getStatus() != null && PENDING_STATUSES.contains(o.getStatus().toUpperCase(Locale.ROOT))) {
                double amt = o.getAmount() != null ? o.getAmount() : 0d;
                if (OperationDesignation.isCredit(o.getDesignation(), o.getType(),
                        "CREDIT".equalsIgnoreCase(o.getType()))) {
                    pendingIn += amt;
                } else {
                    pendingOut += amt;
                }
            }
            if (o.getCreatedAt() == null || o.getCreatedAt().isBefore(startOfMonth)) continue;
            if (!isSuccess(o)) continue;
            monthOps++;
            double displayAmt = amountInDisplay(o, fiatCode);
            boolean credit = OperationDesignation.isCredit(o.getDesignation(), o.getType(),
                    "CREDIT".equalsIgnoreCase(o.getType()));
            if (credit) monthIn += displayAmt;
            else monthOut += displayAmt;
        }

        // Un seul wallet « fiat » côté UI : le solde crypto (USDC en base) est exposé
        // via usdBalance pour la bascule EUR ↔ USD — jamais de puce USDC séparée.
        Map<String, Object> fiatWallet = walletEntry(fiatCode, fiatBalance, pendingIn, pendingOut, 0d, fiatBalance);
        fiatWallet.put("usdBalance", Math.round(usdcBalance * 100.0) / 100.0);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("wallets", List.of(fiatWallet));
        body.put("displayCurrency", fiatCode);
        body.put("monthInflow", Math.round(monthIn * 100.0) / 100.0);
        body.put("monthOutflow", Math.round(monthOut * 100.0) / 100.0);
        body.put("monthOperations", monthOps);
        body.put("username", username);
        body.put("totalValueEUR", 0);

        return ResponseEntity.ok(ApiSuccess.of(body));
    }

    @GetMapping("/history")
    @Operation(summary = "Historique des mouvements du wallet marchand")
    public ResponseEntity<ApiSuccess<PaginatedResponse<Map<String, Object>>>> getHistory(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        String username = resolveWalletUsername();
        int size = Math.min(100, Math.max(1, limit));
        Page<org.akuunda.akuundawallet.wallet.api.entities.Operation> p = operationRepository.findByUsername(
                username,
                PageRequest.of(Math.max(0, page - 1), size, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<Map<String, Object>> items = p.getContent().stream()
                .filter(op -> !OperationDesignation.isHiddenSystemTransfer(
                        op.getDesignation(), op.getCounterpartUsername(), op.getAmount()))
                .map(this::toHistoryRow)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiSuccess.of(
                PaginatedResponse.of(items, page, size, p.getTotalElements())));
    }

    private Map<String, Object> walletEntry(String currency, double balance,
                                            double pendingIn, double pendingOut,
                                            double frozen, double available) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("currency", currency);
        m.put("balance", Math.round(balance * 100.0) / 100.0);
        m.put("pendingIn", Math.round(pendingIn * 100.0) / 100.0);
        m.put("pendingOut", Math.round(pendingOut * 100.0) / 100.0);
        m.put("frozen", Math.round(frozen * 100.0) / 100.0);
        m.put("available", Math.round(available * 100.0) / 100.0);
        return m;
    }

    private Map<String, Object> toHistoryRow(org.akuunda.akuundawallet.wallet.api.entities.Operation op) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", op.getId());
        row.put("reference", op.getOperationHash() != null ? op.getOperationHash() : "");
        row.put("operationHash", op.getOperationHash());
        row.put("type", op.getType() != null ? op.getType() : "");
        row.put("designation", op.getDesignation() != null ? op.getDesignation() : "");
        row.put("transactionType", op.getTransactionType() != null ? op.getTransactionType() : "");
        row.put("provider", op.getProvider() != null ? op.getProvider() : "");
        row.put("status", op.getStatus() != null ? op.getStatus() : "");
        row.put("amount", op.getAmount() != null ? op.getAmount() : 0);
        row.put("devise", op.getDevise() != null ? op.getDevise() : "");
        row.put("currency", op.getDevise() != null ? op.getDevise() : "");
        row.put("convertedAmount", op.getConvertedAmount());
        row.put("counterpartDisplayName", op.getCounterpartDisplayName() != null ? op.getCounterpartDisplayName() : "");
        row.put("counterpartUsername", op.getCounterpartUsername() != null ? op.getCounterpartUsername() : "");
        row.put("walletBalanceAfter", op.getWalletBalanceAfter());
        row.put("walletDeviseBalanceAfter", op.getWalletDeviseBalanceAfter());
        row.put("createdAt", op.getCreatedAt() != null ? op.getCreatedAt().toString() : "");
        return row;
    }

    private List<org.akuunda.akuundawallet.wallet.api.entities.Operation> loadVisibleOperations(String username) {
        return operationRepository.findByUsername(
                        username,
                        PageRequest.of(0, 5000, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent().stream()
                .filter(op -> !OperationDesignation.isHiddenSystemTransfer(
                        op.getDesignation(), op.getCounterpartUsername(), op.getAmount()))
                .collect(Collectors.toList());
    }

    private String resolveWalletUsername() {
        JwtAuthenticationToken auth = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = auth.getToken();
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email absent du token JWT");
        }
        Optional<String> fromBackoffice = backofficeUserRepository.findByEmailIgnoreCase(email.trim())
                .map(u -> u.getWalletUsername())
                .filter(w -> w != null && !w.isBlank());
        if (fromBackoffice.isPresent()) {
            return fromBackoffice.get();
        }
        Optional<Users> akuundaUser = userRepository.findFirstByEmailOrderByCreatedAtAsc(email.trim());
        if (akuundaUser.isPresent()) {
            String username = akuundaUser.get().getUsername();
            if (username != null && !username.isBlank()) {
                return username;
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Aucun compte Akuunda Pay associé à cet email (" + email + ")");
    }

    private static boolean isSuccess(org.akuunda.akuundawallet.wallet.api.entities.Operation o) {
        return o.getStatus() != null && SUCCESS_STATUSES.contains(o.getStatus().toUpperCase(Locale.ROOT));
    }

    private static String normalizeFiat(String code) {
        if (code == null || code.isBlank()) return null;
        String c = code.trim().toUpperCase(Locale.ROOT);
        if (FIAT_WHITELIST.contains(c)) return c;
        if (USD_PEGGED.contains(c)) return "USD";
        return c;
    }

    private static double amountInDisplay(org.akuunda.akuundawallet.wallet.api.entities.Operation o, String displayCur) {
        if (o == null) return 0d;
        Double converted = o.getConvertedAmount();
        if (converted != null && converted != 0d) return converted;
        if (o.getAmount() == null) return 0d;
        if (displayCur == null || o.getDevise() == null
                || displayCur.equalsIgnoreCase(o.getDevise())) {
            return o.getAmount();
        }
        return 0d;
    }
}
