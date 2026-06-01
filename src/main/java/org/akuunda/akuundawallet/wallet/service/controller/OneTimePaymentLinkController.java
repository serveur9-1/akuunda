package org.akuunda.akuundawallet.wallet.service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
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
import org.akuunda.akuundawallet.wallet.api.dto.ConditionalPaymentConfirmationResponse;
import org.akuunda.akuundawallet.wallet.api.dto.CreateAndPayRequest;
import org.akuunda.akuundawallet.wallet.api.dto.CreateAndPayResponse;
import org.akuunda.akuundawallet.wallet.api.dto.CreateConditionalAndPayRequest;
import org.akuunda.akuundawallet.wallet.api.dto.CreateConditionalAndPayYcRequest;
import org.akuunda.akuundawallet.wallet.api.dto.CreateConditionalAndPayYcResponse;
import org.akuunda.akuundawallet.wallet.api.dto.CreateConditionalLinkYcRequest;
import org.akuunda.akuundawallet.wallet.api.dto.CreateConditionalLinkYcResponse;
import org.akuunda.akuundawallet.wallet.api.dto.CreateConditionalOneTimeLinkRequest;
import org.akuunda.akuundawallet.wallet.api.dto.CreateOneTimePaymentLinkRequest;
import org.akuunda.akuundawallet.wallet.api.dto.OneTimePaymentLinkResponse;
import org.akuunda.akuundawallet.wallet.api.dto.OneTimePaymentPayRequest;
import org.akuunda.akuundawallet.wallet.api.dto.OneTimePaymentQuoteRequest;
import org.akuunda.akuundawallet.wallet.api.dto.RefundResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.MeldQuoteResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.MeldSessionResponse;
import org.akuunda.akuundawallet.wallet.api.dao.OneTimePaymentLinkRepository;
import org.akuunda.akuundawallet.wallet.api.entities.OneTimePaymentLink;
import org.akuunda.akuundawallet.wallet.api.dto.external.OnRampRequest;
import org.akuunda.akuundawallet.wallet.service.OneTimePaymentLinkService;
import org.akuunda.akuundawallet.wallet.service.OneTimePaymentMeldService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaYellowCardClientService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping(path = OneTimePaymentLinkController.API_SERVICES_ROOT, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Akuunda Services - One-Time Payment Links", description = """
        API de gestion des liens de paiement uniques (one-time) :
        - Création de liens à usage unique (1 lien = 1 seul paiement)
        - Montant et devise optionnels (métadonnées uniquement)
        - Paiement via mobile money (YellowCard OnRamp)
        - Expiration configurable (24h par défaut)
        """)
@SecurityRequirement(name = ApiConstants.SWAGGER_BASIC_SECURITY_SCHEME)
@RequiredArgsConstructor
public class OneTimePaymentLinkController {

    public static final String API_SERVICES_ROOT = "/api/internal/v1/one-time-payment-links";

    private final OneTimePaymentLinkService oneTimePaymentLinkService;
    private final OneTimePaymentMeldService oneTimePaymentMeldService;
    private final OneTimePaymentLinkRepository oneTimePaymentLinkRepository;
    private final AkuundaYellowCardClientService akuundaYellowCardClientService;

    // ------------------------------------------------------------------------
    // CREATE ONE-TIME PAYMENT LINK (authenticated)
    // ------------------------------------------------------------------------
    @PostMapping(value = "/create", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "createOneTimePaymentLink",
            summary = "Créer un lien de paiement unique (one-time)",
            description = """
        Crée un nouveau lien de paiement à usage unique.
        
        **Règles :**
        - 1 lien = 1 seul paiement
        - Seule la description est OBLIGATOIRE ; montant et devise sont OPTIONNELS (métadonnées)
        - Le montant on-chain est toujours minimal (0.000001 USDC) pour éviter tout blocage
        - Expiration par défaut : 24h après création
        - Code unique de 8 caractères alphanumériques
        """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lien créé avec succès",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = OneTimePaymentLinkResponse.class))),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "404", description = "Utilisateur non trouvé"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<OneTimePaymentLinkResponse> createOneTimePaymentLink(
            @Parameter(name = "username", description = "Username du créateur", required = true)
            @RequestParam String username,
            @RequestBody @Valid CreateOneTimePaymentLinkRequest request) {
        log.info("Request to create one-time payment link for user: {}", username);
        return oneTimePaymentLinkService.createOneTimePaymentLink(username, request);
    }

    // ------------------------------------------------------------------------
    // GET ONE-TIME PAYMENT LINK BY CODE (authenticated)
    // ------------------------------------------------------------------------
    @GetMapping("/{uniqueCode}")
    @Operation(
            operationId = "getOneTimePaymentLinkByCode",
            summary = "Récupérer un lien de paiement unique par son code",
            description = "Récupère les détails d'un lien de paiement unique à partir de son code (8 caractères)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lien trouvé",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = OneTimePaymentLinkResponse.class))),
            @ApiResponse(responseCode = "404", description = "Lien non trouvé"),
            @ApiResponse(responseCode = "410", description = "Lien expiré"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<OneTimePaymentLinkResponse> getOneTimePaymentLinkByCode(
            @Parameter(description = "Code unique du lien (8 caractères)", required = true, example = "a1b2c3d4")
            @PathVariable String uniqueCode) {
        log.info("Request to get one-time payment link by code: {}", uniqueCode);
        return oneTimePaymentLinkService.getOneTimePaymentLinkByCode(uniqueCode);
    }

    // ------------------------------------------------------------------------
    // GET USER ONE-TIME PAYMENT LINKS (authenticated)
    // ------------------------------------------------------------------------
    @GetMapping("/user/{username}")
    @Operation(
            operationId = "getUserOneTimePaymentLinks",
            summary = "Récupérer tous les liens de paiement uniques d'un utilisateur",
            description = "Récupère la liste de tous les liens de paiement uniques créés par un utilisateur, triés par date de création décroissante."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste des liens",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = OneTimePaymentLinkResponse.class))),
            @ApiResponse(responseCode = "404", description = "Utilisateur non trouvé"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<List<OneTimePaymentLinkResponse>> getUserOneTimePaymentLinks(
            @Parameter(description = "Username du créateur", required = true, example = "002250759146858")
            @PathVariable String username) {
        log.info("Request to get one-time payment links for user: {}", username);
        return oneTimePaymentLinkService.getUserOneTimePaymentLinks(username);
    }

    // ------------------------------------------------------------------------
    // CANCEL ONE-TIME PAYMENT LINK (authenticated)
    // ------------------------------------------------------------------------
    @PutMapping("/{uniqueCode}/cancel")
    @Operation(
            operationId = "cancelOneTimePaymentLink",
            summary = "Annuler un lien de paiement unique",
            description = """
        Annule un lien de paiement unique. Seul le créateur peut annuler son lien.
        Seuls les liens en statut CREATED peuvent être annulés.
        """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lien annulé avec succès"),
            @ApiResponse(responseCode = "400", description = "Lien non annulable (déjà payé, en cours, etc.)"),
            @ApiResponse(responseCode = "403", description = "Non autorisé"),
            @ApiResponse(responseCode = "404", description = "Lien ou utilisateur non trouvé"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<String> cancelOneTimePaymentLink(
            @Parameter(description = "Code unique du lien (8 caractères)", required = true, example = "a1b2c3d4")
            @PathVariable String uniqueCode,
            @Parameter(description = "Username du créateur", required = true, example = "002250759146858")
            @RequestParam String username) {
        log.info("Request to cancel one-time payment link: {} for user: {}", uniqueCode, username);
        return oneTimePaymentLinkService.cancelOneTimePaymentLink(username, uniqueCode);
    }

    // ------------------------------------------------------------------------
    // REFUND ONE-TIME PAYMENT LINK (authenticated)
    // ------------------------------------------------------------------------
    @PostMapping("/{uniqueCode}/refund")
    @Operation(
            operationId = "refundOneTimePaymentLink",
            summary = "Rembourser un lien de paiement unique",
            description = """
        Rembourse les USDC du wallet CREATE2 vers le wallet du marchand.
        Seul le créateur du lien peut initier le remboursement.
        Seuls les liens en statut CREATED, PENDING ou EXPIRED peuvent être remboursés.
        """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Remboursement effectué avec succès",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = RefundResponse.class))),
            @ApiResponse(responseCode = "400", description = "Lien non remboursable (statut invalide)"),
            @ApiResponse(responseCode = "403", description = "Non autorisé"),
            @ApiResponse(responseCode = "404", description = "Lien ou utilisateur non trouvé"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<RefundResponse> refundOneTimePaymentLink(
            @Parameter(description = "Code unique du lien (8 caractères)", required = true, example = "a1b2c3d4")
            @PathVariable String uniqueCode,
            @Parameter(description = "Username du créateur", required = true, example = "002250759146858")
            @RequestParam String username) {
        log.info("Request to refund one-time payment link: {} for user: {}", uniqueCode, username);
        return oneTimePaymentLinkService.refundOneTimePaymentLink(username, uniqueCode);
    }

    // ------------------------------------------------------------------------
    // GET QUOTES for a one-time payment link (public)
    // ------------------------------------------------------------------------
    @PostMapping("/{uniqueCode}/quotes")
    @Operation(
            summary = "Obtenir les quotes pour un lien de paiement unique",
            description = "Récupère les providers disponibles et les quotes pour payer un lien de paiement unique via Meld"
    )
    public ResponseEntity<MeldQuoteResponse> getPaymentLinkQuotes(
            @PathVariable String uniqueCode,
            @Valid @RequestBody OneTimePaymentQuoteRequest request) {
        log.info("Request to get quotes for one-time payment link: {}", uniqueCode);
        return oneTimePaymentMeldService.getQuotesForPaymentLink(uniqueCode, request);
    }

    // ------------------------------------------------------------------------
    // CREATE PAYMENT SESSION for a one-time payment link (public)
    // ------------------------------------------------------------------------
    @PostMapping("/{uniqueCode}/pay")
    @Operation(
            summary = "Initier le paiement d'un lien de paiement unique via Meld",
            description = "Crée une session Meld pour permettre au payeur de payer via carte ou MoMo. Les USDC seront envoyés sur le wallet CREATE2 temporaire."
    )
    public ResponseEntity<MeldSessionResponse> initiatePayment(
            @PathVariable String uniqueCode,
            @Valid @RequestBody OneTimePaymentPayRequest request) {
        log.info("Request to initiate payment for one-time payment link: {}", uniqueCode);
        return oneTimePaymentMeldService.createPaymentSession(uniqueCode, request);
    }

    // ------------------------------------------------------------------------
    // INITIATE YELLOWCARD PAYMENT for a one-time payment link (public)
    // ------------------------------------------------------------------------
    @PostMapping(value = "/{uniqueCode}/pay/yellowcard", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "initiateOneTimeYellowCardPayment",
            summary = "Initier un paiement YellowCard sur un lien de paiement unique (public)",
            description = "Initie un paiement fiat via YellowCard (MoMo Afrique) sur le lien de paiement unique.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paiement YellowCard initié"),
            @ApiResponse(responseCode = "404", description = "Lien non trouvé"),
            @ApiResponse(responseCode = "409", description = "Lien déjà payé ou non disponible"),
            @ApiResponse(responseCode = "410", description = "Lien expiré")
    })
    public ResponseEntity<Object> initiateYellowCardPayment(
            @PathVariable String uniqueCode,
            @RequestBody OnRampRequest request) {
        log.info("Request to initiate YellowCard payment for one-time link: {}", uniqueCode);

        OneTimePaymentLink link = oneTimePaymentLinkRepository.findByUniqueCode(uniqueCode).orElse(null);
        if (link == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Lien non trouvé : " + uniqueCode);
        }
        if ("PAID".equalsIgnoreCase(link.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Ce lien a déjà été payé");
        }
        if ("PENDING".equalsIgnoreCase(link.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Un paiement est déjà en cours pour ce lien");
        }
        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(java.time.LocalDateTime.now())) {
            link.setStatus("EXPIRED");
            oneTimePaymentLinkRepository.save(link);
            return ResponseEntity.status(HttpStatus.GONE).body("Ce lien a expiré");
        }
        if (link.getCreate2WalletAddress() == null || link.getCreate2WalletAddress().isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Adresse de paiement non configurée");
        }
        if (link.getCreator() == null || link.getCreator().getUserId() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Créateur du lien introuvable");
        }

        ResponseEntity<Object> ycResponse = akuundaYellowCardClientService.createCollectionForPermanentLink(
                request, link.getCreate2WalletAddress(), link.getCreator().getUserId());

        // Mettre à jour le lien avec le sequenceId YellowCard et le statut PENDING
        if (ycResponse.getStatusCode().is2xxSuccessful() && ycResponse.getBody() instanceof java.util.Map<?, ?> responseMap) {
            Object transactionId = responseMap.get("transactionId");
            if (transactionId != null) {
                link.setYcSequenceId(transactionId.toString());
            }
            link.setStatus("PENDING");
            oneTimePaymentLinkRepository.save(link);
            log.info("✅ OneTimePaymentLink {} mis à PENDING, ycSequenceId={}", uniqueCode, transactionId);
        }

        return ycResponse;
    }

    // ------------------------------------------------------------------------
    // CREATE AND PAY - Combined endpoint (authenticated)
    // ------------------------------------------------------------------------
    @PostMapping(value = "/create-and-pay", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "createAndPayOneTimePaymentLink",
            summary = "Créer un lien de paiement unique ET initier le paiement en un seul appel",
            description = """
    Combine la création du lien de paiement et l'initiation de la session Meld en un seul appel API.
    
    **Étapes internes :**
    1. Crée le lien de paiement (CREATE2 wallet, enregistrement on-chain)
    2. Crée la session de paiement Meld
    3. Retourne les infos du lien + l'URL du widget Meld
    """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lien créé et session Meld initiée",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CreateAndPayResponse.class))),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "404", description = "Utilisateur non trouvé"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<CreateAndPayResponse> createAndPay(
            @Parameter(name = "username", description = "Username du créateur", required = true)
            @RequestParam String username,
            @RequestBody @Valid CreateAndPayRequest request) {
        log.info("Request to create-and-pay one-time payment link for user: {}", username);
        return oneTimePaymentLinkService.createAndPay(username, request);
    }

    // ------------------------------------------------------------------------
    // CREATE CONDITIONAL AND PAY - Combined endpoint (authenticated)
    // ------------------------------------------------------------------------
    @PostMapping(value = "/create-conditional-and-pay", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "createConditionalAndPay",
            summary = "Créer un lien de paiement conditionnel (escrow) + initier le paiement en un seul appel",
            description = """
        Combine la création d'un lien conditionnel et l'initiation du paiement Meld.
        Les fonds seront bloqués dans l'escrow jusqu'au scan du QR code.
        
        Flow :
        1. L'hôtel appelle cet endpoint (crée lien + session Meld)
        2. Le payeur est redirigé vers Meld (widgetUrl) pour payer
        3. Après paiement, les fonds vont dans l'escrow (pas directement au marchand)
        4. Un QR code est généré, téléchargeable par le payeur
        5. Le payeur présente le QR à l'hôtel → scan → fonds libérés
        """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lien conditionnel créé et session Meld initiée",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CreateAndPayResponse.class))),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "404", description = "Utilisateur non trouvé"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<CreateAndPayResponse> createConditionalAndPay(
            @Parameter(name = "username", description = "Username du créateur (hôtel/prestataire)", required = true)
            @RequestParam String username,
            @RequestBody @Valid CreateConditionalAndPayRequest request) {
        log.info("Request to create-conditional-and-pay for user: {}", username);
        return oneTimePaymentLinkService.createConditionalAndPay(username, request);
    }

    // ------------------------------------------------------------------------
    // CREATE CONDITIONAL ONE-TIME PAYMENT LINK (authenticated)
    // ------------------------------------------------------------------------
    @PostMapping(value = "/create-conditional", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "createConditionalOneTimePaymentLink",
            summary = "Créer un lien de paiement conditionnel (escrow) à usage unique",
            description = """
        Crée un lien de paiement unique avec escrow (séquestre) pour hôtels/prestataires.
        Les fonds sont bloqués jusqu'au scan du QR code par le prestataire.
        
        Flow :
        1. L'hôtel crée le lien (cet endpoint)
        2. Le lien est envoyé au client (email, SMS, WhatsApp...)
        3. Le client paie via Meld (carte/MoMo) — sans app
        4. Les fonds sont déposés dans le smart contract escrow
        5. Un QR code est généré et téléchargeable par le client
        6. Le client présente le QR à l'hôtel
        7. L'hôtel scanne → fonds libérés
        """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lien conditionnel créé avec succès",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = OneTimePaymentLinkResponse.class))),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "404", description = "Utilisateur non trouvé"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<OneTimePaymentLinkResponse> createConditionalOneTimePaymentLink(
            @Parameter(name = "username", description = "Username du créateur (hôtel/prestataire)", required = true)
            @RequestParam String username,
            @RequestBody @Valid CreateConditionalOneTimeLinkRequest request) {
        log.info("Request to create conditional one-time payment link for user: {}", username);
        return oneTimePaymentLinkService.createConditionalOneTimePaymentLink(username, request);
    }

    // ------------------------------------------------------------------------
    // GET CONDITIONAL PAYMENT CONFIRMATION (public)
    // ------------------------------------------------------------------------
    @GetMapping("/confirmation/{uniqueCode}")
    @SecurityRequirements
    @Operation(
            operationId = "getConditionalPaymentConfirmation",
            summary = "Page de confirmation après paiement conditionnel",
            description = "Retourne les détails de la réservation et le QR code téléchargeable. Public, sans authentification."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Confirmation récupérée",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ConditionalPaymentConfirmationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Lien non conditionnel"),
            @ApiResponse(responseCode = "404", description = "Lien non trouvé"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<ConditionalPaymentConfirmationResponse> getConditionalPaymentConfirmation(
            @Parameter(description = "Code unique du lien (8 caractères)", required = true, example = "a1b2c3d4")
            @PathVariable String uniqueCode) {
        log.info("Request to get conditional payment confirmation for link: {}", uniqueCode);
        return oneTimePaymentLinkService.getConditionalPaymentConfirmation(uniqueCode);
    }

    // ------------------------------------------------------------------------
    // RECOVER STUCK LINK (admin)
    // ------------------------------------------------------------------------
    @PostMapping("/{uniqueCode}/recover")
    @Operation(
            operationId = "recoverStuckLink",
            summary = "Récupérer un lien CONDITIONAL bloqué après un rollback BDD",
            description = """
        Réexécute la partie BDD (création du ConditionalPayment + QR code + mise à jour du statut
        à ESCROW_FUNDED) pour un lien CONDITIONAL dont les transactions blockchain ont déjà réussi
        (executePayment + depositToEscrow) mais dont la persistance BDD a échoué.
        
        Cas d'usage typique : le lien est resté en `PENDING` ou `ESCROW_FUNDED_PENDING_DB`
        alors que les fonds sont déjà dans l'escrow on-chain.
        
        L'opération est idempotente : si le ConditionalPayment existe déjà et que le QR code
        est présent avec le statut ESCROW_FUNDED, le lien est retourné tel quel sans modification.
        """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lien récupéré avec succès",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = OneTimePaymentLinkResponse.class))),
            @ApiResponse(responseCode = "400", description = "Lien non CONDITIONAL"),
            @ApiResponse(responseCode = "404", description = "Lien non trouvé"),
            @ApiResponse(responseCode = "503", description = "Wallet admin non configuré"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<OneTimePaymentLinkResponse> recoverStuckLink(
            @Parameter(description = "Code unique du lien (8 caractères)", required = true, example = "3uaxagc8")
            @PathVariable String uniqueCode) {
        log.info("Admin recovery request for link: {}", uniqueCode);
        return oneTimePaymentLinkService.recoverStuckLink(uniqueCode);
    }

    // ------------------------------------------------------------------------
    // CREATE CONDITIONAL AND PAY YC - Combined endpoint (authenticated)
    // ------------------------------------------------------------------------
    @PostMapping(value = "/create-conditional-and-pay-yc", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "createConditionalAndPayYc",
            summary = "Créer un lien conditionnel (escrow) + initier le paiement via YellowCard",
            description = """
        Combine création du lien conditionnel et initiation du paiement YellowCard en un seul appel.
        Les fonds seront bloqués dans l'escrow jusqu'au scan du QR code.
        
        Flow :
        1. Le prestataire appelle cet endpoint (crée lien + collection YellowCard)
        2. Le payeur est redirigé vers le lien YellowCard (redirectUrl) ou reçoit une notification MoMo (push)
        3. Après paiement, les fonds vont dans l'escrow (pas directement au prestataire)
        4. Un QR code est généré, téléchargeable par le payeur
        5. Le payeur présente le QR au prestataire → scan → fonds libérés
        """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lien conditionnel créé et collection YellowCard initiée",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CreateConditionalAndPayYcResponse.class))),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "404", description = "Utilisateur non trouvé"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<CreateConditionalAndPayYcResponse> createConditionalAndPayYc(
            @Parameter(name = "username", description = "Username du créateur (hôtel/prestataire)", required = true)
            @RequestParam String username,
            @RequestBody @Valid CreateConditionalAndPayYcRequest request) {
        log.info("Request to create-conditional-and-pay-yc for user: {}", username);
        return oneTimePaymentLinkService.createConditionalAndPayYc(username, request);
    }

    // ------------------------------------------------------------------------
    // 2-STEP YELLOWCARD FLOW
    // ------------------------------------------------------------------------

    @PostMapping(value = "/create-conditional-link-yc", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "createConditionalLinkYc",
            summary = "Créer un lien conditionnel YellowCard (étape 1 — génération du lien)",
            description = """
        Étape 1 du flux YellowCard en 2 temps.
        Crée le lien conditionnel on-chain (escrow) et stocke les données YellowCard pour plus tard.
        YellowCard N'EST PAS appelé à cette étape.
        Le lien retourné (paymentUrl) est envoyé au client pour qu'il confirme le paiement.
        Statut retourné : CREATED.
        """
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Lien conditionnel créé (YellowCard non encore appelé)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = CreateConditionalLinkYcResponse.class))),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "404", description = "Utilisateur non trouvé"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<CreateConditionalLinkYcResponse> createConditionalLinkYc(
            @Parameter(name = "username", description = "Username du créateur (hôtel/prestataire)", required = true)
            @RequestParam String username,
            @RequestBody @Valid CreateConditionalLinkYcRequest request) {
        log.info("Request to create-conditional-link-yc for user: {}", username);
        return oneTimePaymentLinkService.createConditionalLinkYc(username, request);
    }
}