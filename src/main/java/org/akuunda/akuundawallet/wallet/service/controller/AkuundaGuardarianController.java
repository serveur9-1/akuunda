package org.akuunda.akuundawallet.wallet.service.controller;

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
import org.akuunda.akuundawallet.wallet.api.requests.MtPelerinRequest;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaGuardarianClientService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping(path = AkuundaGuardarianController.API_SERVICES_ROOT,
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Akuunda Services - Guardarian", description = """
        API d'intégration avec le service Guardarian pour les opérations :
        - Création de transactions fiat ↔ crypto
        - Suivi des opérations
        - Estimations
        - Catégorisation des opérations
        """)
@SecurityRequirement(name = ApiConstants.SWAGGER_BASIC_SECURITY_SCHEME)
@RequiredArgsConstructor
@Validated
public class AkuundaGuardarianController {

    public static final String API_SERVICES_ROOT = "/api/internal/v1/guardarian";

    private final AkuundaGuardarianClientService guardarianClientService;

    // ================================
    // POST /transaction
    // ================================
    @PostMapping("/transaction/on-ramp/execute")
    @Operation(
            summary = "Créer une transaction on ramp",
            description = """
            Retourne un objet transaction contenant notamment un lien `redirect_url` pour finaliser le checkout.

            **Correspondances :**
            - pour recuperer la liste des categories de paiement : /api/internal/v1/guardarian/payment-categories
            """,
            responses = {
                    @ApiResponse(responseCode = "201", description = "Transaction créée",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = TransactionResponse.class),
                                    examples = @ExampleObject(value = """
                                            {
                                               "status": "success",
                                               "message": "Opération réussie",
                                               "data": {
                                                 "id": 2,
                                                 "redirect_url": "https://example.com/pay",
                                                 "username": "john_doe"
                                               }
                                             }
                                        """))),
                    @ApiResponse(responseCode = "400", description = "Requête invalide",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = TransactionResponse.class),
                                    examples = @ExampleObject(value = """
                                            {
                                               "status": "error",
                                               "message": "Utilisateur inexistant",
                                               "data": {
                                               }
                                             }
                                        """))),
                    @ApiResponse(responseCode = "500", description = "Requête invalide",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = TransactionResponse.class),
                                    examples = @ExampleObject(value = """
                                            {
                                               "status": "error",
                                               "message": "Erreur serveur",
                                               "data": {
                                               }
                                             }
                                        """))),
            }
    )
    public ResponseEntity<GuadarianResponse> createDeposit(@RequestBody @Valid TransactionRequest request) {
        return guardarianClientService.deposit(request);
    }

    // ================================
    // POST /transaction
    // ================================
    @PostMapping("/transaction/off-ramp/execute")
    @Operation(
            summary = "Créer une transaction off ramp",
            description = """
            Retourne un objet transaction contenant notamment un lien `redirect_url` pour finaliser le checkout.
            **Correspondances :**
            - pour recuperer la liste des categories de paiement : /api/internal/v1/guardarian/payment-categories
            """,
            responses = {
                    @ApiResponse(responseCode = "201", description = "Transaction créée",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = TransactionResponse.class),
                                    examples = @ExampleObject(value = """
                                            {
                                               "status": "success",
                                               "message": "Opération réussie",
                                               "data": {
                                                 "id": 2,
                                                 "redirect_url": "https://example.com/pay",
                                                 "username": "john_doe"
                                               }
                                             }
                                        """))),
                    @ApiResponse(responseCode = "400", description = "Requête invalide",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = TransactionResponse.class),
                                    examples = @ExampleObject(value = """
                                            {
                                               "status": "error",
                                               "message": "Utilisateur inexistant",
                                               "data": null
                                             }
                                        """))),
                    @ApiResponse(responseCode = "500", description = "Requête invalide",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = TransactionResponse.class),
                                    examples = @ExampleObject(value = """
                                            {
                                               "status": "error",
                                               "message": "Erreur serveur",
                                               "data": null
                                             }
                                        """))),
            }
    )
    public ResponseEntity<GuadarianResponse> createOffRamp(@RequestBody @Valid TransactionRequest request) {
        return guardarianClientService.withdraw(request);
    }

    // ================================
    // GET /payment-categories
    // ================================
    @GetMapping("/payment-categories")
    @Operation(
            summary = "Lister les catégories de paiement",
            description = "Retourne toutes les catégories de paiement supportées par Guardarian (SEPA, SWIFT, carte, crypto, etc.).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Liste des catégories de paiement",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = PaymentCategoryResponse[].class),
                                    examples = @ExampleObject(value = """
                                            [
                                              {"id":1,"name":"Crypto","category":"CRYPTO"},
                                              {"id":2,"name":"SEPA","category":"SEPA"},
                                              {"id":3,"name":"SWIFT","category":"SWIFT"},
                                              {"id":4,"name":"Visa/MasterCard","category":"VISA_MC"}
                                            ]
                                            """))),
                    @ApiResponse(responseCode = "500", description = "Erreur serveur")
            }
    )
    public ResponseEntity<List<PaymentCategoryResponse>> getPaymentCategories() {
        return guardarianClientService.getPaymentCategories();
    }

    // ================================
    // GET /transaction/{id}
    // ================================
    @GetMapping("/transaction/{id}")
    @Operation(
            summary = "Récupérer les détails d'une transaction Guardarian (format simplifié)",
            description = """
                    Récupère les informations d'une transaction Guardarian via son identifiant unique (ID numérique), au format simplifié.
                    
                    **Format de réponse simplifié :**
                    - Format compréhensible pour un utilisateur final
                    - Contient uniquement les données essentielles : id, status, date, amount, currency, operator
                    - Les montants XOF/XAF sont en centimes, les montants EUR sont en unités
                    - Le champ `operator` est toujours `"GUARDIARAN"` pour cet endpoint
                    
                    **Sécurité :**
                    - ⚠️ **IMPORTANT** : Seules les transactions appartenant à l'utilisateur spécifié sont retournées.
                    - Le système vérifie que la transaction appartient à l'utilisateur en comparant :
                      - Le `username` de l'opération en base de données
                      - Le `external_partner_link_id` dans la réponse Guardarian
                      - Le `username` stocké dans `GuardarianTransaction`
                    - Toute tentative d'accès à une transaction d'un autre utilisateur retourne un **403 FORBIDDEN**.
                    
                    **Endpoint Guardarian utilisé :**
                    - `GET https://api-payments.guardarian.com/v1/transaction/{id}`
                    
                    **Mapping des statuts :**
                    - `finished` → `completed`
                    - `pending` → `pending`
                    - `cancelled` / `failed` → `failed`
                    
                    **Format de l'ID :**
                    - Format : `AKU-YYYYMMDD-XXXX` (ex: `AKU-20260112-0123`)
                    - Basé sur la date de création et l'ID de la transaction
                    """,
            parameters = {
                    @io.swagger.v3.oas.annotations.Parameter(
                            name = "id",
                            description = "ID numérique de la transaction Guardarian (ex: 4368603367)",
                            required = true,
                            example = "4368603367"
                    ),
                    @io.swagger.v3.oas.annotations.Parameter(
                            name = "username",
                            description = "Nom d'utilisateur (phone number) pour vérifier la propriété de la transaction. " +
                                    "Doit correspondre au `external_partner_link_id` utilisé lors de la création de la transaction.",
                            required = true,
                            example = "0033612108828"
                    )
            },
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Transaction trouvée et appartenant à l'utilisateur",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = SimpleTransactionResponse.class),
                                    examples = {
                                            @ExampleObject(
                                                    name = "Transaction XOF (montant en centimes)",
                                                    description = "Les montants XOF sont retournés en centimes (10000 = 100.00 XOF)",
                                                    value = """
                                                            {
                                                              "id": "4368603367",
                                                              "status": "completed",
                                                              "date": "2026-01-12T14:05:00Z",
                                                              "amount": 10000,
                                                              "currency": "XOF",
                                                              "operator": "GUARDIARAN",
                                                              "type": "OFFRAMP"
                                                            }
                                                            """
                                            ),
                                            @ExampleObject(
                                                    name = "Transaction EUR (montant en unités)",
                                                    description = "Les montants EUR sont retournés en unités (92 = 92.00 EUR)",
                                                    value = """
                                                            {
                                                              "id": "4368603367",
                                                              "status": "completed",
                                                              "date": "2026-01-12T14:05:00Z",
                                                              "amount": 92,
                                                              "currency": "EUR",
                                                              "operator": "GUARDIARAN",
                                                              "type": "ONRAMP"
                                                            }
                                                            """
                                            )
                                    }
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Username manquant ou invalide",
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
                            responseCode = "403",
                            description = "Transaction n'appartient pas à l'utilisateur spécifié",
                            content = @Content(
                                    mediaType = "application/json",
                                    examples = @ExampleObject(value = """
                                            {
                                              "error": "Accès non autorisé",
                                              "message": "Cette transaction n'appartient pas à l'utilisateur"
                                            }
                                            """)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Transaction non trouvée (ni en base de données, ni dans l'API Guardarian)",
                            content = @Content(
                                    mediaType = "application/json",
                                    examples = @ExampleObject(value = """
                                            {
                                              "error": "Transaction non trouvée"
                                            }
                                            """)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Erreur serveur (erreur API Guardarian ou erreur interne)",
                            content = @Content(
                                    mediaType = "application/json",
                                    examples = @ExampleObject(value = """
                                            {
                                              "error": "Erreur serveur",
                                              "message": "Erreur lors de la récupération de la transaction"
                                            }
                                            """)
                            )
                    )
            }
    )
    public ResponseEntity<SimpleTransactionResponse> getTransactionById(
            @PathVariable("id") String id,
            @RequestParam("username") String username) {
        return guardarianClientService.getSimpleTransactionById(id, username);
    }

    // ------------------------------------------------------------------------
    // HISTORIQUE DES TRANSACTIONS (LISTE)
    // ------------------------------------------------------------------------
    @GetMapping("/transactions")
    @Operation(
            summary = "Récupérer l'historique des transactions Guardarian (format simplifié)",
            description = """
                    Récupère l'historique des transactions Guardarian pour un utilisateur, au format simplifié.
                    Les transactions sont récupérées depuis la base de données filtrées par username.
                    
                    **Format de réponse simplifié :**
                    - Format compréhensible pour un utilisateur final
                    - Contient uniquement les données essentielles : id, status, date, amount, currency
                    - Les montants XOF/XAF sont en centimes, les montants EUR sont en unités
                    
                    **Sécurité :**
                    - ⚠️ **IMPORTANT** : Seules les transactions appartenant à l'utilisateur spécifié sont retournées.
                    - Le système filtre les transactions en vérifiant que le `username` de la transaction correspond.
                    - Toute transaction appartenant à un autre utilisateur est exclue des résultats.
                    
                    **Mapping des statuts :**
                    - `finished` → `completed`
                    - `pending` → `pending`
                    - `cancelled` / `failed` → `failed`
                    
                    **Format de l'ID :**
                    - Format : `AKU-YYYYMMDD-XXXX` (ex: `AKU-20260112-0123`)
                    - Basé sur la date de création et l'ID de la transaction
                    """,
            parameters = {
                    @Parameter(
                            name = "username",
                            description = "Nom d'utilisateur (phone number) pour filtrer les transactions. " +
                                    "Seules les transactions appartenant à cet utilisateur sont retournées.",
                            required = true,
                            example = "0033612108828"
                    ),
                    @Parameter(
                            name = "fromDate",
                            description = "Date de début (format ISO: yyyy-MM-ddTHH:mm:ss, optionnel)",
                            required = false,
                            example = "2025-12-01T00:00:00"
                    ),
                    @Parameter(
                            name = "toDate",
                            description = "Date de fin (format ISO: yyyy-MM-ddTHH:mm:ss, optionnel)",
                            required = false,
                            example = "2026-01-01T23:59:59"
                    ),
                    @Parameter(
                            name = "skip",
                            description = "Nombre de transactions à ignorer (pagination, optionnel)",
                            required = false,
                            example = "0"
                    ),
                    @Parameter(
                            name = "limit",
                            description = "Nombre maximum de transactions à retourner (pagination, optionnel, défaut: 100)",
                            required = false,
                            example = "100"
                    )
            }
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Historique récupéré avec succès au format simplifié",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = SimpleTransactionResponse.class, type = "array"),
                            examples = {
                                    @ExampleObject(
                                            name = "Transactions EUR (montants en unités)",
                                            description = "Les montants EUR sont retournés en unités (40 = 40.00 EUR)",
                                            value = """
                                                    [
                                                      {
                                                        "id": "4368603367",
                                                        "status": "completed",
                                                        "date": "2025-12-26T18:57:03Z",
                                                        "amount": 40,
                                                        "currency": "EUR",
                                                        "operator": "GUARDIARAN",
                                                        "type": "ONRAMP"
                                                      },
                                                      {
                                                        "id": "4368603368",
                                                        "status": "pending",
                                                        "date": "2025-12-26T19:30:00Z",
                                                        "amount": 50,
                                                        "currency": "EUR",
                                                        "operator": "GUARDIARAN",
                                                        "type": "OFFRAMP"
                                                      }
                                                    ]
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Transactions XOF (montants en centimes)",
                                            description = "Les montants XOF sont retournés en centimes (10000 = 100.00 XOF)",
                                            value = """
                                                    [
                                                      {
                                                        "id": "4368603369",
                                                        "status": "completed",
                                                        "date": "2025-12-26T20:00:00Z",
                                                        "amount": 10000,
                                                        "currency": "XOF",
                                                        "operator": "GUARDIARAN",
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
                    description = "Requête invalide (username manquant)",
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
                    description = "Erreur interne du serveur",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                      "error": "Erreur serveur",
                                      "message": "Erreur lors de la récupération de l'historique"
                                    }
                                    """)
                    )
            )
    })
    public ResponseEntity<List<SimpleTransactionResponse>> getTransactions(
            @RequestParam(required = true) String username,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(required = false, defaultValue = "0") Integer skip,
            @RequestParam(required = false, defaultValue = "100") Integer limit) {
        log.info("Getting Guardarian transactions history for user: {} (from: {}, to: {}, skip: {}, limit: {})", 
                username, fromDate, toDate, skip, limit);
        return guardarianClientService.getSimpleTransactions(username, fromDate, toDate, skip, limit);
    }

}
