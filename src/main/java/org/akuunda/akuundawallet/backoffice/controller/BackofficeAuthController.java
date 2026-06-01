package org.akuunda.akuundawallet.backoffice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.akuunda.akuundawallet.backoffice.dto.ApiSuccess;
import org.akuunda.akuundawallet.backoffice.dto.auth.BackofficeCredentialsRequest;
import org.akuunda.akuundawallet.backoffice.dto.auth.BackofficeLoginRequest;
import org.akuunda.akuundawallet.backoffice.dto.auth.BackofficeLoginResponse;
import org.akuunda.akuundawallet.backoffice.dto.auth.BackofficeMeResponse;
import org.akuunda.akuundawallet.backoffice.dto.auth.BackofficeRegisterAccountRequest;
import org.akuunda.akuundawallet.backoffice.dto.auth.BackofficeRegisterRequest;
import org.akuunda.akuundawallet.backoffice.dto.auth.BackofficeRegisterResponse;
import org.akuunda.akuundawallet.backoffice.service.BackofficeAuthService;
import org.akuunda.akuundawallet.backoffice.service.BackofficeRegistrationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping(path = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Backoffice - Auth", description = "Endpoints Auth (Admin & Pro) pour le backoffice")
@RequiredArgsConstructor
public class BackofficeAuthController {

    private final BackofficeAuthService backofficeAuthService;
    private final BackofficeRegistrationService backofficeRegistrationService;

    // ---------------------------------------------------------------------
    // AUTH — Figma
    // ---------------------------------------------------------------------

    @PostMapping("/login/email")
    @Operation(summary = "Connexion email/mot de passe")
    public ResponseEntity<ApiSuccess<BackofficeLoginResponse>> loginEmail(@Valid @RequestBody BackofficeLoginRequest request) {
        return ResponseEntity.ok(ApiSuccess.of(backofficeAuthService.login(request)));
    }

    @PostMapping("/login/admin")
    @Operation(summary = "Connexion backoffice admin (email/mot de passe)")
    public ResponseEntity<ApiSuccess<BackofficeLoginResponse>> loginAdmin(@Valid @RequestBody BackofficeCredentialsRequest request) {
        BackofficeLoginRequest loginRequest = new BackofficeLoginRequest();
        loginRequest.setEmail(request.getEmail());
        loginRequest.setPassword(request.getPassword());
        loginRequest.setPortal("admin");
        return ResponseEntity.ok(ApiSuccess.of(backofficeAuthService.login(loginRequest)));
    }

    @PostMapping("/login/pro")
    @Operation(summary = "Connexion backoffice pro (email/mot de passe)")
    public ResponseEntity<ApiSuccess<BackofficeLoginResponse>> loginPro(@Valid @RequestBody BackofficeCredentialsRequest request) {
        BackofficeLoginRequest loginRequest = new BackofficeLoginRequest();
        loginRequest.setEmail(request.getEmail());
        loginRequest.setPassword(request.getPassword());
        loginRequest.setPortal("pro");
        return ResponseEntity.ok(ApiSuccess.of(backofficeAuthService.login(loginRequest)));
    }

    /** Alias déjà utilisé dans le front actuel */
    @PostMapping("/login")
    public ResponseEntity<ApiSuccess<BackofficeLoginResponse>> login(@Valid @RequestBody BackofficeLoginRequest request) {
        return loginEmail(request);
    }

    @PostMapping("/register")
    @Operation(summary = "Créer un compte backoffice (base applicative)",
            description = "Enregistre l’utilisateur en base (mot de passe hashé). Connexion via /login/* avec JWT émis par l’API (pas Keycloak). "
                    + "L’inscription admin peut être désactivée (backoffice.registration.allow-admin).")
    public ResponseEntity<ApiSuccess<BackofficeRegisterResponse>> register(@Valid @RequestBody BackofficeRegisterRequest request) {
        return ResponseEntity.ok(ApiSuccess.of(backofficeRegistrationService.register(request)));
    }

    @PostMapping("/register/admin")
    @Operation(summary = "Créer un compte backoffice admin (portal=admin)")
    public ResponseEntity<ApiSuccess<BackofficeRegisterResponse>> registerAdmin(@Valid @RequestBody BackofficeRegisterAccountRequest request) {
        BackofficeRegisterRequest r = new BackofficeRegisterRequest();
        r.setEmail(request.getEmail());
        r.setPassword(request.getPassword());
        r.setFirstName(request.getFirstName());
        r.setLastName(request.getLastName());
        r.setPortal("admin");
        return ResponseEntity.ok(ApiSuccess.of(backofficeRegistrationService.register(r)));
    }

    @PostMapping("/register/pro")
    @Operation(summary = "Créer un compte backoffice pro (portal=pro)")
    public ResponseEntity<ApiSuccess<BackofficeRegisterResponse>> registerPro(@Valid @RequestBody BackofficeRegisterAccountRequest request) {
        BackofficeRegisterRequest r = new BackofficeRegisterRequest();
        r.setEmail(request.getEmail());
        r.setPassword(request.getPassword());
        r.setFirstName(request.getFirstName());
        r.setLastName(request.getLastName());
        r.setPortal("pro");
        return ResponseEntity.ok(ApiSuccess.of(backofficeRegistrationService.register(r)));
    }

    @PostMapping("/login/google")
    @Operation(summary = "OAuth2 Google (placeholder)")
    public ResponseEntity<ApiSuccess<Object>> loginGoogle(@RequestBody(required = false) Object body) {
        return ResponseEntity.ok(ApiSuccess.of(Map.of("message", "Not implemented: use Keycloak/OIDC")));
    }

    @PostMapping("/login/microsoft")
    @Operation(summary = "OAuth2 Microsoft (placeholder)")
    public ResponseEntity<ApiSuccess<Object>> loginMicrosoft(@RequestBody(required = false) Object body) {
        return ResponseEntity.ok(ApiSuccess.of(Map.of("message", "Not implemented: use Keycloak/OIDC")));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rafraîchissement de token JWT")
    public ResponseEntity<ApiSuccess<Object>> refresh(@RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(ApiSuccess.of(Map.of("message", "Use Keycloak refresh token endpoint")));
    }

    @PostMapping("/logout")
    @Operation(summary = "Déconnexion")
    public ResponseEntity<ApiSuccess<Object>> logout(@RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(ApiSuccess.of(Map.of("message", "OK")));
    }

    @PostMapping("/logout/all")
    @Operation(summary = "Déconnexion toutes sessions")
    public ResponseEntity<ApiSuccess<Object>> logoutAll(@RequestBody(required = false) Map<String, Object> body) {
        return ResponseEntity.ok(ApiSuccess.of(Map.of("message", "OK")));
    }

    @GetMapping("/me")
    @Operation(summary = "Profil utilisateur connecté")
    public ResponseEntity<ApiSuccess<BackofficeMeResponse>> me() {
        return ResponseEntity.ok(ApiSuccess.of(backofficeAuthService.me()));
    }

    @GetMapping("/sessions")
    @Operation(summary = "Sessions actives par portail (stub)")
    public ResponseEntity<ApiSuccess<Object>> sessions() {
        return ResponseEntity.ok(ApiSuccess.of(Collections.emptyList()));
    }
}

