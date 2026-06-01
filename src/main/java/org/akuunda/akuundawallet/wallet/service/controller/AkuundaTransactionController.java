package org.akuunda.akuundawallet.wallet.service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.constants.ApiConstants;
import org.akuunda.akuundawallet.wallet.api.dto.TransactionStatusRequest;
import org.akuunda.akuundawallet.wallet.api.dto.external.*;
import org.akuunda.akuundawallet.wallet.api.requests.MtPelerinRequest;
import org.akuunda.akuundawallet.wallet.service.AkuundaTransactionService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaGuardarianClientService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping(path = AkuundaTransactionController.API_SERVICES_ROOT, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(
        name = "Akuunda Services - Transactions Status",
        description = """
                API complète pour avoir le statut des transactions effectuées via divers prestataires de services (Guardarian, MT Pelerin, Yellow Card, etc.).\s:
                - Vérification du statut des transactions en fonction de l'ID et du type de transaction.
                """
)
@SecurityRequirement(name = ApiConstants.SWAGGER_BASIC_SECURITY_SCHEME)
@RequiredArgsConstructor
public class AkuundaTransactionController {

    public static final String API_SERVICES_ROOT = "/api/internal/v1/transactions";

    private final AkuundaTransactionService transactionService;
    private final AkuundaGuardarianClientService guardarianClientService;

    // recuperer le status de toutes les transactions guardarian, mt pelerin, Yellow card etc..
    // en fontion de l'id de la transaction et du type de transaction ( MTPELERIN, YELLOWCARD, GUARDIARAN, etc..)

    @PostMapping(path = "/check", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Vérifier le statut d'une transaction",
            description = """
                    Permet de vérifier le statut d'une transaction en fonction de l'ID et du type de transaction.
                    
                    **Types de transactions supportés :**
                    - `MTPELERIN` : Transactions MT Pelerin (onramp/offramp)
                    - `GUARDIARAN` : Transactions Guardarian (onramp/offramp)
                    - `YELLOWCARD` : Transactions YellowCard (onramp/offramp)
                    
                    **Sécurité :**
                    - ⚠️ **IMPORTANT** : Le système vérifie que la transaction appartient à l'utilisateur spécifié.
                    - Pour Guardarian : vérifie le `username` et le `external_partner_link_id`.
                    - Pour MT Pelerin : vérifie le `username` et le `merchant_oid`.
                    - Toute tentative d'accès non autorisé retourne un **403 FORBIDDEN**.
                    
                    **Note :** Cette méthode est un wrapper qui redirige vers le service approprié selon le type de transaction.
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Requête contenant les informations nécessaires pour vérifier le statut d'une transaction.",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = TransactionStatusRequest.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Transaction MT Pelerin",
                                    value = """
                                {
                                                      "username": "0033612108828",
                                                      "transactionId": "user123-uuid-456",
                                    "transactionType": "MTPELERIN"
                                }
                                """
                                    ),
                                    @ExampleObject(
                                            name = "Transaction Guardarian",
                                            value = """
                                                    {
                                                      "username": "0033612108828",
                                                      "transactionId": "4368603367",
                                                      "transactionType": "GUARDIARAN"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Transaction YellowCard",
                                            value = """
                                                    {
                                                      "username": "0033612108828",
                                                      "transactionId": "yellowcard-tx-123",
                                                      "transactionType": "YELLOWCARD"
                                                    }
                                                    """
                                    )
                            }
                    )
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Statut de la transaction récupéré avec succès",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = TransactionStatusResponse.class),
                                    examples = {
                                            @ExampleObject(
                                                    name = "Transaction terminée (Guardarian)",
                                                    value = """
                                                            {
                                                              "transactionId": "4368603367",
                                                              "status": "finished",
                                                              "username": "0033612108828"
                                                            }
                                                            """
                                            ),
                                            @ExampleObject(
                                                    name = "Transaction en attente (MT Pelerin)",
                                                    value = """
                                                            {
                                                              "transactionId": "user123-uuid-456",
                                                              "status": "pending",
                                                              "username": "0033612108828"
                                                            }
                                                            """
                                            ),
                                            @ExampleObject(
                                                    name = "Transaction complétée (YellowCard)",
                                            value = """
                                        {
                                                              "transactionId": "yellowcard-tx-123",
                                            "status": "COMPLETED",
                                                              "username": "0033612108828"
                                        }
                                        """
                                    )
                                    }
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Requête invalide (champs manquants ou invalides)",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            name = "Exemple de réponse 400",
                                            value = """
                                        {
                                            "error": "Requête invalide",
                                            "message": "Le type de transaction est manquant ou invalide."
                                        }
                                        """
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "403",
                            description = "Transaction n'appartient pas à l'utilisateur",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                      "error": "Accès non autorisé",
                                                      "message": "Cette transaction n'appartient pas à l'utilisateur"
                                                    }
                                                    """
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "404",
                            description = "Transaction non trouvée ou type de transaction non supporté",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                      "error": "Transaction non trouvée",
                                                      "message": "Aucune transaction trouvée avec cet ID et ce type"
                                                    }
                                                    """
                                    )
                            )
                    )
            }
    )
    public ResponseEntity<TransactionStatusResponse> checkTransactionStatus(
            @RequestBody @Valid TransactionStatusRequest request) {
        log.info("Requête reçue pour vérifier le statut de la transaction : {}", request);
        return transactionService.checkTransactionStatus(
                request.getUsername(),
                request.getTransactionId(),
                request.getTransactionType()
        );
    }

    @PostMapping("/transfert/execute")
    @Operation(
            summary = "Exécuter un transfert entre deux comptes utilisateurs",
            description = """
                Permet d'exécuter un transfert entre deux comptes utilisateurs. 
                Cette méthode valide les données d'entrée, vérifie les utilisateurs et leurs portefeuilles, 
                effectue la conversion de devise si nécessaire, et exécute le transfert via un service externe.

                **Sécurité :**
                - ⚠️ **IMPORTANT** : Le système vérifie que les utilisateurs et leurs portefeuilles existent avant d'exécuter le transfert.
                - Toute tentative de transfert non valide retourne une erreur.

                **Étapes principales :**
                - Validation des données d'entrée
                - Vérification des utilisateurs et portefeuilles
                - Conversion de devise (si nécessaire)
                - Verifier si le montant est suffisant pour le transfert
                - Exécution du transfert via un service externe
                - Enregistrement des opérations de débit et crédit
                """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Requête JSON contenant les informations nécessaires pour exécuter le transfert",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = AkuundaTransfertRequest.class),
                            examples = @ExampleObject(
                                    name = "Exemple de requête",
                                    value = """
                                {
                                  "sourceUsername": "user1",
                                  "cibleUsername": "user2",
                                  "amount": 100.0,
                                  "devise": "XOF",
                                  "headerPin": "1234"
                                }
                                """
                            )
                    )
            ),
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Transfert exécuté avec succès",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                        {
                                          "status": "success",
                                          "message": "Transfert exécuté avec succès"
                                        }
                                        """
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Requête invalide (champs manquants ou invalides)",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                        {
                                          "status": "error",
                                          "message": "Requête invalide : champs manquants ou invalides"
                                        }
                                        """
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Erreur serveur lors de l'exécution du transfert",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    examples = @ExampleObject(
                                            value = """
                                        {
                                          "status": "error",
                                          "message": "Erreur serveur lors de l'exécution du transfert"
                                        }
                                        """
                                    )
                            )
                    )
            }
    )
    public ResponseEntity<String> executeTransfertCpteACpte(@RequestBody @Valid AkuundaTransfertRequest request) {
        log.info("Requête reçue pour exécuter un transfert : {}", request);
        return transactionService.executeTransfertCpteACpte(request);
    }


    // ================================
    // POST /transaction
    // ================================
    @PostMapping("/paiement/execute")
    @Operation(
            summary = "Suite du retrait - Exécuter le paiement",
            description = "Retourne le status de paiement apres avoir executé operation.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Requête JSON contenant les informations de paiement",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = MtPelerinRequest.class),
                            examples = @ExampleObject(
                                    name = "JSON Request Example",
                                    value = """
                                    {
                                      "username": "xxxxxxxxxxxxxx",
                                      "transactionId": "xxxxxx-9c32-4a13-xxxxx-xxxxx",
                                      "headerPin": "1234",
                                      "walletAddress": "xxxxxxxxxxxxxxxxxxxxxxxxxxxx",
                                      "amount": 100.0
                                    }
                                    """
                            )
                    )
            ),
            responses = {
                    @ApiResponse(responseCode = "200", description = "Paiement effectué",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = GuardiaranPaiement.class),
                                    examples = @ExampleObject(value = """
                                        {
                                               "status": "success",
                                               "message": "Opération réussie"
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
    public ResponseEntity<GuadarianResponse> paiement(@RequestBody @Valid GuardiaranPaiement request) {
        return guardarianClientService.paiement(request);
    }

    // ------------------------------------------------------------------------
    // GET /transactions - Historique unifié de toutes les transactions
    // ------------------------------------------------------------------------
    @GetMapping("/transactions")
    @Operation(
            summary = "Récupérer l'historique unifié de toutes les transactions (MT Pelerin, YellowCard, Guardarian)",
            description = """
                    Récupère toutes les transactions de tous les opérateurs (MT Pelerin, YellowCard, Guardarian) 
                    pour un utilisateur donné, dans un format unifié et simplifié.
                    
                    **Format de réponse simplifié :**
                    - Format compréhensible pour un utilisateur final
                    - Contient uniquement les données essentielles : id, status, date, amount, currency
                    - Les montants XOF/XAF sont en centimes, les montants EUR sont en unités
                    - Les transactions sont triées par date (plus récent en premier)
                    
                    **Opérateurs inclus :**
                    - MT Pelerin (OnRamp/OffRamp)
                    - YellowCard (OnRamp/OffRamp)
                    - Guardarian (OnRamp/OffRamp)
                    
                    **Sécurité :**
                    - ⚠️ **IMPORTANT** : Seules les transactions appartenant à l'utilisateur spécifié sont retournées.
                    - Chaque opérateur vérifie l'appartenance de la transaction à l'utilisateur.
                    - Toute transaction appartenant à un autre utilisateur est exclue des résultats.
                    
                    **Format de l'ID :**
                    - Chaque opérateur utilise son ID réel :
                      - MT Pelerin : `merchant_oid` (ex: `0033612108828-27301bc3-7715-4966-b9a3-6aadb39e9dd8`)
                      - YellowCard : `sequenceId` (ex: `akuunda-collection-20260112-140504`)
                      - Guardarian : `externalTransactionId` (ex: `4368603367`)
                    
                    **Champ operator :**
                    - Chaque transaction inclut le champ `operator` pour identifier l'opérateur
                    - Valeurs possibles : `"MTPELERIN"`, `"YELLOWCARD"`, `"GUARDIARAN"`
                    
                    **Champ type :**
                    - Chaque transaction inclut le champ `type` pour identifier le type de transaction
                    - Valeurs possibles : `"ONRAMP"` (dépôt fiat vers crypto), `"OFFRAMP"` (retrait crypto vers fiat)
                    
                    **Filtrage des devises :**
                    - ⚠️ **IMPORTANT** : Seules les transactions en devises fiat sont retournées (XOF, EUR, XAF, etc.)
                    - Les transactions en crypto-monnaies (USDC, USDT, BTC, ETH, etc.) sont exclues
                    
                    **Pagination :**
                    - Les transactions de tous les opérateurs sont combinées puis triées par date
                    - La pagination est appliquée sur l'ensemble combiné
                    """,
            parameters = {
                    @io.swagger.v3.oas.annotations.Parameter(
                            name = "username",
                            description = "Nom d'utilisateur (obligatoire)",
                            required = true,
                            example = "0033612108828"
                    ),
                    @io.swagger.v3.oas.annotations.Parameter(
                            name = "fromDate",
                            description = "Date de début (format ISO: yyyy-MM-ddTHH:mm:ss, optionnel)",
                            required = false,
                            example = "2025-12-01T00:00:00"
                    ),
                    @io.swagger.v3.oas.annotations.Parameter(
                            name = "toDate",
                            description = "Date de fin (format ISO: yyyy-MM-ddTHH:mm:ss, optionnel)",
                            required = false,
                            example = "2026-01-01T23:59:59"
                    ),
                    @io.swagger.v3.oas.annotations.Parameter(
                            name = "skip",
                            description = "Nombre de transactions à ignorer pour la pagination (optionnel, défaut: 0)",
                            required = false,
                            example = "0"
                    ),
                    @io.swagger.v3.oas.annotations.Parameter(
                            name = "limit",
                            description = "Nombre maximum de transactions à retourner (optionnel, défaut: 100)",
                            required = false,
                            example = "100"
                    )
            },
            responses = {
                    @ApiResponse(
                    responseCode = "200",
                            description = "Historique des transactions récupéré avec succès",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = TransactionHistoryResponse.class),
                                    examples = {
                                            @ExampleObject(
                                                    name = "Exemple de réponse",
                                                    value = """
                                                    {
                                                      "status": "success",
                                                      "message": "Transactions récupérées avec succès",
                                                      "data": [
                                                        {
                                                          "id": "akuunda-collection-20260112-140504",
                                                          "status": "completed",
                                                          "date": "2026-01-12T14:05:00Z",
                                                          "amount": 10000,
                                                          "currency": "XOF",
                                                          "operator": "YELLOWCARD",
                                                          "type": "ONRAMP"
                                                        },
                                                        {
                                                          "id": "0033612108828-27301bc3-7715-4966-b9a3-6aadb39e9dd8",
                                                          "status": "pending",
                                                          "date": "2026-01-12T15:30:00Z",
                                                          "amount": 5000,
                                                          "currency": "XOF",
                                                          "operator": "MTPELERIN",
                                                          "type": "OFFRAMP"
                                                        },
                                                        {
                                                          "id": "4368603367",
                                                          "status": "completed",
                                                          "date": "2026-01-11T10:20:00Z",
                                                          "amount": 50,
                                                          "currency": "EUR",
                                                          "operator": "GUARDIARAN",
                                                          "type": "ONRAMP"
                                                        }
                                                      ]
                                                    }
                                                    """
                                            )
                                    }
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Requête invalide (username manquant)",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = TransactionHistoryResponse.class),
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                      "status": "error",
                                                      "message": "Username manquant",
                                                      "data": null
                                                    }
                                                    """
                                    )
                            )
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Erreur serveur lors de la récupération de l'historique",
                            content = @Content(
                                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                                    schema = @Schema(implementation = TransactionHistoryResponse.class),
                                    examples = @ExampleObject(
                                            value = """
                                                    {
                                                      "status": "error",
                                                      "message": "Erreur lors de la récupération de l'historique des transactions",
                                                      "data": null
                                                    }
                                                    """
                                    )
                            )
                    )
            }
    )
    public ResponseEntity<TransactionHistoryResponse> getAllTransactions(
            @RequestParam(required = true) String username,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime toDate,
            @RequestParam(required = false, defaultValue = "0") Integer skip,
            @RequestParam(required = false, defaultValue = "100") Integer limit) {
        log.info("Récupération de l'historique unifié pour utilisateur: {} (from: {}, to: {}, skip: {}, limit: {})", 
                username, fromDate, toDate, skip, limit);
        return transactionService.getAllTransactions(username, fromDate, toDate, skip, limit);
    }

}
