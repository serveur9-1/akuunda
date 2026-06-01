package org.akuunda.akuundawallet.wallet.service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import jakarta.validation.Valid;
import org.akuunda.akuundawallet.common.constants.ApiConstants;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.wallet.api.dto.external.*;
import org.akuunda.akuundawallet.wallet.api.requests.MtPelerinRequest;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaMtPelerinServiceClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping(path = AkuundaMtPelerinController.API_SERVICES_ROOT, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(
        name = "Akuunda Services - MtPelerin",
        description = """
                API complète pour l'intégration MT Pelerin :
                - Génération de signatures wallet pour authentification
                - Estimation de prix et calcul de frais pour conversions de devises
                - Récupération de l'historique des transactions marchand
                - Création de liens de dépôt (OnRamp) : conversion fiat → crypto
                - Création de liens de retrait (OffRamp) : conversion crypto → fiat
                """
)
@SecurityRequirement(name = ApiConstants.SWAGGER_BASIC_SECURITY_SCHEME)
@RequiredArgsConstructor
public class AkuundaMtPelerinController {

    public static final String API_SERVICES_ROOT = "/api/internal/v1/mtpelerin";

    private final AkuundaMtPelerinServiceClient akuundaMtPelerinServiceClient;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;

    @GetMapping("/proced-to-signature/{mtpcode}")
    @Operation(
            summary = "Procéder à la signature avec un code OTP",
            description = """
            Permet à un utilisateur de procéder à la signature en fournissant un code OTP.
            Cette méthode valide le code OTP et effectue la signature associée.
            """,
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Signature effectuée avec succès",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(type = "string"),
                                    examples = @ExampleObject(
                                            name = "Réponse réussie",
                                            value = "Signature effectuée avec succès"
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Requête invalide ou paramètres manquants",
                            content = @Content(
                                    mediaType = "application/json",
                                    examples = @ExampleObject(
                                            name = "Erreur 400",
                                            value = """
                                    {
                                      "error": "Paramètres invalides"
                                    }
                                    """
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Erreur interne du serveur",
                            content = @Content(
                                    mediaType = "application/json",
                                    examples = @ExampleObject(
                                            name = "Erreur 500",
                                            value = """
                                    {
                                      "error": "Erreur interne du serveur"
                                    }
                                    """
                                    )
                            )
                    )
            }
    )
    public ResponseEntity<String> procedToSignature(@PathVariable String mtpcode,
                                                    @RequestParam("username") String username) {
        log.info("Processing signature for user: {} and code: {}", username, mtpcode);
        if (username == null || mtpcode == null) {
            log.error("Invalid request: userName={}, otpCode={}", username, mtpcode);
            return ResponseEntity.badRequest().body("Paramètres invalides");
        }
        return akuundaMtPelerinServiceClient.procedToSignature(username, mtpcode);
    }

    @GetMapping("/isSigned-wallet")
    @Operation(
            summary = "Vérifier si un utilisateur a une signature wallet",
            description = """
        Vérifie si un utilisateur possède une signature wallet enregistrée.
        Retourne les informations de la signature si elle existe.
        """,
            parameters = {
                    @Parameter(
                            name = "username",
                            description = "Nom d'utilisateur à vérifier",
                            required = true,
                            example = "user123"
                    )
            },
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Signature trouvée pour l'utilisateur",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = WalletSignatureResponse.class),
                                    examples = @ExampleObject(
                                            name = "Exemple de réponse 200",
                                            value = """
                    {
                      "userName": "user123",
                      "signature": true,
                      "hash": "dGVzdGhhc2gxMjM0NTY3ODkwYWJjZGVm"
                    }
                    """
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Utilisateur non trouvé ou requête invalide",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = WalletSignatureResponse.class),
                                    examples = @ExampleObject(
                                            name = "Exemple de réponse 400",
                                            value = """
                    {
                      "userName": "user123",
                      "signature": false,
                      "hash": null
                    }
                    """
                                    )
                            )
                    )
            }
    )
    public ResponseEntity<WalletSignatureResponse> isSignedWallet(
            @RequestParam String username) {
        log.info("Appel du endpoint isSignedWallet pour l'utilisateur: {}", username);

        if (username == null || username.isBlank()) {
            log.error("Nom d'utilisateur manquant ou invalide");
            return ResponseEntity.badRequest().build();
        }

        return akuundaMtPelerinServiceClient.isSignedWallet(username);
    }

    @PostMapping("/iban-activation")
    @Operation(
            summary = "Activer un IBAN pour un utilisateur",
            description = """
        Génère une URL permettant d'activer un IBAN pour un utilisateur donné.
        L'URL est construite en fonction du `username` fourni.
        """,
            parameters = {
                    @Parameter(
                            name = "username",
                            description = "Nom d'utilisateur pour lequel l'IBAN doit être activé",
                            required = true,
                            example = "002250777832982"
                    )
            },
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "URL générée avec succès",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(type = "string"),
                                    examples = @ExampleObject(
                                            name = "URL générée",
                                            value = "http://example.com/activate-iban?user=002250777832982"
                                    )
                            )
                    ),
                    @ApiResponse(responseCode = "404", description = "Utilisateur introuvable"),
                    @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
            }
    )
    public ResponseEntity<String> ibanActivation(@RequestParam String username) {
        log.info("Appel du endpoint ibanActivation pour l'utilisateur: {}", username);
        return akuundaMtPelerinServiceClient.ibanActivation(username);
    }

    // ------------------------------------------------------------------------
    // GENERATE SIGNATURE
    // ------------------------------------------------------------------------
    @PostMapping(
            value = "/generate-signature",
            consumes = { MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE }
    )
    @Operation(
            summary = "Générer une signature wallet MT Pelerin pour un utilisateur",
            description = """
                    Génère une signature unique pour un utilisateur afin d'interagir avec les services MT Pelerin.
                    La signature est calculée à partir du wallet de l'utilisateur et d'un code unique généré.
                    
                    **Processus :**
                    1. Vérification de l'existence de l'utilisateur et de son wallet
                    2. Génération d'un code unique (4 chiffres)
                    3. Création d'un message de signature : "MtPelerin-{code}"
                    4. Signature du message via Venly avec le wallet de l'utilisateur
                    5. Conversion de la signature hexadécimale en Base64 URL-encodée
                    6. Sauvegarde de la signature en base de données
                    
                    **Formats supportés :**
                    - **JSON** : Envoyer un body JSON avec `Content-Type: application/json`
                    - **Form-data** : Envoyer les paramètres `username` et `signingPinId` en form-data
                    
                    **Utilisation :**
                    La signature générée (`mtpHash`) et le code (`mtpCode`) peuvent être utilisés
                    pour authentifier l'utilisateur dans les widgets MT Pelerin.
                    """,
            requestBody = @RequestBody(
                    required = false,
                    description = "Requête JSON pour la génération d'une signature MT Pelerin (optionnel si form-data est utilisé)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MtPelerinRequest.class),
                            examples = @ExampleObject(
                                    name = "JSON Request Example",
                                    value = """
                                    {
                                      "username": "xxxxxxxxxxxxxxxxx",
                                      "signingPinId": "xxxx-9c32-xxxx-bb57-xxx"
                                    }
                                    """
                            )
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Signature générée avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MtPelerinResponse.class),
                            examples = @ExampleObject(
                                    name = "Signature Response Example",
                                    value = """
                                    {
                                      "userName": "xxxxxxxxxxxxxxxxxxxx",
                                      "mtpHash": "xxxxxxxxxxxxxxxxxxxxxxx",
                                      "mtpCode": "12xxx34",
                                      "walletAddress": "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Requête invalide ou paramètres manquants"),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable ou wallet non disponible"),
            @ApiResponse(responseCode = "417", description = "Erreur lors de la génération de la signature (API Venly)"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<MtPelerinResponse> generateSignature(@RequestBody @Valid MtPelerinRequest request) {
        log.info("Request received: {}", request);
        log.info("Generating MtPelerin signature for user: {}", request.getUsername());
        if (request.getUsername() == null || request.getSigningPinId() == null) {
            log.error("Invalid request: username={}, signingPinId={}",
                    request.getUsername(), request.getSigningPinId());
            return ResponseEntity.badRequest().build();
        }
        return akuundaMtPelerinServiceClient.generateSignature(request.getUsername(), request.getSigningPinId());
    }

    // ------------------------------------------------------------------------
    // PRICE QUOTE (ESTIMATION DE FRAIS)
    // ------------------------------------------------------------------------
    @PostMapping(
            value = "/price-quote",
            consumes = { MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_FORM_URLENCODED_VALUE }
    )
    @Operation(
            summary = "Obtenir une estimation de prix pour une conversion de devise",
            description = """
                    Récupère une estimation de prix (quote) depuis l'API MT Pelerin pour une conversion de devise.
                    Utilisé pour calculer les frais et le montant reçu lors d'une opération de dépôt ou retrait.
                    
                    **⚠️ IMPORTANT - Utilisation pour OffRamp :**
                    - **Le back-end gère automatiquement la conversion** lors de l'appel à `/offramp`
                    - Le front-end n'a **plus besoin** d'appeler `/price-quote` pour OffRamp
                    - Le front-end envoie simplement le montant fiat (EUR) dans `sda`, et le back-end convertit automatiquement
                    - Cet endpoint peut être utilisé pour **afficher une estimation** à l'utilisateur avant de créer le lien OffRamp
                    
                    **Exemple d'utilisation pour OffRamp (estimation uniquement) :**
                    1. Utilisateur veut retirer 100 EUR
                    2. Front peut appeler `/price-quote` pour afficher une estimation : "Vous recevrez environ 100 USDC"
                    3. Front envoie directement `sda: 100.0` (montant fiat) à `/offramp`
                    4. Back-end appelle automatiquement `/price-quote` et utilise le montant USDC calculé
                    
                    **Exemple d'utilisation pour OnRamp :**
                    - Conversion EUR → USDC sur Polygon
                    - Montant source : 100 EUR
                    - Réponse : montant reçu en USDC, taux de change, frais
                    """,
            requestBody = @RequestBody(
                    required = true,
                    description = "Requête contenant les informations de conversion",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PriceQuoteRequest.class),
                            examples = @ExampleObject(
                                    name = "Price Quote Example",
                                    value = """
                                    {
                                      "sourceCurrency": "EUR",
                                      "destCurrency": "USDC",
                                      "sourceAmount": 100,
                                      "sourceNetwork": "fiat",
                                      "destNetwork": "matic_mainnet",
                                      "isCardPayment": false
                                    }
                                    """
                            )
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Estimation de prix récupérée avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PriceQuoteResponse.class),
                            examples = @ExampleObject(
                                    name = "Price Quote Response",
                                    value = """
                                    {
                                      "sourceCurrency": "EUR",
                                      "destCurrency": "USDC",
                                      "sourceAmount": 100.0,
                                      "destAmount": 108.5,
                                      "sourceNetwork": "fiat",
                                      "destNetwork": "matic_mainnet",
                                      "exchangeRate": 1.085,
                                      "fees": 2.5,
                                      "feesCurrency": "EUR"
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<?> getPriceQuote(HttpServletRequest httpRequest) {
        PriceQuoteRequest request = null;
        String contentType = httpRequest.getContentType();
        if (contentType != null && contentType.toLowerCase().contains("application/json")) {
            // Lecture manuelle du body JSON (évite les null quand le body n'est pas correctement lié par @RequestBody)
            try {
                request = objectMapper.readValue(httpRequest.getInputStream(), PriceQuoteRequest.class);
            } catch (IOException e) {
                log.warn("price-quote: erreur lecture JSON: {}", e.getMessage());
                return ResponseEntity.badRequest().body(
                        Map.of("error", "Body JSON invalide", "detail", e.getMessage()));
            }
        } else {
            // Form-data ou application/x-www-form-urlencoded : lecture des paramètres
            String sourceCurrency = firstNonBlank(
                    httpRequest.getParameter("sourceCurrency"),
                    httpRequest.getParameter("source_currency"));
            String destCurrency = firstNonBlank(
                    httpRequest.getParameter("destCurrency"),
                    httpRequest.getParameter("dest_currency"));
            String sourceAmountStr = firstNonBlank(
                    httpRequest.getParameter("sourceAmount"),
                    httpRequest.getParameter("source_amount"));
            String sourceNetwork = firstNonBlank(
                    httpRequest.getParameter("sourceNetwork"),
                    httpRequest.getParameter("source_network"));
            String destNetwork = firstNonBlank(
                    httpRequest.getParameter("destNetwork"),
                    httpRequest.getParameter("dest_network"));
            String isCardPaymentStr = firstNonBlank(
                    httpRequest.getParameter("isCardPayment"),
                    httpRequest.getParameter("is_card_payment"));

            Double sourceAmount = null;
            if (sourceAmountStr != null && !sourceAmountStr.isBlank()) {
                try {
                    sourceAmount = Double.parseDouble(sourceAmountStr.trim());
                } catch (NumberFormatException e) {
                    log.warn("price-quote: sourceAmount invalide: {}", sourceAmountStr);
                }
            }
            Boolean isCardPayment = "true".equalsIgnoreCase(isCardPaymentStr);

            request = new PriceQuoteRequest();
            request.setSourceCurrency(sourceCurrency);
            request.setDestCurrency(destCurrency);
            request.setSourceAmount(sourceAmount);
            request.setSourceNetwork(sourceNetwork);
            request.setDestNetwork(destNetwork);
            request.setIsCardPayment(isCardPayment);
        }

        if (request == null) {
            return ResponseEntity.badRequest().body(
                    Map.of("error", "Body JSON ou paramètres form obligatoires",
                            "example", "{\"sourceCurrency\":\"EUR\",\"destCurrency\":\"USDC\",\"sourceAmount\":100,\"sourceNetwork\":\"fiat\",\"destNetwork\":\"matic_mainnet\"}"));
        }
        log.info("Request received: {}", request);
        if (request.getSourceCurrency() == null || request.getSourceCurrency().isBlank()
                || request.getDestCurrency() == null || request.getDestCurrency().isBlank()
                || request.getSourceAmount() == null || request.getSourceAmount() <= 0
                || request.getSourceNetwork() == null || request.getSourceNetwork().isBlank()
                || request.getDestNetwork() == null || request.getDestNetwork().isBlank()) {
            String errorMessage = "Champs obligatoires manquants ou vides: sourceCurrency, destCurrency, sourceAmount, sourceNetwork, destNetwork. "
                    + "Exemple JSON: {\"sourceCurrency\":\"EUR\",\"destCurrency\":\"USDC\",\"sourceAmount\":100,\"sourceNetwork\":\"fiat\",\"destNetwork\":\"matic_mainnet\"}";
            log.warn("price-quote: {}", errorMessage);
            return ResponseEntity.badRequest().body(Map.of("error", errorMessage));
        }
        log.info("Getting price quote: {} {} -> {} {}",
                request.getSourceAmount(), request.getSourceCurrency(),
                request.getDestCurrency(), request.getDestNetwork());
        return akuundaMtPelerinServiceClient.getPriceQuote(request);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) return a.trim();
        if (b != null && !b.isBlank()) return b.trim();
        return null;
    }

    // ------------------------------------------------------------------------
    // MERCHANT TRANSACTIONS (HISTORIQUE)
    // ------------------------------------------------------------------------
    @GetMapping("/transactions")
    @Operation(
            summary = "Récupérer l'historique des transactions MT Pelerin (format simplifié)",
            description = """
                    Récupère les transactions du marchand depuis l'API MT Pelerin, filtrées par utilisateur, au format simplifié.
                    Les transactions sont automatiquement sauvegardées en base de données.
                    
                    **Format de réponse simplifié :**
                    - Format compréhensible pour un utilisateur final
                    - Contient uniquement les données essentielles : id, status, date, amount, currency, operator
                    - Les montants XOF/XAF sont en centimes, les montants EUR sont en unités
                    - Le champ `operator` est toujours `"MTPELERIN"` pour cet endpoint
                    
                    **Sécurité :**
                    - ⚠️ **IMPORTANT** : Seules les transactions appartenant à l'utilisateur spécifié sont retournées.
                    - Le système filtre les transactions en vérifiant que le `merchant_oid` commence par `{username}-`.
                    - Le format du `merchant_oid` est : `{username}-{uuid}` (ex: `0033612108828-27301bc3-7715-4966-b9a3-6aadb39e9dd8`).
                    - Toute transaction dont le `merchant_oid` ne correspond pas à l'utilisateur est exclue des résultats.
                    
                    **Endpoint MT Pelerin utilisé :**
                    - `GET https://api.mtpelerin.com/transactions/merchantReport/{apiKey}?fromDate={fromDate}&toDate={toDate}&skip={skip}&limit={limit}`
                    
                    **Mapping des statuts :**
                    - `finished` → `completed`
                    - `pending` → `pending`
                    - `failed` → `failed`
                    
                    **Format de l'ID :**
                    - ID réel MT Pelerin : `merchant_oid` (ex: `0033612108828-27301bc3-7715-4966-b9a3-6aadb39e9dd8`)
                    
                    **Filtrage des devises :**
                    - ⚠️ **IMPORTANT** : Seules les transactions en devises fiat sont retournées (XOF, EUR, XAF, etc.)
                    - Les transactions en crypto-monnaies (USDC, USDT, BTC, ETH, etc.) sont exclues
                    
                    **Note importante :**
                    - Seules les transactions payées sont retournées (pas les commandes non complétées)
                    - Le statut "pending" signifie que le paiement a été reçu mais le transfert est en cours
                    - Le filtrage par utilisateur est effectué après la récupération depuis l'API MT Pelerin
                    """,
            parameters = {
                    @io.swagger.v3.oas.annotations.Parameter(
                            name = "fromDate",
                            description = "Date de début (format ISO: yyyy-MM-dd)",
                            required = true,
                            example = "2025-12-01"
                    ),
                    @io.swagger.v3.oas.annotations.Parameter(
                            name = "toDate",
                            description = "Date de fin (format ISO: yyyy-MM-dd)",
                            required = true,
                            example = "2026-01-01"
                    ),
                    @io.swagger.v3.oas.annotations.Parameter(
                            name = "username",
                            description = "Nom d'utilisateur (phone number) pour filtrer les transactions. " +
                                    "Seules les transactions dont le `merchant_oid` commence par `{username}-` sont retournées.",
                            required = true,
                            example = "0033612108828"
                    ),
                    @io.swagger.v3.oas.annotations.Parameter(
                            name = "skip",
                            description = "Nombre de transactions à ignorer (pagination)",
                            required = false,
                            example = "0"
                    ),
                    @io.swagger.v3.oas.annotations.Parameter(
                            name = "limit",
                            description = "Nombre maximum de transactions à retourner",
                            required = false,
                            example = "100"
                    )
            }
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Transactions récupérées avec succès au format simplifié",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SimpleTransactionResponse.class, type = "array"),
                            examples = {
                                    @ExampleObject(
                                            name = "Transactions XOF (montants en centimes)",
                                            description = "Les montants XOF sont retournés en centimes (10000 = 100.00 XOF)",
                                            value = """
                                                    [
                                                      {
                                                        "id": "0033612108828-27301bc3-7715-4966-b9a3-6aadb39e9dd8",
                                                        "status": "completed",
                                                        "date": "2026-01-12T14:05:00Z",
                                                        "amount": 10000,
                                                        "currency": "XOF",
                                                        "operator": "MTPELERIN",
                                                        "type": "ONRAMP"
                                                      },
                                                      {
                                                        "id": "0033612108828-45678def-9012-3456-7890-abcdef123456",
                                                        "status": "pending",
                                                        "date": "2026-01-12T15:30:00Z",
                                                        "amount": 5000,
                                                        "currency": "XOF",
                                                        "operator": "MTPELERIN",
                                                        "type": "OFFRAMP"
                                                      }
                                                    ]
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Transactions EUR (montants en unités)",
                                            description = "Les montants EUR sont retournés en unités (92 = 92.00 EUR)",
                                            value = """
                                                    [
                                                      {
                                                        "id": "0033612108828-789abcde-f012-3456-789a-bcdef012345",
                                                        "status": "completed",
                                                        "date": "2026-01-12T16:45:00Z",
                                                        "amount": 92,
                                                        "currency": "EUR",
                                                        "operator": "MTPELERIN",
                                                        "type": "ONRAMP"
                                                      },
                                                      {
                                                        "id": "0033612108828-01234567-8901-2345-6789-abcdef012345",
                                                        "status": "failed",
                                                        "date": "2026-01-12T17:00:00Z",
                                                        "amount": 50,
                                                        "currency": "EUR",
                                                        "operator": "MTPELERIN",
                                                        "type": "OFFRAMP"
                                                      }
                                                    ]
                                                    """
                                    )
                            }
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Requête invalide (username manquant, dates invalides)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "error": "Username manquant"
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Erreur interne du serveur (erreur API MT Pelerin ou erreur interne)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "error": "Erreur serveur",
                                      "message": "Erreur lors de la récupération des transactions"
                                    }
                                    """)
                    )
            )
    })
    public ResponseEntity<List<SimpleTransactionResponse>> getMerchantTransactions(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(value = "username", required = true) String username,
            @RequestParam(required = false, defaultValue = "0") Integer skip,
            @RequestParam(required = false, defaultValue = "100") Integer limit) {
        log.info("Getting merchant transactions from {} to {} for user: {}", fromDate, toDate, username);
        // Convertir LocalDate en LocalDateTime (début de journée pour fromDate, fin de journée pour toDate)
        LocalDateTime fromDateTime = fromDate.atStartOfDay();
        LocalDateTime toDateTime = toDate.atTime(23, 59, 59);
        return akuundaMtPelerinServiceClient.getSimpleMerchantTransactions(fromDateTime, toDateTime, skip, limit, username);
    }

    // ------------------------------------------------------------------------
    // OFF RAMP (RETRAIT)
    // ------------------------------------------------------------------------
    @PostMapping("/offramp")
    @Operation(
            summary = "Créer un lien de retrait (OffRamp) MT Pelerin",
            description = """
                    Crée un lien de retrait permettant à un utilisateur de convertir des cryptomonnaies en fiat.
                    Le back-end convertit toujours en USDC avant l'envoi à MT Pelerin (jamais d'envoi en euro).
                    
                    **Paramètres envoyés par le front :**
                    - `sdc` : Devise fiat que l'utilisateur souhaite recevoir (ex: EUR)
                    - `sda` : **Montant en devise fiat (sdc)** que l'utilisateur souhaite recevoir (ex: 100 EUR)
                    - `lang`, `phone`, `ctry`
                    
                    **Flow :**
                    1. Le front envoie par ex. `sdc: "EUR"`, `sda: 100` (utilisateur veut recevoir 100 EUR).
                    2. Le back appelle l'endpoint **getPriceQuote** (MT Pelerin) pour obtenir le montant USDC à envoyer.
                    3. Le back envoie à MT Pelerin le montant **USDC** (destAmount) dans le paramètre `ssa`, pas le montant en euro.
                    
                    **Conversion :** L'endpoint `/price-quote` (getPriceQuote) permet de connaître la valeur USDC à envoyer à MT Pelerin ; le back l'appelle automatiquement.
                    """,
            requestBody = @RequestBody(
                    required = true,
                    description = "sda = montant en fiat (sdc) que l'utilisateur souhaite recevoir ; le back convertit en USDC via getPriceQuote",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MtPelerinOffRampRequest.class),
                            examples = @ExampleObject(
                                    name = "OffRamp Request Example",
                                    description = "sda = montant en EUR que l'utilisateur souhaite recevoir ; le back convertit en USDC avant envoi à MT Pelerin",
                                    value = """
                                    {
                                      "sdc": "EUR",
                                      "sda": 100.0,
                                      "lang": "fr",
                                      "phone": "0033612108828",
                                      "ctry": "FR"
                                    }
                                    """
                            )
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Lien de retrait créé avec succès (ssa = montant USDC calculé via getPriceQuote)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MtPelerinOffRampResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "502", description = "Échec getPriceQuote (impossible d'obtenir le montant USDC)"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<MtPelerinOffRampResponse> createOffRampLink(
            @org.springframework.web.bind.annotation.RequestBody @Valid MtPelerinOffRampRequest request,
            @RequestParam(required = false) String username) {
        log.info("Creating OffRamp link for user: {}", username);
        return akuundaMtPelerinServiceClient.buildOffRampLink(request, username);
    }

    // ------------------------------------------------------------------------
    // ON RAMP (DÉPÔT)
    // ------------------------------------------------------------------------
    @PostMapping("/onramp")
    @Operation(
            summary = "Créer un lien de dépôt (OnRamp) MT Pelerin",
            description = """
                    Crée un lien de dépôt permettant à un utilisateur de convertir des fiat en cryptomonnaies.
                    Le lien redirige vers le widget MT Pelerin avec tous les paramètres pré-remplis.
                    
                    **Paramètres envoyés par le front :**
                    - `bsc` : Devise fiat sélectionnée (ex: EUR)
                    - `bsa` : Montant fiat
                    - `phone` : Numéro de téléphone pour login pré-rempli
                    - `ctry` : Code pays (ex: FR)
                    - `lang` : Langue du widget (ex: fr)
                    - `addr` : Adresse wallet utilisateur
                    - `code` : Code de validation (optionnel)
                    - `hash` : Signature optionnelle
                    
                    **Paramètres ajoutés par le back :**
                    - `tab=buy`, `bdc=USDC`, `dnet=matic_mainnet`, `net=matic_mainnet`, `rfr`, `oid`, `_ctkn`
                    """,
            requestBody = @RequestBody(
                    required = true,
                    description = "Requête contenant les paramètres du front",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MtPelerinOnRampRequest.class),
                            examples = @ExampleObject(
                                    name = "OnRamp Request Example",
                                    value = """
                                    {
                                      "bsc": "EUR",
                                      "bsa": 100.0,
                                      "phone": "xxxxxxxxxxx",
                                      "ctry": "FR",
                                      "lang": "fr",
                                      "addr": "xxxxxxxxxxxxxxxxxxxxxxxxxxx",
                                      "code": "1234",
                                      "hash": ""
                                    }
                                    """
                            )
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Lien de dépôt créé avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MtPelerinOnRampResponse.class),
                            examples = @ExampleObject(
                                    name = "OnRamp Response",
                                    value = """
                                    {
                                      "redirectUrl": "https://securepay.akuunda-pay.io/?tab=buy&bsc=EUR&bdc=USDC&bsa=100&dnet=matic_mainnet&net=matic_mainnet&rfr=5qCpmK2X&phone=33612108828&ctry=FR&lang=fr&addr=0x090d3840A4eD99ED40Be741884b7f941A856A23a&code=1234&hash=&oid=user123-uuid&_ctkn=api-key",
                                      "orderId": "user123-uuid"
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<MtPelerinOnRampResponse> createOnRampLink(
            @org.springframework.web.bind.annotation.RequestBody @Valid MtPelerinOnRampRequest request,
            @RequestParam(required = false) String username) {
        log.info("Creating OnRamp link for user: {}", username);
        return akuundaMtPelerinServiceClient.buildOnRampLink(request, username);
    }

    // ── Sign Wallet ───────────────────────────────────────────────────────────

    @PostMapping("/sign-wallet")
    @Operation(
            summary = "Signer le wallet MT Pelerin",
            description = """
                    Signe le wallet de l'utilisateur via Venly pour l'associer à MT Pelerin.
                    Réutilise la signature existante si elle est déjà en base.
                    Nécessaire avant toute activation IBAN.
                    """)
    @ApiResponse(responseCode = "200", description = "Wallet signé avec succès")
    @ApiResponse(responseCode = "428", description = "pinCode requis (wallet non encore signé)")
    @ApiResponse(responseCode = "417", description = "Échec de la signature Venly (PIN incorrect)")
    public ResponseEntity<MtPelerinSignWalletResponse> signWallet(
            @RequestParam String username,
            @org.springframework.web.bind.annotation.RequestBody @Valid MtPelerinSignWalletRequest request) {
        log.info("POST /mtpelerin/sign-wallet username={}", username);
        return akuundaMtPelerinServiceClient.signWallet(username, request.getPinCode());
    }

    // ── Résolution du username (phone) depuis le JWT ──────────────────────────

    @GetMapping("/wallet-username")
    @Operation(
            summary = "Récupérer le username wallet de l'utilisateur connecté",
            description = "Résout le numéro de téléphone (username wallet) depuis le subject du JWT Keycloak.")
    @ApiResponse(responseCode = "200", description = "Username résolu")
    @ApiResponse(responseCode = "401", description = "Non authentifié")
    @ApiResponse(responseCode = "404", description = "Utilisateur introuvable")
    public ResponseEntity<Map<String, String>> getWalletUsername(Authentication authentication) {
        if (!(authentication instanceof JwtAuthenticationToken jwtAuth)) {
            return ResponseEntity.status(401).body(Map.of("error", "Non authentifié"));
        }

        var jwt = jwtAuth.getToken();
        String subject = jwt.getSubject();
        String email   = jwt.getClaimAsString("email");

        // 1. Essai par userId (JWT Keycloak — sub == user_id dans la table users)
        if (subject != null) {
            var byId = userRepository.findById(subject);
            if (byId.isPresent()) {
                log.info("getWalletUsername: résolu par userId={}", subject);
                return ResponseEntity.ok(Map.of("username", byId.get().getUsername()));
            }
        }

        // 2. Fallback par email (JWT backoffice HMAC ou Keycloak sans correspondance par sub)
        if (email != null && !email.isBlank()) {
            var byEmail = userRepository.findFirstByEmailOrderByCreatedAtAsc(email);
            if (byEmail.isPresent()) {
                log.info("getWalletUsername: résolu par email={}", email);
                return ResponseEntity.ok(Map.of("username", byEmail.get().getUsername()));
            }
        }

        log.warn("getWalletUsername: introuvable — subject={}, email={}", subject, email);
        return ResponseEntity.status(404).body(Map.of("error", "Utilisateur introuvable"));
    }
}
