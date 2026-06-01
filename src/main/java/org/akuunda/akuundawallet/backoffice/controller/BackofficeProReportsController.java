package org.akuunda.akuundawallet.backoffice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.akuunda.akuundawallet.backoffice.dto.ApiSuccess;
import org.akuunda.akuundawallet.backoffice.dto.PaginatedResponse;
import org.akuunda.akuundawallet.backoffice.entity.ProReport;
import org.akuunda.akuundawallet.backoffice.repository.BackofficeUserRepository;
import org.akuunda.akuundawallet.backoffice.repository.ProReportRepository;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.wallet.api.dao.OperationRepository;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping(path = "/api/v1/pro/reports", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Backoffice - Pro Reports")
@RequiredArgsConstructor
public class BackofficeProReportsController {

    private final ProReportRepository reportRepository;
    private final OperationRepository operationRepository;
    private final BackofficeUserRepository backofficeUserRepository;
    private final UserRepository userRepository;

    @GetMapping("/list")
    @Operation(summary = "Liste des rapports générés")
    public ResponseEntity<ApiSuccess<PaginatedResponse<Map<String, Object>>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        String email = resolveEmail();
        Page<ProReport> pageResult = reportRepository.findByOwnerEmailOrderByCreatedAtDesc(
                email, PageRequest.of(page - 1, limit));
        List<Map<String, Object>> dtos = pageResult.getContent().stream()
                .map(this::toDto).collect(Collectors.toList());
        return ResponseEntity.ok(ApiSuccess.of(
                PaginatedResponse.of(dtos, page, limit, pageResult.getTotalElements())));
    }

    @GetMapping("/monthly-volume")
    @Operation(summary = "Volume mensuel des transactions")
    public ResponseEntity<ApiSuccess<Object>> monthlyVolume(
            @RequestParam(required = false) Integer year) {
        String username = resolveWalletUsername();
        int targetYear = year != null ? year : LocalDateTime.now().getYear();
        LocalDateTime since = LocalDateTime.of(targetYear, 1, 1, 0, 0);
        List<Object[]> rows = operationRepository.sumAmountByDayForUsername(username, since);
        Map<String, Double> byMonth = new LinkedHashMap<>();
        for (int m = 1; m <= 12; m++) {
            byMonth.put(String.format("%d-%02d", targetYear, m), 0.0);
        }
        for (Object[] row : rows) {
            int y = ((Number) row[0]).intValue();
            int m = ((Number) row[1]).intValue();
            double amt = row[3] instanceof Number ? ((Number) row[3]).doubleValue() : 0.0;
            if (y == targetYear) {
                byMonth.merge(String.format("%d-%02d", y, m), amt, Double::sum);
            }
        }
        List<Map<String, Object>> result = byMonth.entrySet().stream()
                .map(e -> Map.<String, Object>of("month", e.getKey(), "amount", e.getValue()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiSuccess.of(result));
    }

    @GetMapping("/summary")
    @Operation(summary = "Résumé des transactions du marchand")
    public ResponseEntity<ApiSuccess<Object>> summary() {
        String username = resolveWalletUsername();
        long total = operationRepository.findByUsername(username, PageRequest.of(0, 1)).getTotalElements();
        long clients = operationRepository.countDistinctCounterparts(username);
        return ResponseEntity.ok(ApiSuccess.of(Map.of(
                "totalTransactions", total,
                "totalClients", clients
        )));
    }

    @PostMapping("/generate")
    @Operation(summary = "Générer un rapport à partir des données réelles")
    public ResponseEntity<ApiSuccess<Map<String, Object>>> generate(
            @RequestBody(required = false) Map<String, Object> body) {
        String email = resolveEmail();
        String walletUsername = resolveWalletUsername();

        String type = body != null && body.containsKey("type") ? (String) body.get("type") : "TRANSACTIONS";
        LocalDate from = parseDate(body, "from", LocalDate.now().minusMonths(1));
        LocalDate to = parseDate(body, "to", LocalDate.now());

        LocalDateTime fromDt = from.atStartOfDay();
        LocalDateTime toDt = to.atTime(23, 59, 59);

        Page<org.akuunda.akuundawallet.wallet.api.entities.Operation> ops = operationRepository.findByUsername(
                walletUsername, PageRequest.of(0, 10000, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<org.akuunda.akuundawallet.wallet.api.entities.Operation> filtered = ops.getContent().stream()
                .filter(op -> op.getCreatedAt() != null
                        && !op.getCreatedAt().isBefore(fromDt)
                        && !op.getCreatedAt().isAfter(toDt))
                .collect(Collectors.toList());

        double totalAmount = filtered.stream()
                .filter(op -> op.getAmount() != null)
                .mapToDouble(org.akuunda.akuundawallet.wallet.api.entities.Operation::getAmount).sum();
        String currency = filtered.stream()
                .filter(op -> op.getDevise() != null)
                .map(org.akuunda.akuundawallet.wallet.api.entities.Operation::getDevise)
                .findFirst().orElse("USDC");

        ProReport report = ProReport.builder()
                .id(UUID.randomUUID())
                .ownerEmail(email)
                .name(type + " — " + from + " au " + to)
                .type(type)
                .status("COMPLETED")
                .periodFrom(from)
                .periodTo(to)
                .rowCount(filtered.size())
                .totalAmount(Math.round(totalAmount * 100.0) / 100.0)
                .currency(currency)
                .createdAt(Instant.now())
                .completedAt(Instant.now())
                .build();
        reportRepository.save(report);

        return ResponseEntity.ok(ApiSuccess.of(toDto(report)));
    }

    @GetMapping("/{id}/download")
    @Operation(summary = "Télécharger un rapport")
    public ResponseEntity<ApiSuccess<Map<String, Object>>> download(@PathVariable UUID id) {
        String email = resolveEmail();
        ProReport report = reportRepository.findById(id)
                .filter(r -> r.getOwnerEmail().equalsIgnoreCase(email))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rapport introuvable"));
        return ResponseEntity.ok(ApiSuccess.of(toDto(report)));
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String resolveEmail() {
        JwtAuthenticationToken auth = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = auth.getToken();
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email absent du token JWT");
        }
        return email.toLowerCase();
    }

    private String resolveWalletUsername() {
        String email = resolveEmail();
        Optional<String> fromBackoffice = backofficeUserRepository.findByEmailIgnoreCase(email)
                .map(u -> u.getWalletUsername())
                .filter(w -> w != null && !w.isBlank());
        if (fromBackoffice.isPresent()) return fromBackoffice.get();
        return userRepository.findFirstByEmailOrderByCreatedAtAsc(email)
                .map(u -> u.getUsername())
                .filter(w -> w != null && !w.isBlank())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Aucun compte marchand associé à cet email"));
    }

    private Map<String, Object> toDto(ProReport r) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", r.getId());
        dto.put("name", r.getName());
        dto.put("type", r.getType());
        dto.put("status", r.getStatus());
        dto.put("periodFrom", r.getPeriodFrom());
        dto.put("periodTo", r.getPeriodTo());
        dto.put("rowCount", r.getRowCount());
        dto.put("totalAmount", r.getTotalAmount());
        dto.put("currency", r.getCurrency());
        dto.put("createdAt", r.getCreatedAt());
        dto.put("completedAt", r.getCompletedAt());
        return dto;
    }

    private LocalDate parseDate(Map<String, Object> body, String key, LocalDate def) {
        if (body == null || !body.containsKey(key)) return def;
        try { return LocalDate.parse((String) body.get(key)); } catch (Exception e) { return def; }
    }
}
