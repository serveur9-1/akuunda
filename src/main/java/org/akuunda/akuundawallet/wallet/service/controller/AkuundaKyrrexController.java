package org.akuunda.akuundawallet.wallet.service.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.constants.ApiConstants;
import org.akuunda.akuundawallet.wallet.api.dto.external.*;
import org.akuunda.akuundawallet.wallet.service.KyrrexSepaManualOrchestrationService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaKyrrexClientService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller d'intégration Kyrrex Malta Business.
 *
 * <h3>Convention architecturale :</h3>
 * <p>Le paramètre {@code username} est transmis <b>explicitement</b> sur chaque endpoint.
 * Il sert de clé de lookup pour récupérer les credentials Kyrrex (api_key, api_secret)
 * stockés en base et chiffrés en AES-256-GCM. Sans ce username, aucune requête HMAC
 * ne peut être signée vers l'API Kyrrex.</p>
 *
 * <pre>
 * Frontend → [JWT + username] → Controller → Service.lookup(username)
 *   → kyrrex_user_credentials → decrypt(api_key, api_secret)
 *   → KyrrexApiClient.memberHmac(api_key, api_secret) → Kyrrex Malta API
 * </pre>
 *
 * <p>69 endpoints — couverture complète Kyrrex Malta Business API — 2026-04-18.</p>
 */
