package org.akuunda.akuundawallet.wallet.service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.constants.ApiConstants;
import org.akuunda.akuundawallet.wallet.api.dto.MerchantKeyCreateRequest;
import org.akuunda.akuundawallet.wallet.api.dto.MerchantKeyItem;
import org.akuunda.akuundawallet.wallet.api.dto.MerchantKeyResponse;
import org.akuunda.akuundawallet.wallet.service.MerchantKeysService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Gestion des clés API marchand côté dashboard. Auth JWT obligatoire.
 */
@Slf4j
@RestController
@RequestMapping(path = "/api/v1/merchant/keys", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Merchant Keys", description = "Gestion des clés API marchand (dashboard)")
@SecurityRequirement(name = ApiConstants.SWAGGER_BASIC_SECURITY_SCHEME)
public class MerchantKeysController {

    private final MerchantKeysService merchantKeysService;

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Créer une clé API marchand",
            description = """
                    Crée une clé API liée à un marchand Akuunda Pay. Le secret webhook
                    (`webhookSecret`) n'est exposé **qu'une seule fois**, à la création.

                    **Identification du marchand cible**

                    Tous les appels API Akuunda sont authentifiés auprès de Keycloak via un compte de
                    service master (par défaut `akuunda1`, configurable via la propriété
                    `akuunda.public.auth.username`). Le JWT seul ne permet donc pas d'identifier le
                    marchand : c'est le champ **`username` du body qui fait foi**.

                    Règles de résolution :

                    - `username` fourni → le serveur cherche un compte Akuunda dont le login, l'email
                      ou le téléphone correspond. Insensible à la casse ; les formats `+33…` / `0033…` /
                      `06…` sont reconnus.
                    - `username` correspond à un compte de service (`akuunda1`) → **422
                      MERCHANT_USERNAME_RESERVED**.
                    - `username` non reconnu → **422 MERCHANT_NOT_FOUND**.
                    - `username` absent **et** JWT issu d'un compte de service → **400
                      MERCHANT_USERNAME_REQUIRED**.
                    - `username` absent **et** JWT issu d'un vrai marchand (cas dashboard Pro où le
                      marchand est lui-même authentifié) → on retombe sur le marchand JWT (rétro-compat).

                    Toutes les erreurs retournent un corps JSON
                    `{ "success": false, "errors": [{ "code", "message", "field" }] }`.
                    """)
    public ResponseEntity<?> create(@AuthenticationPrincipal Jwt jwt,
                                     @RequestBody(required = false) MerchantKeyCreateRequest request) {
        return merchantKeysService.createKey(jwt, request);
    }

    @GetMapping
    @Operation(summary = "Lister les clés API marchand",
            description = """
                    Retourne les clés du marchand authentifié. Chaque entrée inclut `merchantUsername`,
                    `merchantUserId` et `merchantSlug` pour identifier le compte côté providers.""")
    public ResponseEntity<List<MerchantKeyItem>> list(@AuthenticationPrincipal Jwt jwt) {
        return merchantKeysService.listKeys(jwt);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Révoquer une clé API")
    public ResponseEntity<Void> revoke(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return merchantKeysService.revokeKey(jwt, id);
    }
}
