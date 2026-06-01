package org.akuunda.akuundawallet.wallet.service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.constants.ApiConstants;
import jakarta.validation.Valid;
import org.akuunda.akuundawallet.wallet.api.dto.external.OffRampRequest;
import org.akuunda.akuundawallet.wallet.api.dto.external.OnRampRequest;
import org.akuunda.akuundawallet.wallet.api.dto.external.SimpleTransactionResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.YellowCardWidgetQuoteRequest;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaYellowCardClientService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping(path = AkuundaYellowCardController.API_SERVICES_ROOT, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Akuunda Services - Yellow Card", description = """
        API d'intégration avec le service Yellow Card pour les opérations **On-Ramp** et **Off-Ramp** :
        - Création de transactions fiat ↔ crypto
        - Récupération des canaux (channels), taux (rates) et réseaux (networks)
        """)
@SecurityRequirement(name = ApiConstants.SWAGGER_BASIC_SECURITY_SCHEME)
@RequiredArgsConstructor
public class AkuundaYellowCardController {

    public static final String API_SERVICES_ROOT = "/api/internal/v1/yellow-card";

    private final AkuundaYellowCardClientService akuundaYellowCardService;

    @GetMapping("/transactions/status/{sequenceId}")
    @Operation(
            operationId = "checkTransactionStatus",
            summary = "Vérifier le statut d'une transaction YellowCard (format simplifié)",
            description = """
                    Permet de vérifier le statut d'une transaction YellowCard via son identifiant unique (sequenceId), au format simplifié.
                    
                    **Format de réponse simplifié :**
                    - Format compréhensible pour un utilisateur final
                    - Contient uniquement les données essentielles : id, status, date, amount, currency, operator
                    - Les montants XOF/XAF sont en centimes, les montants EUR sont en unités
                    - Le champ `operator` est toujours `"YELLOWCARD"` pour cet endpoint
                    
                    **Sécurité :**
                    - ⚠️ **IMPORTANT** : Seules les transactions appartenant à l'utilisateur spécifié sont retournées.
                    - Le système vérifie que la transaction appartient à l'utilisateur en comparant :
                      - Le `username` de l'opération en base de données
                      - Le `phone` dans la réponse YellowCard (`sender.phone` pour OffRamp, `recipient.phone` pour OnRamp)
                    - Toute tentative d'accès à une transaction d'un autre utilisateur retourne un **403 FORBIDDEN**.
                    
                    **Endpoints YellowCard utilisés :**
                    - OnRamp (credit) : `GET https://api.yellowcard.io/business/collections/sequence-id/{sequenceId}`
                    - OffRamp (debit) : `GET https://api.yellowcard.io/business/payments/sequence-id/{sequenceId}`
                    
                    **Mapping des statuts :**
                    - `complete` / `completed` → `completed`
                    - `pending` / `processing` → `pending`
                    - `failed` / `cancelled` → `failed`
                    
                    **Format de l'ID :**
                    - Format : `AKU-YYYYMMDD-XXXX` (ex: `AKU-20260112-0123`)
                    - Basé sur la date de création et le sequenceId
                    """,
            parameters = {
                    @Parameter(
                            name = "sequenceId",
                            description = "Identifiant unique de la transaction YellowCard (ex: akuunda-collection-20260112-140504)",
                            required = true,
                            example = "akuunda-collection-20260112-140504"
                    ),
                    @Parameter(
                            name = "username",
                            description = "Nom d'utilisateur (phone number) pour vérifier la propriété de la transaction. " +
                                    "Doit correspondre au `phone` dans la réponse YellowCard.",
                            required = true,
                            example = "0033612108828"
                    )
            }
    )
    @ApiResponses({
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
                                                      "id": "akuunda-collection-20260112-140504",
                                                      "status": "completed",
                                                      "date": "2026-01-12T14:05:00Z",
                                                      "amount": 10000,
                                                      "currency": "XOF",
                                                      "operator": "YELLOWCARD",
                                                      "type": "ONRAMP"
                                                    }
                                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Transaction EUR (montant en unités)",
                                            description = "Les montants EUR sont retournés en unités (92 = 92.00 EUR)",
                                            value = """
                                                    {
                                                      "id": "akuunda-collection-20260112-140504",
                                                      "status": "completed",
                                                      "date": "2026-01-12T14:05:00Z",
                                                      "amount": 92,
                                                      "currency": "EUR",
                                                      "operator": "YELLOWCARD",
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
                    description = "Transaction non trouvée (ni en base de données, ni dans l'API YellowCard)",
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
                    description = "Erreur serveur (erreur API YellowCard ou erreur interne)",
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
    })
    public ResponseEntity<SimpleTransactionResponse> checkTransactionStatus(
            @PathVariable String sequenceId,
            @RequestParam("username") String username) {
        log.info("Requête reçue pour vérifier le statut de la transaction YellowCard : {} pour utilisateur : {}", sequenceId, username);
        return akuundaYellowCardService.getSimpleTransactionStatus(sequenceId, username);
    }

    // ------------------------------------------------------------------------
    // HISTORIQUE DES TRANSACTIONS (LISTE)
    // ------------------------------------------------------------------------
    @GetMapping("/transactions")
    @Operation(
            summary = "Récupérer l'historique des transactions YellowCard (format simplifié)",
            description = """
                    Récupère l'historique des transactions YellowCard pour un utilisateur, au format simplifié.
                    Les transactions sont récupérées depuis la base de données et enrichies avec les détails de l'API YellowCard.
                    
                    **Format de réponse simplifié :**
                    - Format compréhensible pour un utilisateur final
                    - Contient uniquement les données essentielles : id, status, date, amount, currency
                    - Les montants XOF/XAF sont en centimes, les montants EUR sont en unités
                    
                    **Sécurité :**
                    - ⚠️ **IMPORTANT** : Seules les transactions appartenant à l'utilisateur spécifié sont retournées.
                    - Le système filtre les transactions en vérifiant que le `username` de l'opération correspond.
                    - Toute transaction appartenant à un autre utilisateur est exclue des résultats.
                    
                    **Mapping des statuts :**
                    - `complete` / `completed` → `completed`
                    - `pending` / `processing` → `pending`
                    - `failed` / `cancelled` → `failed`
                    
                    **Format de l'ID :**
                    - Format : `AKU-YYYYMMDD-XXXX` (ex: `AKU-20260112-0123`)
                    - Basé sur la date de création et le sequenceId
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
                                            name = "Transactions XOF (montants en centimes)",
                                            description = "Les montants XOF sont retournés en centimes (10000 = 100.00 XOF)",
                                            value = """
                                                    [
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
                                                        "id": "akuunda-payment-20260112-153000",
                                                        "status": "pending",
                                                        "date": "2026-01-12T15:30:00Z",
                                                        "amount": 5000,
                                                        "currency": "XOF",
                                                        "operator": "YELLOWCARD",
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
                                                        "id": "akuunda-collection-20260112-164500",
                                                        "status": "completed",
                                                        "date": "2026-01-12T16:45:00Z",
                                                        "amount": 92,
                                                        "currency": "EUR",
                                                        "operator": "YELLOWCARD",
                                                        "type": "ONRAMP"
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
        log.info("Getting YellowCard transactions history for user: {} (from: {}, to: {}, skip: {}, limit: {})", 
                username, fromDate, toDate, skip, limit);
        return akuundaYellowCardService.getSimpleTransactions(username, fromDate, toDate, skip, limit);
    }

    // ------------------------------------------------------------------------
    // ON-RAMP
    // ------------------------------------------------------------------------
    @PostMapping("/on-ramp/create-collection")
    @Operation(
            operationId = "createOnRampCollection",
            summary = "Créer une collection On-Ramp Yellow Card",
            description = """
        Crée une nouvelle transaction **On-Ramp (fiat → crypto)** via Yellow Card.

        **Fonctionnalités :**
        - Permet aux utilisateurs de convertir des devises fiat en crypto-monnaies.
        - Retourne un objet de collection contenant les détails de la transaction et un identifiant unique.
        - Peut être utilisé pour initier une transaction d'achat crypto à partir d'une application front-end.

        **Paramètres requis (selon spécifications YellowCard) :**
        
        **Ordre des champs :** recipient, source, amount, currency, country, reason, sequenceId, channelId, customerUID, customerType, forceAccept, directSettlement, rampType, redirectUrl, settlementInfo
        
        - `recipient` (object) — Détails du bénéficiaire (ordre : name, country, phone, address, email, dob, idNumber, idType, additionalIdType, additionalIdNumber) :
          - `name` (string) — Nom complet
          - `country` (string) — Code pays ISO Alpha-2 (ex: "CI")
          - `phone` (string) — Numéro de téléphone
          - `address` (string) — Adresse
          - `email` (string) — Email
          - `dob` (string) — Date de naissance (format MM/dd/yyyy)
          - `idNumber` (string) — Numéro de pièce d'identité
          - `idType` (string) — Type de pièce d'identité (ex: "passport", "national_id")
          - `additionalIdType` (string, optionnel) — Type de pièce d'identité supplémentaire
          - `additionalIdNumber` (string, optionnel) — Numéro de pièce d'identité supplémentaire
        - `source` (object) — Informations du compte source (ordre : accountNumber, accountType, networkId, accountName, phoneNumber) :
          - `accountNumber` (string) — Numéro de compte
          - `accountType` (string) — Type de compte (ex: "momo", "bank")
          - `networkId` (string) — Identifiant du réseau
          - `accountName` (string) — Nom du compte
          - `phoneNumber` (string) — Numéro de téléphone (utilise accountNumber si non fourni)
        - `amount` (number) — Montant à convertir
        - `currency` (string) — Devise d'origine (ex: "XOF")
        - `country` (string) — Code pays ISO Alpha-2 (ex: "CI")
        - `reason` (string) — Raison de la transaction (ex: "other")
        - `sequenceId` (string) — Identifiant unique de la transaction (généré automatiquement)
        - `channelId` (string) — Identifiant du canal de paiement
        - `customerUID` (string) — Identifiant unique du client (généré automatiquement)
        - `customerType` (string) — Type de client (ex: "retail", généré automatiquement)
        - `forceAccept` (boolean) — Force l'acceptation de la transaction
        - `directSettlement` (boolean) — Si le règlement doit être direct
        - `rampType` (string) — Type de transaction (ex: "deposit", généré automatiquement)
        - `redirectUrl` (string) — URL de redirection (généré automatiquement)
        - `settlementInfo` (object) — Détails de règlement crypto (ordre : cryptoCurrency, cryptoNetwork, walletAddress) :
          - `cryptoCurrency` (string) — Devise crypto (ex: "USDC", généré automatiquement)
          - `cryptoNetwork` (string) — Réseau crypto (ex: "POLYGON", généré automatiquement)
          - `walletAddress` (string) — Adresse du portefeuille crypto (généré automatiquement)

        **Exemple d’usage :**
        ```bash
        POST /api/internal/v1/yellow-card/on-ramp/create-collection
        ```
        """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Détails complets de la transaction On-Ramp à créer",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OnRampRequest.class),
                            examples = @ExampleObject(
                                    name = "OnRamp Example (selon spécifications YellowCard)",
                                    value = """
                    {
                      "recipient": {
                        "name": "Kouassi David",
                        "country": "CI",
                        "phone": "0033612108828",
                        "address": "Non spécifié",
                        "email": "amandavidk@yahoo.com",
                        "dob": "10/30/1997",
                        "idNumber": "25AA74989",
                        "idType": "passport",
                        "additionalIdType": "",
                        "additionalIdNumber": ""
                      },
                      "source": {
                        "accountNumber": "+2250709997042",
                        "accountType": "momo",
                        "networkId": "90d4d5ea-71a3-4490-8bb7-0f1e0dfa735b",
                        "accountName": "Aman David",
                        "phoneNumber": "+2250709997042"
                      },
                      "amount": 1000,
                      "currency": "XOF",
                      "country": "CI",
                      "reason": "other",
                      "sequenceId": "AKU_CI_1762121062058",
                      "channelId": "b621bf4f-0c60-4884-88a1-75f7a56b1938",
                      "customerUID": "01f8a941-2c88-46a3-a27d-1162911985b7",
                      "customerType": "retail",
                      "forceAccept": true,
                      "directSettlement": true,
                      "rampType": "deposit",
                      "redirectUrl": "https://akuunda-pay.io",
                      "settlementInfo": {
                        "cryptoCurrency": "USDC",
                        "cryptoNetwork": "POLYGON",
                        "walletAddress": "0xB6f7b717403B9d07b582a741a1689d1aAFF6957C"
                      }
                    }
                    """
                            )
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Collection créée avec succès"),
            @ApiResponse(responseCode = "201", description = "Collection créée avec succès"),
            @ApiResponse(responseCode = "400", description = "Requête invalide (montant < 1 USD, champs manquants)"),
            @ApiResponse(responseCode = "404", description = "Utilisateur ou portefeuille non trouvé"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur ou erreur API YellowCard")
    })
    public ResponseEntity<Object> createOnRampCollection(@RequestBody OnRampRequest onRampRequest) {
        log.info("Requête reçue pour créer une collection On-Ramp : {}", onRampRequest);
        return akuundaYellowCardService.createCollection(onRampRequest);
    }

    // ------------------------------------------------------------------------
    // OFF-RAMP
    // ------------------------------------------------------------------------
    @PostMapping("/off-ramp/paiements/{username}")
    @Operation(
            operationId = "createOffRampPaiements",
            summary = "Créer un paiement Off-Ramp Yellow Card",
            description = """
        Crée une transaction **Off-Ramp (crypto → fiat)** via Yellow Card.

        **Fonctionnalités :**
        - Permet aux utilisateurs de vendre leurs crypto-monnaies pour obtenir des devises fiat.
        - Nécessite un code PIN d’authentification via l’en-tête `HeaderPin`.
        - Retourne les détails de la transaction (statut, montant, bénéficiaire, etc.).

        **Paramètres requis (selon spécifications YellowCard) :**
        
        **Ordre des champs :** sender, destination, channelId, sequenceId, currency, country, reason, forceAccept, directSettlement, customerUID, customerType, settlementInfo
        
        - `username` (path) — Nom d'utilisateur effectuant la transaction.
        - `HeaderPin` (header) — Code PIN utilisateur pour valider la transaction.
        - `requestBody` (body) — Détails de la transaction Off-Ramp :
          - `sender` (object) — Informations sur l'expéditeur (ordre : name, country, address, dob, email, idNumber, idType, phone, additionalIdType, additionalIdNumber) :
            - `name` (string) — Nom complet
            - `country` (string) — Code pays ISO Alpha-2 (ex: "CI")
            - `address` (string) — Adresse
            - `dob` (string) — Date de naissance (format MM/dd/yyyy)
            - `email` (string) — Email
            - `idNumber` (string) — Numéro de pièce d'identité
            - `idType` (string) — Type de pièce d'identité (ex: "national_id", "passport")
            - `phone` (string) — Numéro de téléphone
            - `additionalIdType` (string, optionnel) — Type de pièce d'identité supplémentaire
            - `additionalIdNumber` (string, optionnel) — Numéro de pièce d'identité supplémentaire
          - `destination` (object) — Informations sur le compte destinataire (ordre : accountName, accountNumber, accountType, networkId) :
            - `accountName` (string) — Nom du compte
            - `accountNumber` (string) — Numéro de compte
            - `accountType` (string) — Type de compte (ex: "momo", "bank")
            - `networkId` (string) — Identifiant du réseau
          - `channelId` (string) — Identifiant du canal de paiement
          - `sequenceId` (string) — Identifiant unique de la transaction (généré automatiquement)
          - `currency` (string) — Devise fiat concernée (ex: "XOF")
          - `country` (string) — Code pays ISO Alpha-2 (ex: "CI")
          - `reason` (string) — Raison de la transaction (ex: "other")
          - `forceAccept` (boolean) — Force l'acceptation de la transaction
          - `directSettlement` (boolean) — Si le règlement doit être direct
          - `customerUID` (string) — Identifiant unique du client (généré automatiquement)
          - `customerType` (string) — Type de client (ex: "retail", généré automatiquement)
          - `settlementInfo` (object) — Détails de règlement crypto (ordre : cryptoCurrency, cryptoNetwork, cryptoAmount) :
            - `cryptoCurrency` (string) — Devise crypto (ex: "USDT", "USDC")
            - `cryptoNetwork` (string) — Réseau crypto (ex: "TRC20", "POLYGON")
            - `cryptoAmount` (number) — Montant en crypto-monnaie

        **Exemple d’usage :**
        ```bash
        POST /api/internal/v1/yellow-card/off-ramp/paiements/john_doe
        HeaderPin: 1234
        ```
        """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Détails complets de la transaction Off-Ramp",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = OffRampRequest.class),
                            examples = @ExampleObject(
                                    name = "OffRamp Example (selon spécifications YellowCard)",
                                    value = """
                        {
                          "sender": {
                            "name": "David Aman",
                            "country": "CI",
                            "address": "Abidjan, Cocody",
                            "dob": "01/01/1990",
                            "email": "david@akuunda-pay.io",
                            "idNumber": "",
                            "idType": "national_id",
                            "phone": "+2250700123456",
                            "additionalIdType": "",
                            "additionalIdNumber": ""
                          },
                          "destination": {
                            "accountName": "David Aman",
                            "accountNumber": "+2250709997042",
                            "accountType": "momo",
                            "networkId": "8d18204e-b51f-4554-815d-71586d0dac13"
                          },
                          "channelId": "33a82864-6460-43d7-9fc0-911f9bd8d50a",
                          "sequenceId": "akuunda-withdraw-005",
                          "currency": "XOF",
                          "country": "CI",
                          "reason": "other",
                          "forceAccept": true,
                          "directSettlement": true,
                          "customerUID": "01f8a941-2c88-46a3-a27d-1162911985b7",
                          "customerType": "retail",
                          "settlementInfo": {
                            "cryptoCurrency": "USDT",
                            "cryptoNetwork": "TRC20",
                            "cryptoAmount": 20
                          }
                        }
                        """
                            )
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Paiement créé avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Exemple de succès",
                                    value = """
            {
              "success": true,
              "message": "Paiement Off-Ramp créé avec succès",
              "transactionId": "offramp_1234567890"
            }
            """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Requête invalide",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Exemple d'erreur",
                                    value = """
            {
              "success": false,
              "message": "Impossible de récupérer le customerUID: customerUID introuvable",
              "transactionId": ""  // vide en cas d'erreur
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
                                    name = "Exemple d'erreur",
                                    value = """
            {
              "success": false,
              "message": "Erreur lors du traitement de la réponse OffRamp",
              "transactionId": ""  // vide en cas d'erreur
            }
            """
                            )
                    )
            )
    })
    public ResponseEntity<Object> createOffRampPaiements(
            @RequestBody OffRampRequest requestBody,
            @Parameter(name = "HeaderPin", description = "Code PIN d’authentification", required = true, in = ParameterIn.HEADER)
            @RequestHeader("HeaderPin") String headerPin,
            @Parameter(name = "username", description = "Nom d’utilisateur effectuant la transaction", required = true)
            @PathVariable String username
    ) {
        log.info("Requête reçue pour créer un paiement Off-Ramp : {}", requestBody);
        return akuundaYellowCardService.createOffRampPaiements(requestBody, headerPin, username);
    }


    // ------------------------------------------------------------------------
    // CHANNELS
    // ------------------------------------------------------------------------
    @GetMapping("/channels")
    @Operation(
            operationId = "getChannelsByCountry",
            summary = "Récupérer les channels par pays",
            description = """
            Récupère la liste des **canaux de paiement (channels)** disponibles pour un pays donné.

            **Paramètre requis :**
            - `countryCode` (query) — Code pays ISO Alpha-2 (ex: "NG", "CM", "GH").

            **Exemple :**
            ```bash
            GET /api/internal/v1/yellow-card/channels?countryCode=NG
            ```
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Channels récupérés avec succès"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<String> getChannelsByCountry(
            @Parameter(description = "Code pays ISO Alpha-2", required = true, example = "NG")
            @RequestParam String countryCode) {
        log.info("Requête reçue pour récupérer les channels du pays {}", countryCode);
        return akuundaYellowCardService.getChannels(countryCode);
    }

    // ------------------------------------------------------------------------
    // RATES
    // ------------------------------------------------------------------------
    @GetMapping("/rates/{channelId}")
    @Operation(
            operationId = "getRatesByChannelId",
            summary = "Récupérer les taux (rates) par channel",
            description = """
            Récupère les **taux de change** associés à un canal (channel) Yellow Card.
            
            ⚠️ **IMPORTANT** : Utilise le taux YellowCard (buy/sell) depuis l'API YellowCard, pas le taux du marché fiduciaire.

            **Paramètres requis :**
            - `channelId` (path) — Identifiant du canal Yellow Card.
            - `currencyCode` (query) — Code de la devise concernée (ex: "XAF", "NGN", "XOF").

            **Exemple :**
            ```bash
            GET /api/internal/v1/yellow-card/rates/{channelId}?currencyCode=XAF
            ```
            
            **Réponse :**
            ```json
            {
                "buy": 588.96,
                "sell": 554.98,
                "locale": "CI",
                "rateId": "west-african-franc",
                "code": "XOF",
                "updatedAt": "2025-11-11T23:08:18.417Z"
            }
            ```
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rates récupérés avec succès"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<String> getRatesByChannelId(
            @Parameter(description = "Identifiant du canal Yellow Card", required = true, example = "b621bf4f-0c60-4884-88a1-75f7a56b1938")
            @PathVariable String channelId,
            @Parameter(description = "Code de la devise", required = true, example = "XAF")
            @RequestParam String currencyCode) {
        log.info("Requête reçue pour récupérer les rates pour channelId: {}, currency: {}", channelId, currencyCode);
        return akuundaYellowCardService.getRatesByChannelId(channelId, currencyCode);
    }
    
    @GetMapping("/rates")
    @Operation(
            operationId = "getRates",
            summary = "Récupérer les taux (rates) sans channelId",
            description = """
            Récupère les **taux de change** Yellow Card sans spécifier de channel.
            
            ⚠️ **IMPORTANT** : Utilise le taux YellowCard (buy/sell) depuis l'API YellowCard, pas le taux du marché fiduciaire.

            **Paramètre requis :**
            - `currencyCode` (query) — Code de la devise concernée (ex: "XAF", "NGN", "XOF").

            **Exemple :**
            ```bash
            GET /api/internal/v1/yellow-card/rates?currencyCode=XAF
            ```
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rates récupérés avec succès"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<String> getRates(
            @Parameter(description = "Code de la devise", required = true, example = "XAF")
            @RequestParam String currencyCode) {
        log.info("Requête reçue pour récupérer les rates pour {}", currencyCode);
        return akuundaYellowCardService.getRates(currencyCode);
    }

    @PostMapping("/widget/quote")
    @Operation(
            operationId = "getYellowCardWidgetQuote",
            summary = "Quote widget Yellow Card (taux et frais)",
            description = """
            Appelle **POST /business/widget/quote** Yellow Card.
            Pour un retrait (Sell), fournir `localAmount` (converti en cryptoAmount) ou `cryptoAmount`.
            Pour un dépôt (Buy), fournir `localAmount` (montant fiat saisi).
            Retourne notamment `rateLocal`, `serviceFeeLocal`, `partnerFeeLocal`, `fiatReceived`.
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Quote récupéré"),
            @ApiResponse(responseCode = "400", description = "Paramètres invalides"),
            @ApiResponse(responseCode = "502", description = "Erreur Yellow Card")
    })
    public ResponseEntity<String> getWidgetQuote(
            @Valid @org.springframework.web.bind.annotation.RequestBody YellowCardWidgetQuoteRequest request) {
        log.info("Quote widget YellowCard: {} {} channelId={} type={}",
                request.getLocalAmount(), request.getCurrency(), request.getChannelId(), request.getTransactionType());
        return akuundaYellowCardService.getWidgetQuote(request);
    }

    // ------------------------------------------------------------------------
    // NETWORKS
    // ------------------------------------------------------------------------
    @GetMapping("/networks")
    @Operation(
            operationId = "getNetworks",
            summary = "Récupérer les networks disponibles",
            description = """
            Retourne la liste des **réseaux crypto** (blockchains) disponibles pour un pays donné.

            **Paramètre requis :**
            - `countryCode` (query) — Code pays ISO Alpha-2 (ex: "NG", "CM").

            **Exemple :**
            ```bash
            GET /api/internal/v1/yellow-card/networks?countryCode=NG
            ```
            """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Networks récupérés avec succès"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<String> getNetworks(
            @Parameter(description = "Code pays ISO Alpha-2", required = true, example = "NG")
            @RequestParam String countryCode) {
        log.info("Requête reçue pour récupérer les networks du pays {}", countryCode);
        return akuundaYellowCardService.getNetworks(countryCode);
    }
}
