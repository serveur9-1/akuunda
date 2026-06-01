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
import org.akuunda.akuundawallet.common.constants.ApiConstants;
import org.akuunda.akuundawallet.wallet.api.dto.CancelConditionalPaymentRequest;
import org.akuunda.akuundawallet.wallet.api.dto.ConditionalPaymentRequest;
import org.akuunda.akuundawallet.wallet.api.dto.ConditionalPaymentResponse;
import org.akuunda.akuundawallet.wallet.api.dto.ExecuteDepositRequest;
import org.akuunda.akuundawallet.wallet.api.dto.QRCodeValidationRequest;
import org.akuunda.akuundawallet.wallet.service.ConditionalPaymentService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping(path = ConditionalPaymentController.API_SERVICES_ROOT, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Akuunda Services - Conditional Payments", description = """
        API de gestion des paiements conditionnels (Client → Wallet intermédiaire → Smart contract Polygon USDC) :
        - Création : dépôt Client → Wallet intermédiaire → Smart contract (2 transferts)
        - Validation par scan de QR code
        - Libération / annulation avec remboursement total ou partiel
        """)
@SecurityRequirement(name = ApiConstants.SWAGGER_BASIC_SECURITY_SCHEME)
@RequiredArgsConstructor
public class ConditionalPaymentController {

    public static final String API_SERVICES_ROOT = "/api/internal/v1/conditional-payments";

    private final ConditionalPaymentService conditionalPaymentService;

    // ------------------------------------------------------------------------
    // CREATE CONDITIONAL PAYMENT
    // ------------------------------------------------------------------------
    @PostMapping("/create")
    @Operation(
            operationId = "createConditionalPayment",
            summary = "Créer un paiement conditionnel",
            description = """
        Crée un nouveau paiement conditionnel avec séquestre via smart contract.
        
        **Workflow :**
        1. Le montant est débité du wallet du client
        2. Étape 1 Venly : Client → Wallet intermédiaire
        3. Étape 2 Venly : Wallet intermédiaire → Smart contract (séquestre)
        4. Un QR code est généré pour la validation
        5. Statut "pending_condition" jusqu'à validation
        
        **Types de services supportés :**
        - HOTEL : Réservation d'hôtel
        - TRAVEL_AGENCY : Agence de voyage
        - TOURISM : Établissement touristique
        - RENTAL : Location (véhicule, maison, matériel)
        - DELIVERY : Livraison
        
        **Paramètres requis :**
        - `clientUsername` (query) : Username du client qui effectue le paiement
        - `vendorUsername` (body) : Username du vendeur/prestataire
        - `serviceType` (body) : Type de prestation
        - `description` (body) : Description de la prestation
        - `amount` (body) : Montant en USDC
        
        **Paramètres optionnels :**
        - `serviceStartDate` (body) : Date prévue de début de la prestation
        - `cancellationDeadline` (body) : Date limite pour annulation sans pénalité
        """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Détails du paiement conditionnel à créer",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ConditionalPaymentRequest.class)
                    )
            )
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Paiement conditionnel créé avec succès",
                    content = @Content(schema = @Schema(implementation = ConditionalPaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Requête invalide (solde insuffisant, etc.)"),
            @ApiResponse(responseCode = "404", description = "Client ou vendeur non trouvé"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<ConditionalPaymentResponse> createConditionalPayment(
            @Parameter(description = "Username du client (numéro de téléphone)", required = true, example = "0033612108828")
            @RequestParam String clientUsername,
            @Valid @RequestBody ConditionalPaymentRequest request) {
        
        log.info("📥 Création d'un paiement conditionnel: client={}, vendor={}, amount={}", 
                clientUsername, request.getVendorUsername(), request.getAmount());
        
        return conditionalPaymentService.createConditionalPayment(clientUsername, request);
    }

    // ------------------------------------------------------------------------
    // EXECUTE DEPOSIT (blockchain)
    // ------------------------------------------------------------------------
    @PostMapping("/{paymentId}/execute-deposit")
    @Operation(
            operationId = "executeDeposit",
            summary = "Exécuter le dépôt blockchain",
            description = """
        Exécute le dépôt réel sur la blockchain (Client → Wallet intermédiaire → Smart contract).
        À appeler après **create** quand le client confirme avec son PIN.
        
        - Si à la création le dépôt a été enregistré avec un hash factice (ESCROW-DEPOSIT-DB-...),
          cet endpoint lance les 2 transferts Venly et met à jour le hash.
        - Requiert le PIN du client (body: clientPin, ex: "PIN:123456").
        """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ExecuteDepositRequest.class)
                    )
            )
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Dépôt exécuté",
                    content = @Content(schema = @Schema(implementation = ConditionalPaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Dépôt déjà exécuté ou PIN invalide"),
            @ApiResponse(responseCode = "404", description = "Paiement non trouvé"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<ConditionalPaymentResponse> executeDeposit(
            @Parameter(description = "ID du paiement conditionnel (retourné à la création)", required = true)
            @PathVariable Long paymentId,
            @Valid @RequestBody ExecuteDepositRequest request) {
        return conditionalPaymentService.executeDeposit(paymentId, request.getClientPin());
    }

    // ------------------------------------------------------------------------
    // VALIDATE QR CODE
    // ------------------------------------------------------------------------
    @PostMapping("/validate-qr")
    @Operation(
            operationId = "validateQRCode",
            summary = "Valider un paiement conditionnel via QR code",
            description = """
        Valide un paiement conditionnel en scannant le QR code.
        
        **Workflow :**
        1. Le QR code est scanné (par le client ou le vendeur selon le contexte)
        2. Le système identifie qui a scanné le QR code (via le champ `scannedBy`)
        3. Le statut passe à "condition_validated"
        4. Le smart contract libère les fonds de façon autonome
        5. Les fonds sont automatiquement transférés depuis le wallet du smart contract vers le wallet de la personne qui a scanné
        6. Le statut final est "released"
        
        **Important :** Les fonds sont envoyés au wallet de la personne qui a scanné le QR code (identifiée via `scannedBy`), pas forcément au vendeur.
        
        **Contexte d'utilisation :**
        - Hôtel : Client arrive à la réception → QR scanné
        - Agence de voyage : Début du voyage → QR scanné
        - Établissement touristique : Accès au site → QR scanné
        - Location : Remise des clés/matériel → QR scanné
        - Livraison : Livreur arrive → QR scanné
        """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Token du QR code à valider",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = QRCodeValidationRequest.class)
                    )
            )
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "QR code validé et fonds libérés",
                    content = @Content(schema = @Schema(implementation = ConditionalPaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "QR code invalide, expiré ou déjà scanné"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<ConditionalPaymentResponse> validateQRCode(
            @Valid @RequestBody QRCodeValidationRequest request) {
        
        log.info("📥 Validation du QR code: token={}", request.getQrCodeToken());
        
        return conditionalPaymentService.validateQRCode(request);
    }

    // ------------------------------------------------------------------------
    // CANCEL PAYMENT
    // ------------------------------------------------------------------------
    @PostMapping("/{paymentCode}/cancel")
    @Operation(
            operationId = "cancelConditionalPayment",
            summary = "Annuler un paiement conditionnel",
            description = """
        Annule un paiement conditionnel et applique les règles de remboursement.
        
        **Règles de remboursement :**
        - Si aucune règle spécifique n'est définie : remboursement total (100%)
        - Si des règles sont définies : remboursement partiel selon les délais et pénalités
        
        **Exemples de règles :**
        - Annulation 48h avant : 0% pénalité → 100% remboursé
        - Annulation < 24h : 20% retenu → 80% remboursé
        
        **Statuts possibles après annulation :**
        - "refunded" : Remboursement total au client
        - "refunded_partial" : Remboursement partiel + retenue pour le vendeur
        """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = false,
                    description = "Raison de l'annulation (optionnel)",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = CancelConditionalPaymentRequest.class)
                    )
            )
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Paiement annulé et remboursement effectué",
                    content = @Content(schema = @Schema(implementation = ConditionalPaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Paiement déjà validé ou libéré"),
            @ApiResponse(responseCode = "404", description = "Paiement conditionnel non trouvé"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<ConditionalPaymentResponse> cancelPayment(
            @Parameter(description = "Code unique du paiement conditionnel", required = true, example = "CP-1704067200000-ABC12345")
            @PathVariable String paymentCode,
            @Valid @RequestBody(required = false) CancelConditionalPaymentRequest request) {
        
        log.info("📥 Annulation du paiement conditionnel: paymentCode={}", paymentCode);
        
        if (request == null) {
            request = new CancelConditionalPaymentRequest();
        }
        
        return conditionalPaymentService.cancelPayment(paymentCode, request);
    }

    // ------------------------------------------------------------------------
    // GET PAYMENT BY CODE
    // ------------------------------------------------------------------------
    @GetMapping("/{paymentCode}")
    @Operation(
            operationId = "getConditionalPaymentByCode",
            summary = "Récupérer un paiement conditionnel par son code",
            description = """
        Récupère les détails d'un paiement conditionnel à partir de son code unique.
        
        **Informations retournées :**
        - Détails du paiement (montant, statut, dates)
        - Informations client et vendeur
        - URL et token du QR code (si disponible)
        - Hash des transactions blockchain
        - Montants remboursés/retenus (si annulé)
        """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Paiement conditionnel trouvé",
                    content = @Content(schema = @Schema(implementation = ConditionalPaymentResponse.class))),
            @ApiResponse(responseCode = "404", description = "Paiement conditionnel non trouvé")
    })
    public ResponseEntity<ConditionalPaymentResponse> getPaymentByCode(
            @Parameter(description = "Code unique du paiement conditionnel", required = true, example = "CP-1704067200000-ABC12345")
            @PathVariable String paymentCode) {
        
        log.info("📥 Récupération du paiement conditionnel: paymentCode={}", paymentCode);
        
        return conditionalPaymentService.getPaymentByCode(paymentCode);
    }

    // ------------------------------------------------------------------------
    // GET USER PAYMENTS
    // ------------------------------------------------------------------------
    @GetMapping("/user/{username}")
    @Operation(
            operationId = "getUserConditionalPayments",
            summary = "Récupérer tous les paiements conditionnels d'un utilisateur",
            description = """
        Récupère tous les paiements conditionnels d'un utilisateur (en tant que client ou vendeur).
        
        **Types de paiements retournés :**
        - Paiements où l'utilisateur est le client
        - Paiements où l'utilisateur est le vendeur
        
        **Statuts possibles :**
        - pending_condition : En attente de validation
        - condition_validated : QR code scanné, prestation commencée
        - released : Fonds libérés vers le wallet de la personne qui a scanné le QR code
        - refunded : Remboursement total
        - refunded_partial : Remboursement partiel
        """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Liste des paiements conditionnels",
                    content = @Content(schema = @Schema(implementation = ConditionalPaymentResponse.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur non trouvé")
    })
    public ResponseEntity<List<ConditionalPaymentResponse>> getUserPayments(
            @Parameter(description = "Username de l'utilisateur", required = true, example = "0033612108828")
            @PathVariable String username) {
        
        log.info("📥 Récupération des paiements conditionnels de l'utilisateur: username={}", username);
        
        return conditionalPaymentService.getUserPayments(username);
    }

    // ------------------------------------------------------------------------
    // RELEASE FUNDS MANUALLY
    // ------------------------------------------------------------------------
    @PostMapping("/{paymentCode}/release")
    @Operation(
            operationId = "releaseFundsManually",
            summary = "Libérer manuellement les fonds d'un paiement conditionnel",
            description = """
        Libère manuellement les fonds d'un paiement conditionnel déjà validé.
        
        **Cas d'usage :**
        - Si la libération automatique a échoué
        - Pour forcer la libération après validation
        
        **Prérequis :**
        - Le paiement doit être en statut "condition_validated"
        - Le QR code doit avoir été scanné
        
        **Résultat :**
        - Les fonds sont libérés du smart contract vers le wallet de la personne qui a scanné le QR code
        - Le statut passe à "released"
        
        **Note :** Si le QR code a déjà été scanné, les fonds iront au wallet de la personne qui a scanné (identifiée via `scannedBy` dans le QR code).
        """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fonds libérés avec succès",
                    content = @Content(schema = @Schema(implementation = ConditionalPaymentResponse.class))),
            @ApiResponse(responseCode = "400", description = "Paiement non validé ou déjà libéré"),
            @ApiResponse(responseCode = "404", description = "Paiement conditionnel non trouvé"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<ConditionalPaymentResponse> releaseFunds(
            @Parameter(description = "Code unique du paiement conditionnel", required = true, example = "CP-1704067200000-ABC12345")
            @PathVariable String paymentCode) {
        
        log.info("📥 Libération manuelle des fonds: paymentCode={}", paymentCode);
        
        return conditionalPaymentService.releaseFunds(paymentCode);
    }
}