@Slf4j
@RestController
@RequestMapping(path = AkuundaKyrrexController.API_ROOT, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Akuunda Services - Kyrrex Malta Business", description = """
        API d'intégration avec le provider Kyrrex Malta Business.
        
        > ⚠️ Le paramètre `username` est requis sur chaque endpoint :
        > il permet de récupérer les credentials Kyrrex (api_key/api_secret)
        > stockés et chiffrés en base pour signer les requêtes HMAC.
        """)
@SecurityRequirement(name = ApiConstants.SWAGGER_BASIC_SECURITY_SCHEME)
@RequiredArgsConstructor
public class AkuundaKyrrexController {

    public static final String API_ROOT = "/api/internal/v1/kyrrex";

    private final AkuundaKyrrexClientService kyrrexClientService;
    private final KyrrexSepaManualOrchestrationService sepaManualOrchestrationService;
    private final ObjectMapper objectMapper;

    @Value("${akuunda.kyrrex.fiat.advanced-exchange-enabled:false}")
    private boolean advancedExchangeEnabled;

    @Value("${akuunda.kyrrex.fiat.volt-enabled:false}")
    private boolean voltEnabled;

    @Value("${akuunda.kyrrex.fiat.card-provider-enabled:false}")
    private boolean cardProviderEnabled;

    // ══════════════════════════════════════════════════════════
    //  SECTION 1 : MEMBER REGISTRATION (1 endpoint existant)
    // ══════════════════════════════════════════════════════════

    @PostMapping("/register/{username}")
    @Operation(summary = "Inscrire un utilisateur chez Kyrrex",
            description = """
                    Enregistre un utilisateur Akuunda comme membre chez Kyrrex Malta Business.
                    
                    **Flux :**
                    1. Appel Owner API → POST /business/sign-up (crée le membre)
                    2. Kyrrex retourne `api_key` et `api_secret`
                    3. Ces credentials sont chiffrés (AES-256-GCM) et stockés dans `kyrrex_user_credentials`
                    4. Tous les appels suivants pour ce `username` utiliseront ces credentials
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Membre inscrit, credentials stockés"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "409", description = "Utilisateur déjà inscrit"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<?> registerMember(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true, example = "0033612108828")
            @PathVariable String username,
            @Valid @RequestBody KyrrexMemberSignUpRequest request) {
        log.info("📝 POST /kyrrex/register/{}", username);
        return kyrrexClientService.registerMember(username, request);
    }

    @PostMapping("/credentials/{username}/import")
    @Operation(summary = "Importer des credentials Kyrrex existants",
            description = """
                    À utiliser quand le membre existe déjà chez Kyrrex (uid + access_key + secret_key)
                    mais que la ligne locale a été perdue (ex. migration V2062).
                    Ne rappelle pas POST /register — Kyrrex renverrait 409 sans nouvelles clés.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Credentials importés en base"),
            @ApiResponse(responseCode = "400", description = "Corps invalide"),
            @ApiResponse(responseCode = "401", description = "Non authentifié")
    })
    public ResponseEntity<Map<String, String>> importMemberCredentials(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true, example = "0033612108828")
            @PathVariable String username,
            @Valid @RequestBody KyrrexCredentialImportRequest request) {
        log.info("🔑 POST /kyrrex/credentials/{}/import", username);
        return kyrrexClientService.importMemberCredentials(
                username, request.getUid(), request.getAccessKey(), request.getSecretKey());
    }

    // ══════════════════════════════════════════════════════════
    //  SECTION 2 : MEMBERS (3 endpoints — NOUVEAUX)
    // ══════════════════════════════════════════════════════════

    @GetMapping("/members/me")
    @Operation(summary = "Informations du membre authentifié",
            description = "Retourne les informations du profil Kyrrex du membre (uid, email, statut, etc.).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Informations du membre"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Credentials non trouvés"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<KyrrexMemberInfoResponse> getMemberInfo(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true)
            @RequestParam String username) {
        log.info("👤 GET /members/me [user={}]", username);
        return kyrrexClientService.getMemberInfo(username);
    }

    @GetMapping("/members/total-balance")
    @Operation(summary = "Solde total agrégé du membre",
            description = "Retourne le solde total converti dans la devise de référence du membre.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Solde total agrégé"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Credentials non trouvés"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<KyrrexMemberTotalBalanceResponse> getMemberTotalBalance(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true)
            @RequestParam String username) {
        log.info("💰 GET /members/total-balance [user={}]", username);
        return kyrrexClientService.getMemberTotalBalance(username, null);
    }

    @GetMapping("/members/accounts")
    @Operation(summary = "Liste des comptes du membre",
            description = "Retourne tous les comptes (fiat + crypto) rattachés au membre Kyrrex.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste des comptes"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Credentials non trouvés"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<List<KyrrexMemberAccountResponse>> getMemberAccounts(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true)
            @RequestParam String username) {
        log.info("📋 GET /members/accounts [user={}]", username);
        return kyrrexClientService.getMemberAccounts(username);
    }

    // ══════════════════════════════════════════════════════════
    //  SECTION 3 : FIAT PROVIDERS (Données de référence)
    // ══════════════════════════════════════════════════════════

    @GetMapping("/providers")
    @Operation(summary = "Lister les providers fiat Kyrrex")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste des providers"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Credentials non trouvés pour ce username"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<List<KyrrexProviderResponse>> getFiatProviders(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true)
            @RequestParam String username) {
        log.info("📋 GET /providers [user={}]", username);
        return kyrrexClientService.getFiatProviders(username);
    }

    @GetMapping("/fiat/providers/deposits")
    @Operation(summary = "Providers fiat pour les dépôts",
            description = "Clear Junction (cjeur/sepa), Volt (veur/sepa_iframe)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste des providers de dépôt"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Credentials non trouvés"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<List<KyrrexProviderResponse>> getFiatDepositProviders(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true)
            @RequestParam String username) {
        log.info("📋 GET /fiat/providers/deposits [user={}]", username);
        return kyrrexClientService.getFiatDepositProviders(username);
    }

    @GetMapping("/fiat/providers/withdrawals")
    @Operation(summary = "Providers fiat pour les retraits")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste des providers de retrait"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Credentials non trouvés"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<List<KyrrexProviderResponse>> getFiatWithdrawalProviders(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true)
            @RequestParam String username) {
        log.info("📋 GET /fiat/providers/withdrawals [user={}]", username);
        return kyrrexClientService.getFiatWithdrawalProviders(username);
    }

    // ══════════════════════════════════════════════════════════
    //  SECTION 4 : CUSTOMERS (Enregistrement chez providers)
    // ══════════════════════════════════════════════════════════

    @PostMapping("/providers/{providerId}/customers")
    @Operation(summary = "Enregistrer le client chez un provider fiat")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Client enregistré"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Credentials non trouvés"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<KyrrexCustomerResponse> registerCustomerAtProvider(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true)
            @RequestParam String username,
            @Parameter(description = "Identifiant du provider", required = true)
            @PathVariable String providerId) {
        log.info("👤 POST /providers/{}/customers [user={}]", providerId, username);
        return kyrrexClientService.registerCustomerAtProvider(username, providerId);
    }

    @GetMapping("/providers/{providerId}/customers/status")
    @Operation(summary = "Statut du client chez un provider")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statut du client"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Client ou credentials non trouvés"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<KyrrexCustomerResponse> getCustomerStatus(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true)
            @RequestParam String username,
            @Parameter(description = "Identifiant du provider", required = true)
            @PathVariable String providerId) {
        log.info("🔍 GET /providers/{}/customers/status [user={}]", providerId, username);
        return kyrrexClientService.getCustomerStatus(username, providerId);
    }

    @GetMapping("/customer/{username}")
    @Operation(summary = "Détails du client Kyrrex (incluant statut KYC)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Détails récupérés"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Utilisateur non trouvé"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<KyrrexCustomerResponse> getCustomerDetails(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true)
            @PathVariable String username) {
        log.info("📋 GET /customer/{}", username);
        return kyrrexClientService.getCustomerDetails(username);
    }

    // ══════════════════════════════════════════════════════════
    //  SECTION 5 : INSTRUMENTS (Cartes & Virements bancaires)
    // ══════════════════════════════════════════════════════════

    @PostMapping("/providers/{providerId}/instruments/cards")
    @Operation(summary = "Créer un instrument carte")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Instrument créé"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Credentials non trouvés"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<KyrrexCardInstrumentResponse> createCardInstrument(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true)
            @RequestParam String username,
            @PathVariable String providerId,
            @Valid @RequestBody KyrrexCardInstrumentRequest request) {
        log.info("💳 POST /providers/{}/instruments/cards [user={}]", providerId, username);
        return kyrrexClientService.createCardInstrument(username, providerId, request);
    }

    @GetMapping("/providers/{providerId}/instruments/cards")
    @Operation(summary = "Lister les instruments carte d'un provider")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste des instruments carte"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Credentials non trouvés"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<List<KyrrexCardInstrumentResponse>> getCardInstruments(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true)
            @RequestParam String username,
            @PathVariable String providerId,
            @RequestParam String instrument) {
        log.info("💳 GET /providers/{}/instruments/cards [user={}]", providerId, username);
        return kyrrexClientService.getCardInstruments(username, providerId, instrument);
    }

    @GetMapping("/fiat/providers/{providerId}/instruments/bank-transfers")
    @Operation(summary = "Détails instrument virement SEPA")
    public ResponseEntity<String> getBankTransferDetails(
            @RequestParam String username,
            @PathVariable String providerId,
            @RequestParam String instrument) {
        log.info("🏦 GET /fiat/providers/{}/instruments/bank-transfers [user={}]", providerId, username);
        return kyrrexClientService.getBankTransferInstrumentDetails(username, providerId, instrument);
    }

    @PostMapping("/fiat/providers/{providerId}/instruments/bank-transfers")
    @Operation(summary = "Créer un instrument virement bancaire (SEPA / SEPA_IFRAME)")
    public ResponseEntity<String> createBankTransferInstrument(
            @RequestParam String username,
            @PathVariable String providerId,
            @RequestBody Object body) {
        log.info("🏦 POST /fiat/providers/{}/instruments/bank-transfers [user={}]", providerId, username);
        return kyrrexClientService.createBankTransferInstrument(username, providerId, body);
    }

    // ══════════════════════════════════════════════════════════
    //  SECTION 6 : MARKETS & SETTINGS (Données de référence)
    // ══════════════════════════════════════════════════════════

    @GetMapping("/markets")
    @Operation(summary = "Lister les marchés fiat Kyrrex")
    public ResponseEntity<List<KyrrexMarketResponse>> getFiatMarkets(
            @RequestParam String username,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int perPage) {
        log.info("📈 GET /markets [user={}]", username);
        return kyrrexClientService.getFiatMarkets(username, page, perPage);
    }

    @GetMapping("/markets/{marketId}")
    @Operation(summary = "Détails d'un marché Kyrrex")
    public ResponseEntity<KyrrexMarketResponse> getMarketInfo(
            @RequestParam String username,
            @PathVariable String marketId) {
        log.info("📈 GET /markets/{} [user={}]", marketId, username);
        return kyrrexClientService.getMarketInfo(username, marketId);
    }

    @GetMapping("/settings/markets")
    @Operation(summary = "Paramètres des marchés")
    public ResponseEntity<String> getMarketSettings(@RequestParam String username) {
        log.info("⚙️ GET /settings/markets [user={}]", username);
        return kyrrexClientService.getMarketSettings(username);
    }

    @GetMapping("/settings/currencies")
    @Operation(summary = "Paramètres des cryptos")
    public ResponseEntity<String> getCurrencySettings(@RequestParam String username) {
        log.info("⚙️ GET /settings/currencies [user={}]", username);
        return kyrrexClientService.getCurrencySettings(username);
    }

    @GetMapping("/assets")
    @Operation(summary = "Lister les assets disponibles sur Kyrrex")
    public ResponseEntity<KyrrexPaginatedAssetsResponse> getAssets(
            @RequestParam String username,
            @RequestParam(name = "active_deposit", required = false) Boolean activeDeposit,
            @RequestParam(name = "active_withdrawal", required = false) Boolean activeWithdrawal,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "per_page", required = false) Integer perPage) {
        log.info("📋 GET /assets [user={}]", username);
        return kyrrexClientService.getAssets(username, activeDeposit, activeWithdrawal, page, perPage);
    }

    // ══════════════════════════════════════════════════════════
    //  SECTION 7 : FIAT DEPOSITS (On-Ramp)
    //  4 existants + 2 NOUVEAUX
    // ══════════════════════════════════════════════════════════

    @PostMapping("/fiat/deposits/{username}")
    @Operation(summary = "Dépôt fiat via carte (On-Ramp)",
            description = "Initie un dépôt fiat. L'utilisateur DOIT être redirigé vers le `frame_url` retourné.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Dépôt initié — rediriger vers frame_url"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Credentials non trouvés pour ce username"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<KyrrexFiatDepositResponse> createFiatDeposit(
            @PathVariable String username,
            @Valid @RequestBody KyrrexFiatDepositRequest request) {
        ensureVoltAllowed(request.getProviderId());
        log.info("💳 POST /fiat/deposits/{}", username);
        return kyrrexClientService.createFiatDeposit(username, request);
    }

    /** NOUVEAU — Générer un lien de dépôt fiat */
    @PostMapping("/fiat/deposits/generate-link")
    @Operation(summary = "Générer un lien de dépôt fiat",
            description = "Génère un lien de paiement (hosted page) pour un dépôt fiat sans iframe.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lien de dépôt généré"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Credentials non trouvés"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<KyrrexFiatDepositLinkResponse> generateFiatDepositLink(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true)
            @RequestParam String username,
            @Valid @RequestBody KyrrexFiatDepositLinkRequest request) {
        ensureVoltAllowed(request.getProviderId());
        log.info("🔗 POST /fiat/deposits/generate-link [user={}]", username);
        return kyrrexClientService.generateFiatDepositLink(username, request);
    }

    @GetMapping("/fiat/deposits/fees")
    @Operation(summary = "Frais de dépôt fiat")
    public ResponseEntity<String> getFiatDepositFees(
            @RequestParam String username,
            @RequestParam String instrument,
            @RequestParam("provider_id") String providerId) {
        log.info("💰 GET /fiat/deposits/fees [user={}]", username);
        return kyrrexClientService.getFiatDepositFees(username, instrument, providerId);
    }

    @GetMapping("/fiat/deposits/fees/estimate")
    @Operation(summary = "Estimation frais dépôt fiat")
    public ResponseEntity<String> getFiatDepositFeeEstimate(
            @RequestParam String username,
            @RequestParam("provider_id") String providerId,
            @RequestParam String instrument,
            @RequestParam String amount) {
        log.info("💰 GET /fiat/deposits/fees/estimate [user={}]", username);
        return kyrrexClientService.getFiatDepositFeeEstimate(username, providerId, instrument, amount);
    }

    @GetMapping("/fiat/deposits/history")
    @Operation(summary = "Historique des dépôts fiat")
    public ResponseEntity<String> getFiatDepositHistory(@RequestParam String username) {
        log.info("📋 GET /fiat/deposits/history [user={}]", username);
        return kyrrexClientService.getFiatDepositHistory(username);
    }

    /** NOUVEAU — Détail d'un dépôt fiat par ID */
    @GetMapping("/fiat/deposits/{depositId}")
    @Operation(summary = "Détail d'un dépôt fiat par ID",
            description = "Retourne les informations complètes d'un dépôt fiat identifié par son ID Kyrrex.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Détail du dépôt fiat"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Dépôt non trouvé"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<KyrrexFiatDepositDetailResponse> getFiatDepositById(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true)
            @RequestParam String username,
            @Parameter(description = "Identifiant du dépôt Kyrrex", required = true)
            @PathVariable String depositId) {
        log.info("🔍 GET /fiat/deposits/{} [user={}]", depositId, username);
        return kyrrexClientService.getFiatDepositById(username, depositId);
    }

    // ══════════════════════════════════════════════════════════
    //  SECTION 8 : ADVANCED EXCHANGE (On-Ramp Fiat → Crypto)
    // ══════════════════════════════════════════════════════════

    @GetMapping("/advanced-exchange/estimate")
    @Operation(summary = "Estimation On-Ramp (Advanced Exchange)")
    public ResponseEntity<KyrrexAdvancedExchangeEstimateResponse> getAdvancedExchangeEstimate(
            @RequestParam String username,
            @RequestParam String fiatCurrency,
            @RequestParam String cryptoAsset,
            @RequestParam BigDecimal fiatAmount,
            @RequestParam String providerId) {
        ensureAdvancedExchangeEnabled();
        log.info("📊 GET /advanced-exchange/estimate [user={}]", username);
        return kyrrexClientService.getAdvancedExchangeEstimate(
                username, fiatCurrency, cryptoAsset, fiatAmount, providerId);
    }

    @PostMapping("/advanced-exchange/{username}")
    @Operation(summary = "Exécuter un On-Ramp (Advanced Exchange)",
            description = "Exécute un échange fiat → crypto. Retourne une URL de redirection pour paiement.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Échange créé — rediriger vers frame_url"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Credentials non trouvés"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<KyrrexAdvancedExchangeResponse> executeAdvancedExchange(
            @PathVariable String username,
            @Valid @RequestBody KyrrexAdvancedExchangeRequest request) {
        ensureAdvancedExchangeEnabled();
        log.info("🚀 POST /advanced-exchange/{}", username);
        return kyrrexClientService.executeAdvancedExchange(username, request);
    }

    @GetMapping("/advanced-exchange/history")
    @Operation(summary = "Historique des Advanced Exchanges")
    public ResponseEntity<String> getAdvancedExchangeHistory(@RequestParam String username) {
        ensureAdvancedExchangeEnabled();
        log.info("📋 GET /advanced-exchange/history [user={}]", username);
        return kyrrexClientService.getAdvancedExchangeHistory(username);
    }

    // ══════════════════════════════════════════════════════════
    //  SECTION 9 : EXCHANGE / SWAP (Crypto ↔ Crypto)
    // ══════════════════════════════════════════════════════════

    @GetMapping("/exchange/estimate")
    @Operation(summary = "Estimation de swap crypto ↔ crypto")
    public ResponseEntity<KyrrexExchangeEstimateResponse> getExchangeEstimate(
            @RequestParam String username,
            @RequestParam String inputAsset,
            @RequestParam String outputAsset,
            @RequestParam BigDecimal amount,
            @RequestParam String providerId) {
        log.info("📊 GET /exchange/estimate [user={}]", username);
        return kyrrexClientService.getExchangeEstimate(username, inputAsset, outputAsset, amount, providerId);
    }

    @PostMapping("/exchange/{username}")
    @Operation(summary = "Exécuter un swap crypto ↔ crypto")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Swap exécuté"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Credentials non trouvés"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<String> executeExchange(
            @PathVariable String username,
            @Valid @RequestBody KyrrexExchangeRequest request) {
        log.info("🔄 POST /exchange/{}", username);
        return kyrrexClientService.executeExchange(username, request);
    }

    // ══════════════════════════════════════════════════════════
    //  SECTION 10 : SWAPS (1 endpoint — NOUVEAU)
    // ══════════════════════════════════════════════════════════

    /** NOUVEAU — Créer un swap */
    @PostMapping("/swaps/{username}")
    @Operation(summary = "Créer un swap (conversion devise)",
            description = """
                    Exécute un swap entre deux devises (fiat↔fiat, fiat↔crypto, crypto↔crypto).
                    Le taux est calculé au moment de l'exécution.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Swap exécuté avec succès"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Credentials non trouvés"),
            @ApiResponse(responseCode = "422", description = "Solde insuffisant ou paire invalide"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<KyrrexSwapResponse> createSwap(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true)
            @PathVariable String username,
            @Valid @RequestBody KyrrexSwapRequest request) {
        log.info("🔄 POST /swaps/{}", username);
        return kyrrexClientService.createSwap(username, request);
    }

    // ══════════════════════════════════════════════════════════
    //  SECTION 11 : FIAT WITHDRAWALS (Off-Ramp)
    //  4 existants + 2 NOUVEAUX
    // ══════════════════════════════════════════════════════════

    @GetMapping("/fiat/withdrawals/fees")
    @Operation(summary = "Frais de retrait fiat")
    public ResponseEntity<String> getFiatWithdrawalFees(
            @RequestParam String username,
            @RequestParam String instrument,
            @RequestParam("provider_id") String providerId) {
        log.info("💰 GET /fiat/withdrawals/fees [user={}]", username);
        return kyrrexClientService.getFiatWithdrawalFees(username, instrument, providerId);
    }

    @GetMapping("/fiat/withdrawals/fees/estimate")
    @Operation(summary = "Estimation des frais de retrait fiat")
    public ResponseEntity<KyrrexWithdrawalFeeEstimateResponse> getWithdrawalFeeEstimate(
            @RequestParam String username,
            @RequestParam String instrument,
            @RequestParam String amount,
            @RequestParam(name = "provider_id") String providerId) {
        log.info("💰 GET /fiat/withdrawals/fees/estimate [user={}]", username);
        return kyrrexClientService.getWithdrawalFeeEstimate(username, instrument, amount, providerId);
    }

    @PostMapping("/fiat/withdrawals/{username}")
    @Operation(summary = "Retrait fiat (Off-Ramp)",
            description = "Exécute un retrait fiat (SEPA) pour l'utilisateur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Retrait créé"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Credentials non trouvés"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<KyrrexFiatWithdrawalResponse> executeFiatWithdrawal(
            @PathVariable String username,
            @Valid @RequestBody KyrrexFiatWithdrawalRequest request) {
        log.info("💸 POST /fiat/withdrawals/{}", username);
        return kyrrexClientService.executeFiatWithdrawal(username, request);
    }

    /** NOUVEAU — Retrait fiat par virement bancaire (bank_details) */
    @PostMapping("/fiat/withdrawals/bank-details/{username}")
    @Operation(summary = "Retrait fiat par virement bancaire",
            description = "Exécute un retrait fiat vers un compte bancaire (IBAN/BIC). Requiert les coordonnées bancaires complètes.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Retrait par virement initié"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Credentials non trouvés"),
            @ApiResponse(responseCode = "422", description = "Solde insuffisant ou données bancaires invalides"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<KyrrexFiatWithdrawalBankDetailsResponse> createFiatWithdrawalBankDetails(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true)
            @PathVariable String username,
            @Valid @RequestBody KyrrexFiatWithdrawalBankDetailsRequest request) {
        log.info("🏦 POST /fiat/withdrawals/bank-details/{}", username);
        return kyrrexClientService.createFiatWithdrawalBankDetails(username, request);
    }

    /** NOUVEAU — Retrait fiat par carte */
    @PostMapping("/fiat/withdrawals/card/{username}")
    @Operation(summary = "Retrait fiat par carte",
            description = "Exécute un retrait fiat vers une carte bancaire enregistrée.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Retrait par carte initié"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Credentials non trouvés"),
            @ApiResponse(responseCode = "422", description = "Solde insuffisant ou carte invalide"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<KyrrexFiatWithdrawalCardResponse> createFiatWithdrawalCard(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true)
            @PathVariable String username,
            @Valid @RequestBody KyrrexFiatWithdrawalCardRequest request) {
        ensureCardProviderEnabled();
        log.info("💳 POST /fiat/withdrawals/card/{}", username);
        return kyrrexClientService.createFiatWithdrawalCard(username, request);
    }

    private void ensureAdvancedExchangeEnabled() {
        if (!advancedExchangeEnabled) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Kyrrex advanced exchange is currently disabled. Use step-by-step flow: Deposit -> Exchange -> Withdrawal.");
        }
    }

    private void ensureVoltAllowed(String providerId) {
        if (!voltEnabled && providerId != null && providerId.toLowerCase().contains("volt")) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Kyrrex Volt provider is disabled. Use SEPA bank transfer flow.");
        }
    }

    private void ensureCardProviderEnabled() {
        if (!cardProviderEnabled) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "No Kyrrex fiat card provider is currently available. Use SEPA flow.");
        }
    }

    @PostMapping("/fiat/shared/onboarding/readiness/{username}")
    @Operation(summary = "Parcours partagé Kyrrex: readiness dépôt (SEPA/CRYPTO)",
            description = "Exécute automatiquement les étapes automatisables (customer/provider/instrument) et retourne les actions utilisateur restantes (KYC, attente validation).")
    public ResponseEntity<Map<String, Object>> sharedOnboardingReadiness(
            @PathVariable String username,
            @RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> out = new HashMap<>();
        try {
            String providerId = body != null && body.get("providerId") != null
                    ? body.get("providerId").toString()
                    : "cjeur";
            String instrument = body != null && body.get("instrument") != null
                    ? body.get("instrument").toString()
                    : "sepa";

            out.put("success", true);
            out.put("username", username);
            out.put("providerId", providerId);
            out.put("instrument", instrument);

            ResponseEntity<Map<String, String>> credentialStatus = kyrrexClientService.getUserCredentialStatus(username);
            boolean hasCredentials = credentialStatus.getBody() != null &&
                    "true".equalsIgnoreCase(credentialStatus.getBody().get("active"));
            out.put("hasMemberCredentials", hasCredentials);
            if (!hasCredentials) {
                out.put("ready", false);
                out.put("nextAction", "REGISTER_MEMBER");
                out.put("message", "Kyrrex member registration is required before provider onboarding.");
                return ResponseEntity.ok(out);
            }

            KyrrexCustomerResponse customerStatus;
            try {
                customerStatus = kyrrexClientService.getCustomerStatus(username, providerId).getBody();
            } catch (Exception e) {
                customerStatus = null;
            }
            if (customerStatus == null) {
                try {
                    customerStatus = kyrrexClientService.registerCustomerAtProvider(username, providerId).getBody();
                } catch (Exception ignored) {
                }
            }
            out.put("providerCustomerStatus", customerStatus != null ? customerStatus.getVerificationStatus() : null);

            String verificationStatus = customerStatus != null && customerStatus.getVerificationStatus() != null
                    ? customerStatus.getVerificationStatus().toLowerCase()
                    : null;
            boolean kycDone = verificationStatus != null &&
                    (verificationStatus.contains("verified") || verificationStatus.contains("approved"));
            out.put("kycVerified", kycDone);

            if (!kycDone) {
                String kycWebUrl = null;
                try {
                    KyrrexKycWebLinkResponse kyc = kyrrexClientService.generateKycWebLink(username).getBody();
                    if (kyc != null) {
                        kycWebUrl = kyc.getLink();
                    }
                } catch (Exception ignored) {
                }
                out.put("ready", false);
                out.put("nextAction", "COMPLETE_KYC");
                out.put("kycWebUrl", kycWebUrl);
                out.put("message", "KYC is not completed yet. User must complete KYC and wait for provider review.");
                return ResponseEntity.ok(out);
            }

            ResponseEntity<String> instrumentDetails = kyrrexClientService.getBankTransferInstrumentDetails(username, providerId, instrument);
            boolean hasInstrument = hasInstrumentItems(instrumentDetails != null ? instrumentDetails.getBody() : null);
            if (!hasInstrument) {
                try {
                    kyrrexClientService.createBankTransferInstrument(username, providerId, Map.of("instrument", instrument));
                    instrumentDetails = kyrrexClientService.getBankTransferInstrumentDetails(username, providerId, instrument);
                    hasInstrument = hasInstrumentItems(instrumentDetails != null ? instrumentDetails.getBody() : null);
                } catch (Exception ignored) {
                }
            }

            out.put("hasInstrument", hasInstrument);
            out.put("bankTransferDetailsRaw", instrumentDetails != null ? instrumentDetails.getBody() : null);
            out.put("ready", hasInstrument);
            out.put("nextAction", hasInstrument ? "DEPOSIT_ALLOWED" : "WAIT_PROVIDER_INSTRUMENT");
            if (!hasInstrument) {
                out.put("message", "Provider instrument not ready yet. Retry later.");
            }
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            log.error("sharedOnboardingReadiness failed [user={}]", username, e);
            out.put("success", false);
            out.put("ready", false);
            out.put("message", e.getMessage() != null ? e.getMessage() : "KYRREX_ONBOARDING_READINESS_FAILED");
            return ResponseEntity.ok(out);
        }
    }

    private boolean hasInstrumentItems(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return false;
        try {
            JsonNode root = objectMapper.readTree(rawJson);
            JsonNode items = root.has("items") ? root.get("items") : root;
            return items != null && items.isArray() && !items.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    @PostMapping("/fiat/sepa/iban/open/{username}")
    @Operation(summary = "Open/refresh SEPA IBAN state",
            description = "Creates/retrieves a SEPA instrument and persists explicit IBAN status (ACTIVE/CLOSED).")
    public ResponseEntity<Map<String, Object>> openOrRefreshSepaIban(
            @PathVariable String username,
            @RequestBody(required = false) KyrrexSepaOrchestrationRequest request) {
        try {
            if (request == null) {
                request = new KyrrexSepaOrchestrationRequest();
            }
            return ResponseEntity.ok(sepaManualOrchestrationService.openOrRefreshIban(username, request));
        } catch (Exception e) {
            log.error("openOrRefreshSepaIban failed [user={}]", username, e);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", e.getMessage() != null ? e.getMessage() : "SEPA_IBAN_OPEN_FAILED");
            return ResponseEntity.ok(err);
        } catch (Throwable t) {
            log.error("openOrRefreshSepaIban fatal [user={}]", username, t);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", t.getMessage() != null ? t.getMessage() : "SEPA_IBAN_OPEN_FAILED");
            return ResponseEntity.ok(err);
        }
    }

    @PostMapping("/fiat/sepa/orchestration/{username}")
    @Operation(summary = "SEPA manual orchestration: Deposit -> Exchange -> Withdrawal",
            description = "Runs step-by-step SEPA flow and blocks if IBAN is CLOSED.")
    public ResponseEntity<Map<String, Object>> orchestrateSepaManualFlow(
            @PathVariable String username,
            @RequestBody(required = false) KyrrexSepaOrchestrationRequest request) {
        try {
            if (request == null) {
                request = new KyrrexSepaOrchestrationRequest();
            }
            return ResponseEntity.ok(sepaManualOrchestrationService.orchestrate(username, request));
        } catch (Exception e) {
            log.error("orchestrateSepaManualFlow failed [user={}]", username, e);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", e.getMessage() != null ? e.getMessage() : "SEPA_ORCHESTRATION_FAILED");
            return ResponseEntity.ok(err);
        } catch (Throwable t) {
            log.error("orchestrateSepaManualFlow fatal [user={}]", username, t);
            Map<String, Object> err = new HashMap<>();
            err.put("success", false);
            err.put("message", t.getMessage() != null ? t.getMessage() : "SEPA_ORCHESTRATION_FAILED");
            return ResponseEntity.ok(err);
        }
    }

    @GetMapping("/fiat/withdrawals/history")
    @Operation(summary = "Historique des retraits fiat")
    public ResponseEntity<String> getFiatWithdrawalHistory(@RequestParam String username) {
        log.info("📋 GET /fiat/withdrawals/history [user={}]", username);
        return kyrrexClientService.getFiatWithdrawalHistory(username);
    }

    // ══════════════════════════════════════════════════════════
    //  SECTION 12 : CRYPTO DEPOSITS & ADDRESSES
    //  3 existants + 2 NOUVEAUX
    // ══════════════════════════════════════════════════════════

    @PostMapping("/deposit-addresses")
    @Operation(summary = "Créer une adresse de dépôt crypto",
            description = "Le `dchain` correspond à l'identifiant chaîne retourné par GET /assets")
    public ResponseEntity<String> createDepositAddress(
            @RequestParam String username,
            @RequestParam String dchain,
            @RequestParam String name) {
        log.info("📬 POST /deposit-addresses: dchain={} [user={}]", dchain, username);
        return kyrrexClientService.createDepositAddress(username, dchain, name);
    }

    /** NOUVEAU — Générer un lien de dépôt crypto */
    @PostMapping("/deposits/crypto/generate-link")
    @Operation(summary = "Générer un lien de dépôt crypto",
            description = "Génère un lien (QR code / deep link) pour recevoir un dépôt crypto sur une adresse spécifique.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lien de dépôt crypto généré"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Credentials non trouvés"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<KyrrexCryptoDepositLinkResponse> generateCryptoDepositLink(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true)
            @RequestParam String username,
            @Valid @RequestBody KyrrexCryptoDepositLinkRequest request) {
        log.info("🔗 POST /deposits/crypto/generate-link [user={}]", username);
        return kyrrexClientService.generateCryptoDepositLink(username, request);
    }

    @GetMapping("/deposit-addresses")
    @Operation(summary = "Lister les adresses de dépôt crypto")
    public ResponseEntity<String> getDepositAddresses(@RequestParam String username) {
        log.info("📬 GET /deposit-addresses [user={}]", username);
        return kyrrexClientService.getDepositAddresses(username);
    }

    @GetMapping("/deposits")
    @Operation(summary = "Historique des dépôts crypto")
    public ResponseEntity<String> getCryptoDeposits(@RequestParam String username) {
        log.info("📋 GET /deposits [user={}]", username);
        return kyrrexClientService.getCryptoDeposits(username);
    }

    /** NOUVEAU — Détail d'un dépôt crypto par ID */
    @GetMapping("/deposits/crypto/{depositId}")
    @Operation(summary = "Détail d'un dépôt crypto par ID",
            description = "Retourne les informations complètes d'un dépôt crypto identifié par son ID Kyrrex.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Détail du dépôt crypto"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Dépôt non trouvé"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<KyrrexCryptoDepositDetailResponse> getCryptoDepositById(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true)
            @RequestParam String username,
            @Parameter(description = "Identifiant du dépôt Kyrrex", required = true)
            @PathVariable String depositId) {
        log.info("🔍 GET /deposits/crypto/{} [user={}]", depositId, username);
        return kyrrexClientService.getCryptoDepositById(username, depositId);
    }

    // ══════════════════════════════════════════════════════════
    //  SECTION 13 : CRYPTO WITHDRAWALS & REQUISITES
    //  4 existants + 4 NOUVEAUX
    // ══════════════════════════════════════════════════════════

    @PostMapping("/requisites")
    @Operation(summary = "Créer un réquisite de retrait crypto")
    public ResponseEntity<String> createRequisite(
            @RequestParam String username,
            @RequestParam String currency,
            @RequestParam String address,
            @RequestParam String network,
            @RequestParam(required = false) String label) {
        log.info("📝 POST /requisites: {} [user={}]", currency, username);
        return kyrrexClientService.createRequisite(username, currency, address, network, label);
    }

    @GetMapping("/requisites")
    @Operation(summary = "Lister les réquisites de retrait crypto")
    public ResponseEntity<String> getRequisites(@RequestParam String username) {
        log.info("📝 GET /requisites [user={}]", username);
        return kyrrexClientService.getRequisites(username);
    }

    /** NOUVEAU — Prérequis de dépôt */
    @GetMapping("/requisites/deposit")
    @Operation(summary = "Prérequis de dépôt (réseaux, limites)",
            description = "Retourne les prérequis nécessaires pour effectuer un dépôt sur une devise/réseau donné.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Prérequis de dépôt"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Credentials non trouvés"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<KyrrexDepositRequisitesResponse> getDepositRequisites(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true)
            @RequestParam String username,
            @Parameter(description = "Code devise (ex: BTC, ETH, USDT)")
            @RequestParam(required = false) String currency,
            @Parameter(description = "Réseau blockchain (ex: ERC20, TRC20)")
            @RequestParam(required = false) String network) {
        log.info("📋 GET /requisites/deposit [user={}, currency={}, network={}]", username, currency, network);
        return kyrrexClientService.getDepositRequisites(username, currency, network);
    }

    /** NOUVEAU — Prérequis de retrait */
    @GetMapping("/requisites/withdrawal")
    @Operation(summary = "Prérequis de retrait (réseaux, limites, frais)",
            description = "Retourne les prérequis nécessaires pour effectuer un retrait sur une devise/réseau donné.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Prérequis de retrait"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Credentials non trouvés"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<KyrrexWithdrawalRequisitesResponse> getWithdrawalRequisites(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true)
            @RequestParam String username,
            @Parameter(description = "Code devise (ex: BTC, ETH, USDT)")
            @RequestParam(required = false) String currency,
            @Parameter(description = "Réseau blockchain (ex: ERC20, TRC20)")
            @RequestParam(required = false) String network) {
        log.info("📋 GET /requisites/withdrawal [user={}, currency={}, network={}]", username, currency, network);
        return kyrrexClientService.getWithdrawalRequisites(username, currency, network);
    }

    /** NOUVEAU — Valider une adresse crypto */
    @PostMapping("/withdrawals/crypto/validate-address")
    @Operation(summary = "Valider une adresse crypto avant retrait",
            description = "Vérifie la validité d'une adresse crypto pour un réseau et une devise donnés.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Résultat de la validation"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Credentials non trouvés"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<KyrrexCryptoAddressValidationResponse> validateCryptoWithdrawalAddress(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true)
            @RequestParam String username,
            @Valid @RequestBody KyrrexCryptoAddressValidationRequest request) {
        log.info("✅ POST /withdrawals/crypto/validate-address [user={}]", username);
        return kyrrexClientService.validateCryptoWithdrawalAddress(username, request);
    }

    @PostMapping("/withdrawals/{username}")
    @Operation(summary = "Retrait crypto vers adresse externe")
    public ResponseEntity<String> executeCryptoWithdrawal(
            @PathVariable String username,
            @RequestParam String currency,
            @RequestParam BigDecimal amount,
            @RequestParam String requisiteId) {
        log.info("💸 POST /withdrawals/{}: {} {}", username, amount, currency);
        return kyrrexClientService.executeCryptoWithdrawal(username, currency, amount, requisiteId);
    }

    @GetMapping("/withdrawals")
    @Operation(summary = "Historique des retraits crypto")
    public ResponseEntity<String> getCryptoWithdrawalHistory(
            @RequestParam String username,
            @RequestParam(required = false) String asset,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(name = "per_page", required = false, defaultValue = "20") int perPage,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String sort,
            @RequestParam(name = "sort_by", required = false) String sortBy) {
        log.info("📋 GET /withdrawals [user={}]", username);
        return kyrrexClientService.getCryptoWithdrawalHistory(username);
    }

    /** NOUVEAU — Détail d'un retrait crypto par ID */
    @GetMapping("/withdrawals/crypto/{withdrawalId}")
    @Operation(summary = "Détail d'un retrait crypto par ID",
            description = "Retourne les informations complètes d'un retrait crypto identifié par son ID Kyrrex.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Détail du retrait crypto"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Retrait non trouvé"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<KyrrexCryptoWithdrawalDetailResponse> getCryptoWithdrawalById(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true)
            @RequestParam String username,
            @Parameter(description = "Identifiant du retrait Kyrrex", required = true)
            @PathVariable String withdrawalId) {
        log.info("🔍 GET /withdrawals/crypto/{} [user={}]", withdrawalId, username);
        return kyrrexClientService.getCryptoWithdrawalById(username, withdrawalId);
    }

    // ══════════════════════════════════════════════════════════
    //  SECTION 14 : KYC / KYB
    //  4 existants + 1 NOUVEAU (getKycStatus)
    // ══════════════════════════════════════════════════════════

    @PostMapping("/kyc/generate-token/{username}")
    @Operation(summary = "Générer un token KYC (Sumsub SDK)")
    public ResponseEntity<KyrrexKycTokenResponse> generateKycToken(@PathVariable String username) {
        log.info("🔐 POST /kyc/generate-token/{}", username);
        return kyrrexClientService.generateKycToken(username);
    }

    @PostMapping("/kyc/generate-web-link/{username}")
    @Operation(summary = "Générer un lien web KYC hébergé")
    public ResponseEntity<KyrrexKycWebLinkResponse> generateKycWebLink(@PathVariable String username) {
        log.info("🔗 POST /kyc/generate-web-link/{}", username);
        return kyrrexClientService.generateKycWebLink(username);
    }

    @PostMapping("/kyc/shared-token/{username}")
    @Operation(summary = "Générer un token KYC partagé (iframe/partenaires)")
    public ResponseEntity<KyrrexKycTokenResponse> generateKycSharedToken(
            @PathVariable String username) {
        log.info("🔐 POST /kyc/shared-token/{}", username);
        return kyrrexClientService.generateKycSharedToken(username);
    }

    @GetMapping("/kyc/levels/{username}")
    @Operation(summary = "Niveaux KYC disponibles")
    public ResponseEntity<java.util.List<KyrrexKycLevelsResponse>> getKycLevels(@PathVariable String username) {
        log.info("📋 GET /kyc/levels/{}", username);
        return kyrrexClientService.getKycLevels(username);
    }

    /** NOUVEAU — Statut KYC d'un client */
    @GetMapping("/kyc/status/{customerId}")
    @Operation(summary = "Statut KYC d'un client spécifique",
            description = "Retourne le statut KYC détaillé (niveau, date de vérification, raison de rejet éventuelle) pour un customerId donné.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Statut KYC récupéré"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Client ou credentials non trouvés"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<KyrrexKycStatusResponse> getKycStatus(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true)
            @RequestParam String username,
            @Parameter(description = "Identifiant client Kyrrex pour le statut KYC", required = true)
            @PathVariable String customerId) {
        log.info("🔍 GET /kyc/status/{} [user={}]", customerId, username);
        return kyrrexClientService.getKycStatus(username, customerId);
    }

    @PostMapping("/kyb/generate-token/{username}")
    @Operation(summary = "Générer un token KYB")
    public ResponseEntity<KyrrexKybTokenResponse> generateKybToken(@PathVariable String username) {
        log.info("🔐 POST /kyb/generate-token/{}", username);
        return kyrrexClientService.generateKybToken(username);
    }

    @PostMapping("/kyb/generate-web-link/{username}")
    @Operation(summary = "Générer un lien web KYB hébergé")
    public ResponseEntity<KyrrexKybWebLinkResponse> generateKybWebLink(@PathVariable String username) {
        log.info("🔗 POST /kyb/generate-web-link/{}", username);
        return kyrrexClientService.generateKybWebLink(username);
    }

    @GetMapping("/kyb/legal-entity-info/{username}")
    @Operation(summary = "Informations de l'entité légale (KYB)")
    public ResponseEntity<KyrrexLegalEntityInfoResponse> getLegalEntityInfo(@PathVariable String username) {
        log.info("📋 GET /kyb/legal-entity-info/{}", username);
        return kyrrexClientService.getLegalEntityInfo(username);
    }

    // ══════════════════════════════════════════════════════════
    //  SECTION 15 : TOOLS (2 existants + 3 NOUVEAUX)
    // ══════════════════════════════════════════════════════════

    @GetMapping("/tools/countries")
    @Operation(summary = "Liste des pays (référentiel Kyrrex)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste des pays"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<List<KyrrexCountryResponse>> getCountries(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true)
            @RequestParam String username) {
        log.info("🌍 GET /tools/countries [user={}]", username);
        return kyrrexClientService.getCountries(username);
    }

    /** NOUVEAU — Codes pays ISO */
    @GetMapping("/tools/countries-codes")
    @Operation(summary = "Codes pays ISO (alpha-2, alpha-3, numérique)",
            description = "Retourne la liste des codes pays ISO utilisés par Kyrrex pour les formulaires et validations.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste des codes pays"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<List<KyrrexCountryCodeResponse>> getCountriesCodes(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true)
            @RequestParam String username) {
        log.info("🌍 GET /tools/countries-codes [user={}]", username);
        return kyrrexClientService.getCountriesCodes(username);
    }

    @GetMapping("/tools/identification-documents")
    @Operation(summary = "Types de documents d'identification")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste des types de documents"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<List<KyrrexIdentificationDocumentResponse>> getIdentificationDocuments(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true)
            @RequestParam String username) {
        log.info("📄 GET /tools/identification-documents [user={}]", username);
        return kyrrexClientService.getIdentificationDocuments(username);
    }

    /** NOUVEAU — Liste des VASPs */
    @GetMapping("/tools/vasps")
    @Operation(summary = "Liste des VASPs (Virtual Asset Service Providers)",
            description = "Retourne la liste des VASPs enregistrés, utilisés pour les déclarations de transfert crypto (Travel Rule).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste des VASPs"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<List<KyrrexVaspResponse>> getVasps(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true)
            @RequestParam String username) {
        log.info("🏢 GET /tools/vasps [user={}]", username);
        return kyrrexClientService.getVasps(username);
    }

    /** NOUVEAU — Timestamp serveur Kyrrex */
    @GetMapping("/tools/timestamp")
    @Operation(summary = "Horodatage serveur Kyrrex",
            description = "Retourne le timestamp actuel du serveur Kyrrex. Utile pour vérifier la synchronisation des horloges (nonce HMAC).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Timestamp serveur"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<KyrrexTimestampResponse> getTimestamp(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true)
            @RequestParam String username) {
        log.info("⏱️ GET /tools/timestamp [user={}]", username);
        return kyrrexClientService.getTimestamp(username);
    }

    // ══════════════════════════════════════════════════════════
    //  SECTION 16 : SESSIONS & BALANCES
    // ══════════════════════════════════════════════════════════

    @PostMapping("/sessions/{username}")
    @Operation(summary = "Créer une session membre Kyrrex",
            description = "Ouvre une session pour obtenir un Bearer Token temporaire utilisé par le frontend.")
    public ResponseEntity<KyrrexSessionResponse> createSession(@PathVariable String username) {
        log.info("🔑 POST /sessions/{}", username);
        return kyrrexClientService.createSession(username);
    }

    @PostMapping("/sessions/login/{username}")
    @Operation(summary = "Se connecter (créer une session authentifiée)",
            description = "Crée une session Kyrrex et persiste les clés de session pour les appels suivants.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session créée"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Credentials non trouvés"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<KyrrexSessionLoginResponse> loginSession(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true)
            @PathVariable String username) {
        log.info("🔑 POST /sessions/login/{}", username);
        return kyrrexClientService.loginSession(username);
    }

    @PostMapping("/sessions/logout/{username}")
    @Operation(summary = "Se déconnecter (supprimer la session)",
            description = "Supprime la session Kyrrex active et nettoie les clés de session en base.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session supprimée (true/false)"),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Credentials non trouvés"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<Boolean> logoutSession(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true)
            @PathVariable String username) {
        log.info("🔒 POST /sessions/logout/{}", username);
        return kyrrexClientService.logoutSession(username);
    }

    @GetMapping("/sessions/list")
    @Operation(summary = "Lister les sessions actives",
            description = "Retourne la liste paginée des sessions Kyrrex actives pour un utilisateur.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste des sessions"),
            @ApiResponse(responseCode = "401", description = "Non authentifié / Aucune session active"),
            @ApiResponse(responseCode = "404", description = "Credentials non trouvés"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<KyrrexSessionListResponse> listSessions(
            @Parameter(description = "Identifiant utilisateur Akuunda", required = true)
            @RequestParam String username,
            @RequestParam(required = false) Integer page,
            @RequestParam(name = "per_page", required = false) Integer perPage) {
        log.info("📋 GET /sessions/list [user={}]", username);
        return kyrrexClientService.listSessions(username, page, perPage);
    }

    @GetMapping("/balances")
    @Operation(summary = "Soldes du membre Kyrrex")
    public ResponseEntity<List<KyrrexBalanceResponse>> getBalances(@RequestParam String username) {
        log.info("💰 GET /balances [user={}]", username);
        return kyrrexClientService.getBalances(username);
    }

    // ══════════════════════════════════════════════════════════
    //  SECTION 17 : TRANSACTIONS (Format simplifié Akuunda)
    // ══════════════════════════════════════════════════════════

    @GetMapping("/transactions")
    @Operation(summary = "Transactions format simplifié Akuunda",
            description = """
                    Agrège toutes les transactions (advanced-exchange, exchange, fiat withdrawal,
                    crypto withdrawal, swaps) dans un format normalisé Akuunda.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste des transactions simplifiées",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SimpleTransactionResponse.class))),
            @ApiResponse(responseCode = "401", description = "Non authentifié"),
            @ApiResponse(responseCode = "404", description = "Credentials non trouvés"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<List<SimpleTransactionResponse>> getSimpleTransactions(
            @RequestParam String username) {
        log.info("📋 GET /transactions [user={}]", username);
        return kyrrexClientService.getSimpleTransactions(username);
    }
}
