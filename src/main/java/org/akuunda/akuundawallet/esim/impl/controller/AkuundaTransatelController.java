package org.akuunda.akuundawallet.esim.impl.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.constants.ApiConstants;
import org.akuunda.akuundawallet.esim.api.dto.ActivateEsimRequestDto;
import org.akuunda.akuundawallet.esim.api.dto.CancelRenewalRequestDto;
import org.akuunda.akuundawallet.esim.api.dto.LineStatusResponseDto;
import org.akuunda.akuundawallet.esim.api.dto.ManageCreditRequestDto;
import org.akuunda.akuundawallet.esim.api.dto.ManageDataSafeguardRequestDto;
import org.akuunda.akuundawallet.esim.api.dto.ModifySubscriptionRequestDto;
import org.akuunda.akuundawallet.esim.api.dto.MsisdnResponseDto;
import org.akuunda.akuundawallet.esim.api.dto.CatalogPageDto;
import org.akuunda.akuundawallet.esim.api.dto.WebhookRequestDto;
import org.akuunda.akuundawallet.esim.api.dto.PurchaseFlowRequestDto;
import org.akuunda.akuundawallet.esim.api.dto.RenewSubscriptionRequestDto;
import org.akuunda.akuundawallet.esim.api.dto.RenewProductRequestDto;
import org.akuunda.akuundawallet.esim.api.dto.TerminateSubscriberRequestDto;
import org.akuunda.akuundawallet.esim.api.dto.SuspendSubscriberRequestDto;
import org.akuunda.akuundawallet.esim.api.dto.ReactivateSubscriberRequestDto;
import org.akuunda.akuundawallet.esim.api.dto.SubscribeProductRequestDto;
import org.akuunda.akuundawallet.esim.api.service.AkuundaTransatelService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping(path = AkuundaTransatelController.API_SERVICES_ROOT,
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Akuunda Services - Transatel - Esim", description = """
        API d'intégration avec le service Transatel pour les opérations :
        - reupereration du catalogue des produits eSIM
        - Activation eSIM
        - Désactivation eSIM
        """)
@SecurityRequirement(name = ApiConstants.SWAGGER_BASIC_SECURITY_SCHEME)
@RequiredArgsConstructor
@Validated
public class AkuundaTransatelController {

    public static final String API_SERVICES_ROOT = "/api/internal/v1/esim";

    private final AkuundaTransatelService transatelService;

    @GetMapping("/catalog")
    @Operation(
            summary = "Récupérer le catalogue des produits eSIM",
            description = """
                    Retourne une page du catalogue Transatel. Par défaut 50 produits à partir de offset=0.
                    Utiliser offset/limit pour parcourir le catalogue ; la réponse inclut nextOffset pour la page suivante.
                    Filtres optionnels : q, country (ISO3), category,
                    continent (AFRICA, EUROPE, ASIA, OCEANIA, NORTH_AMERICA, SOUTH_AMERICA, CENTRAL_AMERICA,
                    CARIBBEAN, MIDDLE_EAST, AMERICAS, ANTARCTICA — pays du produit dans ce périmètre),
                    world=true (forfaits « monde » type préfixe WW_),
                    region (mot-clé régional sur l’id produit : AFRICA, EUROPE, LATAM, ANZ, …).
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Page catalogue récupérée",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = CatalogPageDto.class),
                                    examples = @ExampleObject(value = """
                                        {
                                          "cos": "M2MA_MWC_COS",
                                          "currentLocale": "fr-FR",
                                          "products": [],
                                          "links": [],
                                          "offset": 0,
                                          "limit": 50,
                                          "totalProducts": 120,
                                          "hasMore": true,
                                          "nextOffset": 50
                                        }
                                        """))),
                    @ApiResponse(responseCode = "500", description = "Erreur serveur",
                            content = @Content(mediaType = "application/json",
                                    examples = @ExampleObject(value = """
                                        {
                                          "status": "error",
                                          "message": "Erreur interne du serveur"
                                        }
                                        """)))
            }
    )
    public ResponseEntity<?> getCatalog(
            @RequestParam(defaultValue = "0") @Min(0) int offset,
            @RequestParam(defaultValue = "50") @Min(1) @Max(200) int limit,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String continent,
            @RequestParam(required = false) Boolean world,
            @RequestParam(required = false) String region) {
        log.info("Appel du endpoint pour récupérer le catalogue des produits eSIM");
        return transatelService.getCatalog(offset, limit, q, country, category, continent, world, region);
    }

    @GetMapping("/catalog/{productId}")
    @Operation(
            summary = "Récupérer un produit eSIM",
            description = "Retourne le détail d'un produit eSIM pour vérifier l'éligibilité.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Produit récupéré"),
                    @ApiResponse(responseCode = "500", description = "Erreur serveur")
            }
    )
    public ResponseEntity<JsonNode> getCatalogProduct(@PathVariable String productId) {
        log.info("Appel du endpoint pour récupérer un produit eSIM");
        return transatelService.getCatalogProduct(productId);
    }

    @PostMapping("/subscribe")
    @Operation(
            summary = "Souscrire apres activation eSIM",
            description = "Si la SIM n'est pas active, retourne le code d'activation/QR. Une fois active, relancer pour souscrire.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Flux terminé"),
                    @ApiResponse(responseCode = "500", description = "Erreur serveur")
            }
    )
    public ResponseEntity<Object> subscribeProduct(@RequestBody PurchaseFlowRequestDto request) {
        log.info("Appel du endpoint pour souscrire un produit eSIM + code");
        return transatelService.purchaseFlow(request);
    }


    @GetMapping("/inventory")
    @Operation(
            summary = "Récupérer l'inventaire des abonnements",
            description = "Retourne les abonnements actifs d'un msisdn via l'API d'inventaire.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Inventaire récupéré"),
                    @ApiResponse(responseCode = "500", description = "Erreur serveur")
            }
    )
    public ResponseEntity<JsonNode> getInventory(@RequestParam String msisdn,
                                                 @RequestParam(defaultValue = "active") String statuses,
                                                 @RequestParam(defaultValue = "false") boolean withBalances) {
        log.info("Appel du endpoint pour récupérer l'inventaire des abonnements");
        return transatelService.getInventory(msisdn, statuses, withBalances);
    }

    @PostMapping("/activate")
    @Operation(
            summary = "Activer une eSIM",
            description = "Active une eSIM à partir d'un sim-serial et d'un ratePlan.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Activation déclenchée"),
                    @ApiResponse(responseCode = "500", description = "Erreur serveur")
            }
    )
    public ResponseEntity<JsonNode> activateEsim(@RequestBody ActivateEsimRequestDto request) {
        log.info("Appel du endpoint pour activer une eSIM");
        return transatelService.activateEsim(request);
    }

    @GetMapping("/esim/{simSerial}")
    @Operation(
            summary = "Récupérer les détails d'une eSIM",
            description = "Récupère les détails eSIM et éventuellement l'historique.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Détails récupérés"),
                    @ApiResponse(responseCode = "500", description = "Erreur serveur")
            }
    )
    public ResponseEntity<JsonNode> getEsimDetails(@PathVariable String simSerial,
                                                   @RequestParam(defaultValue = "false") boolean history) {
        log.info("Appel du endpoint pour récupérer les détails eSIM");
        return transatelService.getEsimDetails(simSerial, history);
    }


    @GetMapping("/subscriber/{simSerial}")
    @Operation(
            summary = "Recuperer le statut subscriber",
            description = "Retourne le statut d'activation du subscriber (connectivity-management).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Statut recuperé"),
                    @ApiResponse(responseCode = "500", description = "Erreur serveur")
            }
    )
    public ResponseEntity<JsonNode> getSubscriberDetails(@PathVariable String simSerial) {
        log.info("Appel du endpoint pour recuperer le statut subscriber");
        return transatelService.getSubscriberDetails(simSerial);
    }

    @GetMapping("/subscriptions/{subscriptionId}")
    @Operation(
            summary = "Recuperer le statut d'une souscription",
            description = "Retourne le statut d'une souscription Transatel (subscriptionId). Requiert msisdn.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Statut souscription recupere"),
                    @ApiResponse(responseCode = "500", description = "Erreur serveur")
            }
    )
    public ResponseEntity<JsonNode> getSubscriptionStatus(@PathVariable String subscriptionId,
                                                          @RequestParam String msisdn) {
        log.info("Appel du endpoint pour recuperer le statut souscription");
        return transatelService.getSubscriptionStatus(subscriptionId, msisdn);
    }

    @GetMapping("/subscriptions/details/{subscriptionId}")
    @Operation(
            summary = "Recuperer le detail d'une souscription",
            description = "Retourne les details d'une souscription Transatel (subscriptionId).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Details souscription recuperes"),
                    @ApiResponse(responseCode = "500", description = "Erreur serveur")
            }
    )
    public ResponseEntity<JsonNode> getSubscriptionDetails(@PathVariable String subscriptionId,
                                                           @RequestParam(defaultValue = "false") boolean withBalances) {
        log.info("Appel du endpoint pour recuperer le detail souscription");
        return transatelService.getSubscriptionDetails(subscriptionId, withBalances);
    }

    @GetMapping("/transactions/{transactionId}")
    @Operation(
            summary = "Recuperer le statut d'une transaction",
            description = "Retourne le statut d'une transaction connectivity-management.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Statut transaction recupere"),
                    @ApiResponse(responseCode = "500", description = "Erreur serveur")
            }
    )
    public ResponseEntity<JsonNode> getTransactionStatus(@PathVariable String transactionId) {
        log.info("Appel du endpoint pour recuperer le statut transaction");
        return transatelService.getTransactionStatus(transactionId);
    }

    @GetMapping("/internet-balance")
    @Operation(
            summary = "Récupérer le solde internet (crédit) par MSISDN",
            description = "Retourne le solde internet Transatel associé à un MSISDN.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Solde récupéré"),
                    @ApiResponse(responseCode = "500", description = "Erreur serveur")
            }
    )
    public ResponseEntity<JsonNode> getInternetBalance(@RequestParam String msisdn) {
        log.info("Appel du endpoint pour récupérer le solde internet");
        return transatelService.getInternetBalance(msisdn);
    }

    @GetMapping("/msisdn/{userId}")
    @Operation(
            summary = "Récupérer le MSISDN d'un utilisateur",
            description = "Retourne le MSISDN associé à l'utilisateur (dernier simSerial attribué).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "MSISDN récupéré"),
                    @ApiResponse(responseCode = "404", description = "MSISDN introuvable"),
                    @ApiResponse(responseCode = "400", description = "Requête invalide")
            }
    )
    public ResponseEntity<MsisdnResponseDto> getMsisdnForUser(@PathVariable String userId) {
        log.info("Appel du endpoint pour récupérer le MSISDN d'un utilisateur");
        return transatelService.getMsisdnForUser(userId);
    }

    @GetMapping("/status/{userId}")
    @Operation(
            summary = "Récupérer le statut eSIM d'un utilisateur",
            description = "Retourne le statut ligne/forfait pour un utilisateur (suspendu, résilié, en cours, sans SIM).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Statut récupéré"),
                    @ApiResponse(responseCode = "400", description = "Requête invalide")
            }
    )
    public ResponseEntity<LineStatusResponseDto> getLineStatusForUser(@PathVariable String userId) {
        log.info("Appel du endpoint pour récupérer le statut eSIM d'un utilisateur");
        return transatelService.getLineStatusForUser(userId);
    }

    @PostMapping("/renew")
    @Operation(
            summary = "Renouveler la dernière souscription eSIM",
            description = "Renouvelle le dernier forfait souscrit pour un utilisateur.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Renouvellement effectué"),
                    @ApiResponse(responseCode = "400", description = "Requête invalide"),
                    @ApiResponse(responseCode = "500", description = "Erreur serveur")
            }
    )
    public ResponseEntity<Object> renewLastSubscription(@RequestBody RenewSubscriptionRequestDto request) {
        log.info("Appel du endpoint pour renouveler une souscription eSIM");
        return transatelService.renewLastSubscription(request);
    }

    @PostMapping("/renew-product")
    @Operation(
            summary = "Renouveler un forfait actif",
            description = "Renouvelle un forfait actif (productId) pour un utilisateur.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Renouvellement effectué"),
                    @ApiResponse(responseCode = "400", description = "Requête invalide"),
                    @ApiResponse(responseCode = "500", description = "Erreur serveur")
            }
    )
    public ResponseEntity<Object> renewProduct(@RequestBody RenewProductRequestDto request) {
        log.info("Appel du endpoint pour renouveler un forfait eSIM");
        return transatelService.renewProduct(request);
    }

    @PostMapping("/terminate/{simSerial}")
    @Operation(
            summary = "Résilier un abonné eSIM",
            description = "Résilie un abonné eSIM via le sim-serial (opération irréversible).",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Résiliation acceptée"),
                    @ApiResponse(responseCode = "400", description = "Requête invalide"),
                    @ApiResponse(responseCode = "500", description = "Erreur serveur")
            }
    )
    public ResponseEntity<JsonNode> terminateSubscriber(@PathVariable String simSerial,
                                                        @RequestBody TerminateSubscriberRequestDto request) {
        log.info("Appel du endpoint pour résilier un abonné eSIM");
        return transatelService.terminateSubscriber(simSerial, request.getRatePlan());
    }

    @PostMapping("/suspend/{simSerial}")
    @Operation(
            summary = "Suspendre un abonné eSIM",
            description = "Suspend temporairement un abonné eSIM via le sim-serial.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Suspension acceptée"),
                    @ApiResponse(responseCode = "400", description = "Requête invalide"),
                    @ApiResponse(responseCode = "500", description = "Erreur serveur")
            }
    )
    public ResponseEntity<JsonNode> suspendSubscriber(@PathVariable String simSerial,
                                                      @RequestBody SuspendSubscriberRequestDto request) {
        log.info("Appel du endpoint pour suspendre un abonné eSIM");
        return transatelService.suspendSubscriber(simSerial, request.getRatePlan());
    }

    @PostMapping("/reactivate/{simSerial}")
    @Operation(
            summary = "Réactiver un abonné eSIM",
            description = "Réactive un abonné eSIM suspendu via le sim-serial.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Réactivation acceptée"),
                    @ApiResponse(responseCode = "400", description = "Requête invalide"),
                    @ApiResponse(responseCode = "500", description = "Erreur serveur")
            }
    )
    public ResponseEntity<JsonNode> reactivateSubscriber(@PathVariable String simSerial,
                                                         @RequestBody ReactivateSubscriberRequestDto request) {
        log.info("Appel du endpoint pour réactiver un abonné eSIM");
        return transatelService.reactivateSubscriber(simSerial, request.getRatePlan());
    }

    @PostMapping("/webhooks")
    @Operation(
            summary = "Créer un webhook Transatel",
            description = "Crée un webhook pour les événements Transatel.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Webhook créé"),
                    @ApiResponse(responseCode = "500", description = "Erreur serveur")
            }
    )
    public ResponseEntity<JsonNode> createWebhook(@RequestBody WebhookRequestDto request) {
        log.info("Appel du endpoint pour créer un webhook Transatel");
        return transatelService.createWebhook(request);
    }

    @PostMapping(value = "/webhooks/events", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            summary = "Recevoir un webhook Transatel",
            description = "Reçoit les événements Transatel et met à jour le statut des SIM locales.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Événement reçu"),
                    @ApiResponse(responseCode = "400", description = "Requête invalide"),
                    @ApiResponse(responseCode = "403", description = "Signature invalide"),
                    @ApiResponse(responseCode = "500", description = "Erreur serveur")
            }
    )
    public ResponseEntity<Void> handleWebhookEvent(@RequestBody String payload,
                                                   @RequestHeader(value = "X-TSL-Signature-256", required = false)
                                                   String signatureHeader) {
        log.info("Webhook Transatel recu");
        return transatelService.handleWebhookEvent(payload, signatureHeader);
    }

    // -------------------------------------------------------------------------
    // OCS Options — Catalog
    // -------------------------------------------------------------------------

    @GetMapping("/catalog/options")
    @Operation(
            summary = "Récupérer toutes les options du catalogue OCS",
            description = "Retourne les options disponibles pour le COS configuré (ex: dataSafeguard).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Options récupérées"),
                    @ApiResponse(responseCode = "500", description = "Erreur serveur")
            }
    )
    public ResponseEntity<JsonNode> getCatalogOptions() {
        log.info("Appel getCatalogOptions");
        return transatelService.getCatalogOptions();
    }

    @GetMapping("/catalog/options/data-safeguard")
    @Operation(
            summary = "Récupérer l'option dataSafeguard du catalogue OCS",
            description = "Retourne les détails de l'option dataSafeguard (plafond de dépense, états possibles).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Option dataSafeguard récupérée"),
                    @ApiResponse(responseCode = "404", description = "Option introuvable"),
                    @ApiResponse(responseCode = "500", description = "Erreur serveur")
            }
    )
    public ResponseEntity<JsonNode> getCatalogDataSafeguardOption() {
        log.info("Appel getCatalogDataSafeguardOption");
        return transatelService.getCatalogDataSafeguardOption();
    }

    // -------------------------------------------------------------------------
    // OCS Options — Inventory
    // -------------------------------------------------------------------------

    @GetMapping("/subscriber-options")
    @Operation(
            summary = "Récupérer les options actives d'un abonné",
            description = "Retourne l'état des options (dataSafeguard) pour un MSISDN donné.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Options abonné récupérées"),
                    @ApiResponse(responseCode = "400", description = "msisdn manquant"),
                    @ApiResponse(responseCode = "404", description = "Abonné introuvable"),
                    @ApiResponse(responseCode = "500", description = "Erreur serveur")
            }
    )
    public ResponseEntity<JsonNode> getSubscriberOptions(@RequestParam String msisdn) {
        log.info("Appel getSubscriberOptions pour msisdn={}", msisdn);
        return transatelService.getSubscriberOptions(msisdn);
    }

    // -------------------------------------------------------------------------
    // OCS Products — Subscriptions (orderType passthrough)
    // -------------------------------------------------------------------------

    @PostMapping("/ocs-subscribe")
    @Operation(
            summary = "Soumettre un ordre de souscription OCS (passthrough direct)",
            description = """
                    Appel direct vers l'API OCS Transatel POST /api/orders/products.
                    Permet de créer ou modifier des souscriptions à des plans OCS (voix, SMS, data).
                    orderType supportés :
                    - subscribe : ajouter un produit à l'inventaire abonné
                    - cancelRenewal : stopper le renouvellement automatique
                    - modify : modifier les balances ou la date d'expiration
                    - terminate : résilier immédiatement la souscription
                    """,
            responses = {
                    @ApiResponse(responseCode = "201", description = "Ordre soumis avec succès"),
                    @ApiResponse(responseCode = "400", description = "Requête invalide"),
                    @ApiResponse(responseCode = "403", description = "Accès refusé"),
                    @ApiResponse(responseCode = "500", description = "Erreur serveur"),
                    @ApiResponse(responseCode = "501", description = "Non implémenté")
            }
    )
    public ResponseEntity<JsonNode> ocsSubscribe(@RequestBody SubscribeProductRequestDto request) {
        log.info("Appel ocsSubscribe orderType={} pour msisdn={}", request.getOrderType(), request.getMsisdn());
        return transatelService.subscribeProduct(request);
    }

    @PostMapping("/cancel-renewal")
    @Operation(
            summary = "Annuler le renouvellement automatique d'une souscription récurrente",
            description = "Soumet un ordre OCS de type cancelRenewal pour stopper le renouvellement d'un forfait récurrent.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Annulation renouvellement soumise"),
                    @ApiResponse(responseCode = "400", description = "Requête invalide"),
                    @ApiResponse(responseCode = "500", description = "Erreur serveur")
            }
    )
    public ResponseEntity<JsonNode> cancelRenewal(@RequestBody CancelRenewalRequestDto request) {
        log.info("Appel cancelRenewal pour msisdn={}", request.getMsisdn());
        return transatelService.cancelRenewal(request);
    }

    @PostMapping("/modify-subscription")
    @Operation(
            summary = "Modifier une souscription active",
            description = "Soumet un ordre OCS de type modify pour changer les balances ou la date d'expiration d'une souscription.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Modification soumise"),
                    @ApiResponse(responseCode = "400", description = "Requête invalide"),
                    @ApiResponse(responseCode = "500", description = "Erreur serveur")
            }
    )
    public ResponseEntity<JsonNode> modifySubscription(@RequestBody ModifySubscriptionRequestDto request) {
        log.info("Appel modifySubscription pour msisdn={}", request.getMsisdn());
        return transatelService.modifySubscription(request);
    }

    // -------------------------------------------------------------------------
    // OCS Options — Subscriptions
    // -------------------------------------------------------------------------

    @PostMapping("/credit")
    @Operation(
            summary = "Gérer le solde crédit d'un abonné",
            description = """
                    Permet d'ajouter, retirer ou définir le solde crédit d'un abonné.
                    Modes supportés : add, remove, set.
                    """,
            responses = {
                    @ApiResponse(responseCode = "201", description = "Opération crédit effectuée"),
                    @ApiResponse(responseCode = "400", description = "Requête invalide"),
                    @ApiResponse(responseCode = "500", description = "Erreur serveur")
            }
    )
    public ResponseEntity<JsonNode> manageCredit(@RequestBody ManageCreditRequestDto request) {
        log.info("Appel manageCredit mode={} pour msisdn={}", request.getCredit() != null ? request.getCredit().getMode() : null, request.getMsisdn());
        return transatelService.manageCredit(request);
    }

    @GetMapping("/exchange-rate")
    @Operation(
            summary = "Taux de change entre deux devises",
            description = "Retourne le taux de change de 1 unité de la devise source vers la devise cible (via CurrencyFreaks).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Taux récupéré"),
                    @ApiResponse(responseCode = "400", description = "Paramètres manquants"),
                    @ApiResponse(responseCode = "502", description = "Erreur CurrencyFreaks")
            }
    )
    public ResponseEntity<java.util.Map<String, String>> getExchangeRate(
            @RequestParam String from,
            @RequestParam String to) {
        return transatelService.getExchangeRate(from, to);
    }

    @PostMapping("/options/data-safeguard")
    @Operation(
            summary = "Gérer l'option dataSafeguard d'un abonné",
            description = """
                    Change l'état de l'option dataSafeguard pour un abonné.
                    États supportés : active, temporarily-inactive, permanently-inactive.
                    """,
            responses = {
                    @ApiResponse(responseCode = "201", description = "État dataSafeguard mis à jour"),
                    @ApiResponse(responseCode = "400", description = "Requête invalide"),
                    @ApiResponse(responseCode = "500", description = "Erreur serveur")
            }
    )
    public ResponseEntity<JsonNode> manageDataSafeguard(@RequestBody ManageDataSafeguardRequestDto request) {
        log.info("Appel manageDataSafeguard state={} pour msisdn={}", request.getState(), request.getMsisdn());
        return transatelService.manageDataSafeguard(request);
    }

    @GetMapping(value = "/activate-page", produces = MediaType.TEXT_HTML_VALUE)
    @Operation(
            summary = "Page HTML d'activation eSIM",
            description = """
                    Retourne une page HTML avec un bouton LPA: pour déclencher l'installation
                    native eSIM sur iOS/Android depuis Safari, sans QR code.
                    Ce endpoint est public (pas de JWT requis) car Safari ne peut pas
                    transmettre d'en-têtes d'authentification.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Page HTML retournée",
                            content = @Content(mediaType = "text/html")),
                    @ApiResponse(responseCode = "400", description = "Paramètre lpa invalide")
            }
    )
    public ResponseEntity<String> getActivationPage(@RequestParam String lpa) {
        // Validate LPA format: 1$<SM-DP+ address>$<matching ID>
        if (lpa == null || !lpa.matches("^1\\$[A-Za-z0-9._-]+\\$[A-Za-z0-9._-]+$")) {
            log.warn("Tentative d'activation eSIM avec un code LPA invalide");
            return ResponseEntity.badRequest().body("Code LPA invalide");
        }
        log.info("Page d'activation eSIM demandée");

        String html = """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width,initial-scale=1">
                  <title>Activer eSIM – Akuunda Pay</title>
                  <style>
                    *{box-sizing:border-box;margin:0;padding:0}
                    body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',sans-serif;
                      background:#f2f2f7;min-height:100vh;display:flex;
                      align-items:center;justify-content:center;padding:20px}
                    .card{background:#fff;border-radius:20px;padding:48px 28px 40px;
                      text-align:center;max-width:360px;width:100%;
                      box-shadow:0 4px 32px rgba(0,0,0,.12)}
                    .icon{font-size:64px;margin-bottom:20px;line-height:1}
                    h1{color:#520C5A;font-size:22px;font-weight:700;margin-bottom:10px}
                    p{color:#6b6b6b;font-size:15px;line-height:1.5;margin-bottom:32px}
                    .btn{display:block;background:#520C5A;color:#fff;padding:18px 24px;
                      border-radius:14px;text-decoration:none;font-size:17px;
                      font-weight:600;letter-spacing:.2px;transition:opacity .15s}
                    .btn:active{opacity:.75}
                    .note{margin-top:20px;color:#aaa;font-size:12px}
                  </style>
                </head>
                <body>
                  <div class="card">
                    <div class="icon">📱</div>
                    <h1>Activer votre eSIM</h1>
                    <p>Appuyez sur le bouton ci-dessous pour installer votre eSIM directement sur cet appareil.</p>
                    <a class="btn" href="LPA:%s">Activer l'eSIM</a>
                    <p class="note">Akuunda Pay</p>
                  </div>
                </body>
                </html>
                """.formatted(lpa);

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(html);
    }
}
