package org.akuunda.akuundawallet.esim.impl.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.TextNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.esim.api.dao.EsimSimSerialRepository;
import org.akuunda.akuundawallet.esim.api.dto.ActivateEsimRequestDto;
import org.akuunda.akuundawallet.esim.api.dto.CancelRenewalRequestDto;
import org.akuunda.akuundawallet.esim.api.dto.ManageCreditRequestDto;
import org.akuunda.akuundawallet.esim.api.dto.ManageDataSafeguardRequestDto;
import org.akuunda.akuundawallet.esim.api.dto.ModifySubscriptionRequestDto;
import org.akuunda.akuundawallet.esim.api.dto.MsisdnResponseDto;
import org.akuunda.akuundawallet.esim.api.dto.PurchaseFlowRequestDto;
import org.akuunda.akuundawallet.esim.api.dto.CatalogPageDto;
import org.akuunda.akuundawallet.esim.api.dto.DisplayDto;
import org.akuunda.akuundawallet.esim.api.dto.LinkDto;
import org.akuunda.akuundawallet.esim.api.dto.ProductDto;
import org.akuunda.akuundawallet.esim.api.dto.PurchaseFlowResponseDto;
import org.akuunda.akuundawallet.esim.api.dto.RenewSubscriptionRequestDto;
import org.akuunda.akuundawallet.esim.api.dto.RenewProductRequestDto;
import org.akuunda.akuundawallet.esim.api.dto.SubscribeProductRequestDto;
import org.akuunda.akuundawallet.esim.api.dto.WebhookRequestDto;
import org.akuunda.akuundawallet.esim.api.entities.EsimSimSerial;
import org.akuunda.akuundawallet.esim.api.entities.EsimSimSerialStatus;
import org.akuunda.akuundawallet.esim.api.service.AkuundaTransatelService;
import org.akuunda.akuundawallet.esim.catalog.EsimCatalogContinentIndex;
import org.akuunda.akuundawallet.esim.catalog.EsimCatalogGeo;
import org.akuunda.akuundawallet.esim.catalog.EsimRrpXlsxCatalogOverlay;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.json.JSONObject;
import org.akuunda.akuundawallet.wallet.api.dto.external.AkuundaTransfertRequest;
import org.akuunda.akuundawallet.wallet.api.service.WalletService;
import org.akuunda.akuundawallet.wallet.service.AkuundaTransactionService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.CurrencyFreaksClientService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@Service
@RequiredArgsConstructor
@Slf4j
public class AkuundaTransatelServiceImpl implements AkuundaTransatelService {

    private final HttpClient client;
    private final ObjectMapper objectMapper;

    @Value("${esim.api.base-url:}")
    private String baseUrl;

    @Value("${esim.api.catalog-url:}")
    private String catalogUrl;

    @Value("${esim.api.auth-url:}")
    private String authUrl;

    @Value("${esim.api.auth-mode:legacy}")
    private String authMode;

    @Value("${esim.api.username:}")
    private String apiUsername;

    @Value("${esim.api.password:}")
    private String apiPassword;

    @Value("${esim.api.subscriptions-url:}")
    private String subscriptionsUrl;
    @Value("${esim.api.subscription-status-url:https://api.transatel.com/ocs/inventory/api/subscriptions/products}")
    private String subscriptionStatusUrl;
    @Value("${esim.api.transaction-url:https://api.transatel.com/connectivity-management/subscribers/api/transactions}")
    private String transactionUrl;

    @Value("${esim.api.accept-language:fr}")
    private String acceptLanguage;

    @Value("${esim.api.inventory-url:}")
    private String inventoryUrl;

    @Value("${esim.api.activation-url:}")
    private String activationUrl;

    @Value("${esim.api.esim-details-url:}")
    private String esimDetailsUrl;

    @Value("${esim.api.credit-url:}")
    private String creditUrl;

    @Value("${esim.api.catalog-options-url:}")
    private String catalogOptionsUrl;

    @Value("${esim.api.subscriber-options-url:}")
    private String subscriberOptionsUrl;

    @Value("${esim.api.credit-orders-url:}")
    private String creditOrdersUrl;

    @Value("${esim.api.data-safeguard-orders-url:}")
    private String dataSafeguardOrdersUrl;

    @Value("${esim.api.subscriber-url:}")
    private String subscriberUrl;

    @Value("${esim.api.webhooks-url:}")
    private String webhooksUrl;
    @Value("${esim.api.mvno-ref:}")
    private String mvnoRef;

    @Value("${esim.api.payment-provider:customer}")
    private String defaultPaymentProvider;
    @Value("${esim.api.free-payment-provider:}")
    private String freePaymentProvider;

    @Value("${esim.api.rate-plan:}")
    private String defaultRatePlan;

    @Value("${esim.webhook.secret:}")
    private String webhookSecret;

    @Value("${esim.payment.check-enabled:false}")
    private boolean balanceCheckEnabled;


    @Value("${esim.api.key:}")
    private String apiKey;

    @Value("${esim.api.secret:}")
    private String apiSecret;

    @Value("${transatel.debug.token:}")
    private String debugToken;

    @Value("${esim.api.allow-debug-token:false}")
    private boolean allowDebugToken;

    @Value("${esim.api.catalog-default-page-size:50}")
    private int catalogDefaultPageSize;

    @Value("${esim.api.catalog-max-page-size:200}")
    private int catalogMaxPageSize;

    @Value("${esim.payment.service-wallet-username:}")
    private String serviceWalletUsername;

    @Value("${esim.payment.default-currency:EUR}")
    private String serviceWalletCurrency;

    private final EsimSimSerialRepository simSerialRepository;
    private final WalletService walletService;
    private final AkuundaTransactionService akuundaTransactionService;
    private final CurrencyFreaksClientService currencyFreaksClientService;
    private final UserRepository userRepository;

    private final EsimRrpXlsxCatalogOverlay esimRrpXlsxCatalogOverlay;








    /**
     * Cette méthode permet de récupérer le catalogue des produits en appelant l'API Transatel.
     * Elle commence par obtenir un jeton d'accès, puis utilise ce jeton
     * pour effectuer une requête GET
     * vers l'endpoint du catalogue. En cas d'erreur, elle retourne une réponse appropriée.
     */
    @Override
    public ResponseEntity<?> getCatalog(int offset, int limit, String q, String country, String category,
            String continent, Boolean world, String region) {
        log.info("recuperer le catalogue des produits (offset={}, limit={}, q={}, country={}, category={}, continent={}, world={}, region={})",
                offset, limit, q, country, category, continent, world, region);
        log.debug("recuperer le catalogue des produits");
        try {
            if (catalogUrl == null || catalogUrl.isBlank() || authUrl == null || authUrl.isBlank()
                    || apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank()) {
                return errorJson(HttpStatus.SERVICE_UNAVAILABLE, "ESIM_CONFIG_MISSING",
                        "Configuration eSIM manquante (catalog/auth/api credentials).");
            }
            int resolvedLimit = limit <= 0 ? catalogDefaultPageSize : limit;
            resolvedLimit = Math.min(resolvedLimit, Math.max(1, catalogMaxPageSize));
            int resolvedOffset = Math.max(0, offset);

            String token = getAccessToken();
            HttpRequest request = createRequestBuilder(catalogUrl, token).GET().build();
            ResponseEntity<JsonNode> upstream = sendJsonRequestWithStatus(request);
            if (!upstream.getStatusCode().is2xxSuccessful() || upstream.getBody() == null) {
                return upstream;
            }
            JsonNode body = upstream.getBody();
            CatalogPageDto page = buildCatalogPage(body, resolvedOffset, resolvedLimit, q, country, category,
                    continent, world, region);
            return ResponseEntity.ok(page);
        } catch (IllegalArgumentException e) {
            return errorJson(HttpStatus.BAD_REQUEST, resolveBusinessErrorCode(e.getMessage()), e.getMessage());
        } catch (Exception e) {
            log.error("Erreur lors de la recuperation du catalogue : {}", e.getMessage(), e);
            return errorJson(HttpStatus.BAD_GATEWAY, "ESIM_CATALOG_UPSTREAM_ERROR",
                    "Erreur lors de l'appel catalogue Transatel.");
        }
    }

    private CatalogPageDto buildCatalogPage(JsonNode body, int offset, int limit, String q, String country, String category,
            String continent, Boolean world, String region) {
        CatalogPageDto dto = new CatalogPageDto();
        dto.setCos(textOrNull(body, "cos"));
        dto.setCurrentLocale(textOrNull(body, "currentLocale"));
        dto.setOffset(offset);
        dto.setLimit(limit);

        JsonNode productsNode = body.path("products");
        if (!productsNode.isArray()) {
            dto.setProducts(new ArrayList<>());
            dto.setTotalProducts(0);
            dto.setHasMore(false);
            dto.setNextOffset(null);
            dto.setLinks(parseLinks(body.path("links")));
            return dto;
        }

        boolean filtered = catalogFiltersActive(q, country, category, continent, world, region);
        if (filtered) {
            List<ProductDto> all = objectMapper.convertValue(productsNode, new TypeReference<List<ProductDto>>() { });
            if (all == null) {
                all = new ArrayList<>();
            }
            enrichCatalogDisplay(all);
            esimRrpXlsxCatalogOverlay.apply(all);
            List<ProductDto> matched = applyCatalogFilters(all, q, country, category, continent, world, region);
            int total = matched.size();
            dto.setTotalProducts(total);
            dto.setLinks(parseLinks(body.path("links")));
            if (offset >= total) {
                dto.setProducts(new ArrayList<>());
                dto.setHasMore(false);
                dto.setNextOffset(null);
                return dto;
            }
            int pageEnd = Math.min(offset + limit, total);
            dto.setProducts(new ArrayList<>(matched.subList(offset, pageEnd)));
            dto.setHasMore(pageEnd < total);
            dto.setNextOffset(dto.isHasMore() ? pageEnd : null);
            return dto;
        }

        int total = productsNode.size();
        dto.setTotalProducts(total);
        int end = Math.min(offset + limit, total);
        ArrayNode sliced = objectMapper.createArrayNode();
        for (int i = offset; i < end; i++) {
            sliced.add(productsNode.get(i));
        }
        List<ProductDto> products = objectMapper.convertValue(sliced, new TypeReference<List<ProductDto>>() { });
        if (products != null) {
            enrichCatalogDisplay(products);
            esimRrpXlsxCatalogOverlay.apply(products);
        }
        dto.setProducts(products == null ? new ArrayList<>() : products);
        dto.setHasMore(end < total);
        dto.setNextOffset(dto.isHasMore() ? end : null);
        dto.setLinks(parseLinks(body.path("links")));
        return dto;
    }

