package org.akuunda.akuundawallet.wallet.service.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.constants.ApiConstants;
import org.akuunda.akuundawallet.wallet.api.dto.SavedPaymentAccountPayload;
import org.akuunda.akuundawallet.wallet.service.SavedPaymentAccountService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping(path = SavedPaymentAccountController.BASE_PATH, produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@SecurityRequirement(name = ApiConstants.SWAGGER_BASIC_SECURITY_SCHEME)
@Tag(name = "Saved payment accounts", description = "Comptes source MoMo / banque enregistrés par l'utilisateur (app mobile)")
public class SavedPaymentAccountController {

    static final String BASE_PATH = "/api/internal/v1/saved-payment-accounts";

    private final SavedPaymentAccountService savedPaymentAccountService;

    @GetMapping
    @Operation(summary = "Lister les comptes enregistrés pour un utilisateur")
    public ResponseEntity<List<SavedPaymentAccountPayload>> list(
            @RequestParam String username) {
        log.debug("list saved-payment-accounts username={}", username);
        return ResponseEntity.ok(savedPaymentAccountService.listForUser(username));
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Créer ou mettre à jour un compte enregistré")
    public ResponseEntity<SavedPaymentAccountPayload> upsert(
            @RequestParam String username,
            @RequestBody @Valid SavedPaymentAccountPayload payload) {
        log.debug("upsert saved-payment-accounts username={} id={}", username, payload.getId());
        try {
            return ResponseEntity.ok(savedPaymentAccountService.upsert(username, payload));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Supprimer un compte enregistré")
    public ResponseEntity<Void> delete(
            @RequestParam String username,
            @PathVariable String id) {
        log.debug("delete saved-payment-accounts username={} id={}", username, id);
        try {
            savedPaymentAccountService.delete(username, id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }
}
