package org.akuunda.akuundawallet.backoffice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.backoffice.dto.ApiSuccess;
import org.akuunda.akuundawallet.backoffice.util.ProMerchantVolumeHelper;
import org.akuunda.akuundawallet.wallet.service.infrastructure.CurrencyFreaksClientService;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.dao.OneTimePaymentLinkRepository;
import org.akuunda.akuundawallet.wallet.api.dao.OperationRepository;
import org.akuunda.akuundawallet.wallet.api.dao.PaymentLinkRepository;
import org.akuunda.akuundawallet.wallet.api.dao.PermanentLinkRepository;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.wallet.api.enums.OperationDesignation;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Endpoints du tableau de bord marchand (Espace Pro).
 *
 * Toutes les agrégations sont scopées sur le username Akuunda résolu depuis le JWT
 * (email → table users, fallback table backoffice_user). On filtre les transferts
 * techniques (off-ramp → wallet externe) côté affichage : ils sont déjà
 * représentés par l'opération « Retrait » du provider et créeraient des doublons
 * dans les KPI et la liste « Dernières transactions ».
 */
@Slf4j
@RestController
@RequestMapping(path = "/api/v1/pro/dashboard", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Backoffice - Pro Dashboard")
@RequiredArgsConstructor
public class BackofficeProDashboardController {

    private final WalletRepository walletRepository;
    private final OperationRepository operationRepository;
    private final UserRepository userRepository;
    private final PaymentLinkRepository paymentLinkRepository;
    private final OneTimePaymentLinkRepository oneTimePaymentLinkRepository;
    private final PermanentLinkRepository permanentLinkRepository;
    private final CurrencyFreaksClientService currencyFreaksClientService;

    /** Statuts considérés comme « en attente » (alignement transactions admin). */
    private static final Set<String> PENDING_STATUSES = Set.of(
            "NEW", "EN_COURS", "EN ATTENTE", "PENDING", "PENDING_CONDITION",
            "PROCESSING", "INITIALIZED", "INITIATED", "WAITING"
    );

    /** Statuts considérés comme « succès » (pour les volumes et chiffrages). */
    private static final Set<String> SUCCESS_STATUSES = Set.of(
            "VALIDEE", "VALID", "VALIDATED", "SUCCESS", "SUCESS", "SUCCEEDED",
            "COMPLETED", "COMPLETE", "DONE", "CONFIRMED", "APPROUVE", "APPROVED",
            "PAID", "OK", "REUSSI", "REUSSIE", "Complétée"
    );

    /**
     * Statuts qui marquent un échec définitif. Une opération en échec n'est jamais
     * comptée dans un KPI de flux quand bien même `walletBalanceAfter` aurait été
     * renseigné dans le passé.
     */
    private static final Set<String> FAILED_STATUSES = Set.of(
            "ECHOUEE", "EXPIRED", "CANCELLED", "CANCELED", "REJETEE", "REJECTED",
            "FAILED", "FAIL", "REFUSED", "REFUSE", "KO", "ERROR", "ERREUR"
    );

    /**
     * Stablecoins indexés sur l'USD : tout ce qui en remonte est présenté en USD
     * dans l'UI marchand (pas de « USDC », « USDT », « DAI »…).
     */
    private static final Set<String> USD_PEGGED = Set.of(
            "USDC", "USDT", "USDP", "DAI", "BUSD", "TUSD", "FDUSD", "PYUSD", "USDD"
    );

    /**
     * Crypto-monnaies natives autorisées dans le wallet Akuunda. Comme les
     * marchands raisonnent toujours en fiat, on les valorise en USD pour
     * éviter tout affichage type « POL », « MATIC », « ETH » côté Pro.
     */
    private static final Set<String> CRYPTO_AS_USD = Set.of(
            "POL", "MATIC", "ETH", "BNB", "BTC", "AVAX", "SOL", "ARB", "OP", "TRX"
    );

    /**
     * Codes fiat affichables en clair côté UI Pro (whitelist défensive).
     * Tout ce qui ne tombe ni dans la whitelist ni dans une famille crypto
     * connue est conservé tel quel (au cas où la base contienne un code rare),
     * mais la philosophie est : « le marchand ne devrait jamais voir autre
     * chose qu'un code ISO 4217 ou USD ».
     */
    private static final Set<String> FIAT_WHITELIST = Set.of(
            "EUR", "USD", "GBP", "CHF", "CAD", "AUD", "JPY", "CNY",
            "XOF", "XAF", "MAD", "DZD", "TND", "EGP",
            "NGN", "GHS", "KES", "UGX", "TZS", "ZAR", "RWF",
            "INR", "AED", "SAR", "TRY", "BRL", "MXN"
    );

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    /** Résout le username Akuunda du marchand connecté (email JWT → users.username). */
    private String resolveWalletUsername() {
        JwtAuthenticationToken auth = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = auth.getToken();
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email absent du token JWT");
        }
        Optional<Users> akuundaUser = userRepository.findFirstByEmailOrderByCreatedAtAsc(email);
        if (akuundaUser.isPresent()) {
            String username = akuundaUser.get().getUsername();
            if (username != null && !username.isBlank()) return username;
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Aucun compte Akuunda Pay associé à cet email (" + email + ")");
    }

    private Optional<Users> resolveAkuundaUser() {
        JwtAuthenticationToken auth = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = auth.getToken();
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) return Optional.empty();
        return userRepository.findFirstByEmailOrderByCreatedAtAsc(email);
    }

    /** Toutes les opérations d'un user, hors transferts techniques masqués. */
    private List<org.akuunda.akuundawallet.wallet.api.entities.Operation> loadVisibleOperations(String username) {
        return operationRepository.findByUsername(
                        username,
                        PageRequest.of(0, 5000, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent().stream()
                .filter(op -> !OperationDesignation.isHiddenSystemTransfer(
                        op.getDesignation(), op.getCounterpartUsername(), op.getAmount()))
                .collect(Collectors.toList());
    }

    @GetMapping("/stats")
    @Operation(summary = "Statistiques marchand (solde, transactions, volumes)")
    public ResponseEntity<ApiSuccess<Object>> getStats(@RequestParam(required = false) String period) {
        String username = resolveWalletUsername();
        List<org.akuunda.akuundawallet.wallet.api.entities.Operation> ops = loadVisibleOperations(username);

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();

        // Toutes les agrégations qui parlent de « flux » (transactions du jour,
        // volume / entrées / sorties / opérations du mois) ne portent QUE sur les
        // opérations réellement réussies. Les opérations en attente sont remontées
        // séparément dans `pendingPayments`. Les opérations échouées ne comptent
        // dans aucun KPI de flux : elles n'ont pas modifié le solde.
        long totalTransactionsToday = ops.stream()
                .filter(o -> o.getCreatedAt() != null && !o.getCreatedAt().isBefore(startOfDay))
                .filter(BackofficeProDashboardController::isSuccess)
                .count();

        long pendingPayments = ops.stream()
                .filter(o -> o.getStatus() != null && PENDING_STATUSES.contains(o.getStatus().toUpperCase()))
                .count();

        // Solde wallet du marchand : on s'aligne sur ce qu'affiche /pro/wallet/balance
        // (la page « Portefeuille » de l'espace Pro). Un seul chiffre, dans la
        // devise d'affichage du wallet (deviseBalance), pour éviter le « 1 EUR · 1 POL »
        // qui mélangeait la balance fiat convertie et le solde natif.
        Map<String, Double> walletBalance = new HashMap<>();
        Wallet wallet = walletRepository.findByUsersUsername(username);
        String walletCurrency = "XOF";
        if (wallet != null) {
            String raw = wallet.getCurrency() != null ? wallet.getCurrency().getCurrencyCode() : "XOF";
            walletCurrency = normalizeFiat(raw);
            if (walletCurrency == null) walletCurrency = "USD";
            double display = wallet.getDeviseBalance() != 0d ? wallet.getDeviseBalance() : wallet.getBalance();
            walletBalance.put(walletCurrency, display);
        } else {
            walletBalance.put(walletCurrency, 0d);
        }

        // Entrées / sorties / volume du mois — strictement les opérations réussies
        // (statuts dans SUCCESS_STATUSES). Pour rester aligné avec la page
        // « Portefeuille », on convertit tous les montants en devise d'affichage du
        // wallet via `convertedAmount` lorsqu'il est présent (les opérations
        // off-ramp/on-ramp en USDC remontent ainsi en EUR comme dans l'historique).
        final String displayCur = walletCurrency;
        double monthInflow = 0d, monthOutflow = 0d;
        long monthOpsCount = 0L;
        Map<String, Double> volumeThisMonth = new HashMap<>();
        for (var o : ops) {
            if (o.getCreatedAt() == null || o.getCreatedAt().isBefore(startOfMonth)) continue;
            if (!isSuccess(o)) continue;
            monthOpsCount++;
            double displayAmt = merchantAmountInDisplay(o, displayCur);
            // On se base sur la designation plutôt que sur `type` (qui est
            // historiquement inversé sur Guardarian/MtPelerin). `isCredit`
            // applique la même logique que le front (cf. walletMapping.ts).
            boolean isCredit = OperationDesignation.isCredit(
                    o.getDesignation(), o.getType(),
                    "CREDIT".equalsIgnoreCase(o.getType()));
            if (isCredit) monthInflow += displayAmt;
            else monthOutflow += displayAmt;
            if (isCredit) {
                // Volume legacy par devise : on regroupe sous le code fiat normalisé
                // (USDC/POL → USD, XOF → XOF, etc.) pour ne jamais exposer un
                // symbole crypto au front. Le montant utilisé est le montant en
                // devise du wallet (convertedAmount) — c'est ce qui correspond à
                // ce que voit le marchand dans son historique.
                String cur = normalizeFiat(o.getDevise());
                if (cur == null) cur = displayCur;
                double amt = displayAmt;
                volumeThisMonth.merge(cur, amt, Double::sum);
            }
        }

        // Clients = contreparties créditrices distinctes (paiements réussis uniquement)
        // Même logique que les KPI : la designation prime sur `type`.
        long clientCount = ops.stream()
                .filter(o -> OperationDesignation.isCredit(o.getDesignation(), o.getType(),
                        "CREDIT".equalsIgnoreCase(o.getType())))
                .filter(BackofficeProDashboardController::isSuccess)
                .map(o -> o.getCounterpartUsername() != null ? o.getCounterpartUsername() : o.getCounterpartDisplayName())
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .count();

        // Liens de paiement actifs (permanents + one-time non expirés)
        long activePaymentLinks = 0L;
        long activeEscrow = 0L;
        Optional<Users> akuundaUser = resolveAkuundaUser();
        if (akuundaUser.isPresent()) {
            try {
                activePaymentLinks += paymentLinkRepository
                        .findByCreatorAndIsActiveTrueOrderByCreatedAtDesc(akuundaUser.get()).size();
            } catch (Exception ignored) { /* repo / colonne absent */ }
            try {
                activePaymentLinks += permanentLinkRepository
                        .findByCreatorAndIsActiveTrueOrderByCreatedAtDesc(akuundaUser.get()).size();
            } catch (Exception ignored) { /* idem */ }
            try {
                activeEscrow = oneTimePaymentLinkRepository.findByCreatorOrderByCreatedAtDesc(akuundaUser.get())
                        .stream()
                        .filter(l -> {
                            String st = l.getStatus();
                            return st != null && !st.equalsIgnoreCase("EXPIRED")
                                    && !st.equalsIgnoreCase("CANCELLED")
                                    && !st.equalsIgnoreCase("COMPLETED");
                        }).count();
            } catch (Exception ignored) { /* idem */ }
        }

        Map<String, Object> body = new HashMap<>();
        body.put("walletBalance", walletBalance);
        body.put("walletCurrency", displayCur);
        body.put("totalTransactionsToday", totalTransactionsToday);
        body.put("volumeThisMonth", volumeThisMonth);
        body.put("monthInflow", monthInflow);
        body.put("monthOutflow", monthOutflow);
        body.put("monthOperationsCount", monthOpsCount);
        body.put("pendingPayments", pendingPayments);
        body.put("activePaymentLinks", activePaymentLinks);
        body.put("activeEscrow", activeEscrow);
        body.put("clientCount", clientCount);
        return ResponseEntity.ok(ApiSuccess.of(body));
    }

    /**
     * Une opération « compte » dans les KPI d'activité du tableau de bord
     * (Volume du mois, Entrées, Sorties, Opérations, Transactions du jour, …)
     * uniquement quand son statut est explicitement <b>validé</b>
     * ({@link #SUCCESS_STATUSES}).
     *
     * <p>Les opérations en attente, en cours, initialisées ou en échec sont
     * exclues : elles ne représentent pas encore (ou plus jamais) du flux
     * confirmé pour le marchand. Elles restent visibles dans la page
     * Transactions et côté admin pour audit.</p>
     */
    private static boolean isSuccess(org.akuunda.akuundawallet.wallet.api.entities.Operation o) {
        if (o == null) return false;
        String st = o.getStatus() == null ? "" : o.getStatus().toUpperCase().trim();
        return SUCCESS_STATUSES.contains(st);
    }

    /**
     * Normalise un code devise pour l'UI Pro : ne ressort que des codes
     * « lisibles par un marchand » (EUR, USD, XOF…). Les stablecoins
     * USD-pegged et les crypto-monnaies natives sont remappés sur USD
     * (équivalent dollar), pour éviter tout affichage type « USDC »,
     * « POL » ou « MATIC » dans le tableau de bord.
     */
    private static String normalizeFiat(String code) {
        if (code == null || code.isBlank()) return null;
        String c = code.trim().toUpperCase();
        if (FIAT_WHITELIST.contains(c)) return c;
        if (USD_PEGGED.contains(c) || CRYPTO_AS_USD.contains(c)) return "USD";
        return c; // codes rares : on les laisse passer plutôt que de les masquer
    }

    private String resolveMerchantDisplayCurrency(String merchantUsername) {
        Wallet w = walletRepository.findByUsersUsername(merchantUsername);
        if (w == null || w.getCurrency() == null) return "EUR";
        String n = normalizeFiat(w.getCurrency().getCurrencyCode());
        return n != null ? n : "EUR";
    }

    private double merchantAmountInDisplay(
            org.akuunda.akuundawallet.wallet.api.entities.Operation o, String displayCur) {
        return ProMerchantVolumeHelper.amountInMerchantCurrency(
                o, displayCur, currencyFreaksClientService);
    }

    @GetMapping({"/charts/revenue", "/weekly"})
    @Operation(summary = "Flux entrant/sortant sur 7 jours pour le wallet marchand")
    public ResponseEntity<ApiSuccess<Object>> weekly(
            @RequestParam(required = false) String period,
            @RequestParam(required = false) String granularity,
            @RequestParam(required = false) String currency) {
        String username = resolveWalletUsername();
        List<org.akuunda.akuundawallet.wallet.api.entities.Operation> ops = loadVisibleOperations(username);
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(6);

        // Bucket pré-rempli (jours sans activité = zéros pour la continuité du graph)
        Map<LocalDate, double[]> buckets = new LinkedHashMap<>();
        for (int i = 0; i < 7; i++) buckets.put(from.plusDays(i), new double[]{0d, 0d});

        // Mêmes règles que /stats : on ne trace que les opérations réussies, et on
        // convertit dans la devise d'affichage du wallet pour que le graph soit
        // lisible (pas de mix EUR + INR + USDC qui se cumulent visuellement).
        String displayCur = resolveMerchantDisplayCurrency(username);
        for (var o : ops) {
            if (o.getCreatedAt() == null) continue;
            if (!isSuccess(o)) continue;
            LocalDate d = o.getCreatedAt().toLocalDate();
            if (d.isBefore(from) || d.isAfter(today)) continue;
            double amt = merchantAmountInDisplay(o, displayCur);
            double[] cell = buckets.get(d);
            if (cell == null) continue;
            boolean credit = OperationDesignation.isCredit(o.getDesignation(), o.getType(),
                    "CREDIT".equalsIgnoreCase(o.getType()));
            if (credit) cell[0] += amt;
            else cell[1] += amt;
        }

        List<Map<String, Object>> series = buckets.entrySet().stream()
                .map(e -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("date", e.getKey().format(ISO_DATE));
                    row.put("inflow", e.getValue()[0]);
                    row.put("outflow", e.getValue()[1]);
                    return row;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiSuccess.of(series));
    }

    @GetMapping({"/charts/methods", "/transaction-types"})
    @Operation(summary = "Répartition des paiements (familles, sur 30j)")
    public ResponseEntity<ApiSuccess<Object>> transactionTypes(
            @RequestParam(required = false) String period) {
        String username = resolveWalletUsername();
        List<org.akuunda.akuundawallet.wallet.api.entities.Operation> ops = loadVisibleOperations(username);
        LocalDateTime since = LocalDate.now().minusDays(30).atStartOfDay();

        // Idem /stats : on ne compte que les opérations réussies. La répartition
        // « Méthodes de paiement » ne doit pas être polluée par les essais
        // abandonnés ou les paiements en attente.
        Map<String, Long> byFamily = new LinkedHashMap<>();
        for (var o : ops) {
            if (o.getCreatedAt() == null || o.getCreatedAt().isBefore(since)) continue;
            if (!isSuccess(o)) continue;
            String family = familyOf(o.getDesignation());
            byFamily.merge(family, 1L, Long::sum);
        }
        long total = byFamily.values().stream().mapToLong(Long::longValue).sum();

        List<Map<String, Object>> data = byFamily.entrySet().stream()
                .map(e -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("method", e.getKey());
                    row.put("count", e.getValue());
                    row.put("percentage", total == 0 ? 0 : Math.round((e.getValue() * 100.0) / total));
                    return row;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiSuccess.of(data));
    }

    @GetMapping("/recent-transactions")
    @Operation(summary = "Dernières transactions du marchand")
    public ResponseEntity<ApiSuccess<Object>> getRecentTransactions(@RequestParam(required = false) Integer limit) {
        int n = (limit == null || limit <= 0) ? 10 : Math.min(50, limit);
        String username = resolveWalletUsername();
        List<Map<String, Object>> rows = loadVisibleOperations(username).stream()
                .limit(n)
                .map(o -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", o.getId());
                    row.put("reference", o.getOperationHash() != null ? o.getOperationHash() : "");
                    row.put("type", o.getType() != null ? o.getType() : "");
                    row.put("designation", o.getDesignation() != null ? o.getDesignation() : "");
                    row.put("transactionType", o.getTransactionType() != null ? o.getTransactionType() : "");
                    row.put("provider", o.getProvider() != null ? o.getProvider() : "");
                    row.put("status", o.getStatus() != null ? o.getStatus() : "");
                    row.put("amount", o.getAmount() != null ? o.getAmount() : 0);
                    // Devise réelle de la transaction (XOF reste XOF, USDC
                    // reste USDC). Seuls les KPI agrégés sont convertis dans
                    // la devise du wallet — pas les lignes individuelles.
                    row.put("currency", o.getDevise() != null ? o.getDevise() : "");
                    row.put("counterpartUsername", o.getCounterpartUsername() != null ? o.getCounterpartUsername() : "");
                    row.put("counterpartDisplayName", o.getCounterpartDisplayName() != null ? o.getCounterpartDisplayName() : "");
                    row.put("createdAt", o.getCreatedAt() != null ? o.getCreatedAt().toString() : "");
                    return row;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiSuccess.of(rows));
    }

    @GetMapping("/recent")
    @Operation(summary = "Alias Figma de /recent-transactions")
    public ResponseEntity<ApiSuccess<Object>> recent(@RequestParam(required = false) Integer limit) {
        return getRecentTransactions(limit);
    }

    @GetMapping("/geo/clients-by-country")
    @Operation(summary = "Clients par pays (top contreparties créditrices)")
    public ResponseEntity<ApiSuccess<Object>> clientsByCountry() {
        // Le pays n'est pas porté par Operation, on renvoie une liste vide tant que la
        // donnée n'est pas exposée (cf. WalletRepository.countUsersByCountry côté admin).
        return ResponseEntity.ok(ApiSuccess.of(Collections.emptyList()));
    }

    /** Famille fonctionnelle d'une designation (alignée front ProTransactions). */
    private static String familyOf(String designation) {
        if (designation == null || designation.isBlank()) return "Autres";
        String d = designation.toUpperCase();
        if (d.contains("ON_RAMP") || d.contains("ONRAMP")) return "Rechargements";
        if (d.contains("OFF_RAMP") || d.contains("OFFRAMP")) return "Retraits";
        if (d.contains("CONDITIONAL")) return "Séquestre";
        if (d.contains("QR_PAYMENT")) return "Paiements QR";
        if (d.contains("ONE_TIME_PAYMENT") || d.equals("PAYMENT_RECEIVED")) return "Paiements reçus";
        if (d.contains("TRANSFER")) return "Transferts";
        return "Autres";
    }
}
