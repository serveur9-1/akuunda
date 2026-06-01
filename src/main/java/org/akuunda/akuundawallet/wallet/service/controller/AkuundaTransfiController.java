package org.akuunda.akuundawallet.wallet.service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.constants.ApiConstants;
import org.akuunda.akuundawallet.wallet.api.dto.external.transfi.*;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaTransfiClientService;
import org.akuunda.akuundawallet.wallet.service.transfi.TransfiCreateOrderContextService;
import org.akuunda.akuundawallet.wallet.service.transfi.TransfiPersistenceService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping(path = AkuundaTransfiController.API_ROOT, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Akuunda Services - TransFi", description = """
        API d'intégration avec TransFi V3 pour :
        - Création d'utilisateurs individuels et professionnels
        - KYC / KYB
        - Ordres : payin, payout, onramp, offramp, swap
        - IBAN virtuels
        - Taux de change et configuration
        """)
@SecurityRequirement(name = ApiConstants.SWAGGER_BASIC_SECURITY_SCHEME)
@RequiredArgsConstructor
@Validated
public class AkuundaTransfiController {

    public static final String API_ROOT = "/api/internal/v1/transfi";

    private final AkuundaTransfiClientService transfiClientService;
    private final TransfiCreateOrderContextService transfiCreateOrderContextService;
    private final TransfiPersistenceService transfiPersistenceService;

    /** TransFi user IDs are always prefixed with {@code UX-} in the V3 API. */
    private static final String TRANSFI_USER_ID_PREFIX = "UX-";

    /**
     * Returns the path variable unchanged if it already looks like a TransFi userId
     * (prefix {@code UX-}); otherwise resolves it as an Akuunda username via {@code transfi_users}.
     * Returns {@code null} if no mapping exists.
     */
    private String resolveTransfiUserIdOrSelf(String userIdOrUsername) {
        if (userIdOrUsername == null) return null;
        String value = userIdOrUsername.trim();
        if (value.isEmpty()) return null;
        if (value.startsWith(TRANSFI_USER_ID_PREFIX)) return value;
        return transfiPersistenceService.resolveTransfiUserId(value).orElse(null);
    }

    private void resolveUserIdFromUsername(TransfiKycRequest request) {
        if (request == null) return;
        if (request.getUserId() != null && !request.getUserId().isBlank()) return;
        if (request.getUsername() == null || request.getUsername().isBlank()) return;
        transfiPersistenceService.resolveTransfiUserId(request.getUsername()).ifPresent(request::setUserId);
    }

    private void resolveUserIdFromUsername(TransfiKybRequest request) {
        if (request == null) return;
        if (request.getUserId() != null && !request.getUserId().isBlank()) return;
        if (request.getUsername() == null || request.getUsername().isBlank()) return;
        transfiPersistenceService.resolveTransfiUserId(request.getUsername()).ifPresent(request::setUserId);
    }

    // ══════════════════════════════════════════
    // CONFIGURATION
    // ══════════════════════════════════════════

    @GetMapping("/config/list-mids")
    @Operation(summary = "Lister les MIDs disponibles")
    @ApiResponse(responseCode = "200", description = "Liste des MIDs")
    public ResponseEntity<Object> listMids(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int limit) {
        log.info("GET /transfi/config/list-mids");
        return transfiClientService.listMids(page, limit);
    }

    @GetMapping("/config/supported-currencies")
    @Operation(summary = "Devises supportées", description = "direction: deposit | withdraw ; userType: individual | business")
    public ResponseEntity<Object> getSupportedCurrencies(
            @RequestParam(defaultValue = "deposit") String direction,
            @RequestParam(defaultValue = "individual") String userType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int limit) {
        log.info("GET /transfi/config/supported-currencies direction={} userType={}", direction, userType);
        return transfiClientService.getSupportedCurrencies(direction, userType, page, limit);
    }

    @GetMapping("/config/payment-methods")
    @Operation(summary = "Méthodes de paiement disponibles")
    public ResponseEntity<Object> getPaymentMethods(
            @RequestParam(defaultValue = "deposit") String direction,
            @RequestParam String currency,
            @RequestParam(defaultValue = "true") boolean headlessMode,
            @RequestParam(defaultValue = "individual") String userType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int limit) {
        log.info("GET /transfi/config/payment-methods currency={} direction={}", currency, direction);
        return transfiClientService.getPaymentMethods(direction, currency, headlessMode, userType, page, limit);
    }

    @GetMapping("/config/payment-methods/iban")
    @Operation(summary = "Méthodes de paiement IBAN supportées + requiredFields pour création",
            description = """
                    Retourne les méthodes IBAN supportées (paymentCode, paymentType, minAmount,
                    maxAmount, logo, ...) ainsi que les requiredFields nécessaires à la création
                    via POST /iban/create-iban (champs customer, country, ...).

                    beneficiaryType : individual (défaut) | business
                    """)
    public ResponseEntity<Object> getIbanPaymentMethods(
            @RequestParam(required = false) String currency,
            @RequestParam(required = false, defaultValue = "individual") String beneficiaryType,
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(defaultValue = "1") int page) {
        log.info("GET /transfi/config/payment-methods/iban currency={} beneficiaryType={}",
                currency, beneficiaryType);
        return transfiClientService.getIbanPaymentMethods(currency, beneficiaryType, limit, page);
    }

    @GetMapping("/config/list-tokens")
    @Operation(summary = "Lister les tokens crypto supportés",
            description = "direction: deposit | withdraw")
    public ResponseEntity<Object> listTokens(
            @RequestParam(defaultValue = "deposit") String direction,
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(defaultValue = "1") int page) {
        log.info("GET /transfi/config/list-tokens direction={} page={} limit={}", direction, page, limit);
        return transfiClientService.listTokens(direction, limit, page);
    }

    @GetMapping("/config/transaction-limits")
    @Operation(summary = "Limites de transaction par utilisateur/montant",
            description = """
                    orderType: deposit | withdraw
                    paymentType: bank_transfer | card | local_wallet | crypto
                    userId doit commencer par 'UX-'
                    """)
    public ResponseEntity<Object> getTransactionLimits(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) Double fiatAmountInUSD,
            @RequestParam(required = false) String orderType,
            @RequestParam(required = false) String paymentType) {
        log.info("GET /transfi/config/transaction-limits userId={} orderType={} paymentType={}",
                userId, orderType, paymentType);
        return transfiClientService.getTransactionLimits(userId, fiatAmountInUSD, orderType, paymentType);
    }

    @GetMapping("/config/qr-code-details")
    @Operation(summary = "Décoder un QR code Fiat (BRL / VND)",
            description = """
                    Décode une chaîne de QR code et retourne les détails marchand/transaction
                    (merchantName, amount, bankName selon la devise).

                    currency: BRL (Brazilian Pix) | VND (Vietnam VietQR)
                    En sandbox : le qrCode doit être préfixé par 'sandbox_qr_'.
                    """)
    public ResponseEntity<Object> getQrCodeDetails(
            @RequestParam String qrCode,
            @RequestParam String currency) {
        log.info("GET /transfi/config/qr-code-details currency={}", currency);
        return transfiClientService.getQrCodeDetails(qrCode, currency);
    }

    // ══════════════════════════════════════════
    // TAUX DE CHANGE
    // ══════════════════════════════════════════

    @GetMapping("/exchange-rates")
    @Operation(summary = "Taux de change en temps réel + détail des fees",
            description = """
                    Tous les champs marqués REQUIRED sont obligatoires côté TransFi.
                    - amount (REQUIRED) : montant à convertir, en string (ex: "100.50")
                    - sourceCurrency : devise source. Optionnel si `username` fourni : déduite du profil
                      (fiat pays) pour onramp/payin/fiat_prefund/gaming, ou du wallet (crypto) pour
                      offramp/payout/crypto_prefund.
                    - destinationCurrency : devise destination. Idem, déduite du wallet/profil si `username` fourni.
                    - orderType (REQUIRED) : onramp | offramp | payin | payout | fiat_prefund | crypto_prefund | swap | gaming
                    - username : username wallet Akuunda — pré-remplit sourceCurrency / destinationCurrency.
                    - direction : forward (défaut) | reverse
                    - paymentCode / paymentType : pertinent pour payin / onramp / fiat_prefund / swap [fiat]
                    - toPaymentCode / toPaymentType : pertinent pour payout / offramp / swap [fiat]
                    """)
    public ResponseEntity<Object> getExchangeRates(
            @RequestParam String amount,
            @RequestParam(required = false) String sourceCurrency,
            @RequestParam(required = false) String destinationCurrency,
            @RequestParam String orderType,
            @RequestParam(required = false) String username,
            @RequestParam(required = false, defaultValue = "forward") String direction,
            @RequestParam(required = false) String paymentCode,
            @RequestParam(required = false) String paymentType,
            @RequestParam(required = false) String toPaymentCode,
            @RequestParam(required = false) String toPaymentType) {
        TransfiCreateOrderContextService.ExchangeRateCurrencies resolved =
                transfiCreateOrderContextService.resolveExchangeRateCurrencies(
                        username, orderType, sourceCurrency, destinationCurrency);
        String src = resolved.sourceCurrency();
        String dest = resolved.destinationCurrency();
        if (src == null || src.isBlank() || dest == null || dest.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "sourceCurrency et destinationCurrency requis (ou fournissez `username` pour onramp/offramp/payin/payout)",
                    "resolvedSourceCurrency", src == null ? "" : src,
                    "resolvedDestinationCurrency", dest == null ? "" : dest));
        }
        log.info("GET /transfi/exchange-rates {} -> {} amount={} orderType={} username={}",
                src, dest, amount, orderType, username);
        return transfiClientService.getExchangeRates(amount, src, dest,
                orderType, direction, paymentCode, paymentType, toPaymentCode, toPaymentType);
    }

    // ══════════════════════════════════════════
    // CONTEXTE WALLET (username Akuunda — aligné Guardarian / MT Pelerin)
    // ══════════════════════════════════════════

    @GetMapping("/context/transfi-user")
    @Operation(summary = "Mapping local username Akuunda ↔ TransFi userId",
            description = """
                    Consulte la table `transfi_users`. Aliment\u00e9e automatiquement à la cr\u00e9ation
                    (POST /users/individual ou /users/business avec champ `username`).

                    Permet au front de savoir si un utilisateur a d\u00e9j\u00e0 \u00e9t\u00e9 enregistr\u00e9 sur TransFi
                    sans interroger l'API distante.
                    """)
    @ApiResponse(responseCode = "200", description = "Mapping trouv\u00e9 (transfiUserId, userType, kycStatus, kybStatus)")
    @ApiResponse(responseCode = "404", description = "Aucun mapping pour ce username")
    public ResponseEntity<Object> getTransfiUserMapping(@RequestParam String username) {
        log.info("GET /transfi/context/transfi-user username={}", username);
        return transfiPersistenceService.findByUsername(username)
                .<ResponseEntity<Object>>map(mapping -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("username", mapping.getUsername());
                    body.put("transfiUserId", mapping.getTransfiUserId());
                    body.put("userType", mapping.getUserType());
                    if (mapping.getKycStatus() != null) body.put("kycStatus", mapping.getKycStatus());
                    if (mapping.getKybStatus() != null) body.put("kybStatus", mapping.getKybStatus());
                    body.put("createdAt", mapping.getCreatedAt());
                    body.put("updatedAt", mapping.getUpdatedAt());
                    return ResponseEntity.ok(body);
                })
                .orElseGet(() -> ResponseEntity.status(404).body(Map.of(
                        "error", "Aucun mapping TransFi pour ce username",
                        "username", username.trim())));
    }

    @GetMapping("/context/wallet")
    @Operation(summary = "Résoudre wallet + devises à partir du username Akuunda",
            description = """
                    Retourne l'adresse on-chain, la devise crypto du wallet (`symbol` en base, défaut USDC si absent)
                    et la devise fiat du pays du profil (`countryCurrency`) — pour pré-remplir le front avant de
                    poster un ordre TransFi ou afficher des taux.

                    Même clé métier que les autres intégrations : le **username** (téléphone wallet).

                    Lien avec /orders :
                    - **onramp/payin** : source.currency = fiatCurrency, destination = walletAddress + cryptoCurrency
                    - **offramp/payout** : source = walletAddress + cryptoCurrency, destination.currency = fiatCurrency

                    POST /transfi/orders accepte aussi un champ optionnel `username` dans le JSON : enrichissement
                    automatique des champs manquants (les valeurs explicites du client ne sont jamais écrasées),
                    et `customerMetaData` est complété avec les infos profil (akuundaUsername, akuundaEmail, ...).
                    """)
    @ApiResponse(responseCode = "200", description = "username, walletAddress, cryptoCurrency, fiatCurrency (si connu)")
    @ApiResponse(responseCode = "404", description = "Utilisateur ou wallet introuvable")
    public ResponseEntity<Object> getTransfiWalletContext(@RequestParam String username) {
        log.info("GET /transfi/context/wallet username={}", username);
        Optional<TransfiCreateOrderContextService.TransfiWalletContext> ctx =
                transfiCreateOrderContextService.resolveWalletContext(username);
        if (ctx.isEmpty()) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "Utilisateur ou wallet introuvable",
                    "username", username.trim()));
        }
        TransfiCreateOrderContextService.TransfiWalletContext c = ctx.get();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("username", c.username());
        body.put("walletAddress", c.walletAddress());
        body.put("cryptoCurrency", c.cryptoCurrency());
        if (c.fiatCurrency() != null) {
            body.put("fiatCurrency", c.fiatCurrency());
        }
        return ResponseEntity.ok(body);
    }

    // ══════════════════════════════════════════
    // UTILISATEURS
    // ══════════════════════════════════════════

    @GetMapping("/users/individual")
    @Operation(summary = "Lister les utilisateurs individuels",
            description = "Filtres optionnels : email, phone. Pagination : page (défaut 1), limit (défaut 5, max 100).")
    public ResponseEntity<Object> listIndividualUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone) {
        log.info("GET /transfi/users/individual page={} limit={} email={}", page, limit, email);
        return transfiClientService.listIndividualUsers(page, limit, email, phone);
    }

    @PostMapping("/users/individual")
    @Operation(summary = "Créer un utilisateur individuel",
            description = """
                    date = DOB au format DD-MM-YYYY. gender: male | female (optionnel).

                    **username** (optionnel) : si fourni, le mapping username Akuunda ↔ TransFi userId
                    est persisté dans `transfi_users` après création réussie. Les autres endpoints
                    (GET /orders, GET /kyc/status, ...) accepteront alors `username` à la place de `userId`.
                    Jamais transmis à TransFi.
                    """)
    @ApiResponse(responseCode = "200", description = "Utilisateur créé")
    public ResponseEntity<Object> createIndividualUser(
            @Valid @RequestBody TransfiCreateIndividualUserRequest request) {
        log.info("POST /transfi/users/individual email={} username={}",
                request.getEmail(), request.getUsername());
        return transfiClientService.createIndividualUser(request);
    }

    @GetMapping("/users/business")
    @Operation(summary = "Lister les utilisateurs professionnels",
            description = "Filtres optionnels : email, phone. Pagination : page (défaut 1), limit (défaut 5, max 100).")
    public ResponseEntity<Object> listBusinessUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String phone) {
        log.info("GET /transfi/users/business page={} limit={} email={}", page, limit, email);
        return transfiClientService.listBusinessUsers(page, limit, email, phone);
    }

    @PostMapping("/users/business")
    @Operation(summary = "Créer un utilisateur professionnel",
            description = """
                    date = date d'incorporation au format DD-MM-YYYY (optionnel).

                    **username** (optionnel) : si fourni, le mapping username Akuunda ↔ TransFi userId
                    est persisté dans `transfi_users` (userType=business). Jamais transmis à TransFi.
                    """)
    @ApiResponse(responseCode = "200", description = "Utilisateur créé")
    public ResponseEntity<Object> createBusinessUser(
            @Valid @RequestBody TransfiCreateBusinessUserRequest request) {
        log.info("POST /transfi/users/business email={} username={}",
                request.getEmail(), request.getUsername());
        return transfiClientService.createBusinessUser(request);
    }

    // ══════════════════════════════════════════
    // RECIPIENTS (bénéficiaires de payout)
    // ══════════════════════════════════════════

    @PostMapping("/recipients/mandatory-fields")
    @Operation(summary = "Champs obligatoires pour créer un recipient",
            description = """
                    Retourne la liste des champs requis pour créer un recipient
                    selon le pays (geo), le type de devise (fiat | crypto) et
                    le type d'entité (individual | organization).
                    """)
    @ApiResponse(responseCode = "200", description = "Spécification des champs obligatoires")
    public ResponseEntity<Object> getRecipientMandatoryFields(
            @Valid @RequestBody TransfiRecipientMandatoryFieldsRequest request) {
        log.info("POST /transfi/recipients/mandatory-fields geo={} currencyType={} type={}",
                request.getGeo(), request.getCurrencyType(), request.getType());
        return transfiClientService.getRecipientMandatoryFields(request);
    }

    @PostMapping("/recipients/individual")
    @Operation(summary = "Créer un recipient individuel",
            description = """
                    Crée un bénéficiaire individuel pour les payouts TransFi.
                    Champs requis côté API : country, currencyType.
                    Les autres champs (firstName, lastName, accountIdentifier...) dépendent
                    de la réponse de /recipients/mandatory-fields pour ce pays.
                    accountIdentifier.type : bank_account | iban | e_wallet | mobile_wallet
                    """)
    @ApiResponse(responseCode = "200", description = "Recipient créé (ou déjà existant : code=USER_ALREADY_EXISTS)")
    public ResponseEntity<Object> createIndividualRecipient(
            @Valid @RequestBody TransfiCreateIndividualRecipientRequest request) {
        log.info("POST /transfi/recipients/individual country={} currencyType={}",
                request.getCountry(), request.getCurrencyType());
        return transfiClientService.createIndividualRecipient(request);
    }

    @PostMapping("/recipients/business")
    @Operation(summary = "Créer un recipient business",
            description = """
                    Crée un bénéficiaire entreprise pour les payouts TransFi.
                    Champs requis côté API : countryOfIncorporation, country, currencyType, businessName.
                    accountIdentifier.type : bank_account | iban | e_wallet | mobile_wallet
                    """)
    @ApiResponse(responseCode = "200", description = "Recipient créé (ou déjà existant : code=USER_ALREADY_EXISTS)")
    public ResponseEntity<Object> createBusinessRecipient(
            @Valid @RequestBody TransfiCreateBusinessRecipientRequest request) {
        log.info("POST /transfi/recipients/business country={} businessName={}",
                request.getCountry(), request.getBusinessName());
        return transfiClientService.createBusinessRecipient(request);
    }

    // ══════════════════════════════════════════
    // KYC
    // ══════════════════════════════════════════

    @PostMapping("/kyc/standard")
    @Operation(summary = "Initier un KYC standard", description = "Retourne une URL de vérification pour l'utilisateur")
    public ResponseEntity<TransfiKycResponse> initiateStandardKyc(@Valid @RequestBody TransfiKycRequest request) {
        resolveUserIdFromUsername(request);
        log.info("POST /transfi/kyc/standard userId={}", request.getUserId());
        return transfiClientService.initiateStandardKyc(request);
    }

    @PostMapping("/kyc/advanced")
    @Operation(summary = "Initier un KYC avancé")
    public ResponseEntity<TransfiKycResponse> initiateAdvancedKyc(@Valid @RequestBody TransfiKycRequest request) {
        resolveUserIdFromUsername(request);
        log.info("POST /transfi/kyc/advanced userId={}", request.getUserId());
        return transfiClientService.initiateAdvancedKyc(request);
    }

    @PostMapping("/kyc/share/same-vendor")
    @Operation(summary = "Partager un KYC (même vendor)")
    public ResponseEntity<Object> shareKycSameVendor(@Valid @RequestBody TransfiKycShareRequest request) {
        log.info("POST /transfi/kyc/share/same-vendor userId={}", request.getUserId());
        return transfiClientService.shareKycSameVendor(request);
    }

    @PostMapping(path = "/kyc/share/third-vendor", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Soumettre les documents KYC (third-vendor, multipart)",
            description = """
                    Soumission directe des documents KYC quand Sumsub n'est pas le KYC provider.
                    Champs requis : userId, country, nationality, idDocIssuerCountry, idDocType,
                    dob, gender, idDocUserName, idDocExpiryDate, street, city, postalCode,
                    idDocFrontSide, idDocBackSide, selfie.
                    Champs optionnels : phoneNo, state.

                    - idDocType : PASSPORT | ID_CARD | DRIVERS
                    - gender    : male | female
                    - Dates au format YYYY-MM-DD
                    """)
    @ApiResponse(responseCode = "200", description = "Documents KYC soumis (status pending)")
    public ResponseEntity<Object> shareKycThirdVendor(@ModelAttribute TransfiKycShareThirdVendorRequest request) {
        log.info("POST /transfi/kyc/share/third-vendor userId={} idDocType={}",
                request.getUserId(), request.getIdDocType());
        return transfiClientService.shareKycThirdVendor(request);
    }

    @PostMapping(path = "/kyc/share/add-docs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Partager des documents KYC additionnels (multipart)",
            description = """
                    Soumet des documents supplémentaires pour augmenter les limites de transaction
                    ou couvrir des exigences pays-spécifiques.
                    Tous les champs sont requis : userId, additionalDoc (fichier), additionalDocType.
                    """)
    @ApiResponse(responseCode = "200", description = "Documents additionnels traités")
    public ResponseEntity<Object> shareKycAdditionalDocs(@ModelAttribute TransfiKycShareAddDocsRequest request) {
        log.info("POST /transfi/kyc/share/add-docs userId={} additionalDocType={}",
                request.getUserId(), request.getAdditionalDocType());
        return transfiClientService.shareKycAdditionalDocs(request);
    }

    @GetMapping("/kyc/status/{userIdOrUsername}")
    @Operation(summary = "Statut KYC d'un utilisateur individuel",
            description = """
                    Accepte au choix le **TransFi userId** (préfixe `UX-`) ou le **username Akuunda**
                    (téléphone). Le username est résolu depuis la table `transfi_users` peuplée à la
                    création de l'utilisateur. Retourne 404 si le username n'est pas mappé.
                    """)
    public ResponseEntity<Object> getKycStatus(@PathVariable String userIdOrUsername) {
        log.info("GET /transfi/kyc/status/{}", userIdOrUsername);
        String resolved = resolveTransfiUserIdOrSelf(userIdOrUsername);
        if (resolved == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "username non mappé à un TransFi userId",
                    "username", userIdOrUsername));
        }
        return transfiClientService.getKycStatus(resolved);
    }

    // ══════════════════════════════════════════
    // KYB
    // ══════════════════════════════════════════

    @PostMapping("/kyb/standard")
    @Operation(summary = "Initier un KYB standard")
    public ResponseEntity<Object> initiateStandardKyb(@Valid @RequestBody TransfiKybRequest request) {
        resolveUserIdFromUsername(request);
        log.info("POST /transfi/kyb/standard userId={}", request.getUserId());
        return transfiClientService.initiateStandardKyb(request);
    }

    @PostMapping("/kyb/advanced")
    @Operation(summary = "Initier un KYB avancé")
    public ResponseEntity<Object> initiateAdvancedKyb(@Valid @RequestBody TransfiKybRequest request) {
        resolveUserIdFromUsername(request);
        log.info("POST /transfi/kyb/advanced userId={}", request.getUserId());
        return transfiClientService.initiateAdvancedKyb(request);
    }

    @GetMapping("/kyb/status/{userIdOrUsername}")
    @Operation(summary = "Statut KYB d'un utilisateur business",
            description = """
                    Accepte au choix le **TransFi userId** (préfixe `UX-`) ou le **username Akuunda**
                    (résolu via `transfi_users`). Retourne le status courant (kyb_pending, kyb_success,
                    user_approved, user_rejected, ...) et les rejectLabels éventuels.
                    """)
    public ResponseEntity<Object> getKybStatus(@PathVariable String userIdOrUsername) {
        log.info("GET /transfi/kyb/status/{}", userIdOrUsername);
        String resolved = resolveTransfiUserIdOrSelf(userIdOrUsername);
        if (resolved == null) {
            return ResponseEntity.status(404).body(Map.of(
                    "error", "username non mappé à un TransFi userId",
                    "username", userIdOrUsername));
        }
        return transfiClientService.getKybStatus(resolved);
    }

    // ══════════════════════════════════════════
    // ORDRES
    // ══════════════════════════════════════════

    @GetMapping("/orders")
    @Operation(summary = "Lister les ordres",
            description = """
                    orderType: payin | payout | onramp | offramp | swap.

                    Filtres optionnels :
                    - `userId` (TransFi UX-...) ou `username` (Akuunda) : si `username` fourni, il est résolu
                      en TransFi userId via `transfi_users`. 404 si non mappé.
                    """)
    public ResponseEntity<Object> listOrders(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int limit,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderType,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String currency) {
        String resolvedUserId = userId;
        if ((resolvedUserId == null || resolvedUserId.isBlank()) && username != null && !username.isBlank()) {
            resolvedUserId = transfiPersistenceService.resolveTransfiUserId(username).orElse(null);
            if (resolvedUserId == null) {
                return ResponseEntity.status(404).body(Map.of(
                        "error", "username non mappé à un TransFi userId",
                        "username", username));
            }
        }
        log.info("GET /transfi/orders orderType={} userId={} username={}", orderType, resolvedUserId, username);
        return transfiClientService.listOrders(page, limit, status, orderType, resolvedUserId, currency);
    }

    @PostMapping("/orders")
    @Operation(summary = "Créer un ordre TransFi",
            description = """
                    Champs requis : purposeCode, orderType, source.currency, destination.currency.
                    userId est requis pour onramp, offramp, payin, payout, swap, gaming.

                    orderType :
                    - **payin**           : fiat → fiat sur compte marchand (paiement entrant)
                    - **payout**          : fiat sortant vers IBAN / wallet bancaire
                    - **onramp**          : fiat → crypto (wallet destinataire)
                    - **offramp**         : crypto → fiat
                    - **swap**            : crypto ↔ crypto (ou fiat ↔ fiat)
                    - **fiat_prefund**    : recharge IBAN virtuel
                    - **crypto_prefund**  : recharge solde crypto
                    - **gaming**          : flux gaming

                    invoiceId optionnel : obtenu via POST /invoices/create (relevant pour payin/payout).
                    URLs de redirection (successRedirectUrl, failureRedirectUrl, sourceUrl) doivent
                    inclure le protocole https://. Requises pour gaming + non-headless onramp/offramp/payin/swap.

                    **username** (optionnel) : username wallet Akuunda — complète automatiquement l'adresse et la
                    devise crypto côté source ou destination selon `orderType` (voir GET /context/wallet). Jamais
                    transmis à TransFi. 404 si utilisateur ou wallet introuvable.
                    """)
    @ApiResponse(responseCode = "200", description = "Ordre créé (data.orderId + data.payUrl)")
    @ApiResponse(responseCode = "404", description = "username fourni mais utilisateur ou wallet introuvable")
    public ResponseEntity<Object> createOrder(
            @Valid @RequestBody TransfiCreateOrderRequest request) {
        Optional<String> enrichError = transfiCreateOrderContextService.enrichFromUsername(request);
        if (enrichError.isPresent()) {
            return ResponseEntity.status(404).body(Map.of("error", enrichError.get()));
        }
        log.info("POST /transfi/orders orderType={} userId={} partnerId={}",
                request.getOrderType(), request.getUserId(), request.getPartnerId());
        return transfiClientService.createOrder(request);
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "Récupérer un ordre par ID",
            description = """
                    Retourne le détail complet d'un ordre :
                    id, status (initiated, fund_settled, fund_failed, ...), type, source, destination,
                    userId, senderName, fees (processingFee, networkFee, fixedFee, rrFee, feeMode),
                    failureCode / failureMessage si applicable, partnerContext.
                    """)
    public ResponseEntity<Object> getOrderById(@PathVariable String orderId) {
        log.info("GET /transfi/orders/{}", orderId);
        return transfiClientService.getOrderById(orderId);
    }

    // ══════════════════════════════════════════
    // INVOICES
    // ══════════════════════════════════════════

    @PostMapping(path = "/invoices/create", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Uploader une facture PDF (multipart)",
            description = """
                    Upload d'un document PDF (facture / reçu / contrat) lié à une transaction.
                    La réponse contient un invoiceId à réutiliser tel quel au root du payload
                    POST /orders (champ invoiceId) pour les ordres Payin / Payout.

                    Champs requis : invoice (PDF, max 4MB), direction (deposit | withdraw),
                    userId (doit commencer par UX-).
                    Champ optionnel : invoiceType (invoice | receipt | contract | commercial_document).
                    """)
    @ApiResponse(responseCode = "200", description = "Invoice uploadée — renvoie invoiceId + url S3")
    public ResponseEntity<Object> uploadInvoice(@ModelAttribute TransfiUploadInvoiceRequest request) {
        log.info("POST /transfi/invoices/create userId={} direction={} invoiceType={}",
                request.getUserId(), request.getDirection(), request.getInvoiceType());
        return transfiClientService.uploadInvoice(request);
    }

    // ══════════════════════════════════════════
    // SOLDE
    // ══════════════════════════════════════════

    @GetMapping("/balance")
    @Operation(summary = "Solde du compte TransFi")
    public ResponseEntity<Object> getBalance(
            @RequestParam(required = false) String currency,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int limit) {
        log.info("GET /transfi/balance currency={}", currency);
        return transfiClientService.getBalance(currency, page, limit);
    }

    @GetMapping("/balance/iban")
    @Operation(summary = "Soldes des comptes IBAN virtuels",
            description = """
                    Vue d'ensemble des soldes IBAN avec ventilation par état :
                    available_balance, locked_balance, frozen_balance, in_transit_balance, total_balance.
                    Filtres optionnels : accountId, currency.
                    """)
    public ResponseEntity<Object> getIbanBalance(
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String currency) {
        log.info("GET /transfi/balance/iban accountId={} currency={}", accountId, currency);
        return transfiClientService.getIbanBalance(accountId, currency);
    }

    // ══════════════════════════════════════════
    // IBAN VIRTUELS
    // ══════════════════════════════════════════

    @PostMapping("/iban")
    @Operation(summary = "Créer un IBAN virtuel",
            description = """
                    Champs requis : currency, paymentCode (obtenu via /config/payment-methods/iban).
                    - Si customer.userId est fourni → IBAN créé pour cet utilisateur
                    - Si customer omis ou userId absent → IBAN créé pour l'organisation
                    - Les autres champs customer (email, street, city...) dépendent des
                      requiredFields retournés par /config/payment-methods/iban
                    - uiEnabled=true → la réponse retourne un redirectUrl pour finaliser via UI
                    """)
    @ApiResponse(responseCode = "200", description = "IBAN créé (data.iban + data.bic) ou redirectUrl si uiEnabled=true")
    public ResponseEntity<Object> createVirtualIban(
            @Valid @RequestBody TransfiCreateIbanRequest request) {
        log.info("POST /transfi/iban currency={} paymentCode={} uiEnabled={}",
                request.getCurrency(), request.getPaymentCode(), request.getUiEnabled());
        return transfiClientService.createVirtualIban(request);
    }

    @GetMapping("/iban")
    @Operation(summary = "Lister les IBANs virtuels",
            description = """
                    Filtres optionnels :
                    - `userId` (TransFi) ou `username` (Akuunda, résolu via `transfi_users`)
                    - `currency`
                    Si ni userId ni username : tous les IBANs de l'organisation.
                    """)
    public ResponseEntity<Object> listVirtualIbans(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String username,
            @RequestParam(required = false) String currency,
            @RequestParam(defaultValue = "5") int limit,
            @RequestParam(defaultValue = "1") int page) {
        String resolvedUserId = userId;
        if ((resolvedUserId == null || resolvedUserId.isBlank()) && username != null && !username.isBlank()) {
            resolvedUserId = transfiPersistenceService.resolveTransfiUserId(username).orElse(null);
            if (resolvedUserId == null) {
                return ResponseEntity.status(404).body(Map.of(
                        "error", "username non mappé à un TransFi userId",
                        "username", username));
            }
        }
        log.info("GET /transfi/iban userId={} username={} currency={} page={} limit={}",
                resolvedUserId, username, currency, page, limit);
        return transfiClientService.listVirtualIbans(resolvedUserId, currency, limit, page);
    }

    @PostMapping("/orders/link")
    @Operation(summary = "Lier un dépôt IBAN unmapped à un utilisateur",
            description = """
                    Assigne un dépôt IBAN orphelin (unmapped) à un userId existant.
                    Après les contrôles de compliance :
                     - succès → ordre marqué fund_settled
                     - échec  → ordre reste en initiated

                    **username** (optionnel) : résout userId via `transfi_users` si userId vide.
                    """)
    @ApiResponse(responseCode = "200", description = "Ordre lié avec succès")
    public ResponseEntity<Object> linkIbanOrder(@Valid @RequestBody TransfiLinkOrderRequest request) {
        if ((request.getUserId() == null || request.getUserId().isBlank())
                && request.getUsername() != null && !request.getUsername().isBlank()) {
            transfiPersistenceService.resolveTransfiUserId(request.getUsername())
                    .ifPresent(request::setUserId);
        }
        log.info("POST /transfi/orders/link orderId={} userId={} username={}",
                request.getOrderId(), request.getUserId(), request.getUsername());
        return transfiClientService.linkIbanOrder(request);
    }

    // ══════════════════════════════════════════
    // SIMULATION (sandbox uniquement)
    // ══════════════════════════════════════════

    @PostMapping("/simulation/iban-deposit")
    @Operation(summary = "[SANDBOX] Simuler un dépôt IBAN entrant")
    public ResponseEntity<Object> simulateIbanDeposit(
            @Valid @RequestBody TransfiSimulateIbanDepositRequest request) {
        log.info("POST /transfi/simulation/iban-deposit ibanId={}", request.getIbanId());
        return transfiClientService.simulateIncomingIbanDeposit(request);
    }

    @GetMapping("/simulation/reconcile-iban")
    @Operation(summary = "[SANDBOX] Réconcilier un dépôt IBAN")
    public ResponseEntity<Object> reconcileIbanDeposit(
            @RequestParam String userId,
            @RequestParam String orderId) {
        log.info("GET /transfi/simulation/reconcile-iban userId={} orderId={}", userId, orderId);
        return transfiClientService.reconcileIbanDeposit(userId, orderId);
    }
}
