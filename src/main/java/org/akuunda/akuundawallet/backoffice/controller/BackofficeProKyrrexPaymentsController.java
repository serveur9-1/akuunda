package org.akuunda.akuundawallet.backoffice.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.akuunda.akuundawallet.backoffice.dto.ApiSuccess;
import org.akuunda.akuundawallet.backoffice.repository.BackofficeUserRepository;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.dto.external.KyrrexAssetDchainResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.KyrrexAssetResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.KyrrexBalanceResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.KyrrexMemberSignUpRequest;
import org.akuunda.akuundawallet.wallet.api.dto.external.KyrrexPaginatedAssetsResponse;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaKyrrexClientService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.KyrrexCredentialMissingException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/**
 * Proxies Kyrrex pour l'Espace Pro (JWT → wallet username).
 * Le front appelle /api/v1/pro/payments/kyrrex/* au lieu de /api/internal/v1/kyrrex/*.
 */
@RestController
@RequestMapping(path = "/api/v1/pro/payments/kyrrex", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Backoffice - Pro Kyrrex Payments")
@RequiredArgsConstructor
public class BackofficeProKyrrexPaymentsController {

    private final AkuundaKyrrexClientService kyrrexClientService;
    private final BackofficeUserRepository backofficeUserRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    @GetMapping("/crypto-assets")
    @Operation(summary = "Catalogue crypto Kyrrex (assets + réseaux) pour le dépôt Pro")
    public ResponseEntity<ApiSuccess<List<Map<String, Object>>>> getCryptoAssets() {
        String username = resolveWalletUsername();
        ensureKyrrexMemberReady(username);
        ResponseEntity<KyrrexPaginatedAssetsResponse> resp =
                kyrrexClientService.getAssets(username, true, null, 1, 500);
        if (resp.getStatusCode() == HttpStatus.NOT_FOUND) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Compte Kyrrex non enregistré pour " + username
                            + ". Appelez POST /api/v1/pro/payments/kyrrex/register.");
        }
        if (!resp.getStatusCode().is2xxSuccessful() || resp.getBody() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Catalogue Kyrrex indisponible");
        }
        return ResponseEntity.ok(ApiSuccess.of(mapAssetCatalog(resp.getBody().getItems())));
    }

    @PostMapping("/register")
    @Operation(summary = "Enregistrer le marchand connecté chez Kyrrex (recrée les credentials en base)")
    public ResponseEntity<ApiSuccess<Object>> registerMember() {
        String username = resolveWalletUsername();
        Users user = loadAkuundaUser(username);
        var signUp = buildSignUpRequest(user);
        var result = kyrrexClientService.ensureKyrrexMemberRegistered(username, signUp);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", username);
        body.put("registered", true);
        body.put("kyrrexUid", result.getBody() != null ? result.getBody().getUid() : null);
        return ResponseEntity.ok(ApiSuccess.of(body));
    }

    @PostMapping("/deposit-addresses")
    @Operation(summary = "Créer une adresse de dépôt crypto Kyrrex (marchand connecté)")
    public ResponseEntity<ApiSuccess<Map<String, Object>>> createDepositAddress(
            @RequestParam String dchain,
            @RequestParam(required = false, defaultValue = "pro-deposit") String name) {
        String username = resolveWalletUsername();
        ensureKyrrexMemberReady(username);
        ResponseEntity<String> raw =
                kyrrexClientService.createDepositAddress(username, dchain, name);
        return ResponseEntity.status(raw.getStatusCode())
                .body(ApiSuccess.of(parseJsonBody(raw.getBody())));
    }

    @GetMapping("/deposit-addresses")
    @Operation(summary = "Lister les adresses de dépôt crypto Kyrrex")
    public ResponseEntity<ApiSuccess<Object>> listDepositAddresses() {
        String username = resolveWalletUsername();
        ensureKyrrexMemberReady(username);
        ResponseEntity<String> raw = kyrrexClientService.getDepositAddresses(username);
        return ResponseEntity.status(raw.getStatusCode())
                .body(ApiSuccess.of(parseJsonBodyAny(raw.getBody())));
    }

    @GetMapping("/balances")
    @Operation(summary = "Soldes Kyrrex du marchand connecté")
    public ResponseEntity<ApiSuccess<List<KyrrexBalanceResponse>>> getBalances() {
        String username = resolveWalletUsername();
        ensureKyrrexMemberReady(username);
        ResponseEntity<List<KyrrexBalanceResponse>> resp = kyrrexClientService.getBalances(username);
        if (!resp.getStatusCode().is2xxSuccessful()) {
            return ResponseEntity.status(resp.getStatusCode())
                    .body(ApiSuccess.of(resp.getBody() != null ? resp.getBody() : List.of()));
        }
        return ResponseEntity.ok(ApiSuccess.of(
                resp.getBody() != null ? resp.getBody() : List.of()));
    }

    private void ensureKyrrexMemberReady(String username) {
        if (kyrrexClientService.hasActiveKyrrexCredentials(username)) {
            return;
        }
        Users user = loadAkuundaUser(username);
        try {
            kyrrexClientService.ensureKyrrexMemberRegistered(username, buildSignUpRequest(user));
        } catch (KyrrexCredentialMissingException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    private KyrrexMemberSignUpRequest buildSignUpRequest(Users user) {
        String email = user.getEmail();
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Email utilisateur requis pour l'inscription Kyrrex");
        }
        String type = "business";
        if (user.getAccountType() != null) {
            String at = user.getAccountType().toLowerCase(Locale.ROOT);
            if (at.contains("personal") || at.contains("particulier") || at.contains("individual")) {
                type = "personal";
            }
        }
        String walletUsername = user.getUsername() != null && !user.getUsername().isBlank()
                ? user.getUsername()
                : user.getMobilePhone();
        int countryId = kyrrexClientService.resolveSignupCountryId(walletUsername, null);
        return new KyrrexMemberSignUpRequest(email.trim(), type, countryId);
    }

    private Users loadAkuundaUser(String username) {
        return userRepository.findFirstByUsernameOrderByCreatedAtAsc(username)
                .or(() -> userRepository.findFirstByMobilePhoneOrderByCreatedAtAsc(username))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Utilisateur Akuunda introuvable pour " + username));
    }

    private List<Map<String, Object>> mapAssetCatalog(List<KyrrexAssetResponse> items) {
        if (items == null) {
            return List.of();
        }
        List<Map<String, Object>> catalog = new ArrayList<>();
        for (KyrrexAssetResponse asset : items) {
            if (asset == null || asset.getAsset() == null || asset.getAsset().isBlank()) continue;
            List<Map<String, Object>> networks = new ArrayList<>();
            if (asset.getDchains() != null) {
                for (KyrrexAssetDchainResponse d : asset.getDchains()) {
                    if (d == null || d.getDchain() == null || d.getDchain().isBlank()) continue;
                    if (Boolean.FALSE.equals(d.getActiveDeposit())) continue;
                    Map<String, Object> net = new LinkedHashMap<>();
                    net.put("dchain", d.getDchain());
                    net.put("displayName",
                            d.getDisplayName() != null && !d.getDisplayName().isBlank()
                                    ? d.getDisplayName() : d.getDchain());
                    if (d.getMinDeposit() != null) {
                        net.put("minDeposit", d.getMinDeposit());
                    }
                    networks.add(net);
                }
            }
            if (networks.isEmpty()) continue;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("currency", asset.getAsset().trim().toUpperCase(Locale.ROOT));
            row.put("name", asset.getName() != null && !asset.getName().isBlank()
                    ? asset.getName() : asset.getAsset());
            if (asset.getPrecision() != null) {
                row.put("precision", asset.getPrecision());
            }
            row.put("networks", networks);
            catalog.add(row);
        }
        return catalog;
    }

    private Map<String, Object> parseJsonBody(String body) {
        if (body == null || body.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Réponse Kyrrex invalide: " + e.getMessage());
        }
    }

    private Object parseJsonBodyAny(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(body, Object.class);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Réponse Kyrrex invalide: " + e.getMessage());
        }
    }

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
