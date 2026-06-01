package org.akuunda.akuundawallet.wallet.service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.constants.ApiConstants;
import org.akuunda.akuundawallet.wallet.api.dto.external.*;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaMeldServiceClient;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping(path = AkuundaMeldController.API_SERVICES_ROOT, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(
        name = "Akuunda Services - Meld",
        description = """
                API complète pour l'intégration Meld :
                - Récupération des monnaies fiat et crypto
                - Méthodes de paiement disponibles
                - Quotes en temps réel pour conversions crypto/fiat
                - Gestion de sessions widget Meld (achat crypto)
                - Consultation des limites d'achat
                """
)
@SecurityRequirement(name = ApiConstants.SWAGGER_BASIC_SECURITY_SCHEME)
@RequiredArgsConstructor
public class AkuundaMeldController {

    public static final String API_SERVICES_ROOT = "/api/internal/v1/meld";

    private final AkuundaMeldServiceClient akuundaMeldServiceClient;

    // ------------------------------------------------------------------------
    // IP EXTRACTION UTILITY
    // ------------------------------------------------------------------------
    private static final String[] IP_HEADERS = {
            "X-Forwarded-For",
            "X-Real-IP",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_CLIENT_IP",
            "CF-Connecting-IP",        // Cloudflare
            "True-Client-IP",          // Akamai
            "X-Cluster-Client-IP"
    };

    /**
     * Récupère l'adresse IP réelle du client depuis les headers HTTP.
     * Gère les proxys, load balancers, et CDNs (Cloudflare, etc.)
     */
    private String getClientIpAddress(HttpServletRequest request) {
        if (request == null) {
            return null;
        }

        for (String header : IP_HEADERS) {
            String ip = request.getHeader(header);
            if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
                // X-Forwarded-For peut contenir plusieurs IPs séparées par des virgules
                // La première est l'IP originale du client
                String clientIp = ip.split(",")[0].trim();
                log.debug("Client IP found in header {}: {}", header, clientIp);
                return clientIp;
            }
        }

        // Fallback sur l'IP directe de la requête
        String remoteAddr = request.getRemoteAddr();
        log.debug("Using remote address as client IP: {}", remoteAddr);
        return remoteAddr;
    }

    // ------------------------------------------------------------------------
    // QUOTES
    // ------------------------------------------------------------------------
    @PostMapping("/quotes")
    @Operation(
            summary = "Obtenir un quote temps réel pour une conversion crypto",
            description = """
                    Récupère un quote Meld pour convertir un montant fiat en crypto ou inversement.
                    Utilise le wallet de l'utilisateur pour préparer la transaction.
                    """,
            requestBody = @RequestBody(
                    required = true,
                    description = "Informations pour obtenir un quote",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = MeldQuoteRequest.class),
                            examples = @ExampleObject(
                                    value = """
                                            {
                                              "username": "user123",
                                              "sourceCurrencyCode": "EUR",
                                              "destinationCurrencyCode": "BTC",
                                              "sourceAmount": "100",
                                              "countryCode": "FR"
                                            }
                                            """
                            )
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Quote récupéré avec succès",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = MeldQuoteResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Utilisateur ou wallet introuvable"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<MeldQuoteResponse> getMeldQuotes(
            @Valid @org.springframework.web.bind.annotation.RequestBody MeldQuoteRequest request) {
        log.info("Getting Meld quote for user {}: {} {} -> {}", request.getUsername(),
                request.getSourceCurrencyCode(), request.getSourceAmount(), request.getDestinationCurrencyCode());
        return akuundaMeldServiceClient.getMeldQuotes(
                request.getUsername(),
                request.getSourceCurrencyCode(),
                request.getDestinationCurrencyCode(),
                request.getSourceAmount() != null ? String.valueOf(request.getSourceAmount()) : null,
                request.getCountryCode(),
                request.getServiceProviders() != null && !request.getServiceProviders().isEmpty()
                        ? request.getServiceProviders().get(0) : null
        );
    }

    // ------------------------------------------------------------------------
    // SESSIONS WIDGET
    // ------------------------------------------------------------------------
    @PostMapping("/session")
    @Operation(
            summary = "Créer une session widget pour achat et vente de crypto",
            description = """
                    Crée une session Meld pour permettre à l'utilisateur d'acheter et de vendre de la crypto via le widget.
                    Le paramètre sessionType doit être "BUY" pour achat ou "SELL" pour la vente.
                    
                    **Important pour UNLIMIT:** L'IP du client est automatiquement récupérée depuis les headers HTTP
                    (X-Forwarded-For, X-Real-IP, etc.) pour éviter les problèmes de mismatch IP.
                    Si clientIpAddress est fourni dans la requête, il sera utilisé en priorité.
                    """
    )
    public ResponseEntity<MeldSessionResponse> createMeldSession(
            @Valid @org.springframework.web.bind.annotation.RequestBody WidgetSessionData request,
            HttpServletRequest httpServletRequest) {
        
        // ★ Récupérer l'IP depuis les headers HTTP si non fournie par le client
        String clientIp = request.getClientIpAddress();
        if (clientIp == null || clientIp.isEmpty()) {
            clientIp = getClientIpAddress(httpServletRequest);
            log.info("Client IP not provided, using IP from headers: {}", clientIp);
        } else {
            log.info("Using client-provided IP: {}", clientIp);
        }
        
        log.info("Creating Meld session for user {} with provider {}, IP: {}", 
                request.getUserName(), request.getServiceProvider(), clientIp);
        
        return akuundaMeldServiceClient.createMeldSession(
                request.getUserName(),
                request.getServiceProvider(),
                request.getSourceCurrencyCode(),
                request.getDestinationCurrencyCode(),
                request.getSourceAmount(),
                request.getSessionType(),
                request.getCountryCode(),
                request.getWalletAddress(),
                clientIp  // ★ Utiliser l'IP extraite des headers
        );
    }

    // ------------------------------------------------------------------------
    // COUNTRY DEFAULTS
    // ------------------------------------------------------------------------
    @GetMapping("/countries/{countryCode}/defaults")
    @Operation(
            summary = "Récupérer les paramètres par défaut pour un pays",
            description = "Retourne la configuration par défaut (monnaie, méthodes de paiement) pour le pays spécifié."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Defaults récupérés avec succès"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<List<MeldCountryDefaults>> getCountryDefaults(
            @PathVariable String countryCode) {
        log.info("Getting country defaults for {}", countryCode);
        return akuundaMeldServiceClient.getCountryDefaults(countryCode);
    }

    // ------------------------------------------------------------------------
    // FIAT CURRENCIES
    // ------------------------------------------------------------------------
    @GetMapping("/countries/{countryCode}/fiat-currencies")
    @Operation(
            summary = "Récupérer les monnaies fiat disponibles pour un pays",
            description = "Retourne la liste des monnaies fiat disponibles pour le pays spécifié."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Monnaies fiat récupérées avec succès"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<List<MeldFiatCurrency>> getFiatCurrencies(
            @PathVariable String countryCode) {
        log.info("Getting fiat currencies for {}", countryCode);
        return akuundaMeldServiceClient.getFiatCurrencies(countryCode);
    }

    // ------------------------------------------------------------------------
    // PAYMENT METHODS
    // ------------------------------------------------------------------------
    @GetMapping("/payment-methods")
    @Operation(
            summary = "Récupérer les méthodes de paiement disponibles pour une monnaie fiat",
            description = "Retourne les méthodes de paiement filtrées par monnaie fiat."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Méthodes de paiement récupérées avec succès"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<List<MeldPaymentMethod>> getPaymentMethods(
            @RequestParam String fiatCurrency) {
        log.info("Getting payment methods for fiat currency {}", fiatCurrency);
        return akuundaMeldServiceClient.getPaymentMethods(fiatCurrency);
    }

    // ------------------------------------------------------------------------
    // CRYPTO CURRENCIES
    // ------------------------------------------------------------------------
    @GetMapping("/countries/{countryCode}/crypto-currencies")
    @Operation(
            summary = "Récupérer les crypto-monnaies disponibles pour un pays",
            description = "Retourne la liste des crypto-monnaies disponibles pour le pays spécifié."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Crypto-monnaies récupérées avec succès"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<MeldCryptoCurrenciesResponse> getCryptoCurrencies(
            @PathVariable String countryCode) {
        log.info("Getting crypto currencies for {}", countryCode);
        return akuundaMeldServiceClient.getCryptoCurrencies(countryCode);
    }

    // ------------------------------------------------------------------------
    // PURCHASE LIMITS
    // ------------------------------------------------------------------------
    @GetMapping("/purchase-limits")
    @Operation(
            summary = "Récupérer les limites d'achat fiat",
            description = "Retourne les limites d'achat pour toutes les monnaies fiat disponibles."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Limites récupérées avec succès"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<List<MeldPurchaseLimit>> getPurchaseLimits() {
        log.info("Getting purchase limits");
        return akuundaMeldServiceClient.getPurchaseLimits();
    }

    // ------------------------------------------------------------------------
    // TRANSACTION STATUS — Search transactions
    // ------------------------------------------------------------------------
    @GetMapping("/transactions")
    @Operation(
            summary = "Rechercher des transactions Meld",
            description = """
                    Recherche des transactions Meld avec filtres optionnels.
                    Permet de vérifier le statut des transactions en cours.
                    
                    Statuts possibles : PENDING_CREATED, PENDING, PROCESSING, AUTHORIZED,
                    AUTHORIZATION_EXPIRED, SETTLING, ONRAMP_SETTLED, SETTLED, REFUNDED,
                    DECLINED, CANCELLED, FAILED, ERROR, VOIDED, TWO_FA_REQUIRED, TWO_FA_PROVIDED
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Transactions récupérées avec succès",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = MeldTransactionDetails.class)
                    )
            ),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<List<MeldTransactionDetails>> searchTransactions(
            @RequestParam(required = false) String statuses,
            @RequestParam(required = false) String externalCustomerIds,
            @RequestParam(required = false) Integer limit) {
        log.info("Searching Meld transactions — statuses={}, customerIds={}, limit={}",
                statuses, externalCustomerIds, limit);
        return akuundaMeldServiceClient.searchTransactions(statuses, externalCustomerIds, limit);
    }

    // ------------------------------------------------------------------------
    // TRANSACTION STATUS — Get by transaction ID
    // ------------------------------------------------------------------------
    @GetMapping("/transactions/{transactionId}")
    @Operation(
            summary = "Récupérer le statut d'une transaction Meld par ID",
            description = """
                    Récupère les détails et le statut d'une transaction Meld via son identifiant.
                    Met à jour automatiquement le statut en base (MeldTransaction + Operation + Wallet).
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Détails de la transaction récupérés avec succès",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = MeldTransactionDetails.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Transaction non trouvée"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<MeldTransactionDetails> getTransactionById(
            @PathVariable String transactionId) {
        log.info("Getting Meld transaction details for {}", transactionId);
        return akuundaMeldServiceClient.getTransactionDetails(transactionId);
    }

    // ------------------------------------------------------------------------
    // TRANSACTION STATUS — Get by session ID (offramp)
    // ------------------------------------------------------------------------
    @GetMapping("/transactions/session/{sessionId}")
    @Operation(
            summary = "Récupérer une transaction Meld par session ID",
            description = """
                    Récupère une transaction depuis le provider via le session ID.
                    Principalement utilisé pour les scénarios offramp (vente de crypto)
                    après que l'utilisateur a terminé son action dans le widget.
                    """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Transaction récupérée avec succès",
                    content = @Content(
                            mediaType = MediaType.APPLICATION_JSON_VALUE,
                            schema = @Schema(implementation = MeldTransactionDetails.class)
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Session non trouvée"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<MeldTransactionDetails> getTransactionBySessionId(
            @PathVariable String sessionId) {
        log.info("Getting Meld transaction by session ID {}", sessionId);
        return akuundaMeldServiceClient.getTransactionBySessionId(sessionId);
    }
}
