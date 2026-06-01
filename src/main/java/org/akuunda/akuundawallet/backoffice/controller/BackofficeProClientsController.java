package org.akuunda.akuundawallet.backoffice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.backoffice.dto.ApiSuccess;
import org.akuunda.akuundawallet.backoffice.dto.PaginatedResponse;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.dao.OperationRepository;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.backoffice.util.ProMerchantVolumeHelper;
import org.akuunda.akuundawallet.wallet.api.enums.OperationDesignation;
import org.akuunda.akuundawallet.wallet.service.infrastructure.CurrencyFreaksClientService;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Endpoints de la page « Mes clients » de l'espace Pro.
 *
 * Modélisation : un « client » d'un marchand est une contrepartie distincte qui
 * lui a versé de l'argent (opérations CREDIT). On dérive donc la liste depuis
 * les `Operation`, on agrège par {@code counterpartUsername} (ou
 * {@code counterpartDisplayName} en fallback) et on enrichit chaque client
 * via la table {@code users} quand le username est résolvable.
 *
 * Les transferts techniques (off-ramp → wallet externe) sont masqués comme
 * partout ailleurs côté Pro pour ne pas polluer la base de clients avec des
 * pseudo-comptes systèmes.
 */
@Slf4j
@RestController
@RequestMapping(path = "/api/v1/pro/clients", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Backoffice - Pro Clients")
@RequiredArgsConstructor
public class BackofficeProClientsController {

    private final OperationRepository operationRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final CurrencyFreaksClientService currencyFreaksClientService;

    /** Statuts considérés comme « réussis ». Aligné sur le tableau de bord. */
    private static final Set<String> SUCCESS_STATUSES = Set.of(
            "VALIDEE", "VALID", "VALIDATED", "SUCCESS", "SUCESS", "SUCCEEDED",
            "COMPLETED", "COMPLETE", "DONE", "CONFIRMED", "APPROUVE", "APPROVED",
            "PAID", "OK", "REUSSI"
    );

    /** Aligné sur {@link BackofficeProDashboardController} — pas de crypto en libellé. */
    private static final Set<String> USD_PEGGED = Set.of(
            "USDC", "USDT", "USDP", "DAI", "BUSD", "TUSD", "FDUSD", "PYUSD", "USDD"
    );

    private static final Set<String> CRYPTO_AS_USD = Set.of(
            "POL", "MATIC", "ETH", "BNB", "BTC", "AVAX", "SOL", "ARB", "OP", "TRX"
    );

    private static final Set<String> FIAT_WHITELIST = Set.of(
            "EUR", "USD", "GBP", "CHF", "CAD", "AUD", "JPY", "CNY",
            "XOF", "XAF", "MAD", "DZD", "TND", "EGP",
            "NGN", "GHS", "KES", "UGX", "TZS", "ZAR", "RWF",
            "INR", "AED", "SAR", "TRY", "BRL", "MXN"
    );

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

    /**
     * Devise d'affichage du wallet marchand (EUR, USD, XOF…), normalisée comme
     * sur le tableau de bord — jamais un symbole crypto brut.
     */
    private String resolveMerchantDisplayCurrency(String merchantUsername) {
        Wallet w = walletRepository.findByUsersUsername(merchantUsername);
        if (w == null || w.getCurrency() == null) return "EUR";
        String raw = w.getCurrency().getCurrencyCode();
        String n = normalizeFiat(raw);
        return n != null ? n : "EUR";
    }

    private static String normalizeFiat(String code) {
        if (code == null || code.isBlank()) return null;
        String c = code.trim().toUpperCase(Locale.ROOT);
        if (FIAT_WHITELIST.contains(c)) return c;
        if (USD_PEGGED.contains(c) || CRYPTO_AS_USD.contains(c)) return "USD";
        return c;
    }

    /**
     * Dérive la liste agrégée des clients du marchand connecté.
     * On parcourt jusqu'à 5000 opérations triées par date décroissante pour
     * éviter un full-scan tout en restant exhaustif sur la durée typique
     * d'utilisation d'un compte. Pour des bases plus volumineuses, on pourra
     * matérialiser cette projection dans une table dédiée (à voir si besoin).
     */
    private ClientAggregation aggregateClients(String merchantUsername) {
        final String displayCur = resolveMerchantDisplayCurrency(merchantUsername);
        var ops = operationRepository.findByUsername(
                        merchantUsername,
                        PageRequest.of(0, 5000, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent();

        Map<String, Aggregate> aggByKey = new LinkedHashMap<>();
        double globalVolumeMerchant = 0d;
        long globalTxCount = 0L;
        Map<String, Double> volumeByCurrency = new LinkedHashMap<>();
        for (var o : ops) {
            if (OperationDesignation.isHiddenSystemTransfer(
                    o.getDesignation(), o.getCounterpartUsername(), o.getAmount())) continue;
            // Seuls les CREDITs créent un « client » du marchand. On se base
            // sur la designation plutôt que sur la colonne `type`, qui est
            // historiquement inversée pour Guardarian/MtPelerin.
            boolean isCredit = OperationDesignation.isCredit(
                    o.getDesignation(), o.getType(),
                    "CREDIT".equalsIgnoreCase(o.getType()));
            if (!isCredit) continue;

            String key = o.getCounterpartUsername();
            String displayName = o.getCounterpartDisplayName();
            if ((key == null || key.isBlank()) && (displayName == null || displayName.isBlank())) {
                continue; // pas de contrepartie identifiable
            }
            String aggKey = key != null && !key.isBlank() ? key.trim() : ("name:" + displayName.trim().toLowerCase(Locale.ROOT));

            // Seules les opérations validées comptent — pour le nombre de
            // transactions affiché ET pour le volume. Les paiements en attente
            // ou échoués sont ignorés (ils restent visibles dans Transactions).
            boolean isValidated = o.getStatus() != null
                    && SUCCESS_STATUSES.contains(o.getStatus().toUpperCase(Locale.ROOT));
            if (!isValidated) continue;

            Aggregate agg = aggByKey.computeIfAbsent(aggKey, k -> new Aggregate(key, displayName));
            agg.txCount++;
            globalTxCount++;

            // Volume par client = somme dans la devise réelle du paiement (ligne tableau).
            double amt = o.getAmount() != null ? o.getAmount() : 0d;
            agg.totalVolume += amt;
            if (o.getDevise() != null && !o.getDevise().isBlank() && amt > 0) {
                String curKey = o.getDevise().trim().toUpperCase(Locale.ROOT);
                volumeByCurrency.merge(curKey, amt, Double::sum);
            }

            if (agg.currency == null && o.getDevise() != null && !o.getDevise().isBlank()) {
                agg.currency = o.getDevise();
            }

            double merchantAmt = ProMerchantVolumeHelper.amountInMerchantCurrency(
                    o, displayCur, currencyFreaksClientService);
            agg.totalVolumeMerchant += merchantAmt;
            globalVolumeMerchant += merchantAmt;
            if (o.getCreatedAt() != null) {
                if (agg.lastPaymentAt == null || o.getCreatedAt().isAfter(agg.lastPaymentAt)) {
                    agg.lastPaymentAt = o.getCreatedAt();
                }
                if (agg.firstSeenAt == null || o.getCreatedAt().isBefore(agg.firstSeenAt)) {
                    agg.firstSeenAt = o.getCreatedAt();
                }
            }
            if (displayName != null && !displayName.isBlank() && agg.displayName == null) {
                agg.displayName = displayName.trim();
            }
        }

        // Enrichissement via la table users pour récupérer email / phone / pays
        // / type de compte. On fait un seul appel par username (pas de findIn
        // pour rester portable, l'agrégat est de taille modeste).
        List<Map<String, Object>> rows = new ArrayList<>(aggByKey.size());
        for (Aggregate agg : aggByKey.values()) {
            Optional<Users> userOpt = agg.username != null && !agg.username.isBlank()
                    ? userRepository.findFirstByUsernameOrderByCreatedAtAsc(agg.username)
                    : Optional.empty();

            String nameFromUser = userOpt.map(u -> {
                String f = nullSafe(u.getFirstname());
                String l = nullSafe(u.getLastname());
                String combined = (f + " " + l).trim();
                return combined.isBlank() ? null : combined;
            }).orElse(null);

            Map<String, Object> row = new HashMap<>();
            row.put("id", agg.username != null ? agg.username
                    : ("anonymous:" + (agg.displayName != null ? agg.displayName : "?")));
            row.put("name", firstNonBlank(nameFromUser, agg.displayName,
                    userOpt.map(Users::getRaisonSociale).orElse(null),
                    agg.username));
            row.put("email", userOpt.map(Users::getEmail).orElse(""));
            row.put("phone", userOpt.map(Users::getMobilePhone).orElse(agg.username != null ? agg.username : ""));
            row.put("companyName", userOpt.map(Users::getRaisonSociale).orElse(""));
            String accountType = userOpt.map(Users::getAccountType).orElse("");
            row.put("type", isBusinessType(accountType) ? "business" : "individual");
            row.put("country", sanitizeCountryCode(userOpt
                    .map(Users::getCountryCurrency)
                    .map(cc -> cc != null ? cc.getCountryCode() : null)
                    .orElse("")));
            // Pour l'instant on n'a pas de notion de « bloqué côté marchand »,
            // on déduit l'état à partir de l'utilisateur :
            //  - pas d'utilisateur résolu OU compte désactivé → inactive
            //  - sinon → active.
            boolean active = userOpt.map(Users::isEnabled).orElse(true);
            row.put("status", active ? "active" : "inactive");
            row.put("transactionCount", agg.txCount);
            // `totalVolume` / `currency` = vue par client (devise réelle des
            // paiements reçus). C'est ce qui s'affiche dans la colonne « Volume
            // total » de la table.
            row.put("totalVolume", agg.totalVolume);
            row.put("currency", agg.currency != null && !agg.currency.isBlank()
                    ? agg.currency
                    : displayCur);
            // `totalVolumeMerchant` / `merchantCurrency` = même volume converti
            // dans la devise du wallet marchand, pour le KPI agrégé du haut.
            row.put("totalVolumeMerchant", agg.totalVolumeMerchant);
            row.put("merchantCurrency", displayCur);
            row.put("lastPaymentAt", agg.lastPaymentAt != null ? agg.lastPaymentAt.toString() : null);
            row.put("createdAt", agg.firstSeenAt != null ? agg.firstSeenAt.toString()
                    : userOpt.map(u -> u.getCreatedAt() != null ? u.getCreatedAt().toString() : null).orElse(null));
            row.put("notes", "");
            row.put("tags", Collections.emptyList());
            rows.add(row);
        }
        // Tri : plus récent en haut, comme dans la maquette.
        rows.sort(Comparator.comparing(
                (Map<String, Object> r) -> {
                    Object v = r.get("lastPaymentAt");
                    return v == null ? "" : v.toString();
                }, Comparator.reverseOrder()));

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalVolume", Math.round(globalVolumeMerchant * 100.0) / 100.0);
        summary.put("currency", displayCur);
        summary.put("transactionCount", globalTxCount);
        summary.put("totalVolumeByCurrency", volumeByCurrency);
        return new ClientAggregation(rows, summary);
    }

    private record ClientAggregation(List<Map<String, Object>> clients, Map<String, Object> summary) {}

    @GetMapping
    @Operation(summary = "Liste paginée des clients du marchand (dérivée des opérations)")
    public ResponseEntity<ApiSuccess<PaginatedResponse<Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String tier) {
        String username = resolveWalletUsername();
        List<Map<String, Object>> all = aggregateClients(username).clients();

        // Filtres serveur (le front gère aussi ses filtres mais on les expose
        // au cas où un autre consommateur paginerait directement).
        List<Map<String, Object>> filtered = all.stream()
                .filter(r -> search == null || search.isBlank()
                        || containsCi(asString(r.get("name")), search)
                        || containsCi(asString(r.get("email")), search)
                        || containsCi(asString(r.get("phone")), search)
                        || containsCi(asString(r.get("companyName")), search))
                .filter(r -> status == null || status.isBlank()
                        || status.equalsIgnoreCase(asString(r.get("status"))))
                .filter(r -> country == null || country.isBlank()
                        || country.equalsIgnoreCase(asString(r.get("country"))))
                .collect(Collectors.toList());

        int total = filtered.size();
        int from = Math.max(0, (page - 1) * limit);
        int to = Math.min(total, from + limit);
        List<Object> slice = from >= to
                ? Collections.emptyList()
                : new ArrayList<>(filtered.subList(from, to));

        return ResponseEntity.ok(ApiSuccess.of(PaginatedResponse.of(slice, page, limit, total)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un client (vue agrégée)")
    public ResponseEntity<ApiSuccess<Object>> get(@PathVariable String id) {
        String username = resolveWalletUsername();
        return aggregateClients(username).clients().stream()
                .filter(r -> id.equalsIgnoreCase(asString(r.get("id"))))
                .findFirst()
                .map(r -> ResponseEntity.ok(ApiSuccess.of((Object) r)))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Client inconnu"));
    }

    @GetMapping("/stats")
    @Operation(summary = "Stats agrégées (clients, volume converti, transactions)")
    public ResponseEntity<ApiSuccess<Object>> stats() {
        String username = resolveWalletUsername();
        ClientAggregation agg = aggregateClients(username);
        List<Map<String, Object>> all = agg.clients();
        LocalDateTime since = LocalDateTime.now().minusDays(30);
        long total = all.size();
        long active = all.stream().filter(r -> "active".equalsIgnoreCase(asString(r.get("status")))).count();
        long newClients = all.stream()
                .filter(r -> {
                    Object v = r.get("createdAt");
                    if (v == null) return false;
                    try {
                        return LocalDateTime.parse(v.toString()).isAfter(since);
                    } catch (Exception e) {
                        return false;
                    }
                })
                .count();
        Map<String, Object> body = new HashMap<>();
        body.put("total", total);
        body.put("active", active);
        body.put("new", newClients);
        body.putAll(agg.summary());
        return ResponseEntity.ok(ApiSuccess.of(body));
    }

    @GetMapping("/by-country")
    @Operation(summary = "Répartition des clients par pays")
    public ResponseEntity<ApiSuccess<Object>> byCountry() {
        String username = resolveWalletUsername();
        Map<String, Long> by = aggregateClients(username).clients().stream()
                .map(r -> asString(r.get("country")))
                .filter(c -> c != null && !c.isBlank())
                .collect(Collectors.groupingBy(c -> c, Collectors.counting()));
        List<Map<String, Object>> data = by.entrySet().stream()
                .map(e -> {
                    Map<String, Object> row = new HashMap<>();
                    row.put("country", e.getKey());
                    row.put("count", e.getValue());
                    return row;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiSuccess.of(data));
    }

    // ---------- Opérations en écriture ----------
    // La page « Mes clients » est aujourd'hui une vue dérivée des opérations.
    // Il n'y a pas (encore) de table « clients du marchand » distincte. On
    // accepte donc les appels POST/PUT/DELETE pour ne pas casser l'UX, mais
    // ils renvoient un message clair : l'ajout/modification persistante
    // doit faire l'objet d'une table dédiée à l'avenir.

    @PostMapping
    @Operation(summary = "Ajouter un client (non implémenté côté persistance)")
    public ResponseEntity<ApiSuccess<Object>> create(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("created", false);
        resp.put("message", "L'ajout manuel de clients n'est pas encore persisté. " +
                "La liste se construit automatiquement à partir des paiements reçus.");
        return ResponseEntity.ok(ApiSuccess.of(resp));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un client (non implémenté côté persistance)")
    public ResponseEntity<ApiSuccess<Object>> update(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        resp.put("updated", false);
        resp.put("message", "La modification manuelle de la fiche client n'est pas encore persistée.");
        return ResponseEntity.ok(ApiSuccess.of(resp));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Retirer un client (non implémenté côté persistance)")
    public ResponseEntity<ApiSuccess<Object>> delete(@PathVariable String id) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        resp.put("deleted", false);
        resp.put("message", "La suppression manuelle d'un client n'est pas encore persistée.");
        return ResponseEntity.ok(ApiSuccess.of(resp));
    }

    @PostMapping("/{id}/invoice")
    @Operation(summary = "Envoyer une facture au client")
    public ResponseEntity<ApiSuccess<Object>> invoice(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> resp = new HashMap<>();
        resp.put("id", id);
        resp.put("sent", false);
        resp.put("message", "L'envoi de facture sera relié au module TransFi /invoices/create lors de l'intégration.");
        return ResponseEntity.ok(ApiSuccess.of(resp));
    }

    @GetMapping("/export")
    @Operation(summary = "Export CSV des clients du marchand")
    public ResponseEntity<ApiSuccess<Object>> export(@RequestParam String format) {
        String username = resolveWalletUsername();
        List<Map<String, Object>> all = aggregateClients(username).clients();
        // Tant qu'on n'a pas de stockage S3, on renvoie le contenu inline base64
        // pour permettre au front de proposer un download immédiat.
        StringBuilder sb = new StringBuilder();
        sb.append("id,name,email,phone,country,type,transactionCount,totalVolume,currency,lastPaymentAt,createdAt\n");
        for (var r : all) {
            sb.append(csv(r.get("id"))).append(',')
              .append(csv(r.get("name"))).append(',')
              .append(csv(r.get("email"))).append(',')
              .append(csv(r.get("phone"))).append(',')
              .append(csv(r.get("country"))).append(',')
              .append(csv(r.get("type"))).append(',')
              .append(csv(r.get("transactionCount"))).append(',')
              .append(csv(r.get("totalVolume"))).append(',')
              .append(csv(r.get("currency"))).append(',')
              .append(csv(r.get("lastPaymentAt"))).append(',')
              .append(csv(r.get("createdAt"))).append('\n');
        }
        String b64 = java.util.Base64.getEncoder().encodeToString(sb.toString().getBytes());
        Map<String, Object> resp = new HashMap<>();
        resp.put("format", format != null ? format : "csv");
        resp.put("fileUrl", "data:text/csv;base64," + b64);
        resp.put("count", all.size());
        return ResponseEntity.ok(ApiSuccess.of(resp));
    }

    private static String sanitizeCountryCode(String raw) {
        if (raw == null) return "";
        String c = raw.trim().toUpperCase(Locale.ROOT);
        if (c.isBlank() || c.equals("--") || c.equals("N/A")) return "";
        return c;
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }
    private static String asString(Object o) { return o == null ? "" : o.toString(); }
    private static boolean containsCi(String a, String needle) {
        return a != null && a.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }
    private static String firstNonBlank(String... ss) {
        if (ss == null) return null;
        for (String s : ss) if (s != null && !s.isBlank()) return s;
        return null;
    }
    private static boolean isBusinessType(String accountType) {
        if (accountType == null) return false;
        String a = accountType.toLowerCase(Locale.ROOT);
        return a.contains("business") || a.contains("entreprise") || a.contains("pro");
    }
    private static String csv(Object v) {
        String s = v == null ? "" : v.toString();
        boolean needQuote = s.contains(",") || s.contains("\"") || s.contains("\n");
        if (!needQuote) return s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    /** État accumulé par contrepartie pendant le scan des opérations. */
    private static final class Aggregate {
        final String username;       // counterpartUsername (peut être null)
        String displayName;          // counterpartDisplayName (peut être null)
        long txCount = 0L;
        /** Somme en devise réelle des paiements (XOF, EUR, USDC, …). */
        double totalVolume = 0d;
        /** Devise réelle dominante du client (1re vue). */
        String currency = null;
        /** Somme convertie dans la devise du wallet marchand — sert au KPI. */
        double totalVolumeMerchant = 0d;
        LocalDateTime firstSeenAt = null;
        LocalDateTime lastPaymentAt = null;

        Aggregate(String username, String displayName) {
            this.username = username;
            this.displayName = displayName;
        }
    }
}
