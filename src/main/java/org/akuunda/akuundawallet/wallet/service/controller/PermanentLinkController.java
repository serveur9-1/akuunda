package org.akuunda.akuundawallet.wallet.service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.Exceptions.ErrorResponse;
import org.akuunda.akuundawallet.common.constants.ApiConstants;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.dao.CountryCurrencyRepository;
import org.akuunda.akuundawallet.wallet.api.dao.PermanentLinkRepository;
import org.akuunda.akuundawallet.wallet.api.dto.CountryOptionResponse;
import org.akuunda.akuundawallet.wallet.api.dto.CreatePermanentLinkRequest;
import org.akuunda.akuundawallet.wallet.api.dto.CreatePermanentLinkSessionRequest;
import org.akuunda.akuundawallet.wallet.api.dto.PermanentLinkResponse;
import org.akuunda.akuundawallet.wallet.api.dto.PermanentLinkSessionResponse;
import org.akuunda.akuundawallet.wallet.api.dto.PermanentLinkStatsResponse;
import org.akuunda.akuundawallet.wallet.api.entities.CountryCurrency;
import org.akuunda.akuundawallet.wallet.api.entities.PermanentLink;
import org.akuunda.akuundawallet.wallet.api.dto.external.MeldCountryDefaults;
import org.akuunda.akuundawallet.wallet.api.dto.external.MeldCryptoCurrenciesResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.MeldFiatCurrency;
import org.akuunda.akuundawallet.wallet.api.dto.external.MeldPaymentMethod;
import org.akuunda.akuundawallet.wallet.api.dto.external.MeldQuoteRequest;
import org.akuunda.akuundawallet.wallet.api.dto.external.MeldQuoteResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.MeldSessionResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.OnRampRequest;
import org.akuunda.akuundawallet.wallet.api.dto.external.SimpleTransactionResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.WidgetSessionData;
import org.akuunda.akuundawallet.wallet.service.PermanentLinkPaymentService;
import org.akuunda.akuundawallet.wallet.service.PermanentLinkService;
import org.akuunda.akuundawallet.wallet.api.dao.PermanentLinkSessionRepository;
import org.akuunda.akuundawallet.wallet.api.entities.PermanentLinkSession;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaMeldServiceClient;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaYellowCardClientService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping(path = PermanentLinkController.API_SERVICES_ROOT, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Akuunda Services - Permanent Payment Links", description = """
        API de gestion des liens de paiement permanents :
        - Création d'un lien permanent réutilisable (QR code, page web, réseaux sociaux)
        - Chaque client génère une session isolée avec son propre wallet CREATE2
        - Paiements simultanés de plusieurs clients sans collision
        - Montant fixe ou libre selon la configuration du lien
        """)
@SecurityRequirement(name = ApiConstants.SWAGGER_BASIC_SECURITY_SCHEME)
@RequiredArgsConstructor
public class PermanentLinkController {

    public static final String API_SERVICES_ROOT = "/api/internal/v1/permanent-links";

    private final PermanentLinkService permanentLinkService;
    private final PermanentLinkPaymentService permanentLinkPaymentService;
    private final AkuundaMeldServiceClient akuundaMeldServiceClient;
    private final AkuundaYellowCardClientService akuundaYellowCardClientService;
    private final PermanentLinkSessionRepository permanentLinkSessionRepository;
    private final PermanentLinkRepository permanentLinkRepository;
    private final CountryCurrencyRepository countryCurrencyRepository;
    private final UserRepository userRepository;

    /**
     * Compte de service master (par défaut {@code akuunda1}) utilisé par tous les appels backend.
     * Il ne peut pas être considéré comme un marchand cible.
     */
    @Value("${akuunda.public.auth.username:akuunda1}")
    private String publicServiceAccountUsername;

    /**
     * Résout l'identité issue du JWT vers un username Akuunda interne, <strong>uniquement si
     * cette identité correspond à un vrai marchand</strong>. Le compte de service master
     * ({@code akuunda1}) est explicitement filtré : il ne peut pas être retourné comme marchand
     * car il authentifie tous les appels backend et n'identifie donc personne en particulier.
     *
     * <p>Stratégie de lookup :</p>
     * <ol>
     *     <li>{@code email} → match exact (case-insensitive) sur {@code users.email}.</li>
     *     <li>{@code preferred_username} → match exact sur {@code users.username}.</li>
     *     <li>{@code sub} → match sur {@code users.user_id} (Keycloak userId).</li>
     * </ol>
     */
    private Optional<String> resolveAuthenticatedMerchantUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            return Optional.empty();
        }
        Jwt jwt = jwtAuth.getToken();
        if (jwt == null) {
            return Optional.empty();
        }
        String resolved = null;
        String email = jwt.getClaimAsString("email");
        if (email != null && !email.isBlank()) {
            Optional<Users> byEmail = userRepository.findFirstByEmailOrderByCreatedAtAsc(email);
            if (byEmail.isPresent() && byEmail.get().getUsername() != null) {
                resolved = byEmail.get().getUsername();
            }
        }
        if (resolved == null) {
            String preferredUsername = jwt.getClaimAsString("preferred_username");
            if (preferredUsername != null && !preferredUsername.isBlank()) {
                Users byUsername = userRepository.getUsersByUsername(preferredUsername);
                if (byUsername != null && byUsername.getUsername() != null) {
                    resolved = byUsername.getUsername();
                }
            }
        }
        if (resolved == null) {
            String sub = jwt.getSubject();
            if (sub != null && !sub.isBlank()) {
                Optional<Users> byUserId = userRepository.findById(sub);
                if (byUserId.isPresent() && byUserId.get().getUsername() != null) {
                    resolved = byUserId.get().getUsername();
                }
            }
        }
        if (resolved != null && isPublicServiceAccountIdentifier(resolved)) {
            // Le JWT vient du compte de service master — on ne peut pas l'utiliser pour identifier un marchand.
            return Optional.empty();
        }
        return Optional.ofNullable(resolved);
    }

    private boolean isPublicServiceAccountIdentifier(String value) {
        if (value == null || value.isBlank()) return false;
        if (publicServiceAccountUsername == null || publicServiceAccountUsername.isBlank()) return false;
        return publicServiceAccountUsername.trim().equalsIgnoreCase(value.trim());
    }

    private ResponseEntity<?> serviceAccountReservedError(String username) {
        ArrayList<ErrorResponse.Error> errors = new ArrayList<>();
        errors.add(new ErrorResponse.Error(
                "MERCHANT_USERNAME_RESERVED",
                "Le compte '" + username + "' est un compte de service Akuunda, "
                        + "il ne peut pas posséder de lien permanent. Indiquez le `username` du marchand cible.",
                "username"));
        ErrorResponse body = new ErrorResponse();
        body.setSuccess(false);
        body.setErrors(errors);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    /**
     * Détermine le marchand cible d'une opération.
     *
     * <p>Tous les appels backend sont authentifiés via le compte de service master
     * ({@code akuunda1}). Le JWT ne suffit donc pas à identifier un marchand : c'est le
     * paramètre {@code requestedUsername} (path / query / body) qui fait foi.</p>
     *
     * <ul>
     *     <li>{@code requestedUsername} fourni et différent du compte de service → utilisé.</li>
     *     <li>{@code requestedUsername} == compte de service → 422 {@code MERCHANT_USERNAME_RESERVED}.</li>
     *     <li>{@code requestedUsername} absent → fallback sur le JWT (uniquement utile dans le
     *         cas dashboard où le marchand est lui-même authentifié, sinon 401).</li>
     * </ul>
     */
    private ResponseEntity<?> requireMerchantUsername(String requestedUsername) {
        if (requestedUsername != null && !requestedUsername.isBlank()) {
            if (isPublicServiceAccountIdentifier(requestedUsername)) {
                log.warn("PermanentLinks: refus operation pour compte de service '{}'", requestedUsername);
                return serviceAccountReservedError(requestedUsername);
            }
            return ResponseEntity.ok(requestedUsername.trim());
        }
        Optional<String> fromJwt = resolveAuthenticatedMerchantUsername();
        if (fromJwt.isPresent()) {
            return ResponseEntity.ok(fromJwt.get());
        }
        ArrayList<ErrorResponse.Error> errors = new ArrayList<>();
        errors.add(new ErrorResponse.Error(
                "MERCHANT_USERNAME_REQUIRED",
                "Le `username` du marchand doit être fourni (param ou body) : le JWT actuel "
                        + "correspond au compte de service Akuunda et n'identifie pas un marchand.",
                "username"));
        ErrorResponse body = new ErrorResponse();
        body.setSuccess(false);
        body.setErrors(errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
    }

    // ------------------------------------------------------------------------
    // CREATE PERMANENT LINK (authenticated)
    // ------------------------------------------------------------------------
    @PostMapping(value = "/create", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "createPermanentLink",
            summary = "Créer un lien de paiement permanent",
            description = """
        Crée un nouveau lien de paiement permanent réutilisable.
        
        **Règles :**
        - Le slug doit être unique et URL-safe (ex: boutique-mama-coco)
        - Seule la description est OBLIGATOIRE ; montant et devise sont OPTIONNELS
        - Aucun appel on-chain lors de la création du lien (juste BDD)
        - Les appels on-chain se font lors de la création de sessions
        """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lien permanent créé avec succès",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PermanentLinkResponse.class))),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "404", description = "Utilisateur non trouvé"),
            @ApiResponse(responseCode = "409", description = "Slug déjà utilisé"),
            @ApiResponse(responseCode = "422", description = "Marchand sans portefeuille Akuunda actif"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<?> createPermanentLink(
            @Parameter(name = "username",
                    description = "Username Akuunda Pay du marchand créateur — **autoritatif**. "
                            + "Tous les appels backend étant authentifiés via le compte de service master "
                            + "(`akuunda1`), ce paramètre est nécessaire pour identifier le marchand. "
                            + "Le JWT n'est utilisé en fallback que pour le cas dashboard Pro où le "
                            + "marchand est lui-même authentifié.",
                    required = false)
            @RequestParam(required = false) String username,
            @RequestBody @Valid CreatePermanentLinkRequest request) {
        ResponseEntity<?> resolution = requireMerchantUsername(username);
        if (!resolution.getStatusCode().is2xxSuccessful()) {
            return resolution;
        }
        String effectiveUsername = (String) resolution.getBody();
        log.info("Request to create permanent link for user: {}", effectiveUsername);
        return permanentLinkService.createPermanentLink(effectiveUsername, request);
    }

    // ------------------------------------------------------------------------
    // GET PERMANENT LINK BY SLUG (public)
    // ------------------------------------------------------------------------
    @GetMapping("/m/{slug}")
    @Operation(
            operationId = "getPermanentLinkBySlug",
            summary = "Récupérer un lien permanent par son slug (public)",
            description = "Récupère les informations publiques d'un lien de paiement permanent à partir de son slug."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lien trouvé",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PermanentLinkResponse.class))),
            @ApiResponse(responseCode = "404", description = "Lien non trouvé"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<PermanentLinkResponse> getPermanentLinkBySlug(
            @Parameter(description = "Slug du marchand", required = true, example = "boutique-mama-coco")
            @PathVariable String slug) {
        log.info("Request to get permanent link by slug: {}", slug);
        return permanentLinkService.getPermanentLinkBySlug(slug);
    }

    // ------------------------------------------------------------------------
    // GET COUNTRIES FOR LINK CURRENCY (public)
    // ------------------------------------------------------------------------
    @GetMapping("/m/{slug}/countries")
    @Operation(
            operationId = "getPermanentLinkAvailableCountries",
            summary = "Liste des pays acceptant la devise du lien (public)",
            description = """
                    Retourne la liste des pays activés acceptant la devise du lien permanent identifié par `slug`.
                    
                    **Pourquoi cet endpoint ?** Plusieurs pays partagent la même devise (XOF = CI/SN/BJ/TG/ML/BF/NE/GW,
                    XAF = CM/GA/TD/CG/CF/GQ, etc.). L'acheteur doit donc **sélectionner son pays avant** que le
                    frontend puisse afficher les moyens de paiement disponibles dans ce pays.
                    
                    **Source de vérité.** Aucune valeur n'est codée en dur : la liste est lue dynamiquement depuis
                    `country_currency.currency_code` avec filtre `is_activated = true`. Activer / désactiver un pays
                    se fait donc directement en base.
                    
                    **Étape suivante.** Une fois le pays choisi, appeler :
                    `GET /api/internal/v1/permanent-links/yellow-card/channels?countryCode={code}` pour récupérer
                    les canaux disponibles, puis `.../yellow-card/networks?countryCode={code}` pour les opérateurs.
                    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste des pays récupérée avec succès",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CountryOptionResponse.class))),
            @ApiResponse(responseCode = "404", description = "Lien permanent introuvable")
    })
    public ResponseEntity<List<CountryOptionResponse>> getCountriesForLinkCurrency(
            @Parameter(description = "Slug du marchand", required = true, example = "boutique-mama-coco")
            @PathVariable String slug) {
        log.info("Request to list available countries for permanent link slug: {}", slug);

        Optional<PermanentLink> linkOpt = permanentLinkRepository.findByMerchantSlug(slug);
        if (linkOpt.isEmpty()) {
            log.warn("Lien permanent introuvable pour slug: {}", slug);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        String currency = linkOpt.get().getCurrency();
        if (currency == null || currency.isBlank()) {
            log.warn("Lien permanent {} sans devise renseignée", slug);
            return ResponseEntity.ok(List.of());
        }
        String currencyUpper = currency.trim().toUpperCase();

        List<CountryCurrency> rows = countryCurrencyRepository.findCountryCurrencyByCurrencyCode(currencyUpper);
        List<CountryOptionResponse> countries = rows == null ? List.of() : rows.stream()
                .filter(CountryCurrency::isActivated)
                .map(cc -> CountryOptionResponse.builder()
                        .code(cc.getCountryCode())
                        .name(cc.getCountryName())
                        .callingCode(cc.getCallingCode())
                        .continentName(cc.getContinentName())
                        .flagUrl(cc.getFlagUrl())
                        .currencyCode(cc.getCurrencyCode())
                        .build())
                .sorted(java.util.Comparator.comparing(
                        CountryOptionResponse::getName,
                        java.util.Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
        log.info("Slug {} (currency {}) -> {} pays activés", slug, currencyUpper, countries.size());
        return ResponseEntity.ok(countries);
    }

    // ------------------------------------------------------------------------
    // CREATE SESSION (public)
    // ------------------------------------------------------------------------
    @PostMapping(value = "/m/{slug}/session", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "createPermanentLinkSession",
            summary = "Créer une session de paiement sur un lien permanent (public)",
            description = """
        Crée une session de paiement isolée pour un client sur un lien permanent.
        
        **Flux :**
        1. Génère un code de session unique (12 caractères)
        2. Crée un wallet CREATE2 via le smart contract Factory
        3. Retourne l'adresse CREATE2 pour que le client puisse payer
        """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Session créée avec succès",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PermanentLinkSessionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "404", description = "Lien permanent non trouvé"),
            @ApiResponse(responseCode = "410", description = "Lien permanent inactif"),
            @ApiResponse(responseCode = "422", description = "Marchand sans portefeuille Akuunda actif (impossible de créer le CREATE2)"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<?> createSession(
            @Parameter(description = "Slug du marchand", required = true, example = "boutique-mama-coco")
            @PathVariable String slug,
            @RequestBody @Valid CreatePermanentLinkSessionRequest request) {
        log.info("Request to create session for permanent link: {}", slug);
        return permanentLinkService.createSession(slug, request);
    }

    // ------------------------------------------------------------------------
    // GET SESSION BY CODE (public)
    // ------------------------------------------------------------------------
    @GetMapping("/session/{sessionCode}")
    @Operation(
            operationId = "getSessionByCode",
            summary = "Récupérer le statut d'une session de paiement (public)",
            description = "Récupère les informations et le statut d'une session de paiement à partir de son code."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session trouvée",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PermanentLinkSessionResponse.class))),
            @ApiResponse(responseCode = "404", description = "Session non trouvée"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<PermanentLinkSessionResponse> getSessionByCode(
            @Parameter(description = "Code unique de la session (12 caractères)", required = true, example = "a1b2c3d4e5f6")
            @PathVariable String sessionCode) {
        log.info("Request to get session by code: {}", sessionCode);
        return permanentLinkService.getSessionByCode(sessionCode);
    }

    // ------------------------------------------------------------------------
    // GET USER PERMANENT LINKS (authenticated)
    // ------------------------------------------------------------------------
    @GetMapping("/user/{username}")
    @Operation(
            operationId = "getUserPermanentLinks",
            summary = "Récupérer tous les liens permanents d'un marchand",
            description = "Récupère la liste de tous les liens permanents créés par un marchand, triés par date de création décroissante."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste des liens permanents",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PermanentLinkResponse.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur non trouvé"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<List<PermanentLinkResponse>> getUserPermanentLinks(
            @Parameter(description = "Username Akuunda Pay du marchand — **autoritatif**. Le compte de "
                    + "service master ne peut pas être listé ici.",
                    required = true, example = "002250759146858")
            @PathVariable String username) {
        if (isPublicServiceAccountIdentifier(username)) {
            log.warn("PermanentLinks: refus du listing pour le compte de service '{}'", username);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(List.of());
        }
        log.info("Request to get permanent links for user: {}", username);
        return permanentLinkService.getUserPermanentLinks(username);
    }

    // ------------------------------------------------------------------------
    // GET STATS (authenticated)
    // ------------------------------------------------------------------------
    @GetMapping("/{slug}/stats")
    @Operation(
            operationId = "getPermanentLinkStats",
            summary = "Statistiques d'un lien permanent",
            description = "Récupère les statistiques d'un lien permanent (sessions, paiements, montant total reçu)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statistiques récupérées",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PermanentLinkStatsResponse.class))),
            @ApiResponse(responseCode = "403", description = "Non autorisé"),
            @ApiResponse(responseCode = "404", description = "Lien non trouvé"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<PermanentLinkStatsResponse> getPermanentLinkStats(
            @Parameter(description = "Slug du marchand", required = true, example = "boutique-mama-coco")
            @PathVariable String slug,
            @Parameter(name = "username",
                    description = "Username Akuunda Pay du marchand — **autoritatif**. Le JWT n'est utilisé "
                            + "en fallback que pour le cas dashboard Pro où le marchand est lui-même authentifié.",
                    required = false)
            @RequestParam(required = false) String username) {
        ResponseEntity<?> resolution = requireMerchantUsername(username);
        if (!resolution.getStatusCode().is2xxSuccessful()) {
            return ResponseEntity.status(resolution.getStatusCode()).body(null);
        }
        String effectiveUsername = (String) resolution.getBody();
        log.info("Request to get stats for permanent link: {} by user: {}", slug, effectiveUsername);
        return permanentLinkService.getPermanentLinkStats(effectiveUsername, slug);
    }

    // ------------------------------------------------------------------------
    // DEACTIVATE (authenticated)
    // ------------------------------------------------------------------------
    @PutMapping("/{slug}/deactivate")
    @Operation(
            operationId = "deactivatePermanentLink",
            summary = "Désactiver un lien permanent",
            description = "Désactive un lien permanent. Le lien ne pourra plus recevoir de nouvelles sessions."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lien désactivé avec succès"),
            @ApiResponse(responseCode = "403", description = "Non autorisé"),
            @ApiResponse(responseCode = "404", description = "Lien non trouvé"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<String> deactivatePermanentLink(
            @Parameter(description = "Slug du marchand", required = true, example = "boutique-mama-coco")
            @PathVariable String slug,
            @Parameter(name = "username",
                    description = "Username Akuunda Pay du marchand — **autoritatif**.",
                    required = false)
            @RequestParam(required = false) String username) {
        ResponseEntity<?> resolution = requireMerchantUsername(username);
        if (!resolution.getStatusCode().is2xxSuccessful()) {
            return ResponseEntity.status(resolution.getStatusCode())
                    .body("{\"success\": false, \"error\": \"Merchant username required or reserved\"}");
        }
        String effectiveUsername = (String) resolution.getBody();
        log.info("Request to deactivate permanent link: {} for user: {}", slug, effectiveUsername);
        return permanentLinkService.deactivatePermanentLink(effectiveUsername, slug);
    }

    // ------------------------------------------------------------------------
    // ACTIVATE (authenticated)
    // ------------------------------------------------------------------------
    @PutMapping("/{slug}/activate")
    @Operation(
            operationId = "activatePermanentLink",
            summary = "Réactiver un lien permanent",
            description = "Réactive un lien permanent précédemment désactivé."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lien réactivé avec succès"),
            @ApiResponse(responseCode = "403", description = "Non autorisé"),
            @ApiResponse(responseCode = "404", description = "Lien non trouvé"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<String> activatePermanentLink(
            @Parameter(description = "Slug du marchand", required = true, example = "boutique-mama-coco")
            @PathVariable String slug,
            @Parameter(name = "username",
                    description = "Username Akuunda Pay du marchand — **autoritatif**.",
                    required = false)
            @RequestParam(required = false) String username) {
        ResponseEntity<?> resolution = requireMerchantUsername(username);
        if (!resolution.getStatusCode().is2xxSuccessful()) {
            return ResponseEntity.status(resolution.getStatusCode())
                    .body("{\"success\": false, \"error\": \"Merchant username required or reserved\"}");
        }
        String effectiveUsername = (String) resolution.getBody();
        log.info("Request to activate permanent link: {} for user: {}", slug, effectiveUsername);
        return permanentLinkService.activatePermanentLink(effectiveUsername, slug);
    }

    // ------------------------------------------------------------------------
    // INITIATE YELLOWCARD PAYMENT (public)
    // ------------------------------------------------------------------------
    @PostMapping(value = "/session/{sessionCode}/pay", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "initiateYellowCardPayment",
            summary = "Initier un paiement YellowCard sur une session (public)",
            description = """
        Initie un paiement fiat via YellowCard (MoMo Afrique) sur la session CREATE2.
        
        **Flux :**
        1. Valide que la session existe, est en statut CREATED et non expirée
        2. Appelle YellowCard avec l'adresse CREATE2 de la session comme wallet de règlement
        3. Met à jour la session en PENDING avec le provider YELLOWCARD
        4. Retourne la réponse YellowCard (transactionId, redirectUrl)
        
        **Compte marchand Akuunda Pay (mapping YellowCard) :**
        Le corps `OnRampRequest` décrit surtout le **payeur** (recipient, source, country, etc.).
        L'identité **marchand** n'est pas dans ce JSON : elle est résolue côté serveur à partir de la session.
        Utilisez `merchantUserId` (Keycloak sub / users.user_id) et `merchantUsername` retournés par
        `GET /api/internal/v1/permanent-links/session/{sessionCode}` pour afficher ou tracer le bon compte marchand.
        YellowCard reçoit le customerUID = `merchantUserId` du créateur du lien.
        """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paiement initié avec succès"),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "404", description = "Session non trouvée"),
            @ApiResponse(responseCode = "409", description = "La session n'est pas en statut CREATED"),
            @ApiResponse(responseCode = "410", description = "La session a expiré"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<Object> initiateYellowCardPayment(
            @Parameter(description = "Code unique de la session (12 caractères)", required = true, example = "a1b2c3d4e5f6")
            @PathVariable String sessionCode,
            @RequestBody OnRampRequest request) {
        log.info("Request to initiate YellowCard payment for session: {}", sessionCode);
        return permanentLinkPaymentService.initiateYellowCardPayment(sessionCode, request);
    }

    // ------------------------------------------------------------------------
    // INITIATE MELD PAYMENT (public)
    // ------------------------------------------------------------------------
    @PostMapping(value = "/session/{sessionCode}/pay/meld", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "initiateMeldPayment",
            summary = "Initier un paiement Meld sur une session (public)",
            description = """
        Initie un paiement via le widget Meld (carte bancaire / Europe) sur la session CREATE2.
        
        **Flux :**
        1. Valide que la session existe, est en statut CREATED et non expirée
        2. Crée une session widget Meld avec l'adresse CREATE2 de la session comme wallet de destination
        3. Met à jour la session en PENDING avec le provider MELD
        4. Retourne la réponse Meld (widgetUrl, id, token)
        
        **Compte marchand Akuunda Pay (mapping Meld) :**
        Meld reçoit `externalCustomerId` = username marchand Akuunda Pay (champ `merchantUsername` sur la réponse
        de création / lecture de session). Le champ `userName` du corps est optionnel : pour ce flux le serveur
        utilise le créateur du lien permanent. Pré-remplissez l'UI avec `merchantUsername` pour cohérence avec nos autres APIs.
        """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session Meld créée avec succès",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = MeldSessionResponse.class))),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "404", description = "Session non trouvée"),
            @ApiResponse(responseCode = "409", description = "La session n'est pas en statut CREATED"),
            @ApiResponse(responseCode = "410", description = "La session a expiré"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<MeldSessionResponse> initiateMeldPayment(
            @Parameter(description = "Code unique de la session (12 caractères)", required = true, example = "a1b2c3d4e5f6")
            @PathVariable String sessionCode,
            @RequestBody WidgetSessionData request) {
        log.info("Request to initiate Meld payment for session: {}", sessionCode);
        return permanentLinkPaymentService.initiateMeldPayment(sessionCode, request);
    }

    // ------------------------------------------------------------------------
    // LIST SESSIONS BY SLUG (authenticated)
    // ------------------------------------------------------------------------
    @GetMapping("/{slug}/sessions")
    @Operation(
            operationId = "getSessionsBySlug",
            summary = "Lister les sessions d'un lien permanent (authentifié)",
            description = "Récupère la liste paginée des sessions d'un lien permanent, triées par date de création décroissante."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste des sessions",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = PermanentLinkSessionResponse.class))),
            @ApiResponse(responseCode = "403", description = "Non autorisé"),
            @ApiResponse(responseCode = "404", description = "Lien permanent non trouvé"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<List<PermanentLinkSessionResponse>> getSessionsBySlug(
            @Parameter(description = "Slug du marchand", required = true, example = "boutique-mama-coco")
            @PathVariable String slug,
            @Parameter(name = "username",
                    description = "Username Akuunda Pay du marchand — **autoritatif**.",
                    required = false)
            @RequestParam(required = false) String username,
            @Parameter(name = "status", description = "Filtre optionnel par statut (CREATED, PENDING, PAID, EXPIRED...)", required = false)
            @RequestParam(required = false) String status,
            @Parameter(name = "page", description = "Numéro de page (commence à 0)", required = false)
            @RequestParam(defaultValue = "0") int page,
            @Parameter(name = "size", description = "Nombre de résultats par page", required = false)
            @RequestParam(defaultValue = "20") int size) {
        ResponseEntity<?> resolution = requireMerchantUsername(username);
        if (!resolution.getStatusCode().is2xxSuccessful()) {
            return ResponseEntity.status(resolution.getStatusCode()).body(null);
        }
        String effectiveUsername = (String) resolution.getBody();
        log.info("Request to get sessions for permanent link: {} by user: {}", slug, effectiveUsername);
        return permanentLinkPaymentService.getSessionsBySlug(effectiveUsername, slug, status, page, size);
    }

    // ------------------------------------------------------------------------
    // MELD — COUNTRY DEFAULTS (public)
    // ------------------------------------------------------------------------
    @GetMapping("/meld/countries/{countryCode}/defaults")
    @Operation(
            operationId = "getPermanentLinkMeldCountryDefaults",
            summary = "Get Meld country defaults config (public)",
            description = "Récupère la configuration par défaut (monnaie, méthodes de paiement) pour le pays spécifié."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Defaults récupérés avec succès"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<List<MeldCountryDefaults>> getMeldCountryDefaults(
            @Parameter(description = "Code pays ISO (ex: FR)", required = true, example = "FR")
            @PathVariable String countryCode) {
        log.info("Request to get Meld country defaults for: {}", countryCode);
        return akuundaMeldServiceClient.getCountryDefaults(countryCode);
    }

    // ------------------------------------------------------------------------
    // MELD — FIAT CURRENCIES (public)
    // ------------------------------------------------------------------------
    @GetMapping("/meld/countries/{countryCode}/fiat-currencies")
    @Operation(
            operationId = "getPermanentLinkMeldFiatCurrencies",
            summary = "Get Meld fiat currencies for a country (public)",
            description = "Retourne la liste des monnaies fiat disponibles pour le pays spécifié."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Monnaies fiat récupérées avec succès"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<List<MeldFiatCurrency>> getMeldFiatCurrencies(
            @Parameter(description = "Code pays ISO (ex: FR)", required = true, example = "FR")
            @PathVariable String countryCode) {
        log.info("Request to get Meld fiat currencies for: {}", countryCode);
        return akuundaMeldServiceClient.getFiatCurrencies(countryCode);
    }

    // ------------------------------------------------------------------------
    // MELD — CRYPTO CURRENCIES (public)
    // ------------------------------------------------------------------------
    @GetMapping("/meld/countries/{countryCode}/crypto-currencies")
    @Operation(
            operationId = "getPermanentLinkMeldCryptoCurrencies",
            summary = "Get Meld crypto currencies for a country (public)",
            description = "Retourne la liste des crypto-monnaies disponibles pour le pays spécifié."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Crypto-monnaies récupérées avec succès"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<MeldCryptoCurrenciesResponse> getMeldCryptoCurrencies(
            @Parameter(description = "Code pays ISO (ex: FR)", required = true, example = "FR")
            @PathVariable String countryCode) {
        log.info("Request to get Meld crypto currencies for: {}", countryCode);
        return akuundaMeldServiceClient.getCryptoCurrencies(countryCode);
    }

    // ------------------------------------------------------------------------
    // MELD — PAYMENT METHODS (public)
    // ------------------------------------------------------------------------
    @GetMapping("/meld/countries/{countryCode}/payment-methods")
    @Operation(
            operationId = "getPermanentLinkMeldPaymentMethods",
            summary = "Get Meld payment methods for a country (public)",
            description = "Retourne les méthodes de paiement disponibles filtrées par monnaie fiat."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Méthodes de paiement récupérées avec succès"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<List<MeldPaymentMethod>> getMeldPaymentMethods(
            @Parameter(description = "Code pays ISO (ex: FR)", required = true, example = "FR")
            @PathVariable String countryCode,
            @Parameter(description = "Code monnaie fiat (ex: EUR)", required = true, example = "EUR")
            @RequestParam String fiatCurrency) {
        log.info("Request to get Meld payment methods for country: {}, fiatCurrency: {}", countryCode, fiatCurrency);
        return akuundaMeldServiceClient.getPaymentMethods(fiatCurrency);
    }

    // ------------------------------------------------------------------------
    // MELD — QUOTES (public)
    // ------------------------------------------------------------------------
    @PostMapping(value = "/meld/quotes", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "getPermanentLinkMeldQuotes",
            summary = "Get real-time Meld quote (public)",
            description = "Récupère un quote Meld pour convertir un montant fiat en crypto ou inversement."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Quote récupéré avec succès",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = MeldQuoteResponse.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur ou wallet introuvable"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<MeldQuoteResponse> getMeldQuotes(
            @RequestBody MeldQuoteRequest request) {
        log.info("Request to get Meld quote for user: {}", request.getUsername());
        return akuundaMeldServiceClient.getMeldQuotes(
                request.getUsername(),
                request.getSourceCurrencyCode(),
                request.getDestinationCurrencyCode(),
                request.getSourceAmount() != null ? String.valueOf(request.getSourceAmount()) : null,
                request.getCountryCode()
        );
    }

    // ------------------------------------------------------------------------
    // YELLOWCARD — CHANNELS (public)
    // ------------------------------------------------------------------------
    @GetMapping("/yellow-card/channels")
    @Operation(
            operationId = "getPermanentLinkYellowCardChannels",
            summary = "Get YellowCard channels by country (public)",
            description = "Récupère les canaux de paiement YellowCard disponibles pour un pays."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Canaux récupérés avec succès"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<String> getYellowCardChannels(
            @Parameter(description = "Code pays ISO (ex: CI)", required = true, example = "CI")
            @RequestParam String countryCode) {
        log.info("Request to get YellowCard channels for country: {}", countryCode);
        return akuundaYellowCardClientService.getChannels(countryCode);
    }

    // ------------------------------------------------------------------------
    // YELLOWCARD — RATES BY CHANNEL (public)
    // ------------------------------------------------------------------------
    @GetMapping("/yellow-card/rates/{channelId}")
    @Operation(
            operationId = "getPermanentLinkYellowCardRates",
            summary = "Get YellowCard rates by channel (public)",
            description = "Récupère les taux YellowCard pour un canal et une devise donnés."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Taux récupérés avec succès"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<String> getYellowCardRatesByChannel(
            @Parameter(description = "Identifiant du canal YellowCard", required = true)
            @PathVariable String channelId,
            @Parameter(description = "Code devise (ex: XOF)", required = true, example = "XOF")
            @RequestParam String currencyCode) {
        log.info("Request to get YellowCard rates for channel: {}, currency: {}", channelId, currencyCode);
        return akuundaYellowCardClientService.getRatesByChannelId(channelId, currencyCode);
    }

    // ------------------------------------------------------------------------
    // YELLOWCARD — RATES (public, without channelId)
    // ------------------------------------------------------------------------
    @GetMapping("/yellow-card/rates")
    @Operation(
            operationId = "getPermanentLinkYellowCardRatesByCurrency",
            summary = "Get YellowCard rates by currency (public)",
            description = "Récupère les taux YellowCard pour une devise donnée."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Taux récupérés avec succès"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<String> getYellowCardRates(
            @Parameter(description = "Code devise (ex: XOF)", required = true, example = "XOF")
            @RequestParam String currencyCode) {
        log.info("Request to get YellowCard rates for currency: {}", currencyCode);
        return akuundaYellowCardClientService.getRates(currencyCode);
    }

    // ------------------------------------------------------------------------
    // YELLOWCARD — NETWORKS (public)
    // ------------------------------------------------------------------------
    @GetMapping("/yellow-card/networks")
    @Operation(
            operationId = "getPermanentLinkYellowCardNetworks",
            summary = "Get YellowCard crypto networks (public)",
            description = "Récupère les réseaux crypto YellowCard disponibles pour un pays."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Réseaux récupérés avec succès"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<String> getYellowCardNetworks(
            @Parameter(description = "Code pays ISO (ex: CI)", required = true, example = "CI")
            @RequestParam String countryCode) {
        log.info("Request to get YellowCard networks for country: {}", countryCode);
        return akuundaYellowCardClientService.getNetworks(countryCode);
    }

    // ------------------------------------------------------------------------
    // YELLOWCARD — TRANSACTION STATUS (public)
    // ------------------------------------------------------------------------
    @GetMapping("/yellow-card/transactions/status/{sequenceId}")
    @Operation(
            operationId = "getPermanentLinkYellowCardTransactionStatus",
            summary = "Poll YellowCard transaction status (public)",
            description = "Vérifie le statut d'une transaction YellowCard via son identifiant unique (sequenceId)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statut récupéré avec succès",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SimpleTransactionResponse.class))),
            @ApiResponse(responseCode = "403", description = "Transaction n'appartient pas à l'utilisateur"),
            @ApiResponse(responseCode = "404", description = "Transaction non trouvée"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<SimpleTransactionResponse> getYellowCardTransactionStatus(
            @Parameter(description = "Identifiant unique de la transaction YellowCard", required = true)
            @PathVariable String sequenceId,
            @Parameter(description = "Username pour vérifier la propriété de la transaction", required = true)
            @RequestParam String username) {
        log.info("Request to get YellowCard transaction status for sequenceId: {}, user: {}", sequenceId, username);
        return akuundaYellowCardClientService.getSimpleTransactionStatus(sequenceId, username);
    }

    // ------------------------------------------------------------------------
    // PAY REDIRECT (public) — short URL that redirects to the provider widget
    // ------------------------------------------------------------------------
    @GetMapping("/pay-redirect/{sessionCode}")
    @Operation(
            operationId = "payRedirect",
            summary = "Redirect to payment provider (public)",
            description = "Redirects the user to the payment provider URL stored in the session. This short URL hides sensitive provider parameters from the client."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "302", description = "Redirect to payment provider"),
            @ApiResponse(responseCode = "404", description = "Session not found or no provider URL"),
            @ApiResponse(responseCode = "410", description = "Session expired")
    })
    public ResponseEntity<Void> payRedirect(
            @Parameter(description = "Session code", required = true)
            @PathVariable String sessionCode) {
        log.info("Pay redirect requested for session: {}", sessionCode);

        java.util.Optional<PermanentLinkSession> sessionOptional = permanentLinkSessionRepository.findBySessionCode(sessionCode);
        if (sessionOptional.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        PermanentLinkSession session = sessionOptional.get();
        String providerWidgetUrl = session.getProviderWidgetUrl();

        if (providerWidgetUrl == null || providerWidgetUrl.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(LocalDateTime.now())) {
            return ResponseEntity.status(HttpStatus.GONE).build();
        }

        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", providerWidgetUrl)
                .build();
    }
}
