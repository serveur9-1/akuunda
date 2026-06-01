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
import org.akuunda.akuundawallet.wallet.api.enums.OperationDesignation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping(path = "/api/v1/pro/transactions", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Backoffice - Pro Transactions")
@RequiredArgsConstructor
public class BackofficeProTransactionsController {

    private final OperationRepository operationRepository;
    private final UserRepository userRepository;
    private final BackofficeUserRepository backofficeUserRepository;

    @GetMapping
    @Operation(summary = "Liste paginée des transactions du marchand connecté")
    public ResponseEntity<ApiSuccess<PaginatedResponse<Object>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String search) {
        String username = resolveWalletUsername();
        int size = Math.min(100, Math.max(1, limit));
        Pageable pageable = PageRequest.of(
                Math.max(0, page - 1), size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<org.akuunda.akuundawallet.wallet.api.entities.Operation> p =
                loadPage(username, pageable, status, search);

        List<org.akuunda.akuundawallet.wallet.api.entities.Operation> visible = p.getContent().stream()
                .filter(op -> !OperationDesignation.isHiddenSystemTransfer(
                        op.getDesignation(), op.getCounterpartUsername(), op.getAmount()))
                .filter(op -> matchesTypeFilter(op, type))
                .filter(op -> matchesCountryFilter(op, country))
                .collect(Collectors.toList());

        List<Object> list = visible.stream().map(this::toRow).collect(Collectors.toList());
        return ResponseEntity.ok(ApiSuccess.of(
                PaginatedResponse.of(list, page, size, p.getTotalElements())));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail transaction (marchand connecté uniquement)")
    public ResponseEntity<ApiSuccess<Object>> get(@PathVariable Long id) {
        String username = resolveWalletUsername();
        Optional<org.akuunda.akuundawallet.wallet.api.entities.Operation> op =
                operationRepository.findById(id);
        if (op.isEmpty() || !username.equals(op.get().getUsername())) {
            return ResponseEntity.notFound().build();
        }
        if (OperationDesignation.isHiddenSystemTransfer(
                op.get().getDesignation(), op.get().getCounterpartUsername(), op.get().getAmount())) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiSuccess.of(toRow(op.get())));
    }

    @GetMapping("/stats")
    @Operation(summary = "Résumé des transactions du marchand")
    public ResponseEntity<ApiSuccess<Object>> stats() {
        String username = resolveWalletUsername();
        List<org.akuunda.akuundawallet.wallet.api.entities.Operation> ops =
                operationRepository.findByUsername(
                        username,
                        PageRequest.of(0, 10_000, Sort.by(Sort.Direction.DESC, "createdAt")))
                .getContent().stream()
                .filter(op -> !OperationDesignation.isHiddenSystemTransfer(
                        op.getDesignation(), op.getCounterpartUsername(), op.getAmount()))
                .collect(Collectors.toList());

        long incoming = ops.stream().filter(o -> "CREDIT".equalsIgnoreCase(o.getType())).count();
        long outgoing = ops.stream().filter(o -> "DEBIT".equalsIgnoreCase(o.getType())).count();

        return ResponseEntity.ok(ApiSuccess.of(Map.of(
                "count", ops.size(),
                "incoming", incoming,
                "outgoing", outgoing,
                "volumeIn", 0,
                "volumeOut", 0
        )));
    }

    @GetMapping("/export")
    @Operation(summary = "Export (stub)")
    public ResponseEntity<ApiSuccess<Object>> export(@RequestParam String format,
                                                     @RequestParam(required = false) String dateFrom,
                                                     @RequestParam(required = false) String dateTo) {
        return ResponseEntity.ok(ApiSuccess.of(Map.of("fileUrl", "", "format", format)));
    }

    private Page<org.akuunda.akuundawallet.wallet.api.entities.Operation> loadPage(
            String username, Pageable pageable, String status, String search) {
        if (search != null && !search.isBlank()) {
            return operationRepository.findByUsernameAndSearch(
                    username, search.trim().toLowerCase(Locale.ROOT), pageable);
        }
        if (status != null && !status.isBlank()) {
            return operationRepository.findByUsernameAndStatusIgnoreCase(username, status, pageable);
        }
        return operationRepository.findByUsername(username, pageable);
    }

    private static boolean matchesTypeFilter(
            org.akuunda.akuundawallet.wallet.api.entities.Operation op, String type) {
        if (type == null || type.isBlank()) return true;
        String t = type.trim();
        if ("CREDIT".equalsIgnoreCase(t) || "DEBIT".equalsIgnoreCase(t)) {
            return op.getType() != null && op.getType().equalsIgnoreCase(t);
        }
        if (op.getDesignation() != null && op.getDesignation().toUpperCase(Locale.ROOT).contains(t.toUpperCase(Locale.ROOT))) {
            return true;
        }
        return op.getTransactionType() != null
                && op.getTransactionType().toUpperCase(Locale.ROOT).contains(t.toUpperCase(Locale.ROOT));
    }

    /** Filtre pays : code devise (ex. XOF, KES) passé par le front. */
    private static boolean matchesCountryFilter(
            org.akuunda.akuundawallet.wallet.api.entities.Operation op, String country) {
        if (country == null || country.isBlank() || "all".equalsIgnoreCase(country.trim())) {
            return true;
        }
        return op.getDevise() != null
                && op.getDevise().equalsIgnoreCase(country.trim());
    }

    private Map<String, Object> toRow(org.akuunda.akuundawallet.wallet.api.entities.Operation op) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", op.getId());
        row.put("reference", op.getOperationHash() != null ? op.getOperationHash() : "");
        row.put("type", op.getType() != null ? op.getType() : "");
        row.put("designation", op.getDesignation() != null ? op.getDesignation() : "");
        row.put("transactionType", op.getTransactionType() != null ? op.getTransactionType() : "");
        row.put("provider", op.getProvider() != null ? op.getProvider() : "");
        row.put("status", op.getStatus() != null ? op.getStatus() : "");
        row.put("amount", op.getAmount() != null ? op.getAmount() : 0);
        row.put("currency", op.getDevise() != null ? op.getDevise() : "");
        row.put("counterpart", op.getCounterpartDisplayName() != null ? op.getCounterpartDisplayName() : "");
        row.put("counterpartUsername", op.getCounterpartUsername() != null ? op.getCounterpartUsername() : "");
        row.put("createdAt", op.getCreatedAt() != null ? op.getCreatedAt().toString() : "");
        return row;
    }

    /** Username Akuunda du marchand (JWT email → backoffice_user ou users). */
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
}
