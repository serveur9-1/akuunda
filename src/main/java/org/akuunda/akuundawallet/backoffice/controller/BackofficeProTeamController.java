package org.akuunda.akuundawallet.backoffice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.akuunda.akuundawallet.backoffice.dto.ApiSuccess;
import org.akuunda.akuundawallet.backoffice.dto.PaginatedResponse;
import org.akuunda.akuundawallet.backoffice.entity.ProTeamMember;
import org.akuunda.akuundawallet.backoffice.repository.BackofficeUserRepository;
import org.akuunda.akuundawallet.backoffice.repository.ProTeamMemberRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping(path = "/api/v1/pro/team", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Backoffice - Pro Team")
@RequiredArgsConstructor
public class BackofficeProTeamController {

    private final ProTeamMemberRepository teamRepository;
    private final BackofficeUserRepository backofficeUserRepository;

    @GetMapping
    @Operation(summary = "Liste des membres de l'équipe")
    public ResponseEntity<ApiSuccess<PaginatedResponse<Map<String, Object>>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) String status) {

        String ownerEmail = resolveEmail();
        List<ProTeamMember> all = teamRepository.findByOwnerEmailOrderByInvitedAtDesc(ownerEmail);

        List<ProTeamMember> filtered = all.stream()
                .filter(m -> search == null || search.isBlank()
                        || m.getEmail().toLowerCase().contains(search.toLowerCase())
                        || (m.getFirstName() != null && m.getFirstName().toLowerCase().contains(search.toLowerCase()))
                        || (m.getLastName() != null && m.getLastName().toLowerCase().contains(search.toLowerCase())))
                .filter(m -> role == null || role.isBlank() || m.getRole().equalsIgnoreCase(role))
                .filter(m -> status == null || status.isBlank() || m.getStatus().equalsIgnoreCase(status))
                .collect(Collectors.toList());

        int total = filtered.size();
        int from = Math.min((page - 1) * limit, total);
        int to = Math.min(from + limit, total);
        List<Map<String, Object>> dtos = filtered.subList(from, to).stream()
                .map(this::toDto).collect(Collectors.toList());

        return ResponseEntity.ok(ApiSuccess.of(PaginatedResponse.of(dtos, page, limit, total)));
    }

    @GetMapping("/stats")
    @Operation(summary = "Statistiques de l'équipe")
    public ResponseEntity<ApiSuccess<Map<String, Object>>> stats() {
        String ownerEmail = resolveEmail();
        long total = teamRepository.countByOwnerEmail(ownerEmail);
        long active = teamRepository.countByOwnerEmailAndStatus(ownerEmail, "ACTIVE");
        long invited = teamRepository.countByOwnerEmailAndStatus(ownerEmail, "INVITED");
        return ResponseEntity.ok(ApiSuccess.of(Map.of(
                "total", total,
                "active", active,
                "pendingInvites", invited
        )));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Détail d'un membre")
    public ResponseEntity<ApiSuccess<Map<String, Object>>> get(@PathVariable UUID id) {
        String ownerEmail = resolveEmail();
        ProTeamMember member = teamRepository.findById(id)
                .filter(m -> m.getOwnerEmail().equalsIgnoreCase(ownerEmail))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membre introuvable"));
        return ResponseEntity.ok(ApiSuccess.of(toDto(member)));
    }

    @PostMapping("/invite")
    @Operation(summary = "Inviter un membre")
    public ResponseEntity<ApiSuccess<Map<String, Object>>> invite(@RequestBody Map<String, Object> body) {
        String ownerEmail = resolveEmail();
        String email = getString(body, "email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email requis");
        }
        if (teamRepository.existsByOwnerEmailAndEmail(ownerEmail, email.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ce membre est déjà dans votre équipe");
        }
        ProTeamMember member = ProTeamMember.builder()
                .id(UUID.randomUUID())
                .ownerEmail(ownerEmail)
                .email(email.toLowerCase())
                .firstName(getString(body, "firstName"))
                .lastName(getString(body, "lastName"))
                .role(getStringOrDefault(body, "role", "OPERATOR"))
                .department(getString(body, "department"))
                .status("INVITED")
                .twoFactorEnabled(false)
                .invitedAt(Instant.now())
                .build();
        teamRepository.save(member);
        return ResponseEntity.ok(ApiSuccess.of(toDto(member)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifier un membre")
    public ResponseEntity<ApiSuccess<Map<String, Object>>> update(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String ownerEmail = resolveEmail();
        ProTeamMember member = teamRepository.findById(id)
                .filter(m -> m.getOwnerEmail().equalsIgnoreCase(ownerEmail))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membre introuvable"));
        if (body.containsKey("firstName")) member.setFirstName(getString(body, "firstName"));
        if (body.containsKey("lastName")) member.setLastName(getString(body, "lastName"));
        if (body.containsKey("department")) member.setDepartment(getString(body, "department"));
        if (body.containsKey("role")) member.setRole(getString(body, "role"));
        teamRepository.save(member);
        return ResponseEntity.ok(ApiSuccess.of(toDto(member)));
    }

    @PatchMapping("/{id}/role")
    @Operation(summary = "Changer le rôle d'un membre")
    public ResponseEntity<ApiSuccess<Map<String, Object>>> updateRole(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String ownerEmail = resolveEmail();
        ProTeamMember member = teamRepository.findById(id)
                .filter(m -> m.getOwnerEmail().equalsIgnoreCase(ownerEmail))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membre introuvable"));
        String role = getString(body, "role");
        if (role != null) member.setRole(role);
        teamRepository.save(member);
        return ResponseEntity.ok(ApiSuccess.of(toDto(member)));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Changer le statut d'un membre")
    public ResponseEntity<ApiSuccess<Map<String, Object>>> updateStatus(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        String ownerEmail = resolveEmail();
        ProTeamMember member = teamRepository.findById(id)
                .filter(m -> m.getOwnerEmail().equalsIgnoreCase(ownerEmail))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membre introuvable"));
        String status = getString(body, "status");
        if (status != null) {
            member.setStatus(status);
            if ("ACTIVE".equals(status) && member.getAcceptedAt() == null) {
                member.setAcceptedAt(Instant.now());
            }
        }
        teamRepository.save(member);
        return ResponseEntity.ok(ApiSuccess.of(toDto(member)));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Retirer un membre de l'équipe")
    public ResponseEntity<ApiSuccess<Map<String, Object>>> delete(@PathVariable UUID id) {
        String ownerEmail = resolveEmail();
        ProTeamMember member = teamRepository.findById(id)
                .filter(m -> m.getOwnerEmail().equalsIgnoreCase(ownerEmail))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Membre introuvable"));
        teamRepository.delete(member);
        return ResponseEntity.ok(ApiSuccess.of(Map.of("id", id, "deleted", true)));
    }

    @GetMapping("/{id}/activity-log")
    @Operation(summary = "Journal d'activité d'un membre")
    public ResponseEntity<ApiSuccess<PaginatedResponse<Object>>> activity(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiSuccess.of(PaginatedResponse.of(Collections.emptyList(), page, limit, 0)));
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

    private Map<String, Object> toDto(ProTeamMember m) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("id", m.getId());
        dto.put("email", m.getEmail());
        dto.put("firstName", m.getFirstName());
        dto.put("lastName", m.getLastName());
        dto.put("fullName", ((m.getFirstName() != null ? m.getFirstName() : "") + " " +
                (m.getLastName() != null ? m.getLastName() : "")).trim());
        dto.put("role", m.getRole());
        dto.put("department", m.getDepartment());
        dto.put("status", m.getStatus());
        dto.put("twoFactorEnabled", m.isTwoFactorEnabled());
        dto.put("lastLoginAt", m.getLastLoginAt());
        dto.put("invitedAt", m.getInvitedAt());
        dto.put("acceptedAt", m.getAcceptedAt());
        return dto;
    }

    private String getString(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v instanceof String s ? s : null;
    }

    private String getStringOrDefault(Map<String, Object> body, String key, String def) {
        String v = getString(body, key);
        return v != null && !v.isBlank() ? v : def;
    }
}