    private boolean catalogFiltersActive(String q, String country, String category, String continent, Boolean world, String region) {
        return notBlank(q) || notBlank(country) || notBlank(category) || notBlank(continent) || notBlank(region)
                || Boolean.TRUE.equals(world);
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private List<ProductDto> applyCatalogFilters(List<ProductDto> all, String q, String country, String category,
            String continent, Boolean world, String region) {
        String qNorm = normalizeSearch(q);
        String countryNorm = normalizeCountryCode(country);
        String catNorm = normalizeSearch(category);
        String continentNorm = normalizeSearch(continent);
        String regionTok = normalizeRegionToken(region);
        List<ProductDto> out = new ArrayList<>();
        for (ProductDto p : all) {
            if (matchesCatalogFilters(p, qNorm, countryNorm, catNorm, continentNorm, world, regionTok)) {
                out.add(p);
            }
        }
        return out;
    }

    private String normalizeSearch(String s) {
        if (!notBlank(s)) {
            return null;
        }
        return s.trim().toLowerCase();
    }

    private String normalizeCountryCode(String s) {
        if (!notBlank(s)) {
            return null;
        }
        return s.trim().toUpperCase();
    }

    private String normalizeRegionToken(String s) {
        if (!notBlank(s)) {
            return null;
        }
        return s.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private boolean matchesCatalogFilters(ProductDto p, String qNorm, String countryNorm, String catNorm,
            String continentNorm, Boolean world, String regionTok) {
        String productId = p.getProductDefinition() != null ? p.getProductDefinition().getProductId() : null;
        if (Boolean.TRUE.equals(world)) {
            if (!EsimCatalogGeo.isWorldwideProduct(productId)) {
                return false;
            }
        }
        List<String> countries = p.getProductDefinition() != null ? p.getProductDefinition().getCountryList() : null;
        if (continentNorm != null) {
            if (!EsimCatalogContinentIndex.anyCountryInBucket(countries, continentNorm)) {
                return false;
            }
        }
        if (regionTok != null) {
            if (!EsimCatalogGeo.matchesRegionBundle(productId, regionTok)) {
                return false;
            }
        }
        if (countryNorm != null) {
            if (countries == null || countries.stream().noneMatch(c -> countryNorm.equalsIgnoreCase(c))) {
                return false;
            }
        }
        if (catNorm != null) {
            String pc = p.getProductDefinition() != null ? p.getProductDefinition().getProductCategory() : null;
            if (pc == null || !pc.toLowerCase().contains(catNorm)) {
                return false;
            }
        }
        if (qNorm != null) {
            return catalogHaystack(p).contains(qNorm);
        }
        return true;
    }

    private String catalogHaystack(ProductDto p) {
        StringBuilder sb = new StringBuilder();
        if (p.getProductDefinition() != null) {
            appendHay(sb, p.getProductDefinition().getProductId());
            appendHay(sb, p.getProductDefinition().getProductCategory());
            appendHay(sb, catalogShortTextFromDefinition(p));
        }
        if (p.getDisplay() != null) {
            appendHay(sb, p.getDisplay().getName());
            appendHay(sb, p.getDisplay().getDescription());
        }
        return sb.toString();
    }

    private static void appendHay(StringBuilder sb, String part) {
        if (part != null && !part.isBlank()) {
            sb.append(part.toLowerCase()).append(' ');
        }
    }

    private void enrichCatalogDisplay(List<ProductDto> products) {
        for (ProductDto p : products) {
            enrichProductDisplay(p);
        }
    }

    private void enrichProductDisplay(ProductDto product) {
        if (product == null) {
            return;
        }
        String fallback = catalogShortTextFromDefinition(product);
        if (fallback == null || fallback.isBlank()) {
            return;
        }
        DisplayDto display = product.getDisplay();
        if (display == null) {
            display = new DisplayDto();
            product.setDisplay(display);
        }
        if (display.getName() == null || display.getName().isBlank()) {
            display.setName(fallback);
        }
        if (display.getDescription() == null || display.getDescription().isBlank()) {
            display.setDescription(fallback);
        }
    }

    private String catalogShortTextFromDefinition(ProductDto product) {
        if (product.getProductDefinition() == null) {
            return null;
        }
        JsonNode desc = product.getProductDefinition().getDescription();
        if (desc == null || desc.isNull() || desc.isMissingNode()) {
            return null;
        }
        if (desc.isTextual()) {
            return desc.asText();
        }
        if (desc.isObject()) {
            JsonNode shortText = desc.path("productShortText");
            if (shortText.isTextual()) {
                return shortText.asText();
            }
        }
        return null;
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode n = node.path(field);
        return n.isMissingNode() || n.isNull() ? null : n.asText();
    }

    private List<LinkDto> parseLinks(JsonNode linksNode) {
        if (linksNode == null || linksNode.isMissingNode() || linksNode.isNull()) {
            return null;
        }
        try {
            return objectMapper.convertValue(linksNode, new TypeReference<List<LinkDto>>() { });
        } catch (IllegalArgumentException e) {
            log.warn("Impossible de parser links du catalogue: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public ResponseEntity<JsonNode> getCatalogProduct(String productId) {
        try {
            String token = getAccessToken();
            String url = catalogUrl + "/" + encode(productId);
            HttpRequest request = createRequestBuilder(url, token).GET().build();
            return sendJsonRequestWithStatus(request);
        } catch (IllegalArgumentException e) {
            return errorJson(HttpStatus.BAD_REQUEST, resolveBusinessErrorCode(e.getMessage()), e.getMessage());
        } catch (Exception e) {
            log.error("Erreur lors de la recuperation du produit : {}", e.getMessage(), e);
            return errorJson(HttpStatus.INTERNAL_SERVER_ERROR, "ESIM_INTERNAL_ERROR", "Erreur interne eSIM.");
        }
    }

    @Override
    public ResponseEntity<JsonNode> subscribeProduct(SubscribeProductRequestDto request) {
        try {
            String token = getAccessToken();
            Map<String, Object> payloadMap = buildSubscriptionPayload(request);
            if (log.isDebugEnabled()) {
                log.debug("Payload Transatel subscribe: {}", payloadMap);
            }
            String payload = objectMapper.writeValueAsString(payloadMap);
            HttpRequest httpRequest = createRequestBuilder(subscriptionsUrl, token)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            return sendJsonRequestWithStatus(httpRequest);
        } catch (IllegalArgumentException e) {
            return errorJson(HttpStatus.BAD_REQUEST, resolveBusinessErrorCode(e.getMessage()), e.getMessage());
        } catch (Exception e) {
            log.error("Erreur lors de la souscription produit : {}", e.getMessage(), e);
            return errorJson(HttpStatus.INTERNAL_SERVER_ERROR, "ESIM_INTERNAL_ERROR", "Erreur interne eSIM.");
        }
    }


    @Override
    public ResponseEntity<JsonNode> getInventory(String msisdn, String statuses, boolean withBalances) {
        try {
            String token = getAccessToken();
            StringBuilder url = new StringBuilder(inventoryUrl)
                    .append("?msisdn=").append(encode(msisdn));
            // N'envoyer statuses que si c'est une valeur unique valide (pas de virgules)
            if (statuses != null && !statuses.isBlank() && !statuses.contains(",")) {
                url.append("&statuses=").append(encode(statuses.trim()));
            }
            if (withBalances) {
                url.append("&withBalances=true");
            }
            HttpRequest request = createRequestBuilder(url.toString(), token).GET().build();
            return sendJsonRequestWithStatus(request);
        } catch (IllegalArgumentException e) {
            return errorJson(HttpStatus.BAD_REQUEST, resolveBusinessErrorCode(e.getMessage()), e.getMessage());
        } catch (Exception e) {
            log.error("Erreur lors de la recuperation de l'inventaire : {}", e.getMessage(), e);
            return errorJson(HttpStatus.INTERNAL_SERVER_ERROR, "ESIM_INTERNAL_ERROR", "Erreur interne eSIM.");
        }
    }

    @Override
    public ResponseEntity<JsonNode> activateEsim(ActivateEsimRequestDto request) {
        try {
            String token = getAccessToken();
            String simSerial = request.getSimSerial();
            Map<String, Object> payload = new HashMap<>();
            payload.put("ratePlan", resolveRatePlan(request));
            putIfNotBlank(payload, "externalReference", request.getExternalReference());
            putIfNotBlank(payload, "group", request.getGroup());
            putIfNotBlank(payload, "subscriberCountryOfResidence", request.getSubscriberCountryOfResidence());
            if (request.getOptions() != null && !request.getOptions().isEmpty()) {
                payload.put("options", request.getOptions());
            }

            String body = objectMapper.writeValueAsString(payload);
            String url = activationUrl + "/" + encode(simSerial) + "/activate";
            HttpRequest httpRequest = createRequestBuilder(url, token)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            return sendJsonRequestWithStatus(httpRequest);
        } catch (IllegalArgumentException e) {
            return errorJson(HttpStatus.BAD_REQUEST, resolveBusinessErrorCode(e.getMessage()), e.getMessage());
        } catch (Exception e) {
            log.error("Erreur lors de l'activation eSIM : {}", e.getMessage(), e);
            return errorJson(HttpStatus.INTERNAL_SERVER_ERROR, "ESIM_INTERNAL_ERROR", "Erreur interne eSIM.");
        }
    }

    @Override
    public ResponseEntity<JsonNode> getEsimDetails(String simSerial, boolean history) {
        try {
            String token = getAccessToken();
            String url = esimDetailsUrl + "/" + encode(simSerial) + "?history=" + history;
            HttpRequest request = createRequestBuilder(url, token).GET().build();
            return sendJsonRequestWithStatus(request);
        } catch (IllegalArgumentException e) {
            return errorJson(HttpStatus.BAD_REQUEST, resolveBusinessErrorCode(e.getMessage()), e.getMessage());
        } catch (Exception e) {
            log.error("Erreur lors de la recuperation des details eSIM : {}", e.getMessage(), e);
            return errorJson(HttpStatus.INTERNAL_SERVER_ERROR, "ESIM_INTERNAL_ERROR", "Erreur interne eSIM.");
        }
    }


    @Override
    public ResponseEntity<JsonNode> getSubscriberDetails(String simSerial) {
        try {
            String token = getAccessToken();
            String url = resolveSubscriberUrl() + "/" + encode(simSerial);
            HttpRequest request = createRequestBuilder(url, token).GET().build();
            return sendJsonRequestWithStatus(request);
        } catch (IllegalArgumentException e) {
            return errorJson(HttpStatus.BAD_REQUEST, resolveBusinessErrorCode(e.getMessage()), e.getMessage());
        } catch (Exception e) {
            log.error("Erreur lors de la recuperation du statut subscriber : {}", e.getMessage(), e);
            return errorJson(HttpStatus.INTERNAL_SERVER_ERROR, "ESIM_INTERNAL_ERROR", "Erreur interne eSIM.");
        }
    }

    @Override
    public ResponseEntity<JsonNode> getSubscriptionStatus(String subscriptionId, String msisdn) {
        try {
            String token = getAccessToken();
            String url = resolveSubscriptionStatusUrl(subscriptionId, msisdn);
            HttpRequest request = createRequestBuilder(url, token).GET().build();
            return sendJsonRequestWithStatus(request);
        } catch (IllegalArgumentException e) {
            return errorJson(HttpStatus.BAD_REQUEST, resolveBusinessErrorCode(e.getMessage()), e.getMessage());
        } catch (Exception e) {
            log.error("Erreur lors de la recuperation du statut souscription : {}", e.getMessage(), e);
            return errorJson(HttpStatus.INTERNAL_SERVER_ERROR, "ESIM_INTERNAL_ERROR", "Erreur interne eSIM.");
        }
    }

    @Override
    public ResponseEntity<JsonNode> getSubscriptionDetails(String subscriptionId, boolean withBalances) {
        try {
            String token = getAccessToken();
            String base = inventoryUrl.endsWith("/") ? inventoryUrl.substring(0, inventoryUrl.length() - 1) : inventoryUrl;
            String url = base + "/" + encode(subscriptionId);
            if (withBalances) {
                url = url + "?withBalances=true";
            }
            HttpRequest request = createRequestBuilder(url, token).GET().build();
            return sendJsonRequestWithStatus(request);
        } catch (IllegalArgumentException e) {
            return errorJson(HttpStatus.BAD_REQUEST, resolveBusinessErrorCode(e.getMessage()), e.getMessage());
        } catch (Exception e) {
            log.error("Erreur lors de la recuperation du detail souscription : {}", e.getMessage(), e);
            return errorJson(HttpStatus.INTERNAL_SERVER_ERROR, "ESIM_INTERNAL_ERROR", "Erreur interne eSIM.");
        }
    }

    @Override
    public ResponseEntity<JsonNode> getTransactionStatus(String transactionId) {
        try {
            String token = getAccessToken();
            String url = transactionUrl + "/" + encode(transactionId);
            HttpRequest request = createRequestBuilder(url, token).GET().build();
            return sendJsonRequestWithStatus(request);
        } catch (IllegalArgumentException e) {
            return errorJson(HttpStatus.BAD_REQUEST, resolveBusinessErrorCode(e.getMessage()), e.getMessage());
        } catch (Exception e) {
            log.error("Erreur lors de la recuperation du statut transaction : {}", e.getMessage(), e);
            return errorJson(HttpStatus.INTERNAL_SERVER_ERROR, "ESIM_INTERNAL_ERROR", "Erreur interne eSIM.");
        }
    }

    @Override
    public ResponseEntity<JsonNode> getInternetBalance(String msisdn) {
        try {
            if (creditUrl == null || creditUrl.isBlank()) {
                log.error("creditUrl non configure.");
                return errorJson(HttpStatus.INTERNAL_SERVER_ERROR, "ESIM_INTERNAL_ERROR", "Erreur interne eSIM.");
            }
            String token = getAccessToken();
            String normalizedMsisdn = normalizeMsisdn(msisdn);
            String url = creditUrl + "?msisdn=" + encode(normalizedMsisdn);
            HttpRequest request = createRequestBuilder(url, token).GET().build();
            return sendJsonRequestWithStatus(request);
        } catch (IllegalArgumentException e) {
            return errorJson(HttpStatus.BAD_REQUEST, resolveBusinessErrorCode(e.getMessage()), e.getMessage());
        } catch (Exception e) {
            log.error("Erreur lors de la recuperation du solde internet : {}", e.getMessage(), e);
            return errorJson(HttpStatus.INTERNAL_SERVER_ERROR, "ESIM_INTERNAL_ERROR", "Erreur interne eSIM.");
        }
    }

    @Override
    public ResponseEntity<MsisdnResponseDto> getMsisdnForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        // Lookup direct : fonctionne que userId soit un UUID ou un username (téléphone)
        EsimSimSerial record = simSerialRepository.findFirstByAssignedUserIdOrderByIdDesc(userId).orElse(null);

        // Fallback : résoudre vers l'UUID Keycloak si l'ancienne souscription a stocké l'UUID
        if (record == null) {
            try {
                Users user = findUserByUsernameOrId(userId);
                if (user != null && user.getUserId() != null && !user.getUserId().equals(userId)) {
                    record = simSerialRepository.findFirstByAssignedUserIdOrderByIdDesc(user.getUserId()).orElse(null);
                }
            } catch (Exception ignored) {
                log.debug("getMsisdnForUser fallback UUID non trouvé pour identifier {}", userId);
            }
        }

        if (record == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        String msisdn = record.getMsisdn();
        if (msisdn == null || msisdn.isBlank()) {
            String fetched = fetchMsisdnFromTransatel(record.getSimSerial());
            if (fetched != null && !fetched.isBlank()) {
                msisdn = normalizeMsisdn(fetched);
                record.setMsisdn(msisdn);
                simSerialRepository.save(record);
            }
        }

        if (msisdn == null || msisdn.isBlank()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        MsisdnResponseDto response = new MsisdnResponseDto();
        response.setUserId(userId);
        response.setSimSerial(record.getSimSerial());
        response.setMsisdn(normalizeMsisdn(msisdn));
        response.setStatus(record.getStatus() == null ? null : record.getStatus().name());

        // Retourner les IDs de souscriptions appartenant à cet utilisateur
        String rawIds = record.getSubscriptionIds();
        if (rawIds != null && !rawIds.isBlank()) {
            response.setSubscriptionIds(java.util.Arrays.asList(rawIds.split(",")));
        } else {
            response.setSubscriptionIds(new java.util.ArrayList<>());
        }
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<org.akuunda.akuundawallet.esim.api.dto.LineStatusResponseDto> getLineStatusForUser(String userId) {
        if (userId == null || userId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        org.akuunda.akuundawallet.esim.api.dto.LineStatusResponseDto response =
                new org.akuunda.akuundawallet.esim.api.dto.LineStatusResponseDto();
        response.setUserId(userId);

        EsimSimSerial record = simSerialRepository.findFirstByAssignedUserIdOrderByIdDesc(userId).orElse(null);
        if (record == null) {
            response.setUserStatus("NO_SIM");
            return ResponseEntity.ok(response);
        }

        response.setSimSerial(record.getSimSerial());
        response.setLocalStatus(record.getStatus() == null ? null : record.getStatus().name());

        if (record.getMsisdn() != null && !record.getMsisdn().isBlank()) {
            response.setMsisdn(normalizeMsisdn(record.getMsisdn()));
        }

        String subscriberStatus = null;
        String normalizedSubscriberStatus = null;
        if (record.getSimSerial() != null && !record.getSimSerial().isBlank()) {
            ResponseEntity<JsonNode> subscriberResponse = getSubscriberDetails(record.getSimSerial());
            if (subscriberResponse.getStatusCode().is2xxSuccessful() && subscriberResponse.getBody() != null) {
                subscriberStatus = extractFirstValue(subscriberResponse.getBody(),
                        List.of("status", "state", "subscriberStatus", "lineStatus"));
                normalizedSubscriberStatus = normalizeStatus(subscriberStatus);
                updateLocalSimStatusFromRemote(record, normalizedSubscriberStatus);
            }
        }

        if (normalizedSubscriberStatus != null) {
            response.setSubscriberStatus(normalizedSubscriberStatus);
        } else if (subscriberStatus != null && !subscriberStatus.isBlank()) {
            response.setSubscriberStatus(subscriberStatus);
        }

        boolean hasActivePlan = false;
        if (response.getMsisdn() != null && !response.getMsisdn().isBlank()) {
            ResponseEntity<JsonNode> inventoryResponse = getInventory(response.getMsisdn(), "active", false);
            if (inventoryResponse.getStatusCode().is2xxSuccessful() && inventoryResponse.getBody() != null) {
                hasActivePlan = hasActivePlanFromInventory(inventoryResponse.getBody());
            }
        }
        response.setHasActivePlan(hasActivePlan);
        response.setPlanStatus(hasActivePlan ? "ACTIVE" : "NONE");

        boolean terminated = record.getStatus() == EsimSimSerialStatus.TERMINATED
                || isTerminatedStatus(normalizedSubscriberStatus);
        boolean suspended = record.getStatus() == EsimSimSerialStatus.SUSPENDED
                || isSuspendedStatus(normalizedSubscriberStatus);

        if (terminated) {
            response.setUserStatus("TERMINATED");
        } else if (suspended) {
            response.setUserStatus("SUSPENDED");
        } else if (hasActivePlan) {
            response.setUserStatus("IN_PROGRESS");
        } else if (response.getMsisdn() == null || response.getMsisdn().isBlank()) {
            response.setUserStatus("SIM_NOT_READY");
        } else {
            response.setUserStatus("NO_ACTIVE_PLAN");
        }

        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<JsonNode> terminateSubscriber(String simSerial, String ratePlan) {
        try {
            if (simSerial == null || simSerial.isBlank()) {
                return errorJson(HttpStatus.BAD_REQUEST, "INVALID_SIM_SERIAL", "simSerial requis.");
            }
            String token = getAccessToken();
            String resolvedRatePlan = resolveRatePlan(ratePlan, "resilier une eSIM");
            Map<String, Object> payload = new HashMap<>();
            payload.put("ratePlan", resolvedRatePlan);
            String body = objectMapper.writeValueAsString(payload);
            String url = resolveSubscriberUrl() + "/" + encode(simSerial) + "/terminate";
            HttpRequest httpRequest = createRequestBuilder(url, token)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            ResponseEntity<JsonNode> response = sendJsonRequestWithStatus(httpRequest);
            if (response.getStatusCode().is2xxSuccessful()) {
                markSimSerialTerminated(simSerial);
            }
            return response;
        } catch (IllegalArgumentException e) {
            return errorJson(HttpStatus.BAD_REQUEST, resolveBusinessErrorCode(e.getMessage()), e.getMessage());
        } catch (Exception e) {
            log.error("Erreur lors de la résiliation subscriber : {}", e.getMessage(), e);
            return errorJson(HttpStatus.INTERNAL_SERVER_ERROR, "ESIM_INTERNAL_ERROR", "Erreur interne eSIM.");
        }
    }

    @Override
    public ResponseEntity<JsonNode> suspendSubscriber(String simSerial, String ratePlan) {
        try {
            if (simSerial == null || simSerial.isBlank()) {
                return errorJson(HttpStatus.BAD_REQUEST, "INVALID_SIM_SERIAL", "simSerial requis.");
            }
            String token = getAccessToken();
            String resolvedRatePlan = resolveRatePlan(ratePlan, "suspendre une eSIM");
            Map<String, Object> payload = new HashMap<>();
            payload.put("ratePlan", resolvedRatePlan);
            String body = objectMapper.writeValueAsString(payload);
            String url = resolveSubscriberUrl() + "/" + encode(simSerial) + "/suspend";
            HttpRequest httpRequest = createRequestBuilder(url, token)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            ResponseEntity<JsonNode> response = sendJsonRequestWithStatus(httpRequest);
            if (response.getStatusCode().is2xxSuccessful()) {
                markSimSerialSuspended(simSerial);
            }
            return response;
        } catch (IllegalArgumentException e) {
            return errorJson(HttpStatus.BAD_REQUEST, resolveBusinessErrorCode(e.getMessage()), e.getMessage());
        } catch (Exception e) {
            log.error("Erreur lors de la suspension subscriber : {}", e.getMessage(), e);
            return errorJson(HttpStatus.INTERNAL_SERVER_ERROR, "ESIM_INTERNAL_ERROR", "Erreur interne eSIM.");
        }
    }

    @Override
    public ResponseEntity<JsonNode> reactivateSubscriber(String simSerial, String ratePlan) {
        try {
            if (simSerial == null || simSerial.isBlank()) {
                return errorJson(HttpStatus.BAD_REQUEST, "INVALID_SIM_SERIAL", "simSerial requis.");
            }
            EsimSimSerial existing = simSerialRepository.findBySimSerial(simSerial).orElse(null);
            if (existing != null && existing.getStatus() == EsimSimSerialStatus.TERMINATED) {
                return errorJson(HttpStatus.CONFLICT, "SUBSCRIBER_TERMINATED",
                        "La ligne eSIM est resiliee et ne peut pas etre reactivee.");
            }
            String token = getAccessToken();
            String resolvedRatePlan = resolveRatePlan(ratePlan, "reactiver une eSIM");
            Map<String, Object> payload = new HashMap<>();
            payload.put("ratePlan", resolvedRatePlan);
            String body = objectMapper.writeValueAsString(payload);
            String url = resolveSubscriberUrl() + "/" + encode(simSerial) + "/reactivate";
            HttpRequest httpRequest = createRequestBuilder(url, token)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            ResponseEntity<JsonNode> response = sendJsonRequestWithStatus(httpRequest);
            if (response.getStatusCode().is2xxSuccessful()) {
                simSerialRepository.findBySimSerial(simSerial).ifPresent(record -> {
                    if (record.getStatus() != EsimSimSerialStatus.USED) {
                        record.setStatus(EsimSimSerialStatus.USED);
                        simSerialRepository.save(record);
                    }
                });
            }
            return response;
        } catch (Exception e) {
            log.error("Erreur lors de la réactivation subscriber : {}", e.getMessage(), e);
            return errorJson(HttpStatus.INTERNAL_SERVER_ERROR, "ESIM_INTERNAL_ERROR",
                    "Erreur interne eSIM.");
        }
    }

    @Override
    public ResponseEntity<JsonNode> createWebhook(WebhookRequestDto request) {
        try {
            String token = getAccessToken();
            String payload = objectMapper.writeValueAsString(request);
            HttpRequest httpRequest = createRequestBuilder(webhooksUrl, token)
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            return sendJsonRequestWithStatus(httpRequest);
        } catch (IllegalArgumentException e) {
            return errorJson(HttpStatus.BAD_REQUEST, resolveBusinessErrorCode(e.getMessage()), e.getMessage());
        } catch (Exception e) {
            log.error("Erreur lors de la creation du webhook : {}", e.getMessage(), e);
            return errorJson(HttpStatus.INTERNAL_SERVER_ERROR, "ESIM_INTERNAL_ERROR", "Erreur interne eSIM.");
        }
    }

    @Override
    public ResponseEntity<Void> handleWebhookEvent(String payload, String signatureHeader) {
        try {
            if (!isWebhookSignatureValid(payload, signatureHeader)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            JsonNode event = objectMapper.readTree(payload);
            String eventType = extractFirstValue(event, List.of("eventType"));
            if (eventType == null) {
                eventType = extractNestedValue(event, "header", "eventType");
            }
            String normalizedEventType = eventType == null ? "" : eventType.trim().toUpperCase();
            if (!"OCS/PRODUCT/ACTIVATED".equals(normalizedEventType)) {
                return ResponseEntity.noContent().build();
            }

            JsonNode body = event.path("body");
            String simSerial = extractFirstValue(body, List.of("iccid", "simSerial", "simserial", "sim_serial"));
            if (simSerial == null || simSerial.isBlank()) {
                log.warn("Webhook Transatel sans simSerial/iccid exploitable.");
                return ResponseEntity.noContent().build();
            }

            simSerialRepository.findBySimSerial(simSerial).ifPresent(record -> {
                if (record.getStatus() != EsimSimSerialStatus.USED) {
                    record.setStatus(EsimSimSerialStatus.USED);
                    simSerialRepository.save(record);
                }
            });

            return ResponseEntity.noContent().build();
        } catch (Exception e) {
            log.error("Erreur lors du traitement du webhook Transatel : {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public ResponseEntity<Object> purchaseFlow(PurchaseFlowRequestDto request) {
        EsimSimSerial reservedSim = null;
        try {
            // Résoudre l'identifiant principal : username (téléphone) prioritaire sur userId
            final String resolvedUsername = resolveUsername(request);
            verifyBalanceOrThrow(resolvedUsername, request.getAmount(),
                    request.getProductId(), request.getOrderType());

            reservedSim = reserveSimSerial(request.getSimSerial(), resolvedUsername, request.getMsisdn());
            String simSerial = reservedSim.getSimSerial();
            String msisdn = resolveMsisdn(reservedSim, request.getMsisdn());
            if (reservedSim.getMsisdn() == null || reservedSim.getMsisdn().isBlank()) {
                reservedSim.setMsisdn(normalizeMsisdn(msisdn));
                simSerialRepository.save(reservedSim);
            }

            ResponseEntity<JsonNode> subscriberResponse = getSubscriberDetails(simSerial);
            String subscriberStatus = null;
            String normalizedSubscriberStatus = null;
            if (subscriberResponse.getStatusCode().is2xxSuccessful()) {
                subscriberStatus = extractFirstValue(subscriberResponse.getBody(),
                        List.of("status", "state", "subscriberStatus", "lineStatus"));
                normalizedSubscriberStatus = normalizeStatus(subscriberStatus);
            }

            SubscribeProductRequestDto subscribeRequest = new SubscribeProductRequestDto();
            subscribeRequest.setMsisdn(msisdn);
            subscribeRequest.setProductId(request.getProductId());
            if (request.getAmount() != null && request.getAmount() <= 0) {
                if (freePaymentProvider != null && !freePaymentProvider.isBlank()) {
                    subscribeRequest.setPaymentProvider(freePaymentProvider);
                } else {
                    log.warn("Souscription gratuite sans free-payment-provider: utilisation de {}", defaultPaymentProvider);
                }
            }
            subscribeRequest.setOrderType(resolveOrderType(request.getProductId(), request.getOrderType(), normalizedSubscriberStatus));
            ResponseEntity<JsonNode> subscriptionResponse = subscribeProduct(subscribeRequest);
            if (!subscriptionResponse.getStatusCode().is2xxSuccessful()) {
                releaseSimSerial(reservedSim);
                return ResponseEntity.status(subscriptionResponse.getStatusCode()).body(subscriptionResponse.getBody());
            }

            String subscriptionId = extractFirstValue(subscriptionResponse.getBody(),
                    List.of("subscriptionId", "id"));
            if (request.getProductId() != null && reservedSim != null) {
                reservedSim.setLastProductId(request.getProductId());
                reservedSim.setLastSubscriptionId(subscriptionId);
                // Ajouter le subscriptionId à la liste de l'utilisateur (filtre côté mobile)
                if (subscriptionId != null && !subscriptionId.isBlank()) {
                    String existing = reservedSim.getSubscriptionIds();
                    java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
                    if (existing != null && !existing.isBlank()) {
                        java.util.Collections.addAll(ids, existing.split(","));
                    }
                    ids.add(subscriptionId);
                    reservedSim.setSubscriptionIds(String.join(",", ids));
                }
                simSerialRepository.save(reservedSim);
            }

            String transactionId = null;
            String transactionStatus = null;
            boolean activationRequiredFlag = false;
            String activationMessage = null;
            if (!isActiveStatus(normalizedSubscriberStatus)) {
                ActivateEsimRequestDto activateRequest = new ActivateEsimRequestDto();
                activateRequest.setSimSerial(simSerial);
                ResponseEntity<JsonNode> activationResponse = activateEsim(activateRequest);
                if (activationResponse.getStatusCode().is2xxSuccessful() && activationResponse.getBody() != null) {
                    transactionId = extractFirstValue(activationResponse.getBody(),
                            List.of("transactionId", "id"));
                    transactionStatus = extractFirstValue(activationResponse.getBody(),
                            List.of("transactionStatus", "status"));
                } else if (!activationResponse.getStatusCode().is2xxSuccessful()) {
                    if (isSimNotReadyForActivation(activationResponse.getBody())) {
                        activationRequiredFlag = true;
                        activationMessage = "SIM_NOT_READY";
                    } else {
                        releaseSimSerial(reservedSim);
                        return ResponseEntity.status(activationResponse.getStatusCode())
                                .body(activationResponse.getBody());
                    }
                }
            }

            ResponseEntity<JsonNode> esimDetailsResponse = getEsimDetails(simSerial, false);
            String activationCode = null;
            String qrCodeValue = null;
            String qrCodeDataUrl = null;
            String esimStatus = null;
            if (esimDetailsResponse.getStatusCode().is2xxSuccessful() && esimDetailsResponse.getBody() != null) {
                activationCode = extractFirstValue(esimDetailsResponse.getBody(),
                        List.of("activationCode", "lpa", "lpaString", "lpaCode"));
                qrCodeValue = extractNestedValue(esimDetailsResponse.getBody(), "qrCode", "value");
                qrCodeDataUrl = extractNestedValue(esimDetailsResponse.getBody(), "qrCode", "dataUrl");
                esimStatus = extractFirstValue(esimDetailsResponse.getBody(),
                        List.of("status", "state", "simStatus", "esimStatus"));
            }

            PurchaseFlowResponseDto response = new PurchaseFlowResponseDto();
            response.setSubscriptionId(subscriptionId);
            response.setTransactionId(transactionId);
            response.setTransactionStatus(transactionStatus);
            response.setSimSerial(simSerial);
            response.setEsimStatus(esimStatus);
            response.setSubscriberStatus(subscriberStatus);
            response.setActivationMessage(activationMessage);
            response.setActivationCode(activationCode);
            response.setQrCodeValue(qrCodeValue);
            response.setQrCodeDataUrl(qrCodeDataUrl);

            boolean activationPending = transactionStatus != null
                    && "PENDING".equalsIgnoreCase(transactionStatus.trim());
            response.setActivationRequired(activationPending
                    || activationRequiredFlag
                    || !isActiveStatus(normalizedSubscriberStatus));

            if (!activationPending && (activationCode != null || qrCodeValue != null || qrCodeDataUrl != null)) {
                markSimSerialUsed(reservedSim);
            }

            // Débit wallet utilisateur → wallet de service après souscription réussie
            if (request.getHeaderPin() == null || request.getHeaderPin().isBlank()) {
                log.warn("purchaseFlow: headerPin absent, débit wallet ignoré.");
            } else if (request.getAmount() == null || request.getAmount() <= 0) {
                log.info("purchaseFlow: montant nul ou gratuit, débit wallet ignoré.");
            } else if (serviceWalletUsername == null || serviceWalletUsername.isBlank()) {
                log.error("purchaseFlow: ESIM_SERVICE_WALLET_USERNAME non configuré — débit wallet impossible. Configurez cette variable d'environnement sur le serveur.");
            }
            if (request.getHeaderPin() != null && !request.getHeaderPin().isBlank()
                    && request.getAmount() != null && request.getAmount() > 0
                    && serviceWalletUsername != null && !serviceWalletUsername.isBlank()) {
                try {
                    AkuundaTransfertRequest transfert = new AkuundaTransfertRequest(
                            resolvedUsername,
                            serviceWalletUsername,
                            request.getAmount(),
                            request.getDevise() != null && !request.getDevise().isBlank()
                                    ? request.getDevise() : serviceWalletCurrency,
                            request.getHeaderPin()
                    );
                    ResponseEntity<String> transfertResponse = akuundaTransactionService.executeTransfertCpteACpte(transfert);
                    if (!transfertResponse.getStatusCode().is2xxSuccessful()) {
                        log.warn("Transfert wallet eSIM échoué (non bloquant): status={}, body={}",
                                transfertResponse.getStatusCode(), transfertResponse.getBody());
                    } else {
                        log.info("Transfert wallet eSIM OK: {} {} de {} vers {}",
                                request.getAmount(), transfert.getDevise(), resolvedUsername, serviceWalletUsername);
                    }
                } catch (Exception ex) {
                    log.warn("Erreur transfert wallet eSIM (non bloquant): {}", ex.getMessage());
                }
            }

            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            releaseSimSerial(reservedSim);
            return error(HttpStatus.BAD_REQUEST, resolveBusinessErrorCode(e.getMessage()), e.getMessage());
        } catch (Exception e) {
            releaseSimSerial(reservedSim);
            log.error("Erreur lors du purchase flow eSIM : {}", e.getMessage(), e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "ESIM_INTERNAL_ERROR", "Erreur interne eSIM.");
        }
    }

    @Override
    public ResponseEntity<Object> renewLastSubscription(RenewSubscriptionRequestDto request) {
        try {
            if (request.getUserId() == null || request.getUserId().isBlank()) {
                throw new IllegalArgumentException("userId requis pour le renouvellement.");
            }
            EsimSimSerial record = simSerialRepository
                    .findFirstByAssignedUserIdAndLastProductIdIsNotNullOrderByIdDesc(request.getUserId())
                    .orElseThrow(() -> new IllegalArgumentException("Aucune souscription precedente pour cet utilisateur."));

            String msisdn = request.getMsisdn();
            if (msisdn == null || msisdn.isBlank()) {
                msisdn = record.getMsisdn();
            }
            if (msisdn == null || msisdn.isBlank()) {
                throw new IllegalArgumentException("msisdn requis pour le renouvellement.");
            }

            verifyBalanceOrThrow(request.getUserId(), request.getAmount(),
                    record.getLastProductId(), request.getOrderType());

            SubscribeProductRequestDto subscribeRequest = new SubscribeProductRequestDto();
            subscribeRequest.setMsisdn(resolveMsisdn(record, msisdn));
            subscribeRequest.setProductId(record.getLastProductId());
            if (request.getAmount() != null && request.getAmount() <= 0) {
                if (freePaymentProvider != null && !freePaymentProvider.isBlank()) {
                    subscribeRequest.setPaymentProvider(freePaymentProvider);
                } else {
                    log.warn("Renouvellement gratuit sans free-payment-provider: utilisation de {}", defaultPaymentProvider);
                }
            }
            subscribeRequest.setOrderType(resolveOrderType(record.getLastProductId(), request.getOrderType()));

            ResponseEntity<JsonNode> response = subscribeProduct(subscribeRequest);
            if (response.getStatusCode().is2xxSuccessful()) {
                String subscriptionId = extractFirstValue(response.getBody(), List.of("subscriptionId", "id"));
                record.setLastSubscriptionId(subscriptionId);
                if (subscriptionId != null && !subscriptionId.isBlank()) {
                    String existing = record.getSubscriptionIds();
                    java.util.LinkedHashSet<String> ids = new java.util.LinkedHashSet<>();
                    if (existing != null && !existing.isBlank()) {
                        java.util.Collections.addAll(ids, existing.split(","));
                    }
                    ids.add(subscriptionId);
                    record.setSubscriptionIds(String.join(",", ids));
                }
                simSerialRepository.save(record);
            }
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, resolveBusinessErrorCode(e.getMessage()), e.getMessage());
        } catch (Exception e) {
            log.error("Erreur lors du renouvellement eSIM : {}", e.getMessage(), e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "ESIM_INTERNAL_ERROR", "Erreur interne eSIM.");
        }
    }

    @Override
    public ResponseEntity<Object> renewProduct(RenewProductRequestDto request) {
        try {
            if (request.getUserId() == null || request.getUserId().isBlank()) {
                throw new IllegalArgumentException("userId requis pour le renouvellement.");
            }
            if (request.getProductId() == null || request.getProductId().isBlank()) {
                throw new IllegalArgumentException("productId requis pour le renouvellement.");
            }

            EsimSimSerial record = simSerialRepository
                    .findFirstByAssignedUserIdOrderByIdDesc(request.getUserId())
                    .orElseThrow(() -> new IllegalArgumentException("Aucune eSIM associée à cet utilisateur."));

            String msisdn = request.getMsisdn();
            if (msisdn != null && !msisdn.isBlank()
                    && record.getMsisdn() != null
                    && !normalizeMsisdn(msisdn).equals(normalizeMsisdn(record.getMsisdn()))) {
                throw new IllegalArgumentException("msisdn ne correspond pas à l'utilisateur.");
            }

            msisdn = resolveMsisdn(record, msisdn);
            verifyBalanceOrThrow(request.getUserId(), request.getAmount(),
                    request.getProductId(), request.getOrderType());

            SubscribeProductRequestDto subscribeRequest = new SubscribeProductRequestDto();
            subscribeRequest.setMsisdn(msisdn);
            subscribeRequest.setProductId(request.getProductId());
            if (request.getAmount() != null && request.getAmount() <= 0) {
                if (freePaymentProvider != null && !freePaymentProvider.isBlank()) {
                    subscribeRequest.setPaymentProvider(freePaymentProvider);
                } else {
                    log.warn("Renouvellement produit gratuit sans free-payment-provider: utilisation de {}", defaultPaymentProvider);
                }
            }
            subscribeRequest.setOrderType(resolveOrderType(request.getProductId(), request.getOrderType()));

            ResponseEntity<JsonNode> response = subscribeProduct(subscribeRequest);
            if (response.getStatusCode().is2xxSuccessful()) {
                String subscriptionId = extractFirstValue(response.getBody(), List.of("subscriptionId", "id"));
                record.setLastProductId(request.getProductId());
                record.setLastSubscriptionId(subscriptionId);
                simSerialRepository.save(record);
            }
            return ResponseEntity.status(response.getStatusCode()).body(response.getBody());
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, resolveBusinessErrorCode(e.getMessage()), e.getMessage());
        } catch (Exception e) {
            log.error("Erreur lors du renouvellement produit eSIM : {}", e.getMessage(), e);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "ESIM_INTERNAL_ERROR", "Erreur interne eSIM.");
        }
    }


    /////////////////////////////////////////////////////////////////
    ////////// METHODES PRIVEES /////////////////////////////////////
    /////////////////////////////////////////////////////////////////

    /**
     * Cette méthode permet d'obtenir un jeton d'accès (access token) en envoyant une requête POST
     * avec les identifiants (nom d'utilisateur et mot de passe) au point d'accès d'authentification.
     * En cas de succès, elle retourne le jeton d'accès encapsulé dans une ResponseEntity.
     * En cas d'échec, elle retourne une réponse avec le code d'erreur approprié.
     */
    private String getAccessToken() throws Exception {
        if (allowDebugToken && debugToken != null && !debugToken.isBlank()) {
            log.info(">>> [AUTH] Utilisation du token DEBUG manuel.");
            return normalizeToken(debugToken);
        }

        if (authMode != null && "legacy".equalsIgnoreCase(authMode.trim())) {
            throw new IllegalStateException("Le mode auth legacy n'est pas autorise en production.");
        }

        String requestBody = "grant_type=client_credentials";

        String basicCredentials = Base64.getEncoder()
                .encodeToString((apiKey + ":" + apiSecret).getBytes(StandardCharsets.UTF_8));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(authUrl))
                .header("Authorization", "Basic " + basicCredentials)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        log.info(">>> [AUTH] Tentative de connexion à Transatel...");
        log.debug(">>> [AUTH] Tentative de connexion à Transatel...");
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200 || response.statusCode() == 201) {
            JSONObject res = new JSONObject(response.body());
            return res.getString("access_token");
        }

        log.error("Erreur lors de la récupération du token avec le code : {} et le message : {} ",
                response.statusCode(), response.body());
        throw new RuntimeException("Echec authentification");
    }

    private String normalizeToken(String token) {
        String trimmed = token.trim();
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            return trimmed.substring(1, trimmed.length() - 1);
        }
        return trimmed;
    }

    /**
     * Envoie une requête GET simple à l'endpoint spécifié avec le token d'accès fourni.
     * En cas de succès, elle retourne la réponse encapsulée dans une ResponseEntity.
     * En cas d'erreur, elle retourne une réponse avec le code d'erreur approprié.
     */
    private <T> ResponseEntity<T> sendSimpleGet(String endpoint, String token, Class<T> clazz) {
        try {
            T response = sendRequest(createRequestBuilder(endpoint, token).GET().build(), clazz);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Erreur GET {} : {}", endpoint, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Crée un constructeur de requête HTTP avec l'endpoint spécifié et le token d'accès.
     */
    private HttpRequest.Builder createRequestBuilder(String endpoint, String token) {
        String language = (acceptLanguage == null || acceptLanguage.isBlank())
                ? "fr"
                : acceptLanguage.trim();
        return HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Accept-Language", language);
    }

    /**
     * Envoie la requête HTTP spécifiée et désérialise la réponse dans l'objet de type spécifié.
     * En cas d'erreur, elle lance une exception.
     */
    private <T> T sendRequest(HttpRequest request, Class<T> clazz) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) return objectMapper.readValue(response.body(), clazz);
        throw new RuntimeException("Transatel API error: " + response.body());
    }

    private ResponseEntity<JsonNode> sendJsonRequestWithStatus(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        JsonNode body = parseJsonOrText(response.body());
        if (response.statusCode() >= 400) {
            log.warn("Transatel API error {}: {}", response.statusCode(), body);
        }
        return ResponseEntity.status(response.statusCode()).body(body);
    }

    private Map<String, Object> buildSubscriptionPayload(SubscribeProductRequestDto request) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("bind", Map.of("msisdn", request.getMsisdn()));
        payload.put("product", Map.of("productId", request.getProductId()));

        String provider = request.getPaymentProvider();
        if (provider == null || provider.isBlank()) {
            provider = defaultPaymentProvider;
        }
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("payment provider requis.");
        }
        if (!"customer".equalsIgnoreCase(provider.trim())) {
            throw new IllegalArgumentException("payment provider invalide: customer requis.");
        }
        payload.put("payment", Map.of("provider", provider));

        String source = request.getSource();
        if (source == null || source.isBlank()) {
            source = "api";
        }
        payload.put("source", source);

        String orderType = request.getOrderType();
        if (orderType == null || orderType.isBlank()) {
            orderType = "subscribe";
        }
        payload.put("orderType", orderType);

        String mvno = request.getMvnoRef();
        if (mvno == null || mvno.isBlank()) {
            mvno = mvnoRef;
        }
        payload.put("mvnoRef", mvno);
        return payload;
    }

    private String resolveRatePlan(ActivateEsimRequestDto request) {
        if (request.getRatePlan() != null && !request.getRatePlan().isBlank()) {
            return request.getRatePlan();
        }
        if (defaultRatePlan != null && !defaultRatePlan.isBlank()) {
            return defaultRatePlan;
        }
        throw new IllegalArgumentException("ratePlan est requis pour activer une eSIM.");
    }

    private String resolveRatePlan(String ratePlan, String action) {
        if (ratePlan != null && !ratePlan.isBlank()) {
            return ratePlan;
        }
        if (defaultRatePlan != null && !defaultRatePlan.isBlank()) {
            return defaultRatePlan;
        }
        throw new IllegalArgumentException("ratePlan est requis pour " + action + ".");
    }

    private void releaseSimSerial(String simSerial) {
        simSerialRepository.findBySimSerial(simSerial).ifPresent(record -> {
            record.setStatus(EsimSimSerialStatus.USED);
            record.setAssignedUserId(null);
            simSerialRepository.save(record);
        });
    }

    private String resolveSubscriptionStatusUrl(String subscriptionId, String msisdn) {
        if (subscriptionStatusUrl == null || subscriptionStatusUrl.isBlank()) {
            throw new IllegalStateException("subscriptionStatusUrl manquant.");
        }
        if (msisdn == null || msisdn.isBlank()) {
            throw new IllegalArgumentException("msisdn requis pour recuperer le statut souscription.");
        }
        String encodedSubscriptionId = encode(subscriptionId);
        String encodedMsisdn = encode(normalizeMsisdn(msisdn));
        String resolved = subscriptionStatusUrl;

        if (resolved.contains("{subscriptionId}")) {
            resolved = resolved.replace("{subscriptionId}", encodedSubscriptionId);
        }
        if (resolved.contains("{msisdn}")) {
            resolved = resolved.replace("{msisdn}", encodedMsisdn);
        }

        if (resolved.contains("subscriptionId=") && resolved.endsWith("subscriptionId=")) {
            resolved = resolved + encodedSubscriptionId;
        }
        if (resolved.contains("msisdn=") && resolved.endsWith("msisdn=")) {
            resolved = resolved + encodedMsisdn;
        }

        boolean hasSubscriptionId = resolved.contains("subscriptionId=");
        boolean hasMsisdn = resolved.contains("msisdn=");
        if (!hasMsisdn || !hasSubscriptionId) {
            String separator = resolved.contains("?") ? "&" : "?";
            if (!hasMsisdn) {
                resolved = resolved + separator + "msisdn=" + encodedMsisdn;
                separator = "&";
            }
            if (!hasSubscriptionId) {
                resolved = resolved + separator + "subscriptionId=" + encodedSubscriptionId;
            }
        }

        return resolved;
    }

    private String resolveSubscriberUrl() {
        if (subscriberUrl != null && !subscriberUrl.isBlank()) {
            return subscriberUrl;
        }
        if (baseUrl != null && !baseUrl.isBlank()) {
            return baseUrl + "/connectivity-management/subscribers/api/subscribers/sim-serial";
        }
        throw new IllegalStateException("subscriberUrl manquant.");
    }

    private String resolveMsisdn(EsimSimSerial record, String msisdn) {
        if (record != null && record.getMsisdn() != null && !record.getMsisdn().isBlank()) {
            return normalizeMsisdn(record.getMsisdn());
        }
        if (msisdn != null && !msisdn.isBlank()) {
            return normalizeMsisdn(msisdn);
        }
        throw new IllegalArgumentException("msisdn requis pour souscrire un plan.");
    }

    private String fetchMsisdnFromTransatel(String simSerial) {
        if (simSerial == null || simSerial.isBlank()) {
            return null;
        }
        try {
            ResponseEntity<JsonNode> response = getSubscriberDetails(simSerial);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Impossible de recuperer msisdn Transatel pour simSerial {} (status {}).",
                        simSerial, response.getStatusCode());
                return null;
            }
            JsonNode body = response.getBody();
            String msisdn = extractFirstValue(body, List.of("msisdn", "msisdnNumber", "phoneNumber", "number"));
            if (msisdn == null || msisdn.isBlank()) {
                msisdn = extractNestedValue(body, "line", "msisdn");
            }
            if (msisdn == null || msisdn.isBlank()) {
                msisdn = extractNestedValue(body, "line", "number");
            }
            return msisdn;
        } catch (Exception e) {
            log.warn("Erreur recuperation msisdn Transatel pour simSerial {}: {}", simSerial, e.getMessage());
            return null;
        }
    }

    private String resolveOrderType(String productId, String requestedOrderType) {
        return resolveOrderType(productId, requestedOrderType, null);
    }

    private String resolveOrderType(String productId, String requestedOrderType, String normalizedSubscriberStatus) {
        if (requestedOrderType != null && !requestedOrderType.isBlank()) {
            return requestedOrderType;
        }
        if (isPreactivatedStatus(normalizedSubscriberStatus)) {
            return "preload";
        }
        return "subscribe";
    }

    private EsimSimSerial reserveSimSerial(String simSerial, String userId, String msisdn) {
        EsimSimSerial record = null;
        String normalizedMsisdn = msisdn == null ? null : normalizeMsisdn(msisdn);

        if (userId != null && !userId.isBlank()) {
            record = simSerialRepository.findFirstByAssignedUserIdOrderByIdDesc(userId).orElse(null);
            if (record != null) {
                if (record.getStatus() == EsimSimSerialStatus.TERMINATED) {
                    throw new IllegalArgumentException("SUBSCRIBER_TERMINATED");
                }
                if (record.getStatus() == EsimSimSerialStatus.SUSPENDED) {
                    throw new IllegalArgumentException("SUBSCRIBER_SUSPENDED");
                }
                if (simSerial != null && !simSerial.isBlank() && !simSerial.equals(record.getSimSerial())) {
                    throw new IllegalArgumentException("Utilisateur déjà associé à un autre simSerial.");
                }
                if ((record.getMsisdn() == null || record.getMsisdn().isBlank())
                        && normalizedMsisdn != null && !normalizedMsisdn.isBlank()) {
                    record.setMsisdn(normalizedMsisdn);
                }
                if (record.getMsisdn() == null || record.getMsisdn().isBlank()) {
                    String fetchedMsisdn = fetchMsisdnFromTransatel(record.getSimSerial());
                    if (fetchedMsisdn != null && !fetchedMsisdn.isBlank()) {
                        try {
                            record.setMsisdn(normalizeMsisdn(fetchedMsisdn));
                        } catch (IllegalArgumentException e) {
                            log.warn("MSISDN Transatel invalide pour simSerial {}: {}", record.getSimSerial(), e.getMessage());
                        }
                    }
                }
                if (record.getMsisdn() == null || record.getMsisdn().isBlank()) {
                    throw new IllegalArgumentException("msisdn manquant pour ce simSerial.");
                }
                if (record.getStatus() == EsimSimSerialStatus.USED) {
                    return simSerialRepository.save(record);
                }
                if (record.getStatus() == EsimSimSerialStatus.RESERVED) {
                    return simSerialRepository.save(record);
                }
                record.setStatus(EsimSimSerialStatus.RESERVED);
                record.setAssignedUserId(userId);
                return simSerialRepository.save(record);
            }
        }

        if (simSerial != null && !simSerial.isBlank()) {
            record = simSerialRepository.findBySimSerial(simSerial)
                    .orElseThrow(() -> new IllegalArgumentException("simSerial introuvable."));
        } else if (userId != null && !userId.isBlank()) {
            record = simSerialRepository
                    .findFirstByAssignedUserIdAndStatusOrderByIdAsc(userId, EsimSimSerialStatus.RESERVED)
                    .orElseGet(() -> simSerialRepository.findFirstByStatusOrderByIdAsc(EsimSimSerialStatus.AVAILABLE)
                            .orElseThrow(() -> new IllegalArgumentException("Aucun simSerial disponible en base.")));
        } else {
            record = simSerialRepository.findFirstByStatusOrderByIdAsc(EsimSimSerialStatus.AVAILABLE)
                    .orElseThrow(() -> new IllegalArgumentException("Aucun simSerial disponible en base."));
        }

        if (record.getAssignedUserId() != null
                && userId != null
                && !record.getAssignedUserId().equals(userId)) {
            throw new IllegalArgumentException("simSerial deja attribue à un autre utilisateur.");
        }
        if (record.getStatus() == EsimSimSerialStatus.TERMINATED) {
            throw new IllegalArgumentException("SUBSCRIBER_TERMINATED");
        }
        if (record.getStatus() == EsimSimSerialStatus.SUSPENDED) {
            throw new IllegalArgumentException("SUBSCRIBER_SUSPENDED");
        }
        if (record.getStatus() == EsimSimSerialStatus.USED) {
            throw new IllegalArgumentException("simSerial deja utilise.");
        }
        if (record.getStatus() == EsimSimSerialStatus.RESERVED && (userId == null || !userId.equals(record.getAssignedUserId()))) {
            throw new IllegalArgumentException("simSerial deja reserve.");
        }
        if ((record.getMsisdn() == null || record.getMsisdn().isBlank())
                && normalizedMsisdn != null && !normalizedMsisdn.isBlank()) {
            record.setMsisdn(normalizedMsisdn);
        }
        if (record.getMsisdn() == null || record.getMsisdn().isBlank()) {
            String fetchedMsisdn = fetchMsisdnFromTransatel(record.getSimSerial());
            if (fetchedMsisdn != null && !fetchedMsisdn.isBlank()) {
                try {
                    record.setMsisdn(normalizeMsisdn(fetchedMsisdn));
                } catch (IllegalArgumentException e) {
                    log.warn("MSISDN Transatel invalide pour simSerial {}: {}", record.getSimSerial(), e.getMessage());
                }
            }
        }
        if (record.getMsisdn() == null || record.getMsisdn().isBlank()) {
            throw new IllegalArgumentException("msisdn manquant pour ce simSerial.");
        }
        record.setStatus(EsimSimSerialStatus.RESERVED);
        record.setAssignedUserId(userId);
        return simSerialRepository.save(record);
    }

    private void releaseSimSerial(EsimSimSerial record) {
        if (record == null) {
            return;
        }
        record.setStatus(EsimSimSerialStatus.AVAILABLE);
        record.setAssignedUserId(null);
        simSerialRepository.save(record);
    }

    private void markSimSerialUsed(EsimSimSerial record) {
        if (record == null) {
            return;
        }
        record.setStatus(EsimSimSerialStatus.USED);
        simSerialRepository.save(record);
    }

    private void markSimSerialSuspended(String simSerial) {
        simSerialRepository.findBySimSerial(simSerial).ifPresent(record -> {
            record.setStatus(EsimSimSerialStatus.SUSPENDED);
            simSerialRepository.save(record);
        });
    }

    private void markSimSerialTerminated(String simSerial) {
        simSerialRepository.findBySimSerial(simSerial).ifPresent(record -> {
            record.setStatus(EsimSimSerialStatus.TERMINATED);
            simSerialRepository.save(record);
        });
    }

    /**
     * Résout le username (numéro de téléphone) à partir du body de la requête.
     * Priorité : request.username → lookup via userId.
     */
    private String resolveUsername(PurchaseFlowRequestDto request) {
        if (request.getUsername() != null && !request.getUsername().isBlank()) {
            return request.getUsername().trim();
        }
        if (request.getUserId() != null && !request.getUserId().isBlank()) {
            Users user = findUserByUsernameOrId(request.getUserId());
            return user.getUsername();
        }
        throw new IllegalArgumentException("username ou userId requis.");
    }

    /**
     * Lookup flexible : UUID Keycloak ou username (numéro de téléphone).
     */
    private Users findUserByUsernameOrId(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            throw new IllegalArgumentException("Identifiant utilisateur requis.");
        }
        if (identifier.contains("-")) {
            Optional<Users> byId = userRepository.findById(identifier);
            if (byId.isPresent()) {
                return byId.get();
            }
        }
        return userRepository.findFirstByUsernameIgnoreCase(identifier)
                .orElseThrow(() -> {
                    log.warn("Utilisateur introuvable pour identifier {}", identifier);
                    return new IllegalArgumentException("Utilisateur introuvable.");
                });
    }

