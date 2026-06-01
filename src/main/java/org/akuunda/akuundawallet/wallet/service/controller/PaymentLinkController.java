package org.akuunda.akuundawallet.wallet.service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.constants.ApiConstants;
import org.akuunda.akuundawallet.wallet.api.dao.CountryCurrencyRepository;
import org.akuunda.akuundawallet.wallet.api.dto.CountryResponseDto;
import org.akuunda.akuundawallet.wallet.api.dto.CreatePaymentLinkRequest;
import org.akuunda.akuundawallet.wallet.api.dto.OperatorResponseDto;
import org.akuunda.akuundawallet.wallet.api.dto.PaymentLinkInfoResponse;
import org.akuunda.akuundawallet.wallet.api.dto.PaymentLinkResponse;
import org.akuunda.akuundawallet.wallet.api.dto.ProcessPaymentRequest;
import org.akuunda.akuundawallet.wallet.api.dto.WebPaymentRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.akuunda.akuundawallet.wallet.api.entities.CountryCurrency;
import org.akuunda.akuundawallet.wallet.service.PaymentLinkService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaYellowCardClientService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping(path = PaymentLinkController.API_SERVICES_ROOT, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Akuunda Services - Payment Links", description = """
        API de gestion des liens de paiement en masse :
        - Création de liens uniques pour recevoir des paiements
        - Consultation des liens créés
        - Traitement des paiements via liens
        - Désactivation de liens
        """)
@SecurityRequirement(name = ApiConstants.SWAGGER_BASIC_SECURITY_SCHEME)
@RequiredArgsConstructor
public class PaymentLinkController {

    public static final String API_SERVICES_ROOT = "/api/internal/v1/payment-links";

    private final PaymentLinkService paymentLinkService;
    private final AkuundaYellowCardClientService yellowCardClientService;
    private final CountryCurrencyRepository countryCurrencyRepository;
    private final ObjectMapper objectMapper;

    // ------------------------------------------------------------------------
    // CREATE PAYMENT LINK
    // ------------------------------------------------------------------------
    @PostMapping("/create")
    @Operation(
            operationId = "createPaymentLink",
            summary = "Créer un lien de paiement unique",
            description = """
        Crée un nouveau lien de paiement unique qui peut être partagé pour recevoir des paiements en masse.
        
        **Fonctionnalités :**
        - Génère un code unique court (ex: "gn1mb") pour le lien
        - Permet de définir un montant fixe ou un montant libre
        - Peut avoir une date d'expiration optionnelle
        - Le lien peut être utilisé par plusieurs payeurs
        
        **Paramètres requis :**
        - `username` (query parameter) : Username (numéro de téléphone) du créateur du lien
        - `description` (body) : Description/libellé du paiement (obligatoire)
        
        **Paramètres optionnels :**
        - `amount` (body) : Montant fixe (optionnel, si omis = montant libre choisi par le payeur)
        - `currency` (body) : Code devise ISO (optionnel, si omis = devise choisie par le payeur, ex: XOF, EUR, USD)
        - `expiresAt` (body) : Date d'expiration ISO-8601 (optionnel)
        
        **Note :** Si `amount` et `currency` sont omis, le payeur pourra choisir le montant et la devise lors du paiement via l'interface web.
        
        **Exemple d'usage :**
        ```bash
        POST /api/internal/v1/payment-links/create?username=002250759146890
        Content-Type: application/json
        
        {
          "description": "Paiement facture électricité - Janvier 2024"
        }
        ```
        """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Détails du lien de paiement à créer",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CreatePaymentLinkRequest.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Lien simple (montant et devise libres)",
                                            value = """
                                            {
                                              "description": "Paiement facture électricité - Janvier 2024"
                                            }
                                            """
                                    ),
                                    @ExampleObject(
                                            name = "Lien avec montant fixe",
                                            value = """
                                            {
                                              "description": "Paiement facture électricité - Janvier 2024",
                                              "amount": 5000.0,
                                              "currency": "XOF",
                                              "expiresAt": "2024-12-31T23:59:59"
                                            }
                                            """
                                    ),
                                    @ExampleObject(
                                            name = "Lien avec devise fixe mais montant libre",
                                            value = """
                                            {
                                              "description": "Cagnotte anniversaire",
                                              "currency": "XOF"
                                            }
                                            """
                                    )
                            }
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Lien de paiement créé avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PaymentLinkResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "id": 1,
                                      "uniqueCode": "gn1mb",
                                      "paymentUrl": "https://akuunda-pay.io/pay/gn1mb",
                                      "description": "Paiement facture électricité - Janvier 2024",
                                      "amount": 5000.0,
                                      "currency": "XOF",
                                      "isActive": true,
                                      "expiresAt": "2024-12-31T23:59:59",
                                      "totalPayments": 0,
                                      "totalAmountReceived": 0.0,
                                      "creatorUsername": "002250759146858",
                                      "createdAt": "2024-01-15T10:30:00",
                                      "updatedAt": "2024-01-15T10:30:00"
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Requête invalide (body manquant, champs manquants ou invalides)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "timestamp": "2025-11-06T13:20:07.012+00:00",
                                      "status": 400,
                                      "error": "Bad Request",
                                      "path": "/api/internal/v1/payment-links/create",
                                      "message": "Required request body is missing"
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Utilisateur non trouvé"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<PaymentLinkResponse> createPaymentLink(
            @Parameter(name = "username", description = "Username (numéro de téléphone) du créateur du lien", required = true)
            @RequestParam String username,
            @RequestBody @Valid CreatePaymentLinkRequest request) {
        log.info("Request to create payment link for user: {}", username);
        return paymentLinkService.createPaymentLink(username, request);
    }

    // ------------------------------------------------------------------------
    // GET PAYMENT LINK BY CODE
    // ------------------------------------------------------------------------
    @GetMapping("/{uniqueCode}")
    @Operation(
            operationId = "getPaymentLinkByCode",
            summary = "Récupérer un lien de paiement par son code unique",
            description = """
        Récupère les détails d'un lien de paiement à partir de son code unique.
        
        **Usage :**
        - Permet de vérifier les détails d'un lien avant de payer
        - Vérifie automatiquement si le lien est actif et non expiré
        
        **Exemple :**
        ```bash
        GET /api/internal/v1/payment-links/gn1mb
        ```
        """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Lien de paiement trouvé",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PaymentLinkResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Lien de paiement non trouvé"),
            @ApiResponse(responseCode = "410", description = "Lien de paiement expiré"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<PaymentLinkResponse> getPaymentLinkByCode(
            @Parameter(description = "Code unique du lien de paiement", required = true, example = "gn1mb")
            @PathVariable String uniqueCode) {
        log.info("Request to get payment link by code: {}", uniqueCode);
        return paymentLinkService.getPaymentLinkByCode(uniqueCode);
    }

    // ------------------------------------------------------------------------
    // GET USER PAYMENT LINKS
    // ------------------------------------------------------------------------
    @GetMapping("/user/{username}")
    @Operation(
            operationId = "getUserPaymentLinks",
            summary = "Récupérer tous les liens de paiement d'un utilisateur",
            description = """
        Récupère la liste de tous les liens de paiement créés par un utilisateur.
        
        **Usage :**
        - Permet à un utilisateur de voir tous ses liens de paiement
        - Les liens sont triés par date de création (plus récents en premier)
        
        **Exemple :**
        ```bash
        GET /api/internal/v1/payment-links/user/002250759146858
        ```
        """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Liste des liens de paiement",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PaymentLinkResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Utilisateur non trouvé"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<List<PaymentLinkResponse>> getUserPaymentLinks(
            @Parameter(description = "Username (numéro de téléphone) du créateur", required = true, example = "002250759146858")
            @PathVariable String username) {
        log.info("Request to get payment links for user: {}", username);
        return paymentLinkService.getUserPaymentLinks(username);
    }

    // ------------------------------------------------------------------------
    // DEACTIVATE PAYMENT LINK
    // ------------------------------------------------------------------------
    @PutMapping("/{uniqueCode}/deactivate")
    @Operation(
            operationId = "deactivatePaymentLink",
            summary = "Désactiver un lien de paiement",
            description = """
        Désactive un lien de paiement. Seul le créateur du lien peut le désactiver.
        
        **Usage :**
        - Empêche de nouveaux paiements via ce lien
        - Les paiements déjà effectués ne sont pas affectés
        
        **Exemple :**
        ```bash
        PUT /api/internal/v1/payment-links/gn1mb/deactivate?username=002250759146858
        ```
        """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Lien de paiement désactivé avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "success": true,
                                      "message": "Payment link deactivated successfully"
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(responseCode = "403", description = "Non autorisé (vous n'êtes pas le créateur du lien)"),
            @ApiResponse(responseCode = "404", description = "Lien de paiement ou utilisateur non trouvé"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<String> deactivatePaymentLink(
            @Parameter(description = "Code unique du lien de paiement", required = true, example = "gn1mb")
            @PathVariable String uniqueCode,
            @Parameter(description = "Username (numéro de téléphone) du créateur", required = true, example = "002250759146858")
            @RequestParam String username) {
        log.info("Request to deactivate payment link: {} for user: {}", uniqueCode, username);
        return paymentLinkService.deactivatePaymentLink(username, uniqueCode);
    }

    // ------------------------------------------------------------------------
    // PROCESS PAYMENT
    // ------------------------------------------------------------------------
    @PostMapping("/process-payment")
    @Operation(
            operationId = "processPayment",
            summary = "Effectuer un paiement via un lien de paiement",
            description = """
        Traite un paiement effectué via un lien de paiement.
        
        **Fonctionnalités :**
        - Vérifie que le lien est actif et non expiré
        - Vérifie le montant si le lien a un montant fixe
        - Vérifie le solde du payeur
        - Effectue le transfert du payeur vers le créateur du lien
        - Crée les opérations de débit et crédit
        - Met à jour les statistiques du lien
        
        **Exemple d'usage :**
        ```bash
        POST /api/internal/v1/payment-links/process-payment
        ```
        """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Détails du paiement à effectuer",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProcessPaymentRequest.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Paiement avec montant fixe",
                                            value = """
                                            {
                                              "uniqueCode": "gn1mb",
                                              "amount": 5000.0,
                                              "payerUsername": "002250777832982",
                                              "note": "Paiement facture électricité - Janvier 2024"
                                            }
                                            """
                                    ),
                                    @ExampleObject(
                                            name = "Paiement avec montant libre",
                                            value = """
                                            {
                                              "uniqueCode": "abc12",
                                              "amount": 10000.0,
                                              "payerUsername": "002250777832982",
                                              "note": "Contribution cagnotte"
                                            }
                                            """
                                    )
                            }
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Paiement traité avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "success": true,
                                      "message": "Payment processed successfully",
                                      "amount": 5000.0,
                                      "currency": "XOF"
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Requête invalide (montant incorrect, solde insuffisant, lien inactif)"),
            @ApiResponse(responseCode = "404", description = "Lien de paiement, payeur ou wallet non trouvé"),
            @ApiResponse(responseCode = "410", description = "Lien de paiement expiré"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<String> processPayment(@RequestBody @Valid ProcessPaymentRequest request) {
        log.info("Request to process payment for link: {} by user: {}", request.getUniqueCode(), request.getPayerUsername());
        return paymentLinkService.processPayment(request);
    }

    // ------------------------------------------------------------------------
    // WEB PAYMENT ENDPOINTS (Public, no authentication required)
    // ------------------------------------------------------------------------

    @GetMapping("/web/{uniqueCode}")
    @SecurityRequirements // Endpoint public, pas d'authentification requise
    @Operation(
            operationId = "getPaymentLinkInfoForWeb",
            summary = "Récupérer les informations publiques d'un lien de paiement (interface web)",
            description = """
        Récupère les informations publiques d'un lien de paiement pour l'interface web.
        Cet endpoint est public et ne nécessite pas d'authentification.
        
        **⚠️ Endpoint public :** Aucune authentification requise
        
        **Utilisé par :** L'interface web de paiement (comme Djamo Pay)
        
        **Retourne :**
        - Description du paiement
        - Montant fixe (si défini)
        - Devise fixe (si définie)
        - Statistiques (nombre de paiements, montant total reçu)
        - Nom du créateur
        
        **Exemple d'usage :**
        ```bash
        GET /api/internal/v1/payment-links/web/gn1mb
        ```
        """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Informations du lien récupérées avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = PaymentLinkInfoResponse.class),
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "uniqueCode": "gn1mb",
                                      "description": "Paiement facture électricité - Janvier 2024",
                                      "amount": null,
                                      "currency": null,
                                      "isActive": true,
                                      "expiresAt": null,
                                      "totalPayments": 5,
                                      "totalAmountReceived": 25000.0,
                                      "creatorName": "Jean Dupont"
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Lien de paiement non trouvé"),
            @ApiResponse(responseCode = "410", description = "Lien de paiement expiré ou inactif")
    })
    public ResponseEntity<PaymentLinkInfoResponse> getPaymentLinkInfoForWeb(
            @Parameter(description = "Code unique du lien de paiement", required = true, example = "gn1mb")
            @PathVariable String uniqueCode) {
        log.info("Request to get payment link info for web: {}", uniqueCode);
        return paymentLinkService.getPaymentLinkInfoForWeb(uniqueCode);
    }

    @PostMapping("/web/payment")
    @SecurityRequirements // Endpoint public, pas d'authentification requise
    @Operation(
            operationId = "processWebPayment",
            summary = "Effectuer un paiement via l'interface web (mobile money)",
            description = """
        Traite un paiement effectué via l'interface web en utilisant mobile money.
        Cet endpoint est public et ne nécessite pas d'authentification.
        
        **⚠️ Endpoint public :** Aucune authentification requise
        
        **Fonctionnalités :**
        - Vérifie que le lien est actif et non expiré
        - Vérifie le montant si le lien a un montant fixe
        - Vérifie la devise si le lien a une devise fixe
        - Intègre avec YellowCard OnRamp pour le paiement mobile money
        - Crédite le wallet du créateur
        - Crée les opérations et transactions
        - Met à jour les statistiques du lien
        
        **Flux de paiement (5 étapes) :**
        1. **Étape 1 - Pays** : L'utilisateur sélectionne le pays (GET /web/countries)
           ➡️ Les moyens de paiement s'affichent automatiquement (GET /web/payment-methods/{countryCode})
        2. **Étape 2 - Moyen de paiement** : L'utilisateur choisit le moyen de paiement (Wave, Orange, MTN, Moov, Bank, Card, etc.)
        3. **Étape 3 - Informations utilisateur** (optionnel) : L'utilisateur peut renseigner son nom et email
        4. **Étape 4 - Montant** : L'utilisateur saisit le montant à payer
        5. **Étape 5 - Validation du paiement** :
           - **🟢 Mobile Money (Afrique)** : L'utilisateur saisit son numéro de téléphone, puis clique sur "Payer"
           - **🔵 Carte/Virement (Europe/Diaspora)** : L'utilisateur est redirigé vers le partenaire (Guardarian, etc.)
        6. **Initiation du paiement** : Cet endpoint initie le paiement et retourne un `redirectUrl`
        7. **Redirection** : L'utilisateur est redirigé vers YellowCard (mobile money) ou Guardarian (carte/virement)
        8. **Confirmation** : Après le paiement, le partenaire redirige vers l'URL de retour (webhook à implémenter)
        
        **Exemple d'usage :**
        ```bash
        POST /api/internal/v1/payment-links/web/payment
        Content-Type: application/json
        
        {
          "uniqueCode": "gn1mb",
          "amount": 5000.0,
          "currency": "XOF",
          "phoneNumber": "+2250700123456",
          "countryCode": "CI",
          "paymentMethodId": "37b63794-284b-4a09-863b-9b74a3f621e1",
          "paymentMethodType": "momo",
          "payerName": "Jean Dupont",
          "payerEmail": "jean.dupont@example.com",
          "note": "Paiement facture électricité"
        }
        ```
        """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Détails du paiement web à effectuer",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = WebPaymentRequest.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Paiement avec montant libre",
                                            value = """
                                            {
                                              "uniqueCode": "gn1mb",
                                              "amount": 5000.0,
                                              "currency": "XOF",
                                              "phoneNumber": "+2250700123456",
                                              "countryCode": "CI",
                                              "paymentMethodId": "37b63794-284b-4a09-863b-9b74a3f621e1",
                                              "paymentMethodType": "momo",
                                              "payerName": "Jean Dupont",
                                              "payerEmail": "jean.dupont@example.com",
                                              "note": "Paiement facture électricité"
                                            }
                                            """
                                    ),
                                    @ExampleObject(
                                            name = "Paiement minimal",
                                            value = """
                                            {
                                              "uniqueCode": "abc12",
                                              "amount": 10000.0,
                                              "currency": "XOF",
                                              "phoneNumber": "+2250700999999",
                                              "countryCode": "CI",
                                              "paymentMethodId": "37b63794-284b-4a09-863b-9b74a3f621e1",
                                              "paymentMethodType": "momo"
                                            }
                                            """
                                    )
                            }
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Paiement initié avec succès - Redirection vers YellowCard",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "success": true,
                                      "message": "Payment initiated successfully",
                                      "redirectUrl": "https://yellowcard.io/payment/...",
                                      "transactionId": "tx_1234567890",
                                      "amount": 5000.0,
                                      "currency": "XOF"
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Requête invalide (montant incorrect, devise incorrecte, lien inactif)"),
            @ApiResponse(responseCode = "404", description = "Lien de paiement ou wallet du créateur non trouvé"),
            @ApiResponse(responseCode = "410", description = "Lien de paiement expiré"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<String> processWebPayment(@RequestBody @Valid WebPaymentRequest request) {
        log.info("Request to process web payment for link: {} by phone: {}", request.getUniqueCode(), request.getPhoneNumber());
        return paymentLinkService.processWebPayment(request);
    }

    @GetMapping("/web/countries")
    @SecurityRequirements // Endpoint public, pas d'authentification requise
    @Operation(
            operationId = "getCountriesForWeb",
            summary = "Récupérer la liste des pays disponibles (étape 1)",
            description = """
        Récupère la liste de tous les pays activés disponibles pour les paiements.
        Cet endpoint est public et ne nécessite pas d'authentification.
        
        **⚠️ Endpoint public :** Aucune authentification requise
        
        **Utilisé par :** L'interface web de paiement - Étape 1 : Sélection du pays
        
        **Exemple d'usage :**
        ```bash
        GET /api/internal/v1/payment-links/web/countries
        ```
        """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Liste des pays récupérée avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CountryResponseDto.class),
                            examples = @ExampleObject(
                                    value = """
                                    [
                                      {
                                        "id": 1,
                                        "countryCode": "CI",
                                        "countryName": "Côte d'Ivoire",
                                        "currencyCode": "XOF",
                                        "callingCode": 225,
                                        "capital": "Abidjan",
                                        "continentName": "Afrique"
                                      },
                                      {
                                        "id": 2,
                                        "countryCode": "SN",
                                        "countryName": "Sénégal",
                                        "currencyCode": "XOF",
                                        "callingCode": 221,
                                        "capital": "Dakar",
                                        "continentName": "Afrique"
                                      },
                                      {
                                        "id": 3,
                                        "countryCode": "FR",
                                        "countryName": "France",
                                        "currencyCode": "EUR",
                                        "callingCode": 33,
                                        "capital": "Paris",
                                        "continentName": "Europe"
                                      }
                                    ]
                                    """
                            )
                    )
            ),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<List<CountryResponseDto>> getCountriesForWeb() {
        log.info("Request to get countries for web payment");
        var countries = countryCurrencyRepository.findCountryCurrencyByIsActivated(true);
        var countryDtos = countries.stream()
                .map(country -> CountryResponseDto.builder()
                        .id(country.getId())
                        .countryCode(country.getCountryCode())
                        .countryName(country.getCountryName())
                        .currencyCode(country.getCurrencyCode())
                        .callingCode(country.getCallingCode())
                        .capital(country.getCapital())
                        .continentName(country.getContinentName())
                        .build())
                .toList();
        return ResponseEntity.ok(countryDtos);
    }

    @GetMapping("/web/payment-methods/{countryCode}")
    @SecurityRequirements // Endpoint public, pas d'authentification requise
    @Operation(
            operationId = "getPaymentMethodsForCountry",
            summary = "Récupérer les moyens de paiement disponibles pour un pays (étape 1 → étape 2)",
            description = """
        Récupère la liste de tous les moyens de paiement disponibles pour un pays donné.
        Les moyens de paiement s'affichent automatiquement après la sélection du pays.
        
        **⚠️ Endpoint public :** Aucune authentification requise
        
        **Utilisé par :** L'interface web de paiement - Étape 2 : Choix du moyen de paiement
        (appelé automatiquement après l'étape 1 : Sélection du pays)
        
        **Types de moyens de paiement retournés :**
        - **Mobile Money (momo)** : Wave, Orange Money, MTN, Moov, etc. (Afrique)
        - **Virement bancaire (bank)** : Pour paiements par virement
        - **Carte bancaire (card)** : Pour paiements par carte (Europe/Diaspora)
        
        **Note :** 
        - Les channels sont filtrés pour ne retourner que ceux avec `status = "active"`
        - La devise est automatiquement déterminée par le pays
        - Pour mobile money, le téléphone sera demandé à l'étape 5
        - Pour carte/virement, l'utilisateur sera redirigé vers le partenaire (Guardarian, etc.)
        
        **Exemple d'usage :**
        ```bash
        GET /api/internal/v1/payment-links/web/payment-methods/CI
        ```
        """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Liste des moyens de paiement récupérée avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OperatorResponseDto.class),
                            examples = @ExampleObject(
                                    value = """
                                    [
                                      {
                                        "id": "37b63794-284b-4a09-863b-9b74a3f621e1",
                                        "name": "Mobile Money",
                                        "channelType": "momo",
                                        "country": "CI",
                                        "currency": "XOF",
                                        "rampType": "INSTANT",
                                        "minAmount": 500.0,
                                        "maxAmount": 250000.0,
                                        "status": "active"
                                      },
                                      {
                                        "id": "fa21a1b1-3db4-4412-b733-f71cb3a23b4a",
                                        "name": "Bank Transfer",
                                        "channelType": "bank",
                                        "country": "CI",
                                        "currency": "XOF",
                                        "rampType": "MANUAL",
                                        "minAmount": 10.0,
                                        "maxAmount": 100000.0,
                                        "status": "active"
                                      }
                                    ]
                                    """
                            )
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Code pays invalide"),
            @ApiResponse(responseCode = "404", description = "Pays non trouvé"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<List<OperatorResponseDto>> getPaymentMethodsForCountry(
            @Parameter(description = "Code pays ISO (ex: CI, SN, ML, FR)", required = true, example = "CI")
            @PathVariable String countryCode) {
        log.info("Request to get payment methods (all channels) for country: {}", countryCode);
        
        try {
            // 1. Récupérer la devise du pays
            CountryCurrency countryCurrency = countryCurrencyRepository.findCountryCurrencyByCountryCode(countryCode);
            if (countryCurrency == null) {
                log.warn("Country not found: {}", countryCode);
                return ResponseEntity.notFound().build();
            }
            String currency = countryCurrency.getCurrencyCode();
            
            // 2. Récupérer les channels depuis YellowCard
            ResponseEntity<String> channelsResponse = yellowCardClientService.getChannels(countryCode);
            
            if (!channelsResponse.getStatusCode().is2xxSuccessful() || channelsResponse.getBody() == null) {
                log.warn("Failed to retrieve channels from YellowCard for country: {}", countryCode);
                return ResponseEntity.status(channelsResponse.getStatusCode()).build();
            }
            
            // 3. Parser la réponse JSON
            JsonNode rootNode = objectMapper.readTree(channelsResponse.getBody());
            JsonNode dataNode = rootNode.has("data") ? rootNode.get("data") : rootNode;
            
            if (!dataNode.isArray()) {
                log.warn("Unexpected response format from YellowCard channels API");
                return ResponseEntity.ok(List.of());
            }
            
            // 4. Filtrer et mapper TOUS les channels actifs (momo, bank, card, etc.)
            List<OperatorResponseDto> paymentMethods = new java.util.ArrayList<>();
            
            for (JsonNode channelNode : dataNode) {
                // Filtrer uniquement les channels actifs (tous types)
                String status = channelNode.has("status") 
                        ? channelNode.get("status").asText() 
                        : "";
                
                if ("active".equalsIgnoreCase(status)) {
                    String channelType = channelNode.has("channelType") 
                            ? channelNode.get("channelType").asText() 
                            : "";
                    
                    OperatorResponseDto paymentMethod = OperatorResponseDto.builder()
                            .id(channelNode.has("id") ? channelNode.get("id").asText() : null)
                            .name(channelNode.has("paymentMethod") 
                                    ? channelNode.get("paymentMethod").asText() 
                                    : channelNode.has("name") ? channelNode.get("name").asText() : "Payment Method")
                            .channelType(channelType)
                            .country(channelNode.has("country") ? channelNode.get("country").asText() : countryCode)
                            .currency(channelNode.has("currency") ? channelNode.get("currency").asText() : currency)
                            .rampType(channelNode.has("rampType") ? channelNode.get("rampType").asText() : null)
                            .minAmount(channelNode.has("minAmount") ? channelNode.get("minAmount").asDouble() : null)
                            .maxAmount(channelNode.has("maxAmount") ? channelNode.get("maxAmount").asDouble() : null)
                            .status(status)
                            .build();
                    paymentMethods.add(paymentMethod);
                }
            }
            
            log.info("Found {} active payment methods for country: {}", paymentMethods.size(), countryCode);
            return ResponseEntity.ok(paymentMethods);
            
        } catch (Exception e) {
            log.error("Error processing channels response for country: {}", countryCode, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

}

