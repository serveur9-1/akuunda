package org.akuunda.akuundawallet.backoffice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.akuunda.akuundawallet.backoffice.dto.ApiSuccess;
import org.akuunda.akuundawallet.backoffice.dto.PaginatedResponse;
import org.akuunda.akuundawallet.backoffice.dto.admin.UserProfileDto;
import org.akuunda.akuundawallet.backoffice.service.BackofficeAdminService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path = "/api/v1/admin/users", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Backoffice - Admin Users")
@RequiredArgsConstructor
public class BackofficeAdminUsersController {

    private final BackofficeAdminService backofficeAdminService;

    @GetMapping
    @Operation(summary = "Liste paginée des utilisateurs")
    public ResponseEntity<ApiSuccess<PaginatedResponse<UserProfileDto>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status) {
        PaginatedResponse<UserProfileDto> data = backofficeAdminService.getUsers(
                PageRequest.of(Math.max(0, page - 1), Math.min(100, limit)), search, status);
        return ResponseEntity.ok(ApiSuccess.of(data));
    }

    @GetMapping("/{userId}")
    @Operation(summary = "Détail d'un utilisateur")
    public ResponseEntity<ApiSuccess<UserProfileDto>> get(@PathVariable String userId) {
        UserProfileDto data = backofficeAdminService.getUserById(userId);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiSuccess.of(data));
    }
}
