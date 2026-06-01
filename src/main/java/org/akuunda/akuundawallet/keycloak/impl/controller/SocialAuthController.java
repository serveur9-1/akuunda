package org.akuunda.akuundawallet.keycloak.impl.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.keycloak.impl.service.UserServiceImpl;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.service.impl.SocialTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping(path = "/api/v1/{realm}/users", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Social Auth")
@RequiredArgsConstructor
@Slf4j
public class SocialAuthController {

    private final UserServiceImpl userService;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final SocialTokenService socialTokenService;

    @Data
    public static class SocialLoginRequest {
        @NotBlank String token;
        @NotBlank String typeLogin;
    }

    @Data
    public static class LinkSocialRequest {
        @NotBlank String token;
        @NotBlank String typeLogin;
    }

    // ── POST /social-login (public — pas de JWT requis) ───────────────────

    @PostMapping("/social-login")
    @Transactional(readOnly = true)
    @Operation(summary = "Connexion via Google, Facebook ou Apple — retourne un social JWT Akuunda")
    public ResponseEntity<Map<String, Object>> socialLogin(
            @PathVariable String realm,
            @Valid @RequestBody SocialLoginRequest body) {

        String type = body.getTypeLogin().toLowerCase();

        String socialId = verifySocialToken(type, body.getToken());
        if (socialId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Token " + type + " invalide ou expiré");
        }

        Users user = switch (type) {
            case "google"   -> userRepository.getUsersByGoogleId(socialId);
            case "facebook" -> userRepository.getUsersByFacebookId(socialId);
            case "apple"    -> userRepository.getUsersByAppleId(socialId);
            default         -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                    "typeLogin invalide : google, facebook, apple");
        };

        if (user == null || user.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Aucun compte Akuunda lié à ce profil " + type +
                    ". Connectez-vous d'abord avec votre numéro de téléphone puis associez votre compte dans Paramètres > Mode de connexion.");
        }

        // Safety check: if found user has no wallet, it is likely the Keycloak service account
        // (social IDs were incorrectly linked to it). Reject and ask user to re-link.
        var wallets = walletRepository.findWalletByUsers(user);
        if (wallets.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Compte " + type + " mal configuré. Connectez-vous avec votre numéro de téléphone puis re-associez votre compte dans Paramètres > Mode de connexion.");
        }

        String socialJwt = socialTokenService.generateToken(user.getUsername(), type);
        String walletAddress = wallets.get(0).getAddress();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("access_token",  socialJwt);
        response.put("token_type",    "Bearer");
        response.put("expires_in",    86400);
        response.put("refresh_token", "");
        response.put("user", Map.of(
                "userId",        user.getUserId(),
                "username",      user.getUsername(),
                "firstname",     user.getFirstname()    != null ? user.getFirstname()    : "",
                "lastname",      user.getLastname()     != null ? user.getLastname()     : "",
                "email",         user.getEmail()        != null ? user.getEmail()        : "",
                "mobilePhone",   user.getMobilePhone()  != null ? user.getMobilePhone()  : "",
                "walletAddress", walletAddress          != null ? walletAddress          : ""
        ));

        log.info("Social login OK username={} provider={}", user.getUsername(), type);
        return ResponseEntity.ok(response);
    }

    // ── PATCH /{username}/link-social (protégé — JWT requis) ─────────────

    @PatchMapping("/{username}/link-social")
    @Transactional
    @Operation(summary = "Associer un compte Google/Facebook/Apple à un compte Akuunda existant")
    public ResponseEntity<Map<String, String>> linkSocial(
            @PathVariable String realm,
            @PathVariable String username,
            @Valid @RequestBody LinkSocialRequest body) {

        try {
        // The JWT is a Keycloak service account (not the end user).
        // Always use the path {username} which the mobile sends as the real user's username.
        String type = body.getTypeLogin().toLowerCase();

        String socialId = verifySocialToken(type, body.getToken());
        if (socialId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Token " + type + " invalide ou expiré");
        }

        // Find the target user by path username
        Users user = userRepository.findFirstByUsernameOrderByCreatedAtAsc(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Utilisateur introuvable : " + username));

        // If this social ID is already linked to a different account, move it (correct wrong links)
        Users existingOwner = switch (type) {
            case "google"   -> userRepository.getUsersByGoogleId(socialId);
            case "facebook" -> userRepository.getUsersByFacebookId(socialId);
            case "apple"    -> userRepository.getUsersByAppleId(socialId);
            default         -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                    "typeLogin invalide");
        };

        if (existingOwner != null && !existingOwner.getUserId().equals(user.getUserId())) {
            // Clear the stale link from the wrong account (e.g. Keycloak service account)
            log.warn("Social ID {} already linked to {}, moving to {}", type, existingOwner.getUsername(), username);
            switch (type) {
                case "google"   -> existingOwner.setGoogleId(null);
                case "facebook" -> existingOwner.setFacebookId(null);
                case "apple"    -> existingOwner.setAppleId(null);
            }
            userRepository.save(existingOwner);
        }

        switch (type) {
            case "google"   -> user.setGoogleId(socialId);
            case "facebook" -> user.setFacebookId(socialId);
            case "apple"    -> user.setAppleId(socialId);
        }
        userRepository.save(user);

        log.info("Social account linked username={} provider={} socialId={}", username, type, socialId);
        return ResponseEntity.ok(Map.of("message",
                "Compte " + type + " associé avec succès à " + username));

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("linkSocial unexpected error username={} type={}: {}", username, body.getTypeLogin(), e.getMessage(), e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Erreur interne: " + e.getMessage());
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private String verifySocialToken(String type, String token) {
        return switch (type) {
            case "google"   -> userService.verifyGoogleToken(token);
            case "facebook" -> userService.verifyFacebookToken(token);
            case "apple"    -> userService.verifyAppleToken(token);
            default         -> null;
        };
    }

    private String resolveCallerUsername() {
        try {
            JwtAuthenticationToken auth = (JwtAuthenticationToken)
                    SecurityContextHolder.getContext().getAuthentication();
            Jwt jwt = auth.getToken();
            String preferred = jwt.getClaimAsString("preferred_username");
            return preferred != null && !preferred.isBlank() ? preferred : jwt.getSubject();
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Non authentifié");
        }
    }
}