    private void verifyBalanceOrThrow(String userId, Double requestedAmount,
                                      String productId, String orderType) {
        if (!balanceCheckEnabled) {
            log.info("Verification du solde ignoree (feature flag desactivee).");
            return;
        }
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId requis pour verifier le solde.");
        }
        CatalogPrice catalogPrice = (productId != null && !productId.isBlank())
                ? fetchCatalogPrice(productId, orderType)
                : null;
        Double requiredAmount = resolveRequiredAmountWithCatalog(requestedAmount, productId, orderType, catalogPrice);
        if (requiredAmount == null) {
            throw new IllegalArgumentException("amount requis pour verifier le solde.");
        }
        if (requiredAmount <= 0) {
            log.info("Check solde eSIM: productId={}, catalogAmount={}, catalogCurrency={}, requestedAmount={}, requiredAmount={}, walletBalance=SKIPPED",
                    productId,
                    catalogPrice != null ? catalogPrice.amount : null,
                    catalogPrice != null ? catalogPrice.currency : null,
                    requestedAmount,
                    requiredAmount);
            return;
        }
        Users user = findUserByUsernameOrId(userId);
        ResponseEntity<String> response = walletService.getWalletBalance(user.getUsername());
        if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
            throw new IllegalArgumentException("Impossible de recuperer le solde utilisateur.");
        }
        double balance;
        try {
            balance = Double.parseDouble(response.getBody());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Solde utilisateur invalide.");
        }
        log.info("Check solde eSIM: productId={}, catalogAmount={}, catalogCurrency={}, requestedAmount={}, requiredAmount={}, walletBalance={}",
                productId,
                catalogPrice != null ? catalogPrice.amount : null,
                catalogPrice != null ? catalogPrice.currency : null,
                requestedAmount,
                requiredAmount,
                balance);
        if (balance < requiredAmount) {
            throw new IllegalArgumentException("Solde insuffisant.");
        }
    }

    private Double resolveRequiredAmountWithCatalog(Double requestedAmount, String productId, String orderType,
                                                    CatalogPrice catalogPrice) {
        if (catalogPrice != null && catalogPrice.amount != null) {
            Double catalogAmount = maybeConvertToUsdc(catalogPrice);
            if (catalogAmount != null && requestedAmount != null && requestedAmount > 0
                    && Math.abs(requestedAmount - catalogAmount) > 0.01) {
                log.warn("Montant request ({}) different du catalogue ({}) pour productId={}",
                        requestedAmount, catalogAmount, productId);
            }
            return catalogAmount;
        }
        return requestedAmount;
    }

    private CatalogPrice fetchCatalogPrice(String productId, String orderType) {
        try {
            ResponseEntity<JsonNode> response = getCatalogProduct(productId);
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                log.warn("Impossible de recuperer le catalogue pour productId={}", productId);
                return null;
            }
            CatalogPrice price = extractPriceFromCatalog(response.getBody(), productId, orderType);
            if (price == null || price.amount == null) {
                log.warn("Montant introuvable dans le catalogue pour productId={}", productId);
            }
            return price;
        } catch (Exception e) {
            log.warn("Erreur catalogue pour productId={}: {}", productId, e.getMessage());
            return null;
        }
    }

    private CatalogPrice extractPriceFromCatalog(JsonNode node, String productId, String orderType) {
        if (node == null) {
            return null;
        }
        JsonNode target = node;
        JsonNode products = node.path("products");
        if (products.isArray()) {
            JsonNode match = null;
            for (JsonNode product : products) {
                String candidateId = extractFirstValue(product, List.of("productId"));
                if (candidateId != null && candidateId.equals(productId)) {
                    match = product;
                    break;
                }
            }
            if (match == null && products.size() == 1) {
                match = products.get(0);
            }
            if (match != null) {
                target = match;
            }
        }
        JsonNode prices = target.path("prices");
        if (prices.isMissingNode() || prices.isNull()) {
            JsonNode productNode = target.path("product");
            prices = productNode.path("prices");
        }
        if (!prices.isMissingNode() && !prices.isNull()) {
            if (prices.has("amount") && prices.get("amount").isNumber()) {
                return new CatalogPrice(
                        normalizePriceAmount(prices.get("amount").asDouble(), prices.path("unit").asText(null)),
                        prices.path("currency").asText(null)
                );
            }
            String feeKey = (orderType != null && orderType.toLowerCase().contains("renew"))
                    ? "renewalFee" : "subscriptionFee";
            JsonNode feeNode = prices.path(feeKey);
            CatalogPrice feePrice = extractPriceFromFeeArray(feeNode);
            if (feePrice != null && feePrice.amount != null) {
                return feePrice;
            }
            String altKey = "renewalFee".equals(feeKey) ? "subscriptionFee" : "renewalFee";
            return extractPriceFromFeeArray(prices.path(altKey));
        }
        return null;
    }

    private CatalogPrice extractPriceFromFeeArray(JsonNode feeNode) {
        if (feeNode == null || feeNode.isMissingNode() || feeNode.isNull()) {
            return null;
        }
        if (feeNode.isArray()) {
            for (JsonNode inner : feeNode) {
                if (inner.isArray()) {
                    for (JsonNode item : inner) {
                        if (item != null && item.has("amount") && item.get("amount").isNumber()) {
                            return new CatalogPrice(
                                    normalizePriceAmount(item.get("amount").asDouble(), item.path("unit").asText(null)),
                                    item.path("currency").asText(null)
                            );
                        }
                    }
                } else if (inner.has("amount") && inner.get("amount").isNumber()) {
                    return new CatalogPrice(
                            normalizePriceAmount(inner.get("amount").asDouble(), inner.path("unit").asText(null)),
                            inner.path("currency").asText(null)
                    );
                }
            }
        } else if (feeNode.has("amount") && feeNode.get("amount").isNumber()) {
            return new CatalogPrice(
                    normalizePriceAmount(feeNode.get("amount").asDouble(), feeNode.path("unit").asText(null)),
                    feeNode.path("currency").asText(null)
            );
        }
        return null;
    }

    private Double maybeConvertToUsdc(CatalogPrice price) {
        if (price == null || price.amount == null) {
            return null;
        }
        if (price.amount <= 0) {
            return price.amount;
        }
        String currency = price.currency;
        if (currency == null || currency.isBlank() || "USDC".equalsIgnoreCase(currency)) {
            return price.amount;
        }
        try {
            var conversion = currencyFreaksClientService.convertCurrency(currency, "USDC", price.amount);
            if (conversion.getStatusCode().is2xxSuccessful() && conversion.getBody() != null) {
                String converted = conversion.getBody().getConvertedAmount();
                if (converted != null && !converted.isBlank()) {
                    return Double.parseDouble(converted);
                }
            }
        } catch (Exception e) {
            log.warn("Conversion devise eSIM {}->USDC échouée: {}", currency, e.getMessage());
        }
        return price.amount;
    }

    private Double normalizePriceAmount(double amount, String unit) {
        if (unit != null && "CENTS".equalsIgnoreCase(unit.trim())) {
            return amount / 100.0;
        }
        return amount;
    }

    private static final class CatalogPrice {
        private final Double amount;
        private final String currency;

        private CatalogPrice(Double amount, String currency) {
            this.amount = amount;
            this.currency = currency;
        }
    }

    private String normalizeMsisdn(String msisdn) {
        String normalized = msisdn.replaceAll("[^0-9]", "");
        if (normalized.length() < 6 || normalized.length() > 15) {
            throw new IllegalArgumentException("msisdn invalide.");
        }
        return normalized;
    }


    private String normalizeStatus(String status) {
        if (status == null) {
            return null;
        }
        String trimmed = status.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.replaceAll("[^A-Za-z]", "").toUpperCase();
    }

    private boolean isSuspendedStatus(String normalizedStatus) {
        if (normalizedStatus == null) {
            return false;
        }
        return normalizedStatus.contains("SUSPEND");
    }

    private boolean isTerminatedStatus(String normalizedStatus) {
        if (normalizedStatus == null) {
            return false;
        }
        return normalizedStatus.contains("TERMINAT")
                || normalizedStatus.contains("CANCEL")
                || normalizedStatus.contains("RESILI")
                || normalizedStatus.contains("DEACTIV")
                || normalizedStatus.contains("CLOSED");
    }

    private void updateLocalSimStatusFromRemote(EsimSimSerial record, String normalizedSubscriberStatus) {
        if (record == null || normalizedSubscriberStatus == null) {
            return;
        }
        EsimSimSerialStatus current = record.getStatus();
        EsimSimSerialStatus target = null;

        if (isSuspendedStatus(normalizedSubscriberStatus)) {
            target = EsimSimSerialStatus.SUSPENDED;
        } else if (isTerminatedStatus(normalizedSubscriberStatus)) {
            target = EsimSimSerialStatus.TERMINATED;
        } else if (isActiveStatus(normalizedSubscriberStatus)) {
            target = EsimSimSerialStatus.USED;
        } else if (isPreactivatedStatus(normalizedSubscriberStatus)) {
            target = EsimSimSerialStatus.RESERVED;
        }

        if (target != null && current != target) {
            try {
                record.setStatus(target);
                simSerialRepository.save(record);
            } catch (Exception e) {
                log.warn("Impossible de synchroniser le statut local {} -> {} pour simSerial {}: {}",
                        current, target, record.getSimSerial(), e.getMessage());
            }
        }
    }

    private boolean hasActivePlanFromInventory(JsonNode body) {
        if (body == null) {
            return false;
        }
        if (hasNonEmptyArray(body, "subscriptions")
                || hasNonEmptyArray(body, "products")
                || hasNonEmptyArray(body, "items")) {
            return true;
        }
        JsonNode data = body.get("data");
        if (data != null && (hasNonEmptyArray(data, "subscriptions")
                || hasNonEmptyArray(data, "products")
                || hasNonEmptyArray(data, "items"))) {
            return true;
        }
        return hasAnyArrayWithItems(body, 3);
    }

    private boolean hasNonEmptyArray(JsonNode node, String key) {
        if (node == null || key == null) {
            return false;
        }
        JsonNode value = node.get(key);
        return value != null && value.isArray() && value.size() > 0;
    }

    private boolean hasAnyArrayWithItems(JsonNode node, int depth) {
        if (node == null || depth < 0) {
            return false;
        }
        if (node.isArray()) {
            return node.size() > 0;
        }
        if (node.isObject()) {
            for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
                String field = it.next();
                JsonNode value = node.get(field);
                if (value == null) {
                    continue;
                }
                if (value.isArray() && value.size() > 0) {
                    return true;
                }
                if (value.isObject() && hasAnyArrayWithItems(value, depth - 1)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isActiveStatus(String normalizedStatus) {
        if (normalizedStatus == null) {
            return false;
        }
        return "ACTIVE".equals(normalizedStatus) || "ACTIVATED".equals(normalizedStatus);
    }

    private boolean isPreactivatedStatus(String normalizedStatus) {
        if (normalizedStatus == null) {
            return false;
        }
        return normalizedStatus.startsWith("PREACTIV");
    }

    private boolean isSimNotReadyForActivation(JsonNode body) {
        if (body == null) {
            return false;
        }
        String raw = body.toString().toLowerCase();
        return raw.contains("not ready for activation");
    }

    private JsonNode parseJsonOrText(String body) throws IOException {
        if (body == null || body.isBlank()) {
            return TextNode.valueOf("");
        }
        try {
            return objectMapper.readTree(body);
        } catch (IOException e) {
            return TextNode.valueOf(body);
        }
    }

    private String extractFirstValue(JsonNode node, List<String> keys) {
        if (node == null) {
            return null;
        }
        if (node.isObject()) {
            for (String key : keys) {
                if (node.has(key) && !node.get(key).isNull()) {
                    return node.get(key).asText();
                }
            }
        }
        for (JsonNode child : node) {
            String found = extractFirstValue(child, keys);
            if (found != null && !found.isBlank()) {
                return found;
            }
        }
        return null;
    }

    private String extractNestedValue(JsonNode node, String... path) {
        if (node == null || path == null) {
            return null;
        }
        JsonNode current = node;
        for (String key : path) {
            if (current == null || current.isMissingNode() || current.isNull()) {
                return null;
            }
            current = current.path(key);
        }
        if (current == null || current.isMissingNode() || current.isNull()) {
            return null;
        }
        return current.asText();
    }

    private void putIfNotBlank(Map<String, Object> payload, String key, String value) {
        if (value != null && !value.isBlank()) {
            payload.put(key, value);
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String resolveBusinessErrorCode(String message) {
        if (message == null || message.isBlank()) {
            return "ESIM_BAD_REQUEST";
        }
        String normalized = message.trim().toUpperCase().replace(' ', '_');
        if (normalized.matches("[A-Z0-9_]+")) {
            return normalized;
        }
        return "ESIM_BAD_REQUEST";
    }

    private ResponseEntity<JsonNode> errorJson(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(objectMapper.createObjectNode()
                .put("success", false)
                .put("code", code)
                .put("message", message));
    }

    private ResponseEntity<Object> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(objectMapper.createObjectNode()
                .put("success", false)
                .put("code", code)
                .put("message", message));
    }

    private boolean isWebhookSignatureValid(String payload, String signatureHeader) throws Exception {
        if (webhookSecret == null || webhookSecret.isBlank()) {
            return true;
        }
        if (signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        if (!signatureHeader.startsWith("sha256=")) {
            return false;
        }
        String expected = "sha256=" + hmacSha256Hex(payload, webhookSecret);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signatureHeader.getBytes(StandardCharsets.UTF_8));
    }

    private String hmacSha256Hex(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return toHex(digest);
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // OCS Options — Catalog
    // -------------------------------------------------------------------------

    @Override
    public ResponseEntity<JsonNode> getCatalogOptions() {
        try {
            if (catalogOptionsUrl == null || catalogOptionsUrl.isBlank()) {
                return errorJson(HttpStatus.SERVICE_UNAVAILABLE, "ESIM_CONFIG_MISSING",
                        "catalogOptionsUrl non configuré.");
            }
            String token = getAccessToken();
            HttpRequest request = createRequestBuilder(catalogOptionsUrl, token).GET().build();
            return sendJsonRequestWithStatus(request);
        } catch (Exception e) {
            log.error("Erreur getCatalogOptions : {}", e.getMessage(), e);
            return errorJson(HttpStatus.INTERNAL_SERVER_ERROR, "ESIM_INTERNAL_ERROR", "Erreur interne eSIM.");
        }
    }

    @Override
    public ResponseEntity<JsonNode> getCatalogDataSafeguardOption() {
        try {
            if (catalogOptionsUrl == null || catalogOptionsUrl.isBlank()) {
                return errorJson(HttpStatus.SERVICE_UNAVAILABLE, "ESIM_CONFIG_MISSING",
                        "catalogOptionsUrl non configuré.");
            }
            String token = getAccessToken();
            String url = catalogOptionsUrl.endsWith("/")
                    ? catalogOptionsUrl + "dataSafeguard"
                    : catalogOptionsUrl + "/dataSafeguard";
            HttpRequest request = createRequestBuilder(url, token).GET().build();
            return sendJsonRequestWithStatus(request);
        } catch (Exception e) {
            log.error("Erreur getCatalogDataSafeguardOption : {}", e.getMessage(), e);
            return errorJson(HttpStatus.INTERNAL_SERVER_ERROR, "ESIM_INTERNAL_ERROR", "Erreur interne eSIM.");
        }
    }

    // -------------------------------------------------------------------------
    // OCS Options — Inventory
    // -------------------------------------------------------------------------

    @Override
    public ResponseEntity<JsonNode> getSubscriberOptions(String msisdn) {
        try {
            if (subscriberOptionsUrl == null || subscriberOptionsUrl.isBlank()) {
                return errorJson(HttpStatus.SERVICE_UNAVAILABLE, "ESIM_CONFIG_MISSING",
                        "subscriberOptionsUrl non configuré.");
            }
            if (msisdn == null || msisdn.isBlank()) {
                return errorJson(HttpStatus.BAD_REQUEST, "ESIM_BAD_REQUEST", "msisdn requis.");
            }
            String token = getAccessToken();
            String url = subscriberOptionsUrl + "?msisdn=" + encode(normalizeMsisdn(msisdn));
            HttpRequest request = createRequestBuilder(url, token).GET().build();
            return sendJsonRequestWithStatus(request);
        } catch (IllegalArgumentException e) {
            return errorJson(HttpStatus.BAD_REQUEST, resolveBusinessErrorCode(e.getMessage()), e.getMessage());
        } catch (Exception e) {
            log.error("Erreur getSubscriberOptions : {}", e.getMessage(), e);
            return errorJson(HttpStatus.INTERNAL_SERVER_ERROR, "ESIM_INTERNAL_ERROR", "Erreur interne eSIM.");
        }
    }

    // -------------------------------------------------------------------------
    // OCS Products — Subscriptions (orderType passthrough)
    // -------------------------------------------------------------------------

    @Override
    public ResponseEntity<JsonNode> cancelRenewal(CancelRenewalRequestDto request) {
        try {
            if (request.getMsisdn() == null || request.getMsisdn().isBlank()) {
                return errorJson(HttpStatus.BAD_REQUEST, "ESIM_BAD_REQUEST", "msisdn requis.");
            }
            SubscribeProductRequestDto dto = new SubscribeProductRequestDto();
            dto.setMsisdn(normalizeMsisdn(request.getMsisdn()));
            dto.setProductId(request.getProductId());
            dto.setOrderType("cancelRenewal");
            dto.setSource(request.getSource() != null ? request.getSource() : "api");
            dto.setMvnoRef(request.getMvnoRef());
            return subscribeProduct(dto);
        } catch (IllegalArgumentException e) {
            return errorJson(HttpStatus.BAD_REQUEST, resolveBusinessErrorCode(e.getMessage()), e.getMessage());
        } catch (Exception e) {
            log.error("Erreur cancelRenewal : {}", e.getMessage(), e);
            return errorJson(HttpStatus.INTERNAL_SERVER_ERROR, "ESIM_INTERNAL_ERROR", "Erreur interne eSIM.");
        }
    }

    @Override
    public ResponseEntity<JsonNode> modifySubscription(ModifySubscriptionRequestDto request) {
        try {
            if (request.getMsisdn() == null || request.getMsisdn().isBlank()) {
                return errorJson(HttpStatus.BAD_REQUEST, "ESIM_BAD_REQUEST", "msisdn requis.");
            }
            SubscribeProductRequestDto dto = new SubscribeProductRequestDto();
            dto.setMsisdn(normalizeMsisdn(request.getMsisdn()));
            dto.setProductId(request.getProductId());
            dto.setOrderType("modify");
            dto.setSource(request.getSource() != null ? request.getSource() : "api");
            dto.setMvnoRef(request.getMvnoRef());
            return subscribeProduct(dto);
        } catch (IllegalArgumentException e) {
            return errorJson(HttpStatus.BAD_REQUEST, resolveBusinessErrorCode(e.getMessage()), e.getMessage());
        } catch (Exception e) {
            log.error("Erreur modifySubscription : {}", e.getMessage(), e);
            return errorJson(HttpStatus.INTERNAL_SERVER_ERROR, "ESIM_INTERNAL_ERROR", "Erreur interne eSIM.");
        }
    }

    // -------------------------------------------------------------------------
    // OCS Options — Subscriptions
    // -------------------------------------------------------------------------

    @Override
    public ResponseEntity<JsonNode> manageCredit(ManageCreditRequestDto request) {
        try {
            if (creditOrdersUrl == null || creditOrdersUrl.isBlank()) {
                return errorJson(HttpStatus.SERVICE_UNAVAILABLE, "ESIM_CONFIG_MISSING",
                        "creditOrdersUrl non configuré.");
            }
            if (request.getMsisdn() == null || request.getMsisdn().isBlank()) {
                return errorJson(HttpStatus.BAD_REQUEST, "ESIM_BAD_REQUEST", "msisdn requis.");
            }
            if (request.getCredit() == null || request.getCredit().getMode() == null) {
                return errorJson(HttpStatus.BAD_REQUEST, "ESIM_BAD_REQUEST",
                        "credit.mode requis (add, remove ou set).");
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("bind", Map.of("msisdn", normalizeMsisdn(request.getMsisdn())));
            payload.put("mvnoRef", request.getMvnoRef() != null ? request.getMvnoRef() : mvnoRef);
            payload.put("source", request.getSource() != null ? request.getSource() : "api");

            Map<String, Object> credit = new HashMap<>();
            credit.put("mode", request.getCredit().getMode());
            credit.put("amount", request.getCredit().getAmount() != null ? request.getCredit().getAmount() : 0);
            credit.put("unit", request.getCredit().getUnit() != null ? request.getCredit().getUnit() : "CENTS");
            if (request.getCredit().getExpirationDate() != null) {
                credit.put("expirationDate", request.getCredit().getExpirationDate());
            }
            payload.put("credit", credit);

            if (request.getTransactionReference() != null) {
                payload.put("transactionReference", request.getTransactionReference());
            }

            String token = getAccessToken();
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest httpRequest = createRequestBuilder(creditOrdersUrl, token)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            return sendJsonRequestWithStatus(httpRequest);
        } catch (IllegalArgumentException e) {
            return errorJson(HttpStatus.BAD_REQUEST, resolveBusinessErrorCode(e.getMessage()), e.getMessage());
        } catch (Exception e) {
            log.error("Erreur manageCredit : {}", e.getMessage(), e);
            return errorJson(HttpStatus.INTERNAL_SERVER_ERROR, "ESIM_INTERNAL_ERROR", "Erreur interne eSIM.");
        }
    }

    @Override
    public ResponseEntity<Map<String, String>> getExchangeRate(String from, String to) {
        try {
            if (from == null || from.isBlank() || to == null || to.isBlank()) {
                return ResponseEntity.badRequest().build();
            }
            String fromUpper = from.trim().toUpperCase();
            String toUpper = to.trim().toUpperCase();
            if (fromUpper.equals(toUpper)) {
                return ResponseEntity.ok(Map.of("from", fromUpper, "to", toUpper, "rate", "1.0"));
            }
            var conversion = currencyFreaksClientService.convertCurrency(fromUpper, toUpper, 1.0);
            if (!conversion.getStatusCode().is2xxSuccessful() || conversion.getBody() == null) {
                log.warn("CurrencyFreaks getExchangeRate {}->{} failed: {}", fromUpper, toUpper, conversion.getStatusCode());
                return ResponseEntity.status(conversion.getStatusCode()).build();
            }
            var body = conversion.getBody();
            Map<String, String> result = new HashMap<>();
            result.put("from", body.getFrom() != null ? body.getFrom() : fromUpper);
            result.put("to", body.getTo() != null ? body.getTo() : toUpper);
            result.put("rate", body.getRate() != null ? body.getRate() : "1.0");
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Erreur getExchangeRate {}->{}: {}", from, to, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public ResponseEntity<JsonNode> manageDataSafeguard(ManageDataSafeguardRequestDto request) {
        try {
            if (dataSafeguardOrdersUrl == null || dataSafeguardOrdersUrl.isBlank()) {
                return errorJson(HttpStatus.SERVICE_UNAVAILABLE, "ESIM_CONFIG_MISSING",
                        "dataSafeguardOrdersUrl non configuré.");
            }
            if (request.getMsisdn() == null || request.getMsisdn().isBlank()) {
                return errorJson(HttpStatus.BAD_REQUEST, "ESIM_BAD_REQUEST", "msisdn requis.");
            }
            if (request.getState() == null || request.getState().isBlank()) {
                return errorJson(HttpStatus.BAD_REQUEST, "ESIM_BAD_REQUEST",
                        "state requis (active, temporarily-inactive ou permanently-inactive).");
            }

            Map<String, Object> payload = new HashMap<>();
            payload.put("bind", Map.of("msisdn", normalizeMsisdn(request.getMsisdn())));
            payload.put("source", request.getSource() != null ? request.getSource() : "api");
            payload.put("state", request.getState());
            if (request.getTransactionReference() != null) {
                payload.put("transactionReference", request.getTransactionReference());
            }

            String token = getAccessToken();
            String body = objectMapper.writeValueAsString(payload);
            HttpRequest httpRequest = createRequestBuilder(dataSafeguardOrdersUrl, token)
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            return sendJsonRequestWithStatus(httpRequest);
        } catch (IllegalArgumentException e) {
            return errorJson(HttpStatus.BAD_REQUEST, resolveBusinessErrorCode(e.getMessage()), e.getMessage());
        } catch (Exception e) {
            log.error("Erreur manageDataSafeguard : {}", e.getMessage(), e);
            return errorJson(HttpStatus.INTERNAL_SERVER_ERROR, "ESIM_INTERNAL_ERROR", "Erreur interne eSIM.");
        }
    }
}
