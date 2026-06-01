package org.akuunda.akuundawallet.wallet.service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.akuunda.akuundawallet.common.constants.ApiConstants;
import org.akuunda.akuundawallet.wallet.api.dao.OneTimePaymentLinkRepository;
import org.akuunda.akuundawallet.wallet.api.dto.OneTimePaymentPayRequest;
import org.akuunda.akuundawallet.wallet.api.dto.OneTimePaymentQuoteRequest;
import org.akuunda.akuundawallet.wallet.api.dto.external.MeldQuoteResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.MeldSessionResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.OnRampRequest;
import org.akuunda.akuundawallet.wallet.api.entities.OneTimePaymentLink;
import org.akuunda.akuundawallet.wallet.api.dto.partner.*;
import org.akuunda.akuundawallet.wallet.api.dto.GenereLinkRequest;
import org.akuunda.akuundawallet.wallet.api.dto.GenereLinkResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.RecipientDto;
import org.akuunda.akuundawallet.wallet.service.GenereLinkYcService;
import org.akuunda.akuundawallet.wallet.service.OneTimePaymentMeldService;
import org.akuunda.akuundawallet.wallet.service.PartnerContractService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaYellowCardClientService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@CrossOrigin("*")
@RequestMapping(path = PartnerContractController.API_ROOT, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Partner Smart Contracts", description = """
        API de gestion des contrats intelligents modulables pour les partenaires.
        Permet de configurer des règles de redistribution personnalisées et d'initier des paiements
        avec distribution automatique vers plusieurs bénéficiaires.
        """)
@SecurityRequirement(name = ApiConstants.SWAGGER_BASIC_SECURITY_SCHEME)
@RequiredArgsConstructor
public class PartnerContractController {

    public static final String API_ROOT = "/api/internal/v1/partner-contracts";

    private final PartnerContractService partnerContractService;
    private final OneTimePaymentMeldService oneTimePaymentMeldService;
    private final OneTimePaymentLinkRepository oneTimePaymentLinkRepository;
    private final AkuundaYellowCardClientService akuundaYellowCardClientService;
    private final GenereLinkYcService genereLinkYcService;

    // -------------------------------------------------------------------------
    // Contract management
    // -------------------------------------------------------------------------

    @PostMapping("/{partnerUsername}")
    @Operation(
            operationId = "createPartnerContract",
            summary = "Créer ou mettre à jour un contrat partenaire",
            description = """
                    Crée un nouveau contrat avec ses bénéficiaires et ses règles de redistribution.
                    La somme des pourcentages des bénéficiaires doit être ≤ 100%.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Contrat créé avec succès"),
            @ApiResponse(responseCode = "400", description = "Validation échouée (pourcentages > 100%, champs manquants)"),
            @ApiResponse(responseCode = "404", description = "Partenaire introuvable")
    })
    public ResponseEntity<PartnerContractResponse> createContract(
            @Parameter(description = "Username du partenaire", required = true)
            @PathVariable String partnerUsername,
            @Valid @RequestBody CreatePartnerContractRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(partnerContractService.createContract(partnerUsername, request));
    }

    // -------------------------------------------------------------------------
    // Création contrat + paiement en un seul appel (Meld & YellowCard)
    // -------------------------------------------------------------------------

    @PostMapping("/{partnerUsername}/payment")
    @Operation(
            operationId = "createPartnerPayment",
            summary = "Créer un contrat + paiement en un seul appel",
            description = """
                    Crée le contrat partenaire ET initie le paiement en une seule requête.
                    Retourne directement le `qrUrl` à envoyer au client.
                    Le client ouvre ce lien et choisit son provider (Meld ou YellowCard).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Contrat + paiement créés, qrUrl disponible"),
            @ApiResponse(responseCode = "400", description = "Paramètres invalides"),
            @ApiResponse(responseCode = "404", description = "Partenaire ou client introuvable")
    })
    public ResponseEntity<Object> createContractAndPayment(
            @Parameter(description = "Username du partenaire", required = true)
            @PathVariable String partnerUsername,
            @Valid @RequestBody CreatePartnerPaymentRequest request) {
        try {
            // 1. Créer le contrat
            CreatePartnerContractRequest contractRequest = new CreatePartnerContractRequest();
            contractRequest.setName(request.getName());
            contractRequest.setDescription(request.getDescription());
            contractRequest.setServiceType(request.getServiceType());
            contractRequest.setTriggerType(request.getTriggerType());
            contractRequest.setPaymentLinkType("ONE_TIME_ONLY");
            contractRequest.setCancellationPenaltyRate(request.getCancellationPenaltyRate());
            contractRequest.setFreeCancellationHours(request.getFreeCancellationHours());
            contractRequest.setAutoReleaseHours(request.getAutoReleaseHours());
            contractRequest.setDisputeWindowHours(request.getDisputeWindowHours());
            contractRequest.setBeneficiaries(request.getBeneficiaries());

            PartnerContractResponse contract = partnerContractService.createContract(partnerUsername, contractRequest);

            // 2. Initier le paiement
            InitiatePartnerPaymentRequest paymentRequest = new InitiatePartnerPaymentRequest();
            paymentRequest.setClientUsername(request.getClientUsername());
            paymentRequest.setClientPhone(request.getClientPhone());
            paymentRequest.setClientEmail(request.getClientEmail());
            paymentRequest.setAmount(request.getAmount());
            paymentRequest.setCurrency(request.getCurrency());
            paymentRequest.setLocalAmount(request.getLocalAmount());
            paymentRequest.setLocalCurrency(request.getLocalCurrency());
            paymentRequest.setServiceStartDate(request.getServiceStartDate());
            paymentRequest.setWebhookUrl(request.getWebhookUrl());
            paymentRequest.setWebhookSecret(request.getWebhookSecret());

            PartnerPaymentResponse payment = partnerContractService.initiatePayment(contract.getContractCode(), paymentRequest);
            String paymentCode = payment.getPaymentCode();

            // 3. Si provider spécifié → initier directement la session provider
            if (request.getProvider() != null && !request.getProvider().isBlank()) {

                if ("MELD".equalsIgnoreCase(request.getProvider())) {
                    var meldRequest = new org.akuunda.akuundawallet.wallet.api.dto.OneTimePaymentPayRequest();
                    meldRequest.setServiceProvider(request.getServiceProvider());
                    meldRequest.setSourceCurrencyCode(request.getSourceCurrencyCode());
                    meldRequest.setSourceAmount(request.getSourceAmount() != null
                            ? request.getSourceAmount() : String.valueOf(request.getAmount()));
                    meldRequest.setCountryCode(request.getCountryCode());

                    var meldResponse = oneTimePaymentMeldService.createPaymentSession(paymentCode, meldRequest);
                    if (!meldResponse.getStatusCode().is2xxSuccessful() || meldResponse.getBody() == null) {
                        return ResponseEntity.status(meldResponse.getStatusCode())
                                .body(java.util.Map.of("error", "Échec initiation Meld"));
                    }
                    String providerUrl = meldResponse.getBody().getPayRedirectUrl() != null
                            ? meldResponse.getBody().getPayRedirectUrl()
                            : meldResponse.getBody().getWidgetUrl();

                    return ResponseEntity.status(HttpStatus.CREATED).body(
                            CreatePartnerPaymentResponse.builder()
                                    .paymentCode(paymentCode)
                                    .provider("MELD")
                                    .providerUrl(providerUrl)
                                    .status("PENDING")
                                    .build());

                } else if ("YELLOWCARD".equalsIgnoreCase(request.getProvider())) {
                    var yc = request.getYellowCard();
                    if (yc == null) {
                        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(java.util.Map.of("error", "yellowCard requis pour provider YELLOWCARD"));
                    }
                    OneTimePaymentLink link = oneTimePaymentLinkRepository.findByUniqueCode(paymentCode).orElse(null);
                    if (link == null) {
                        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(java.util.Map.of("error", "Lien introuvable"));
                    }

                    // Recipient auto-renseigné depuis le profil clientUsername
                    var clientUser = link.getCreator();
                    RecipientDto recipient = new RecipientDto();

                    // Nom complet
                    String fullName = (clientUser.getFirstname() != null ? clientUser.getFirstname() : "")
                            + (clientUser.getLastname() != null ? " " + clientUser.getLastname() : "");
                    recipient.setName(fullName.isBlank() ? clientUser.getUsername() : fullName.trim());

                    // Téléphone
                    recipient.setPhone(clientUser.getMobilePhone() != null
                            ? clientUser.getMobilePhone() : clientUser.getUsername());

                    // Email
                    recipient.setEmail(clientUser.getEmail());

                    // Pays — depuis le profil utilisateur ou le champ country fourni
                    String country = yc.getCountry();
                    if ((country == null || country.isBlank()) && clientUser.getCountryCurrency() != null) {
                        country = clientUser.getCountryCurrency().getCountryCode();
                    }
                    recipient.setCountry(country);

                    // Adresse
                    recipient.setAddress(clientUser.getAdresse() != null
                            ? clientUser.getAdresse() : "Non spécifié");

                    // Date de naissance (profil : dateNaissance)
                    recipient.setDob(clientUser.getDateNaissance());

                    // KYC
                    recipient.setIdNumber(clientUser.getIdNumber());
                    recipient.setIdType(clientUser.getIdType());
                    recipient.setAdditionalIdNumber(clientUser.getAdditionalIdNumber());
                    recipient.setAdditionalIdType(clientUser.getAdditionalIdType());

                    // Entreprise (si compte business)
                    if (clientUser.getRaisonSociale() != null) {
                        recipient.setBusinessName(clientUser.getRaisonSociale());
                    }
                    if (clientUser.getSiret() != null) {
                        recipient.setBusinessId(clientUser.getSiret());
                    }

                    GenereLinkRequest genereLinkRequest = GenereLinkRequest.builder()
                            .recipient(recipient)
                            .amount(request.getLocalAmount() != null ? request.getLocalAmount() : request.getAmount())
                            .currency(request.getLocalCurrency() != null ? request.getLocalCurrency() : "XOF")
                            .country(yc.getCountry())
                            .reason("other")
                            .settlementWalletAddress(link.getCreate2WalletAddress())
                            .uniqueCode(paymentCode)
                            .build();

                    GenereLinkResponse genereLinkResponse = genereLinkYcService.generateLink(genereLinkRequest);

                    if (genereLinkResponse != null && genereLinkResponse.getPaymentUrl() != null) {
                        link.setYcSequenceId(genereLinkResponse.getTransactionId());
                        link.setStatus("PENDING");
                        oneTimePaymentLinkRepository.save(link);

                        return ResponseEntity.status(HttpStatus.CREATED).body(
                                CreatePartnerPaymentResponse.builder()
                                        .paymentCode(paymentCode)
                                        .provider("YELLOWCARD")
                                        .providerUrl(genereLinkResponse.getPaymentUrl())
                                        .status("PENDING")
                                        .build());
                    }
                    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                            .body(java.util.Map.of("error", "Échec génération lien YellowCard"));
                }
            }

            // Pas de provider → retourner le qrUrl de la page de choix
            return ResponseEntity.status(HttpStatus.CREATED).body(payment);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(java.util.Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(java.util.Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(java.util.Map.of("error", "Erreur interne : " + e.getMessage()));
        }
    }

    @GetMapping("/{contractCode}")
    @Operation(
            operationId = "getPartnerContract",
            summary = "Récupérer un contrat par son code",
            description = "Retourne la configuration complète d'un contrat avec ses bénéficiaires.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Contrat trouvé"),
            @ApiResponse(responseCode = "404", description = "Contrat introuvable")
    })
    public ResponseEntity<PartnerContractResponse> getContract(
            @Parameter(description = "Code unique du contrat", required = true)
            @PathVariable String contractCode) {
        return ResponseEntity.ok(partnerContractService.getContract(contractCode));
    }

    @GetMapping("/partner/{partnerUsername}")
    @Operation(
            operationId = "getPartnerContracts",
            summary = "Lister les contrats actifs d'un partenaire",
            description = "Retourne tous les contrats actifs associés au partenaire.")
    public ResponseEntity<List<PartnerContractResponse>> getPartnerContracts(
            @PathVariable String partnerUsername) {
        return ResponseEntity.ok(partnerContractService.getPartnerContracts(partnerUsername));
    }

    @DeleteMapping("/{contractCode}/partner/{partnerUsername}")
    @Operation(
            operationId = "deactivatePartnerContract",
            summary = "Désactiver un contrat",
            description = "Désactive le contrat. Les paiements déjà initiés ne sont pas affectés.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Contrat désactivé"),
            @ApiResponse(responseCode = "403", description = "Partenaire non autorisé sur ce contrat"),
            @ApiResponse(responseCode = "404", description = "Contrat introuvable")
    })
    public ResponseEntity<Void> deactivateContract(
            @PathVariable String contractCode,
            @PathVariable String partnerUsername) {
        partnerContractService.deactivateContract(contractCode, partnerUsername);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // Payment lifecycle
    // -------------------------------------------------------------------------

    @PostMapping("/{contractCode}/payments")
    @Operation(
            operationId = "initiatePartnerPayment",
            summary = "Initier un paiement sous un contrat",
            description = """
                    Crée un paiement sous le contrat spécifié.
                    Si `triggerType = QR_CODE`, un QR code est généré et retourné dans la réponse.
                    Si `triggerType = WEBHOOK`, une URL webhook est fournie.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Paiement initié"),
            @ApiResponse(responseCode = "400", description = "Contrat inactif ou paramètres invalides"),
            @ApiResponse(responseCode = "404", description = "Contrat introuvable")
    })
    public ResponseEntity<PartnerPaymentResponse> initiatePayment(
            @Parameter(description = "Code du contrat", required = true)
            @PathVariable String contractCode,
            @Valid @RequestBody InitiatePartnerPaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(partnerContractService.initiatePayment(contractCode, request));
    }

    @PostMapping("/admin/grant-relayer-role")
    @Operation(operationId = "grantRelayerRole",
            summary = "Accorder RELAYER_ROLE au wallet intermédiaire (opération unique)")
    public ResponseEntity<String> grantRelayerRole() {
        String txHash = partnerContractService.grantRelayerRole();
        return txHash != null
                ? ResponseEntity.ok("RELAYER_ROLE accordé. txHash=" + txHash)
                : ResponseEntity.status(500).body("Échec — vérifier les logs");
    }

    @PostMapping("/payments/{paymentCode}/retry")
    @Operation(operationId = "retryDistribution",
            summary = "Rejouer la distribution sur un paiement en échec",
            description = """
                    Si distribute() a échoué (fonds toujours dans le smart contract),
                    cet endpoint réinitialise le paiement et retente la distribution.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Distribution relancée"),
            @ApiResponse(responseCode = "400", description = "Paiement non en échec ou fonds non déposés"),
            @ApiResponse(responseCode = "404", description = "Paiement introuvable")
    })
    public ResponseEntity<PartnerPaymentResponse> retryDistribution(@PathVariable String paymentCode) {
        return ResponseEntity.ok(partnerContractService.retryDistribution(paymentCode));
    }

    @PostMapping("/payments/{paymentCode}/cancel")
    @Operation(
            operationId = "cancelPartnerPayment",
            summary = "Annuler un paiement",
            description = """
                    Annule le paiement et déclenche le remboursement selon les règles du contrat.
                    Si la fenêtre gratuite est dépassée, une pénalité peut s'appliquer.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paiement annulé"),
            @ApiResponse(responseCode = "400", description = "Paiement non annulable dans son état actuel"),
            @ApiResponse(responseCode = "404", description = "Paiement introuvable")
    })
    public ResponseEntity<PartnerPaymentResponse> cancelPayment(
            @PathVariable String paymentCode,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(partnerContractService.cancelPayment(paymentCode, reason));
    }

    @GetMapping("/payments/{paymentCode}")
    @Operation(
            operationId = "getPartnerPayment",
            summary = "Récupérer le statut d'un paiement",
            description = "Retourne l'état complet du paiement avec le détail des distributions.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paiement trouvé"),
            @ApiResponse(responseCode = "404", description = "Paiement introuvable")
    })
    public ResponseEntity<PartnerPaymentResponse> getPayment(@PathVariable String paymentCode) {
        return ResponseEntity.ok(partnerContractService.getPayment(paymentCode));
    }

    /**
     * Endpoint public (sans JWT) — utilisé par la page web qr.akuunda-pay.io/PCP-xxx
     */
    @GetMapping("/payments/{paymentCode}/web")
    @Operation(
            operationId = "getPartnerPaymentPublic",
            summary = "Récupérer un paiement partner (public, sans auth)",
            description = "Endpoint public utilisé par la page web de paiement.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paiement trouvé"),
            @ApiResponse(responseCode = "404", description = "Paiement introuvable")
    })
    public ResponseEntity<PartnerPaymentResponse> getPaymentPublic(@PathVariable String paymentCode) {
        try {
            return ResponseEntity.ok(partnerContractService.getPayment(paymentCode));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // -------------------------------------------------------------------------
    // PAYMENT — Quotes (public)
    // -------------------------------------------------------------------------

    @PostMapping(value = "/payments/{paymentCode}/quotes", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "getPartnerPaymentQuotes",
            summary = "Obtenir les quotes Meld pour un paiement partner (public)",
            description = "Récupère les providers et quotes disponibles pour payer un lien PCP via Meld.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Quotes récupérés"),
            @ApiResponse(responseCode = "404", description = "Paiement introuvable")
    })
    public ResponseEntity<MeldQuoteResponse> getPaymentQuotes(
            @PathVariable String paymentCode,
            @RequestBody OneTimePaymentQuoteRequest request) {
        return oneTimePaymentMeldService.getQuotesForPaymentLink(paymentCode, request);
    }

    // -------------------------------------------------------------------------
    // PAYMENT — Pay via Meld (public)
    // -------------------------------------------------------------------------

    @PostMapping(value = "/payments/{paymentCode}/pay/meld", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "initiatePartnerPaymentMeld",
            summary = "Initier un paiement Meld sur un lien PCP (public)",
            description = "Crée une session Meld (carte, virement SEPA…). Retourne l'URL du widget.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session Meld créée"),
            @ApiResponse(responseCode = "404", description = "Paiement introuvable"),
            @ApiResponse(responseCode = "400", description = "Lien non en statut CREATED")
    })
    public ResponseEntity<MeldSessionResponse> initiatePaymentMeld(
            @PathVariable String paymentCode,
            @RequestBody OneTimePaymentPayRequest request) {
        return oneTimePaymentMeldService.createPaymentSession(paymentCode, request);
    }

    // -------------------------------------------------------------------------
    // PAYMENT — Pay via YellowCard (public)
    // -------------------------------------------------------------------------

    @PostMapping(value = "/payments/{paymentCode}/pay/yellowcard", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "initiatePartnerPaymentYellowCard",
            summary = "Initier un paiement YellowCard sur un lien PCP (public)",
            description = "Initie un paiement fiat via YellowCard (MoMo, virement bancaire Afrique).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paiement YellowCard initié"),
            @ApiResponse(responseCode = "404", description = "Paiement introuvable"),
            @ApiResponse(responseCode = "409", description = "Lien déjà payé ou en cours"),
            @ApiResponse(responseCode = "410", description = "Lien expiré")
    })
    public ResponseEntity<Object> initiatePaymentYellowCard(
            @PathVariable String paymentCode,
            @RequestBody OnRampRequest request) {

        OneTimePaymentLink link = oneTimePaymentLinkRepository.findByUniqueCode(paymentCode).orElse(null);
        if (link == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Paiement introuvable : " + paymentCode);
        }
        if ("PAID".equalsIgnoreCase(link.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Ce lien a déjà été payé");
        }
        if ("PENDING".equalsIgnoreCase(link.getStatus())) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Un paiement est déjà en cours");
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

        if (ycResponse.getStatusCode().is2xxSuccessful() && ycResponse.getBody() instanceof java.util.Map<?, ?> responseMap) {
            Object transactionId = responseMap.get("transactionId");
            if (transactionId != null) {
                link.setYcSequenceId(transactionId.toString());
            }
            link.setStatus("PENDING");
            oneTimePaymentLinkRepository.save(link);
        }

        return ycResponse;
    }

    @GetMapping("/payments/client/{clientUsername}")
    @Operation(
            operationId = "getClientPayments",
            summary = "Lister les paiements d'un client",
            description = "Retourne l'historique des paiements effectués par le client, triés par date décroissante.")
    public ResponseEntity<List<PartnerPaymentResponse>> getClientPayments(
            @PathVariable String clientUsername) {
        return ResponseEntity.ok(partnerContractService.getClientPayments(clientUsername));
    }

    @GetMapping("/{contractCode}/payments/partner/{partnerUsername}")
    @Operation(
            operationId = "getContractPayments",
            summary = "Lister les paiements d'un contrat",
            description = "Retourne tous les paiements effectués sous un contrat (accès réservé au partenaire propriétaire).")
    public ResponseEntity<List<PartnerPaymentResponse>> getContractPayments(
            @PathVariable String contractCode,
            @PathVariable String partnerUsername) {
        return ResponseEntity.ok(partnerContractService.getContractPayments(contractCode, partnerUsername));
    }

    // -------------------------------------------------------------------------
    // Payment validation
    // -------------------------------------------------------------------------

    // ── Modes existants ──────────────────────────────────────────────────────

    @PostMapping("/payments/validate/qr/{qrToken}")
    @Operation(operationId = "validatePaymentByQrCode", summary = "QR_CODE – Valider par scan QR",
            description = "Valide le paiement associé au token QR et déclenche la redistribution.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Redistribution déclenchée"),
            @ApiResponse(responseCode = "400", description = "QR expiré ou paiement déjà traité"),
            @ApiResponse(responseCode = "404", description = "QR token introuvable")
    })
    public ResponseEntity<PartnerPaymentResponse> validateByQrCode(
            @PathVariable String qrToken,
            @RequestParam(required = false) String scannedBy) {
        return ResponseEntity.ok(partnerContractService.validateByQrCode(qrToken, scannedBy));
    }

    @PostMapping("/payments/{paymentCode}/validate/manual")
    @Operation(operationId = "validatePaymentManually", summary = "MANUAL – Validation opérateur",
            description = "Validation manuelle par un opérateur ou admin. Déclenche la redistribution.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paiement validé"),
            @ApiResponse(responseCode = "400", description = "Paiement déjà traité"),
            @ApiResponse(responseCode = "404", description = "Paiement introuvable")
    })
    public ResponseEntity<PartnerPaymentResponse> validateManually(
            @PathVariable String paymentCode,
            @RequestParam(required = false) String validatedBy) {
        return ResponseEntity.ok(partnerContractService.validateManually(paymentCode, validatedBy));
    }

    @PostMapping("/payments/{paymentCode}/validate/webhook")
    @Operation(operationId = "validatePaymentByWebhook", summary = "WEBHOOK – Callback système tiers",
            description = "Reçoit un webhook entrant. Vérifie la signature HMAC-SHA256 (header `X-Webhook-Signature`).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Redistribution déclenchée"),
            @ApiResponse(responseCode = "400", description = "Signature invalide"),
            @ApiResponse(responseCode = "404", description = "Paiement introuvable")
    })
    public ResponseEntity<PartnerPaymentResponse> validateByWebhook(
            @PathVariable String paymentCode,
            @RequestBody String webhookPayload,
            @RequestHeader("X-Webhook-Signature") String signature) {
        return ResponseEntity.ok(partnerContractService.validateByWebhook(paymentCode, webhookPayload, signature));
    }

    // ── Nouveaux modes ───────────────────────────────────────────────────────

    @PostMapping("/payments/validate/confirmation/{confirmationToken}")
    @Operation(operationId = "confirmReceiptByClient", summary = "REMOTE_CONFIRMATION – Client confirme la réception",
            description = """
                    Le client clique sur le lien reçu par push ou e-mail.
                    Le token est à usage unique et a une durée de validité configurée.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Réception confirmée, redistribution déclenchée"),
            @ApiResponse(responseCode = "400", description = "Token expiré ou déjà utilisé"),
            @ApiResponse(responseCode = "404", description = "Token introuvable")
    })
    public ResponseEntity<PartnerPaymentResponse> confirmReceiptByClient(
            @Parameter(description = "Token de confirmation reçu par le client", required = true)
            @PathVariable String confirmationToken) {
        return ResponseEntity.ok(partnerContractService.confirmReceiptByClient(confirmationToken));
    }

    @PostMapping("/payments/{paymentCode}/validate/otp")
    @Operation(operationId = "validateByOtp", summary = "OTP – Provider saisit le code client",
            description = """
                    Le provider saisit le code OTP à 6 chiffres que le client a reçu par push.
                    Vérifie la correspondance et l'expiration avant de déclencher la redistribution.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "OTP valide, redistribution déclenchée"),
            @ApiResponse(responseCode = "400", description = "OTP invalide ou expiré"),
            @ApiResponse(responseCode = "404", description = "Paiement introuvable")
    })
    public ResponseEntity<PartnerPaymentResponse> validateByOtp(
            @PathVariable String paymentCode,
            @RequestParam String otpCode,
            @RequestParam(required = false) String validatedBy) {
        return ResponseEntity.ok(partnerContractService.validateByOtp(paymentCode, otpCode, validatedBy));
    }

    @PostMapping("/payments/{paymentCode}/validate/provider")
    @Operation(operationId = "confirmByProvider", summary = "DUAL_CONFIRMATION – Provider confirme la livraison",
            description = """
                    Le prestataire confirme qu'il a livré le service.
                    La redistribution est déclenchée uniquement quand le client a également confirmé.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Confirmation enregistrée (redistribution si les 2 parties ont confirmé)"),
            @ApiResponse(responseCode = "404", description = "Paiement introuvable")
    })
    public ResponseEntity<PartnerPaymentResponse> confirmByProvider(
            @PathVariable String paymentCode,
            @RequestParam String providerUsername) {
        return ResponseEntity.ok(partnerContractService.confirmByProvider(paymentCode, providerUsername));
    }

    @PostMapping("/payments/{paymentCode}/validate/client")
    @Operation(operationId = "confirmByClient", summary = "DUAL_CONFIRMATION – Client confirme la réception",
            description = """
                    Le client confirme qu'il a reçu le service.
                    La redistribution est déclenchée uniquement quand le provider a également confirmé.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Confirmation enregistrée (redistribution si les 2 parties ont confirmé)"),
            @ApiResponse(responseCode = "403", description = "Client non autorisé sur ce paiement"),
            @ApiResponse(responseCode = "404", description = "Paiement introuvable")
    })
    public ResponseEntity<PartnerPaymentResponse> confirmByClient(
            @PathVariable String paymentCode,
            @RequestParam String clientUsername) {
        return ResponseEntity.ok(partnerContractService.confirmByClient(paymentCode, clientUsername));
    }

    @PostMapping("/payments/{paymentCode}/validate/geolocation")
    @Operation(operationId = "validateByGeolocation", summary = "GEOLOCATION – Client confirme sa présence GPS",
            description = """
                    L'app mobile du client envoie ses coordonnées GPS.
                    Vérifie que le client est dans le rayon de tolérance configuré sur le contrat.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Position validée, redistribution déclenchée"),
            @ApiResponse(responseCode = "400", description = "Position trop éloignée du lieu de service"),
            @ApiResponse(responseCode = "404", description = "Paiement introuvable")
    })
    public ResponseEntity<PartnerPaymentResponse> validateByGeolocation(
            @PathVariable String paymentCode,
            @RequestParam double latitude,
            @RequestParam double longitude) {
        return ResponseEntity.ok(partnerContractService.validateByGeolocation(paymentCode, latitude, longitude));
    }

    @PostMapping("/payments/{paymentCode}/validate/admin")
    @Operation(operationId = "approveByAdmin", summary = "ADMIN_APPROVAL – Admin approuve le paiement",
            description = "Un admin Akuunda approuve manuellement le paiement via le backoffice.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Approuvé, redistribution déclenchée"),
            @ApiResponse(responseCode = "404", description = "Paiement introuvable")
    })
    public ResponseEntity<PartnerPaymentResponse> approveByAdmin(
            @PathVariable String paymentCode,
            @RequestParam String adminUsername) {
        return ResponseEntity.ok(partnerContractService.approveByAdmin(paymentCode, adminUsername));
    }

    @PostMapping("/payments/{paymentCode}/assign")
    @Operation(operationId = "assignPaymentBeneficiaries",
            summary = "DYNAMIC_ASSIGNMENT – Admin assigne les professionnels",
            description = """
                    Après réception du paiement (USDC sécurisés dans le wallet intermédiaire),
                    l'admin assigne les professionnels qui effectueront la prestation.
                    Le client reçoit un lien de confirmation et devra valider la fin du service.
                    La redistribution est déclenchée à cette confirmation.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Bénéficiaires assignés, notification envoyée au client"),
            @ApiResponse(responseCode = "400", description = "Somme des pourcentages != 100% ou mode != DYNAMIC_ASSIGNMENT"),
            @ApiResponse(responseCode = "404", description = "Paiement introuvable")
    })
    public ResponseEntity<PartnerPaymentResponse> assignBeneficiaries(
            @Parameter(description = "Code unique du paiement", required = true)
            @PathVariable String paymentCode,
            @Valid @RequestBody AssignPaymentBeneficiariesRequest request) {
        return ResponseEntity.ok(partnerContractService.assignBeneficiaries(paymentCode, request));
    }

    @PostMapping("/payments/{paymentCode}/dispute")
    @Operation(operationId = "disputePayment", summary = "Contester un paiement",
            description = """
                    Le client conteste le paiement et bloque la libération automatique.
                    Disponible pour les modes DISPUTE_WINDOW et TIME_BASED.
                    Ouvre un dossier de litige traité par l'équipe Akuunda.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Litige enregistré, libération bloquée"),
            @ApiResponse(responseCode = "400", description = "Paiement déjà contesté ou non contestable"),
            @ApiResponse(responseCode = "403", description = "Client non autorisé"),
            @ApiResponse(responseCode = "404", description = "Paiement introuvable")
    })
    public ResponseEntity<PartnerPaymentResponse> disputePayment(
            @PathVariable String paymentCode,
            @RequestParam String clientUsername,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(partnerContractService.disputePayment(paymentCode, clientUsername, reason));
    }
}
