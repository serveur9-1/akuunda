package org.akuunda.akuundawallet.wallet.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.Exceptions.ErrorResponse;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.keycloak.api.service.KeycloakUserService;
import org.akuunda.akuundawallet.wallet.api.dao.MerchantApiKeyRepository;
import org.akuunda.akuundawallet.wallet.api.dao.PermanentLinkRepository;
import org.akuunda.akuundawallet.wallet.api.dto.MerchantKeyCreateRequest;
import org.akuunda.akuundawallet.wallet.api.dto.MerchantKeyItem;
import org.akuunda.akuundawallet.wallet.api.dto.MerchantKeyResponse;
import org.akuunda.akuundawallet.wallet.api.entities.MerchantApiKey;
import org.akuunda.akuundawallet.wallet.api.entities.PermanentLink;
import org.akuunda.akuundawallet.wallet.service.MerchantKeysService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.keycloak.representations.idm.UserRepresentation;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MerchantKeysServiceImpl implements MerchantKeysService {

    private final MerchantApiKeyRepository merchantApiKeyRepository;
    private final UserRepository userRepository;
    private final PermanentLinkRepository permanentLinkRepository;
    private final KeycloakUserService keycloakUserService;

    /**
     * Compte de service public utilisé par {@code frontend-web-pay} et par le BFF interne pour
     * s'authentifier auprès de Keycloak. Toutes les requêtes "publiques" arrivent avec ce JWT.
     * Il ne peut donc pas être considéré comme un marchand : on l'identifie via la propriété
     * {@code akuunda.public.auth.username} pour pouvoir lui refuser explicitement la création
     * d'une clé en son propre nom.
     */
    @Value("${akuunda.public.auth.username:akuunda1}")
    private String publicServiceAccountUsername;

    private static final String LIVE_KEY_PREFIX = "sk_live_";
    private static final String TEST_KEY_PREFIX = "sk_test_";
    private static final String WEBHOOK_SECRET_PREFIX = "whsec_";
    private static final int API_KEY_LENGTH = 32;
    private static final int WEBHOOK_SECRET_LENGTH = 48;
    private static final String MODE_LIVE = "live";
    private static final String MODE_TEST = "test";
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    @Override
    public ResponseEntity<?> createKey(Jwt jwt, MerchantKeyCreateRequest request) {
        try {
            // Étape 1 : identifier le marchand cible.
            // L'architecture côté Akuunda repose sur un compte de service master (`akuunda1`)
            // qui authentifie l'ensemble des appels backend auprès de Keycloak. Le JWT seul ne
            // suffit donc pas à identifier le marchand : le `username` du body est autoritatif.
            String claimedUsername = request != null ? trim(request.getUsername()) : null;

            Users merchant;
            if (claimedUsername != null) {
                // Cas BFF / dashboard : le caller envoie explicitement le username du marchand.
                if (isPublicServiceAccountIdentifier(claimedUsername)) {
                    log.warn("MerchantKeys: refus de creation pour le compte de service '{}' (username body)", claimedUsername);
                    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                            .body(buildError("MERCHANT_USERNAME_RESERVED",
                                    "Le compte '" + claimedUsername + "' est un compte de service Akuunda, "
                                            + "il ne peut pas posséder de clé marchand. Indiquez le `username` "
                                            + "du marchand cible (login Akuunda Pay, email ou téléphone).",
                                    "username"));
                }
                merchant = resolveUserByUsername(claimedUsername);
                if (merchant == null) {
                    log.warn("MerchantKeys: username='{}' inconnu dans la base utilisateurs", claimedUsername);
                    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                            .body(buildError("MERCHANT_NOT_FOUND",
                                    "Aucun compte Akuunda trouvé pour `username='" + claimedUsername + "'`. "
                                            + "Le marchand doit d'abord créer son compte Akuunda Pay.",
                                    "username"));
                }
            } else {
                // Cas legacy / dashboard où le marchand lui-même est authentifié : on retombe
                // sur la résolution via le JWT (préserve la rétro-compat).
                merchant = resolveUser(jwt);
                if (merchant == null) {
                    merchant = provisionLocalUserFromJwt(jwt);
                }
                if (merchant == null) {
                    log.error("MerchantKeys: aucun marchand identifiable (pas de username dans le body, JWT non rattaché)");
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(buildError("MERCHANT_USERNAME_REQUIRED",
                                    "Le champ `username` est requis dans le body : indiquez le login Akuunda Pay "
                                            + "du marchand pour lequel créer cette clé (le JWT ne suffit pas à "
                                            + "identifier un marchand lorsqu'il est issu d'un compte de service).",
                                    "username"));
                }
                if (isPublicServiceAccountIdentifier(merchant.getUsername())
                        || isPublicServiceAccountIdentifier(merchant.getEmail())) {
                    log.warn("MerchantKeys: refus de creation : JWT resout le compte de service '{}' sans username explicite",
                            merchant.getUsername());
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(buildError("MERCHANT_USERNAME_REQUIRED",
                                    "Le JWT correspond au compte de service Akuunda ('" + merchant.getUsername()
                                            + "') et non à un marchand. Renseignez explicitement `username` dans le body "
                                            + "pour cibler le marchand pour lequel créer la clé.",
                                    "username"));
                }
            }

            // À ce stade `merchant` est un vrai compte marchand (jamais un compte de service).
            Users user = merchant;

            // Résolution / création automatique d'un PermanentLink (couplage interne)
            PermanentLink permanentLink = resolveOrCreatePermanentLink(user);

            MerchantKeyCreateRequest safe = request != null ? request : MerchantKeyCreateRequest.builder().build();
            String mode = normalizeMode(safe.getMode());
            String apiKey = (MODE_TEST.equals(mode) ? TEST_KEY_PREFIX : LIVE_KEY_PREFIX) + randomToken(API_KEY_LENGTH);
            String webhookSecret = WEBHOOK_SECRET_PREFIX + randomToken(WEBHOOK_SECRET_LENGTH);

            MerchantApiKey merchantApiKey = MerchantApiKey.builder()
                    .apiKey(apiKey)
                    .apiSecret(webhookSecret) // réutilisé comme secret de signature des webhooks
                    .merchant(user)
                    .permanentLink(permanentLink)
                    .name(safe.getName())
                    .mode(mode)
                    .webhookUrl(safe.getWebhookUrl())
                    .callbackUrl(safe.getReturnUrl())
                    .cancelUrl(safe.getCancelUrl())
                    .isActive(true)
                    .build();

            merchantApiKeyRepository.save(merchantApiKey);
            log.info("API key created for user {} (id={}, mode={})", user.getUsername(), merchantApiKey.getId(), mode);

            MerchantKeyResponse response = MerchantKeyResponse.builder()
                    .id(merchantApiKey.getId())
                    .name(safe.getName())
                    .mode(mode)
                    .apiKey(apiKey)
                    .webhookSecret(webhookSecret)
                    .webhookUrl(merchantApiKey.getWebhookUrl())
                    .returnUrl(merchantApiKey.getCallbackUrl())
                    .cancelUrl(merchantApiKey.getCancelUrl())
                    .merchantUsername(user.getUsername())
                    .merchantUserId(user.getUserId())
                    .merchantEmail(user.getEmail())
                    .merchantSlug(permanentLink != null ? permanentLink.getMerchantSlug() : null)
                    .createdAt(merchantApiKey.getCreatedAt())
                    .build();

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception e) {
            log.error("Erreur création clé API : {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(buildError("INTERNAL_ERROR", "Erreur interne lors de la création de la clé API", null));
        }
    }

    @Override
    public ResponseEntity<List<MerchantKeyItem>> listKeys(Jwt jwt) {
        try {
            Users user = resolveUser(jwt);
            if (user == null) {
                // Pas de compte Akuunda → on retourne une liste vide (200) plutôt qu'un 403
                // pour ne pas casser l'affichage du dashboard quand le marchand vient juste d'être créé.
                return ResponseEntity.ok(List.of());
            }
            List<MerchantApiKey> keys = merchantApiKeyRepository.findByMerchantOrderByCreatedAtDesc(user);
            List<MerchantKeyItem> items = keys.stream()
                    .map(k -> MerchantKeyItem.builder()
                            .id(k.getId())
                            .name(k.getName())
                            .mode(normalizeMode(k.getMode()))
                            .apiKey(k.getApiKey())
                            .active(k.getIsActive())
                            .webhookUrl(k.getWebhookUrl())
                            .returnUrl(k.getCallbackUrl())
                            .cancelUrl(k.getCancelUrl())
                            .merchantUsername(user.getUsername())
                            .merchantUserId(user.getUserId())
                            .merchantSlug(k.getPermanentLink() != null ? k.getPermanentLink().getMerchantSlug() : null)
                            .createdAt(k.getCreatedAt())
                            .build())
                    .collect(Collectors.toList());
            return ResponseEntity.ok(items);
        } catch (Exception e) {
            log.error("Erreur listing clés API : {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Résout un marchand à partir d'un identifiant libre fourni par l'intégrateur :
     * login Akuunda, email ou téléphone (avec ou sans indicatif). Utilise la même logique
     * robuste que la résolution JWT pour absorber les variantes ({@code +33…}, {@code 0033…},
     * casse, etc.).
     */
    private Users resolveUserByUsername(String username) {
        if (username == null || username.isBlank()) return null;
        return tryResolveLoginHint(username.trim());
    }

    /**
     * Vrai si l'identifiant fourni correspond au compte de service Akuunda public
     * ({@code akuunda1} par défaut, configurable via {@code akuunda.public.auth.username}).
     * Sert à interdire la création d'une clé marchand sur ce compte technique.
     */
    private boolean isPublicServiceAccountIdentifier(String value) {
        if (value == null || value.isBlank()) return false;
        if (publicServiceAccountUsername == null || publicServiceAccountUsername.isBlank()) return false;
        return publicServiceAccountUsername.trim().equalsIgnoreCase(value.trim());
    }

    private static ErrorResponse buildError(String code, String message, String field) {
        ArrayList<ErrorResponse.Error> errors = new ArrayList<>();
        errors.add(new ErrorResponse.Error(code, message, field));
        ErrorResponse body = new ErrorResponse();
        body.setSuccess(false);
        body.setErrors(errors);
        return body;
    }

    private String normalizeMode(String mode) {
        if (mode == null) return MODE_LIVE;
        String m = mode.trim().toLowerCase();
        return MODE_TEST.equals(m) ? MODE_TEST : MODE_LIVE;
    }

    @Override
    public ResponseEntity<Void> revokeKey(Jwt jwt, Long keyId) {
        try {
            Users user = resolveUser(jwt);
            if (user == null) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            MerchantApiKey key = merchantApiKeyRepository.findById(keyId).orElse(null);
            if (key == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }
            if (!key.getMerchant().getUserId().equals(user.getUserId())) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }
            key.setIsActive(false);
            merchantApiKeyRepository.save(key);
            log.info("API key {} revoked by {}", keyId, user.getUsername());
            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Erreur révocation clé API {} : {}", keyId, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Résout l'utilisateur Akuunda associé au JWT.
     * <p>
     * Important : à la création compte, {@code users.user_id} est souvent l'ID Venly, pas le {@code sub}
     * Keycloak — d'où un dernier recours via l'API Admin Keycloak ({@code getUserById(sub)}) pour relier
     * l'identité Keycloak au profil local (email / username / téléphone).
     */
    private Users resolveUser(Jwt jwt) {
        if (jwt == null) return null;

        String email = trim(jwt.getClaimAsString("email"));
        if (email != null) {
            Users byEmail = userRepository.findFirstByEmailIgnoreCase(email).orElse(null);
            if (byEmail != null) return byEmail;
        }

        String preferred = trim(jwt.getClaimAsString("preferred_username"));
        if (preferred != null) {
            Users u = tryResolveLoginHint(preferred);
            if (u != null) return u;
        }

        for (String hint : extraJwtLoginHints(jwt)) {
            Users u = tryResolveLoginHint(hint);
            if (u != null) return u;
        }

        String sub = trim(jwt.getSubject());
        if (sub != null) {
            Users byId = userRepository.findById(sub).orElse(null);
            if (byId != null) return byId;
            Users viaKc = resolveLocalUserViaKeycloakUserId(sub);
            if (viaKc != null) return viaKc;
        }

        log.warn("Aucun compte Akuunda local pour le JWT (email={}, preferred_username={}, sub={})",
                email, preferred, sub);
        return null;
    }

    /** Claims additionnels parfois injectés par des mappers Keycloak / proxies. */
    private static java.util.List<String> extraJwtLoginHints(Jwt jwt) {
        java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
        add(set, jwt.getClaimAsString("upn"));
        add(set, jwt.getClaimAsString("wallet_username"));
        add(set, jwt.getClaimAsString("akuunda_username"));
        add(set, jwt.getClaimAsString("phone_number"));
        add(set, jwt.getClaimAsString("phone"));
        add(set, jwt.getClaimAsString("mobile"));
        return new java.util.ArrayList<>(set);
    }

    private static void add(java.util.Set<String> set, String v) {
        if (v != null) {
            String t = v.trim();
            if (!t.isEmpty()) set.add(t);
        }
    }

    /** Tente d'associer un libellé de login (email, pseudo, téléphone, variantes). */
    private Users tryResolveLoginHint(String raw) {
        if (raw == null) return null;
        String hint = raw.trim();
        if (hint.isEmpty()) return null;

        if (hint.contains("@")) {
            Users byPreferredEmail = userRepository.findFirstByEmailIgnoreCase(hint).orElse(null);
            if (byPreferredEmail != null) return byPreferredEmail;
        }
        Users byUsernameExact = userRepository.findFirstByUsernameOrderByCreatedAtAsc(hint).orElse(null);
        if (byUsernameExact != null) return byUsernameExact;
        Users byUsernameCi = userRepository.findFirstByUsernameIgnoreCase(hint).orElse(null);
        if (byUsernameCi != null) return byUsernameCi;
        Users byPhoneExact = userRepository.findFirstByMobilePhoneOrderByCreatedAtAsc(hint).orElse(null);
        if (byPhoneExact != null) return byPhoneExact;
        Users byPhoneCi = userRepository.findFirstByMobilePhoneIgnoreCase(hint).orElse(null);
        if (byPhoneCi != null) return byPhoneCi;

        return tryDigitsOnlyMatch(hint);
    }

    private Users tryDigitsOnlyMatch(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String digits = raw.replaceAll("[^0-9]", "");
        if (digits.length() < 9) return null;
        return userRepository.findFirstByPhoneOrUsernameDigits(raw).orElse(null);
    }

    /** Interroge Keycloak Admin avec l'UUID utilisateur puis recherche le user local par email/username/tel. */
    private Users resolveLocalUserViaKeycloakUserId(String keycloakUserId) {
        if (keycloakUserId == null || !looksLikeUuid(keycloakUserId)) {
            return null;
        }
        log.info("MerchantKeys: pont Keycloak pour sub={}", keycloakUserId);
        try {
            UserRepresentation kc = keycloakUserService.getUserById(keycloakUserId);
            if (kc == null) {
                log.warn("MerchantKeys: Keycloak a retourne un utilisateur null pour sub={}", keycloakUserId);
                return null;
            }

            java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>();
            addCandidate(candidates, kc.getEmail());
            addCandidate(candidates, kc.getUsername());
            if (kc.getAttributes() != null) {
                for (var e : kc.getAttributes().entrySet()) {
                    String key = e.getKey() != null ? e.getKey().toLowerCase() : "";
                    if (!key.contains("phone") && !key.contains("mobile") && !key.contains("tel") && !key.contains("msisdn")) {
                        continue;
                    }
                    if (e.getValue() == null) continue;
                    for (String v : e.getValue()) {
                        addCandidate(candidates, v);
                    }
                }
            }

            for (String c : candidates) {
                Users u = tryResolveLoginHint(c);
                if (u != null) {
                    log.info("MerchantKeys: user local resolu via profil Keycloak (sub={})", keycloakUserId);
                    return u;
                }
            }

            log.warn("MerchantKeys: profil Keycloak sans correspondance locale (kcEmail={}, kcUsername={}, sub={})",
                    kc.getEmail(), kc.getUsername(), keycloakUserId);
        } catch (Exception e) {
            log.warn("MerchantKeys: Keycloak getUserById a echoue pour sub={}: {}", keycloakUserId, e.getMessage(), e);
        }
        return null;
    }

    private static void addCandidate(java.util.Set<String> out, String value) {
        if (value == null) return;
        String t = value.trim();
        if (!t.isEmpty()) out.add(t);
    }

    private static boolean looksLikeUuid(String s) {
        return s.length() == 36 && s.chars().filter(ch -> ch == '-').count() == 4;
    }

    /**
     * Crée une ligne minimale dans {@code users} pour un marchand qui n'a qu'un compte Keycloak
     * (dashboard Pro) et pas encore d'enregistrement wallet. La ligne est rattachée à l'UUID
     * Keycloak ({@code user_id}) — toutes les futures résolutions par {@code sub} fonctionneront.
     */
    private Users provisionLocalUserFromJwt(Jwt jwt) {
        if (jwt == null) return null;
        String sub = trim(jwt.getSubject());
        if (sub == null) {
            log.warn("Provisioning impossible : JWT sans subject");
            return null;
        }

        // Double check : un autre thread a pu créer le user entre temps
        Users existing = userRepository.findById(sub).orElse(null);
        if (existing != null) return existing;

        String email = trim(jwt.getClaimAsString("email"));
        String preferred = trim(jwt.getClaimAsString("preferred_username"));
        String givenName = trim(jwt.getClaimAsString("given_name"));
        String familyName = trim(jwt.getClaimAsString("family_name"));
        String fullName = trim(jwt.getClaimAsString("name"));

        // username : preferred_username puis email puis sub. Garantit non-null.
        String username = pickFirstNonBlank(preferred, email, sub);
        // mobilePhone : champ NOT NULL côté entité — on stocke le username comme placeholder
        // si on n'a pas de vrai téléphone (à compléter via /api/v1/pro/settings/profile).
        String mobile = pickFirstNonBlank(
                trim(jwt.getClaimAsString("phone_number")),
                trim(jwt.getClaimAsString("phone")),
                trim(jwt.getClaimAsString("mobile")),
                preferred,
                email,
                sub
        );

        String firstName = pickFirstNonBlank(givenName, fullName, "Marchand");
        String lastName = pickFirstNonBlank(familyName, "");

        Users user = Users.builder()
                .userId(sub)
                .username(username)
                .mobilePhone(mobile)
                .email(email)
                .firstname(firstName)
                .lastname(lastName)
                .accountType("PRO")
                .enabled(true)
                .emailVerified(Boolean.TRUE.equals(jwt.getClaimAsBoolean("email_verified")))
                .cguAcceptation(true)
                .dateCguAcceptation(new java.sql.Timestamp(System.currentTimeMillis()))
                .createdAt(new java.sql.Timestamp(System.currentTimeMillis()))
                .identyVerify(false)
                .build();
        try {
            userRepository.save(user);
            log.info("MerchantKeys: provisioned local users row (user_id={}, email={}, username={})",
                    sub, email, username);
            return user;
        } catch (Exception e) {
            log.error("MerchantKeys: echec du provisioning local pour sub={} : {}", sub, e.getMessage(), e);
            // En cas de race condition, on retente la lecture
            return userRepository.findById(sub).orElse(null);
        }
    }

    private static String pickFirstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v;
        }
        return null;
    }

    private String trim(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private PermanentLink resolveOrCreatePermanentLink(Users user) {
        List<PermanentLink> existing = permanentLinkRepository
                .findByCreatorAndIsActiveTrueOrderByCreatedAtDesc(user);
        if (!existing.isEmpty()) {
            return existing.get(0);
        }
        // Création silencieuse d'un PermanentLink par défaut pour le marchand
        PermanentLink link = PermanentLink.builder()
                .merchantSlug(generateMerchantSlug(user))
                .creator(user)
                .description("Default checkout link")
                .isActive(true)
                .totalSessions(0)
                .totalCompletedPayments(0)
                .totalAmountReceived(0.0)
                .build();
        return permanentLinkRepository.save(link);
    }

    private String generateMerchantSlug(Users user) {
        String base = user.getUsername() != null ? user.getUsername() : "merchant";
        String cleaned = base.toLowerCase().replaceAll("[^a-z0-9-]", "");
        if (cleaned.isEmpty()) cleaned = "merchant";
        return cleaned + "-" + randomToken(6).toLowerCase();
    }

    private static String randomToken(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
