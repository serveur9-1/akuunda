package org.akuunda.akuundawallet.wallet.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dto.FeesCalculationRequest;
import org.akuunda.akuundawallet.wallet.api.dto.FeesCalculationResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.PriceQuoteRequest;
import org.akuunda.akuundawallet.wallet.api.dto.external.PriceQuoteResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.QuoteRequestDTO;
import org.akuunda.akuundawallet.wallet.service.FeesCalculationService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaGuardarianClientService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaMtPelerinServiceClient;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaYellowCardClientService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.CurrencyFreaksClientService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
@Slf4j
@RequiredArgsConstructor
public class FeesCalculationServiceImpl implements FeesCalculationService {

    private static final double AKUUNDA_FEE_PERCENTAGE_ONRAMP = 0.0218; // 2,18% pour OnRamp
    private static final double AKUUNDA_FEE_PERCENTAGE_OFFRAMP = 0.035; // 3.5% pour OffRamp
    private static final String OPERATOR_YELLOWCARD = "yellowcard";
    private static final String OPERATOR_GUARDIAN = "guardarian";
    private static final String OPERATOR_MTPELERIN = "mtpelerin";

    private final AkuundaYellowCardClientService yellowCardClientService;
    private final AkuundaGuardarianClientService guardarianClientService;
    private final CurrencyFreaksClientService currencyFreaksClientService;
    private final AkuundaMtPelerinServiceClient mtPelerinServiceClient;
    private final ObjectMapper objectMapper;

    @Override
    public ResponseEntity<FeesCalculationResponse> calculateFees(FeesCalculationRequest request) {
        try {
            log.info("Calculating fees for amount: {} {}, country: {}, operator: {}", 
                    request.getAmount(), request.getCurrency(), request.getCountryCode(), request.getOperator());

            // Détecter l'opérateur si non spécifié
            String operator = request.getOperator();
            if (operator == null || operator.isEmpty()) {
                operator = detectOperator(request.getCountryCode());
            }

            if (OPERATOR_YELLOWCARD.equalsIgnoreCase(operator)) {
                return calculateYellowCardFees(request);
            } else if (OPERATOR_GUARDIAN.equalsIgnoreCase(operator)) {
                return calculateGuardarianFees(request);
            } else if (OPERATOR_MTPELERIN.equalsIgnoreCase(operator)) {
                return calculateMtPelerinFees(request);
            } else {
                log.error("Opérateur non supporté: {}", operator);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(FeesCalculationResponse.builder()
                                .amountSent(request.getAmount())
                                .currency(request.getCurrency())
                                .build());
            }
        } catch (Exception e) {
            log.error("Erreur lors du calcul des frais", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(FeesCalculationResponse.builder()
                            .amountSent(request.getAmount())
                            .currency(request.getCurrency())
                            .build());
        }
    }

    /**
     * Calcule les frais pour YellowCard (Afrique) selon la procédure Akuunda Pay
     * 
     * 🔹 Étapes de calcul:
     * 1. L'utilisateur saisit un montant en monnaie locale (XOF)
     * 2. Le backend interroge l'API Currency Freaks pour convertir ce montant en USD, selon le taux réel du marché
     * 3. Le montant converti en USD est ensuite multiplié par le taux du jour de Yellow Card pour obtenir sa valeur équivalente en XOF
     * 4. Ce montant total est multiplié par le taux de frais Akuunda Pay (2,18 %)
     * 5. Le résultat permet d'afficher à l'utilisateur une estimation des frais ainsi que le montant net qu'il recevra après déduction
     * 
     * 🧮 Exemple concret:
     * - Vous déposez : 2 005 XOF
     * - Conversion XOF → USD via Currency Freaks (ex: 2005 XOF = 3.40 USD)
     * - Conversion USD → XOF via YellowCard (ex: 3.40 USD × 588.96 = 2002.46 XOF)
     * - Frais estimés : 2002.46 × 0.0218 = 43.65 XOF ≈ 44 XOF
     * - Vous recevrez : 2005 - 44 = 1961 XOF
     * 
     * 🧾 Formule de calcul:
     * - Montant en USD = Montant XOF / Taux USD (Currency Freaks)
     * - Montant après conversion = Montant USD × Taux YC (YellowCard)
     * - Frais estimés (XOF) = Montant après conversion × 0.0218
     * - Montant reçu = Montant initial - Frais estimés
     * 
     * Utilise BigDecimal pour éviter les erreurs d'arrondi
     */
    private ResponseEntity<FeesCalculationResponse> calculateYellowCardFees(FeesCalculationRequest request) {
        try {
            // Utiliser BigDecimal pour tous les calculs financiers
            BigDecimal amountXOF = BigDecimal.valueOf(request.getAmount()); // Montant saisi par l'utilisateur
            String currency = request.getCurrency();

            log.info("🔹 Étape 1: Conversion XOF → USD via Currency Freaks pour {} {}", amountXOF, currency);

            // 1. Convertir XOF → USD via Currency Freaks (taux réel du marché)
            var currencyFreaksResponse = currencyFreaksClientService.convertCurrency(currency, "USD", amountXOF.doubleValue());
            
            if (currencyFreaksResponse.getStatusCode() != HttpStatus.OK || currencyFreaksResponse.getBody() == null) {
                log.error("Erreur lors de la conversion Currency Freaks (XOF → USD)");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            var currencyFreaksBody = currencyFreaksResponse.getBody();
            if (currencyFreaksBody == null) {
                log.error("Réponse Currency Freaks vide");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            String convertedAmountStr = currencyFreaksBody.getConvertedAmount();
            BigDecimal amountInUSD;
            try {
                amountInUSD = new BigDecimal(convertedAmountStr);
            } catch (NumberFormatException e) {
                log.error("Format invalide pour le montant converti Currency Freaks: {}", convertedAmountStr);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            // Calculer le taux USD utilisé par Currency Freaks (pour information)
            BigDecimal currencyFreaksRate = amountInUSD.divide(amountXOF, 10, RoundingMode.HALF_UP);
            log.info("✅ Étape 1 résultat: {} {} → {} USD (taux Currency Freaks: {} USD/{} = {})", 
                    amountXOF, currency, amountInUSD, amountXOF, currency, currencyFreaksRate);

            // 2. Récupérer le taux YellowCard buy (USD → XOF)
            log.info("🔹 Étape 2: Récupération du taux YellowCard buy (USD → XOF)");
            
            String channelId = null;
            ResponseEntity<String> yellowCardRatesResponse = null;
            
            if (request.getCountryCode() != null) {
                channelId = getFirstChannelIdForCountry(request.getCountryCode());
                if (channelId != null && !channelId.isEmpty()) {
                    log.debug("Tentative de récupération des taux avec channelId: {}", channelId);
                    yellowCardRatesResponse = yellowCardClientService.getRatesByChannelId(channelId, currency);
                }
            }
            
            if (yellowCardRatesResponse == null || 
                yellowCardRatesResponse.getStatusCode() != HttpStatus.OK || 
                yellowCardRatesResponse.getBody() == null) {
                log.debug("Fallback: Getting YellowCard rate without channelId");
                yellowCardRatesResponse = yellowCardClientService.getRates(currency);
            }
            
            if (yellowCardRatesResponse.getStatusCode() != HttpStatus.OK || yellowCardRatesResponse.getBody() == null) {
                log.error("Erreur lors de la récupération des taux YellowCard");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            BigDecimal yellowCardBuyRate = parseYellowCardBuyRateBigDecimal(yellowCardRatesResponse.getBody(), currency);
            
            if (yellowCardBuyRate.compareTo(BigDecimal.ZERO) <= 0) {
                log.error("Taux YellowCard 'buy' invalide ou non trouvé");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            log.info("✅ Étape 2 résultat: Taux YellowCard buy = {} (1 USD = {} {})", yellowCardBuyRate, yellowCardBuyRate, currency);

            // 3. Convertir USD → XOF via le taux YellowCard
            log.info("🔹 Étape 3: Conversion USD → XOF via taux YellowCard");
            BigDecimal amountAfterYellowCardRate = amountInUSD.multiply(yellowCardBuyRate);
            log.info("✅ Étape 3 résultat: {} USD × {} = {} {} (montant après conversion YellowCard)", 
                    amountInUSD, yellowCardBuyRate, amountAfterYellowCardRate, currency);

            // 4. Calculer les frais Akuunda Pay (2,18%)
            log.info("🔹 Étape 4: Calcul des frais Akuunda Pay (2,18%)");
            BigDecimal feePercentageBD = BigDecimal.valueOf(AKUUNDA_FEE_PERCENTAGE_ONRAMP);
            // Frais = Montant après conversion × 0.0218
            BigDecimal estimatedFees = amountAfterYellowCardRate.multiply(feePercentageBD);
            log.info("✅ Étape 4 résultat: Frais estimés = {} {} × 0.0218 = {} {}", 
                    amountAfterYellowCardRate, currency, estimatedFees, currency);
            
            // 5. Calculer le montant net que l'utilisateur recevra
            log.info("🔹 Étape 5: Calcul du montant net reçu");
            // Montant reçu = Montant initial - Frais estimés
            BigDecimal amountReceived = amountXOF.subtract(estimatedFees);
            log.info("✅ Étape 5 résultat: Montant reçu = {} {} - {} {} = {} {}", 
                    amountXOF, currency, estimatedFees, currency, amountReceived, currency);
            
            // Arrondir uniquement à la fin pour l'affichage (2 décimales)
            estimatedFees = estimatedFees.setScale(2, RoundingMode.HALF_UP);
            amountReceived = amountReceived.setScale(2, RoundingMode.HALF_UP);

            log.info("📊 Résultat final: Montant déposé={} {}, Frais estimés={} {} (2,18%), Montant reçu={} {}", 
                    amountXOF, currency, estimatedFees, currency, amountReceived, currency);

            // Pourcentage de frais
            BigDecimal feePercentage = feePercentageBD.multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);

            // Construire la réponse avec les détails du calcul
            FeesCalculationResponse.FeesBreakdown breakdown = FeesCalculationResponse.FeesBreakdown.builder()
                    .yellowCardRate(yellowCardBuyRate.doubleValue()) // Taux YellowCard buy utilisé
                    .akuundaFeeRate(AKUUNDA_FEE_PERCENTAGE_ONRAMP) // Taux de frais Akuunda (2,18%)
                    .amountInUsd(amountInUSD.setScale(6, RoundingMode.HALF_UP).doubleValue()) // Montant en USD (via Currency Freaks)
                    .amountAfterYellowCardRate(amountAfterYellowCardRate.setScale(6, RoundingMode.HALF_UP).doubleValue()) // Montant après conversion YellowCard
                    .build();

            double exchangeRateValue = yellowCardBuyRate.setScale(2, RoundingMode.HALF_UP).doubleValue();
            FeesCalculationResponse response = FeesCalculationResponse.builder()
                    .amountSent(amountXOF.setScale(2, RoundingMode.HALF_UP).doubleValue()) // Montant déposé (montant initial)
                    .currency(currency)
                    .estimatedFees(estimatedFees.doubleValue()) // Frais estimés (montant après conversion × 0.0218)
                    .amountReceived(amountReceived.doubleValue()) // Montant reçu (montant initial - frais)
                    .exchangeRate(exchangeRateValue)
                    .estimatedExchangeRate(exchangeRateValue) // Alias de exchangeRate pour compatibilité
                    .feePercentage(feePercentage.doubleValue())
                    .operator(OPERATOR_YELLOWCARD)
                    .breakdown(breakdown)
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Erreur lors du calcul des frais YellowCard", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private ResponseEntity<FeesCalculationResponse> calculateMtPelerinFees(FeesCalculationRequest request) {

        log.debug("calculateMtPelerinFees called with request: {}", request);
        log.info("calculateMtPelerinFees called with request: {}", request);
        PriceQuoteRequest priceQuoteRequest = new PriceQuoteRequest();
        priceQuoteRequest.setDestCurrency("USDC"); // Supposons que la destination est toujours USDC
        priceQuoteRequest.setSourceCurrency(request.getCurrency());
        priceQuoteRequest.setSourceAmount(request.getAmount());
        priceQuoteRequest.setIsCardPayment(false);
        priceQuoteRequest.setDestNetwork("matic_mainnet");
        priceQuoteRequest.setSourceNetwork("fiat");

        ResponseEntity<PriceQuoteResponse> mtPelerinResponse = mtPelerinServiceClient
                .getPriceQuote(priceQuoteRequest);
        if (mtPelerinResponse.getStatusCode() == HttpStatus.OK) {
            PriceQuoteResponse response = mtPelerinResponse.getBody();
            if (response == null) {
                log.error("PriceQuoteResponse est null");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            } else {
                log.info("PriceQuoteResponse reçu: sourceCurrency={}, destCurrency={}, " +
                                "sourceAmount={}, destAmount={}, exchangeRate={}, fees={}",
                        response.getSourceCurrency(), response.getDestCurrency(),
                        response.getSourceAmount(), response.getDestAmount(),
                        response.getExchangeRate(), response.getFees());
                FeesCalculationResponse feesResponse = FeesCalculationResponse.builder()
                        .amountSent(response.getSourceAmount())
                        .currency(response.getSourceCurrency())
                        .estimatedFees(response.getFees() != null ? response.getFees() : 0.0)
                        .amountReceived(response.getDestAmount())
                        .exchangeRate(response.getExchangeRate() != null ? response.getExchangeRate() : 0.0)
                        .estimatedExchangeRate(response.getExchangeRate() != null ? response.getExchangeRate() : 0.0)
                        .feePercentage(response.getFees() != null && response.getSourceAmount() != null && response.getSourceAmount() > 0 ?
                                (response.getFees() / response.getSourceAmount()) * 100 : 0.0)
                        .operator(OPERATOR_MTPELERIN)
                        .build();

                return new ResponseEntity<>(feesResponse, HttpStatus.OK);
            }
        } else {
            log.error("Erreur lors de la récupération de l'estimation des prix MT Pelerin");
            return ResponseEntity.status(mtPelerinResponse.getStatusCode()).build();
        }
    }

    /**
     * Calcule les frais pour Guardarian (Europe/International)
     * 
     * Stratégie:
     * 1. Essayer d'abord d'utiliser l'endpoint /estimate de Guardarian (si disponible)
     * 2. Si l'endpoint n'est pas disponible (404), utiliser un calcul de frais fixe (0.5%)
     * 
     * Selon l'exemple fourni:
     * - Montant saisi: 30 EUR
     * - Frais de service: 0.15 EUR (0.5%)
     * - Montant reçu: 29.85 EUR
     * 
     * Utilise BigDecimal pour éviter les erreurs d'arrondi
     */
    private ResponseEntity<FeesCalculationResponse> calculateGuardarianFees(FeesCalculationRequest request) {
        try {
            BigDecimal amountSent = BigDecimal.valueOf(request.getAmount());
            String currency = request.getCurrency();

            log.info("Calculating Guardarian fees for: currency={}, amount={}", 
                    currency, amountSent);

            // Essayer d'abord d'utiliser l'endpoint /estimate de Guardarian
            // Note: L'endpoint essaie plusieurs variantes (/estimate, /estimate-amount, /estimates)
            try {
                // Créer une EstimateRequest pour Guardarian
                // Note: EstimateRequest nécessite from_currency, to_currency, amount
                // Pour l'OnRamp, on convertit généralement vers USDC
                var estimateRequest = new org.akuunda.akuundawallet.wallet.api.dto.external.EstimateRequest(
                        currency,  // from
                        "USDC",    // to (par défaut pour OnRamp)
                        request.getAmount()
                );

                var guardarianResponse = guardarianClientService.getEstimate(estimateRequest);
                
                if (guardarianResponse != null && guardarianResponse.getStatusCode() == HttpStatus.OK 
                        && guardarianResponse.getBody() != null) {
                    var estimate = guardarianResponse.getBody();
                    
                    // Vérification supplémentaire pour éviter les null pointer
                    if (estimate == null) {
                        log.warn("EstimateResponse est null malgré le check précédent");
                        throw new RuntimeException("EstimateResponse is null");
                    }
                    
                    // Utiliser les données de Guardarian si disponibles
                    // Pour OnRamp (devise d'origine → USDC):
                    // - estimatedAmount = value (montant reçu en USDC, ex: 54.83 USDC)
                    // - convertedAmount = converted_amount.amount (montant après frais dans la devise d'origine, ex: 49.75)
                    // - Les frais = amountSent - convertedAmount (si disponible)
                    // - amountReceived = toujours dans la devise d'origine (currency du request)
                    BigDecimal estimatedAmount = BigDecimal.valueOf(estimate.estimatedAmount());
                    BigDecimal estimatedFees;
                    BigDecimal amountReceived;
                    
                    // Si convertedAmount est disponible, l'utiliser pour calculer les frais correctement
                    // convertedAmount est déjà dans la devise d'origine (from_currency)
                    log.info("🔍 Debug Guardarian OnRamp: amountSent={} {}, estimatedAmount={} (USDC), convertedAmount={} ({}), rate={}", 
                            amountSent, currency, estimate.estimatedAmount(), estimate.convertedAmount(), currency, estimate.rate());
                    
                    // ⚠️ CRITIQUE : Vérifier si convertedAmount est disponible et valide
                    // Ne pas vérifier la devise ici, utiliser convertedAmount s'il est disponible
                    boolean useConvertedAmount = estimate.convertedAmount() != null && estimate.convertedAmount() > 0;
                    log.info("🔍 Vérification convertedAmount: null={}, >0={}, useConvertedAmount={}", 
                            estimate.convertedAmount() == null, 
                            estimate.convertedAmount() != null && estimate.convertedAmount() > 0,
                            useConvertedAmount);
                    
                    // Variable pour stocker si on a utilisé convertedAmount (pour diagnostic)
                    boolean actuallyUsedConvertedAmount = false;
                    
                    if (useConvertedAmount) {
                        BigDecimal convertedAmountBD = BigDecimal.valueOf(estimate.convertedAmount());
                        estimatedFees = amountSent.subtract(convertedAmountBD)
                                .setScale(2, RoundingMode.HALF_UP);
                        // amountReceived = montant reçu dans la devise d'origine (convertedAmount)
                        amountReceived = convertedAmountBD.setScale(2, RoundingMode.HALF_UP);
                        actuallyUsedConvertedAmount = true;
                        log.info("✅ ✅ ✅ Utilisation de convertedAmount pour calculer les frais: {} {} - {} {} = {} {} (frais), amountReceived={} {}", 
                                amountSent, currency, convertedAmountBD, currency, estimatedFees, currency, amountReceived, currency);
                    } else {
                        log.warn("⚠️ convertedAmount non disponible (null ou <= 0), utilisation du fallback avec estimatedAmount");
                        actuallyUsedConvertedAmount = false; // On n'utilise pas convertedAmount
                        // Fallback: Si convertedAmount n'est pas disponible, convertir estimatedAmount (USDC) en devise d'origine
                        double rate = estimate.rate();
                        if (rate > 0) {
                            // ⚠️ IMPORTANT : Détecter la direction du taux
                            // Si rate < 1, c'est probablement USDC → devise d'origine (ex: 0.841 = USDC → EUR)
                            // Si rate > 1, c'est probablement devise d'origine → USDC (ex: 1.188 = EUR → USDC)
                            BigDecimal estimatedAmountInOriginalCurrency;
                            if (rate < 1.0) {
                                // Taux inversé : USDC → devise d'origine, donc on multiplie
                                estimatedAmountInOriginalCurrency = estimatedAmount.multiply(BigDecimal.valueOf(rate))
                                        .setScale(2, RoundingMode.HALF_UP);
                                log.info("✅ Taux détecté comme inversé (USDC → {}): {} - conversion par multiplication", currency, rate);
                            } else {
                                // Taux normal : devise d'origine → USDC, donc on divise
                                estimatedAmountInOriginalCurrency = estimatedAmount.divide(BigDecimal.valueOf(rate), 2, RoundingMode.HALF_UP);
                                log.info("✅ Taux détecté comme normal ({} → USDC): {} - conversion par division", currency, rate);
                            }
                            
                            estimatedFees = amountSent.subtract(estimatedAmountInOriginalCurrency)
                                    .setScale(2, RoundingMode.HALF_UP);
                            // amountReceived = montant reçu dans la devise d'origine (conversion de USDC)
                            amountReceived = estimatedAmountInOriginalCurrency.setScale(2, RoundingMode.HALF_UP);
                            log.info("✅ Calcul des frais avec conversion (fallback): {} {} - {} {} = {} {} (frais), amountReceived={} {}", 
                                    amountSent, currency, estimatedAmountInOriginalCurrency, currency, estimatedFees, currency, amountReceived, currency);
                        } else {
                            // Si le taux n'est pas disponible, on ne peut pas calculer correctement
                            // Utiliser le fallback avec frais fixe
                            log.warn("⚠️ convertedAmount et rate non disponibles, utilisation du calcul fixe");
                            throw new RuntimeException("convertedAmount and rate not available");
                        }
                    }
                    
                    BigDecimal feePercentage = estimatedFees.divide(amountSent, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP);

                    log.info("✅ Guardarian API estimate utilisé: Amount Sent={} {}, Fees={} {}, Amount Received={} {}", 
                            amountSent, currency, estimatedFees, currency, amountReceived, currency);

                    // Pour Guardarian, le taux peut ne pas être disponible dans l'API
                    // Si le taux n'est pas disponible, on met 0.0 pour qu'il soit toujours affiché dans la réponse JSON
                    double guardarianRate = estimate.rate() > 0 ? estimate.rate() : 0.0;
                    
                    // guardarianConvertedAmount = montant reçu en USDC (estimatedAmount) - pour information
                    // guardarianServiceFee = frais dans la devise d'origine
                    // amountReceived = montant reçu dans la devise d'origine (convertedAmount)
                    FeesCalculationResponse.FeesBreakdown breakdown = FeesCalculationResponse.FeesBreakdown.builder()
                            .guardarianExchangeRate(guardarianRate)
                            .guardarianConvertedAmount(estimatedAmount.doubleValue()) // Montant reçu en USDC (pour information)
                            .guardarianServiceFee(estimatedFees.doubleValue()) // Frais dans la devise d'origine
                            .guardarianEstimatedAmount(estimatedAmount.doubleValue()) // Pour diagnostic
                            .guardarianConvertedAmountFromAPI(estimate.convertedAmount() != null ? estimate.convertedAmount() : null) // Pour diagnostic
                            .usedConvertedAmount(actuallyUsedConvertedAmount) // Pour diagnostic - indique si convertedAmount a été utilisé pour calculer les frais
                            .build();

                    // Si le taux n'est pas disponible, on met 0.0 pour qu'il soit toujours affiché
                    double exchangeRateValue = guardarianRate;
                    return ResponseEntity.ok(FeesCalculationResponse.builder()
                            .amountSent(amountSent.setScale(2, RoundingMode.HALF_UP).doubleValue())
                            .currency(currency)
                            .estimatedFees(estimatedFees.doubleValue())
                            .amountReceived(amountReceived.doubleValue())
                            .exchangeRate(exchangeRateValue)
                            .estimatedExchangeRate(exchangeRateValue) // 0.0 si non disponible, toujours affiché
                            .feePercentage(feePercentage.doubleValue())
                            .operator(OPERATOR_GUARDIAN)
                            .breakdown(breakdown)
                            .build());
                }
            } catch (Exception e) {
                log.warn("⚠️ Impossible d'utiliser l'endpoint Guardarian /estimate: {}. Utilisation du calcul fixe (0.5%)", 
                        e.getMessage());
            }

            // Fallback: Calcul avec frais fixe (0.5%)
            log.info("Utilisation du calcul de frais fixe Guardarian (0.5%) - Les endpoints API Guardarian ne sont pas disponibles");
            BigDecimal guardarianFeeRate = BigDecimal.valueOf(0.005); // 0.5%
            BigDecimal estimatedFees = amountSent.multiply(guardarianFeeRate)
                    .setScale(2, RoundingMode.HALF_UP);
            
            BigDecimal amountReceived = amountSent.subtract(estimatedFees)
                    .setScale(2, RoundingMode.HALF_UP);
            
            BigDecimal feePercentage = guardarianFeeRate.multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);

            log.info("Guardarian fees calculation (fallback): Amount Sent={} {}, Fees={} {} (0.5%), Amount Received={} {}", 
                    amountSent, currency, estimatedFees, currency, amountReceived, currency);

            // Pour le fallback, on ne renseigne que les champs pertinents
            FeesCalculationResponse.FeesBreakdown breakdown = FeesCalculationResponse.FeesBreakdown.builder()
                    .guardarianServiceFee(estimatedFees.doubleValue())
                    // Note: Les autres champs (guardarianExchangeRate, guardarianConvertedAmount) 
                    // ne sont pas disponibles car l'API Guardarian n'est pas accessible
                    .build();

            // En fallback, le taux n'est pas disponible, on met 0.0 pour qu'il soit toujours affiché dans la réponse JSON
            FeesCalculationResponse response = FeesCalculationResponse.builder()
                    .amountSent(amountSent.setScale(2, RoundingMode.HALF_UP).doubleValue())
                    .currency(currency)
                    .estimatedFees(estimatedFees.doubleValue())
                    .amountReceived(amountReceived.doubleValue())
                    .exchangeRate(0.0) // Non disponible en fallback (API Guardarian non accessible) - 0.0 pour toujours l'afficher
                    .estimatedExchangeRate(0.0) // Non disponible en fallback (API Guardarian non accessible) - 0.0 pour toujours l'afficher
                    .feePercentage(feePercentage.doubleValue())
                    .operator(OPERATOR_GUARDIAN)
                    .breakdown(breakdown)
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Erreur lors du calcul des frais Guardarian pour amount={}, currency={}, countryCode={}", 
                    request.getAmount(), request.getCurrency(), request.getCountryCode(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(FeesCalculationResponse.builder()
                            .amountSent(request.getAmount())
                            .currency(request.getCurrency())
                            .operator(OPERATOR_GUARDIAN)
                            .build());
        }
    }

    /**
     * Récupère le premier channelId disponible pour un pays
     */
    private String getFirstChannelIdForCountry(String countryCode) {
        try {
            var channelsResponse = yellowCardClientService.getChannels(countryCode);
            if (channelsResponse.getStatusCode().is2xxSuccessful() && channelsResponse.getBody() != null) {
                JsonNode channelsJson = objectMapper.readTree(channelsResponse.getBody());
                if (channelsJson.isArray() && channelsJson.size() > 0) {
                    JsonNode firstChannel = channelsJson.get(0);
                    if (firstChannel.has("id")) {
                        return firstChannel.get("id").asText();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Erreur lors de la récupération des channels pour le pays {}: {}", countryCode, e.getMessage());
        }
        return null;
    }

    /**
     * Détecte l'opérateur selon le pays
     * Pays africains → YellowCard, autres → Guardarian
     */
    private String detectOperator(String countryCode) {
        // Liste des pays africains (à compléter selon vos besoins)
        String[] africanCountries = {"CI", "SN", "ML", "BF", "NE", "TG", "BJ", "GN", "GW", "MR", 
                                     "CM", "TD", "CF", "CG", "CD", "GA", "GQ", "ST", "AO", "ZM",
                                     "ZW", "BW", "NA", "ZA", "LS", "SZ", "MW", "MZ", "MG", "MU",
                                     "SC", "KM", "KE", "UG", "RW", "BI", "TZ", "ET", "ER", "DJ",
                                     "SO", "SD", "SS", "EG", "LY", "TN", "DZ", "MA", "EH"};
        
        for (String african : africanCountries) {
            if (african.equalsIgnoreCase(countryCode)) {
                return OPERATOR_YELLOWCARD;
            }
        }
        
        return OPERATOR_GUARDIAN;
    }

    private BigDecimal parseBigDecimal(String value) {
        try {
            if (value == null || value.isEmpty()) {
                return BigDecimal.ZERO;
            }
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid number format: '{}', using default 0.0", value);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Parse le taux YellowCard "buy" (pour OnRamp)
     * Format attendu: {"buy": 588.96, "sell": 554.98, "code": "XAF", ...}
     */
    private BigDecimal parseYellowCardBuyRateBigDecimal(String jsonResponse, String currency) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            
            // Format YellowCard: {"rates": [{"buy": 588.96, "sell": 554.98, "code": "XAF", ...}, ...]}
            // ou format direct: {"buy": 588.96, "sell": 554.98, "code": "XAF", ...}
            if (root.has("rates") && root.get("rates").isArray()) {
                for (JsonNode rateItem : root.get("rates")) {
                    if (rateItem.has("code") && rateItem.get("code").asText().equals(currency)) {
                        // Utiliser le taux "buy" (taux d'achat) pour OnRamp
                        if (rateItem.has("buy")) {
                            return new BigDecimal(rateItem.get("buy").asText());
                        }
                        // Fallback sur "sell" si "buy" n'existe pas
                        if (rateItem.has("sell")) {
                            log.warn("Taux 'buy' non trouvé pour {}, utilisation du taux 'sell' comme fallback", currency);
                            return new BigDecimal(rateItem.get("sell").asText());
                        }
                    }
                }
            }
            
            // Format direct (objet unique)
            if (root.has("code") && root.get("code").asText().equals(currency)) {
                if (root.has("buy")) {
                    return new BigDecimal(root.get("buy").asText());
                }
                if (root.has("sell")) {
                    log.warn("Taux 'buy' non trouvé pour {}, utilisation du taux 'sell' comme fallback", currency);
                    return new BigDecimal(root.get("sell").asText());
                }
            }
            
            // Formats alternatifs (fallback)
            if (root.has("data") && root.get("data").isArray()) {
                for (JsonNode item : root.get("data")) {
                    if (item.has("code") && item.get("code").asText().equals(currency)) {
                        if (item.has("buy")) {
                            return new BigDecimal(item.get("buy").asText());
                        }
                        if (item.has("rate")) {
                            return parseBigDecimal(item.get("rate").asText());
                        }
                    }
                }
            }
            
            if (root.has("currency") && root.get("currency").asText().equals(currency)) {
                if (root.has("buy")) {
                    return new BigDecimal(root.get("buy").asText());
                }
                if (root.has("rate")) {
                    return parseBigDecimal(root.get("rate").asText());
                }
            }
            
            if (root.has("rates") && root.get("rates").isObject() && root.get("rates").has(currency)) {
                return parseBigDecimal(root.get("rates").get(currency).asText());
            }
            
            log.warn("Impossible de trouver le taux YellowCard 'buy' pour {} dans la réponse: {}", currency, jsonResponse);
            return BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("Erreur lors du parsing du taux YellowCard 'buy': {}", e.getMessage(), e);
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal parseYellowCardRateBigDecimal(String jsonResponse, String currency) {
        // Délègue à parseYellowCardBuyRateBigDecimal pour maintenir la compatibilité
        return parseYellowCardBuyRateBigDecimal(jsonResponse, currency);
    }

    /**
     * Parse le taux YellowCard "sell" (pour OffRamp)
     */
    private BigDecimal parseYellowCardSellRateBigDecimal(String jsonResponse, String currency) {
        try {
            JsonNode root = objectMapper.readTree(jsonResponse);
            
            // Format YellowCard: {"rates": [{"buy": 566.94, "sell": 566.94, "code": "XOF", ...}, ...]}
            if (root.has("rates") && root.get("rates").isArray()) {
                for (JsonNode rateItem : root.get("rates")) {
                    if (rateItem.has("code") && rateItem.get("code").asText().equals(currency)) {
                        // Utiliser le taux "sell" (taux de vente) pour l'OffRamp
                        if (rateItem.has("sell")) {
                            return new BigDecimal(rateItem.get("sell").asText());
                        }
                        // Fallback sur "buy" si "sell" n'existe pas
                        if (rateItem.has("buy")) {
                            return new BigDecimal(rateItem.get("buy").asText());
                        }
                    }
                }
            }
            
            log.warn("Taux YellowCard 'sell' non trouvé pour la devise: {}", currency);
            return BigDecimal.ZERO;
        } catch (Exception e) {
            log.error("Erreur lors du parsing du taux YellowCard 'sell': {}", e.getMessage(), e);
            return BigDecimal.ZERO;
        }
    }

    @Override
    public ResponseEntity<FeesCalculationResponse> calculateOffRampFees(FeesCalculationRequest request) {
        try {
            log.info("Calculating OffRamp fees for amount: {} {}, country: {}, operator: {}", 
                    request.getAmount(), request.getCurrency(), request.getCountryCode(), request.getOperator());

            // Détecter l'opérateur si non spécifié
            String operator = request.getOperator();
            if (operator == null || operator.isEmpty()) {
                operator = detectOperator(request.getCountryCode());
            }

            // Support pour YellowCard et Guardarian OffRamp
            if (OPERATOR_YELLOWCARD.equalsIgnoreCase(operator)) {
                return calculateYellowCardOffRampFees(request);
            } else if (OPERATOR_GUARDIAN.equalsIgnoreCase(operator)) {
                return calculateGuardarianOffRampFees(request);
            } else if (OPERATOR_MTPELERIN.equalsIgnoreCase(operator)) {
                return calculateMtPelerinFees(request);
            } else {
                String errorMessage = String.format(
                    "OffRamp non supporté pour l'opérateur '%s'. Seuls YellowCard (Afrique) et Guardarian (Europe/International) sont supportés pour les opérations OffRamp. " +
                    "Veuillez utiliser un pays africain (ex: CI, SN, ML) pour YellowCard ou un pays européen/international pour Guardarian, ou spécifier 'operator': 'yellowcard' ou 'guardarian'.",
                    operator
                );
                log.warn("{} - Country: {}, Currency: {}", errorMessage, request.getCountryCode(), request.getCurrency());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(FeesCalculationResponse.builder()
                                .amountSent(request.getAmount())
                                .currency(request.getCurrency())
                                .operator(operator)
                                .build());
            }
        } catch (Exception e) {
            log.error("Erreur lors du calcul des frais OffRamp", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Calcule les frais pour YellowCard OffRamp (Afrique)
     * 
     * Formule selon la procédure:
     * 1. L'utilisateur saisit un montant en XOF (ce qu'il veut retirer)
     * 2. Le backend interroge l'API Rate Yellowcard en ciblant le taux "sell" pour convertir ce montant en USD
     *    - Montant USD = Montant XOF / Taux YC (sell)
     * 3. Le montant converti en USD est ensuite multiplié par le taux "sell" du jour de Yellow Card
     *    - Montant après conversion = USD × Taux YC (sell) = (Montant XOF / Taux YC) × Taux YC
     * 4. Ce montant total est multiplié par le taux de frais Akuunda Pay (3.5%)
     *    - Frais estimés = (Montant XOF/Taux YC × Taux YC) × 0.035
     * 5. Montant net = (Montant XOF/Taux YC × Taux YC) - Frais estimés
     * 
     * Utilise BigDecimal pour éviter les erreurs d'arrondi
     */
    private ResponseEntity<FeesCalculationResponse> calculateYellowCardOffRampFees(FeesCalculationRequest request) {
        try {
            BigDecimal amountXOF = BigDecimal.valueOf(request.getAmount()); // Montant saisi par l'utilisateur
            String currency = request.getCurrency();

            log.info("Calculating YellowCard OffRamp fees for: amount={} {}", amountXOF, currency);

            // 1. Récupérer le taux YellowCard "sell" (pour OffRamp) depuis l'endpoint avec channelId si disponible
            // ⚠️ IMPORTANT : Utiliser DIRECTEMENT le taux YellowCard (pas Currency Freaks)
            // Cela garantit qu'on retrouve exactement le montant initial après reconversion
            log.debug("Step 1: Getting YellowCard 'sell' rate for {}", currency);
            
            // Essayer d'abord avec channelId si disponible
            String channelId = null;
            ResponseEntity<String> yellowCardRatesResponse = null;
            
            if (request.getCountryCode() != null) {
                // Récupérer le channelId pour le pays
                channelId = getFirstChannelIdForCountry(request.getCountryCode());
                if (channelId != null && !channelId.isEmpty()) {
                    log.debug("Tentative de récupération des taux sell avec channelId: {}", channelId);
                    yellowCardRatesResponse = yellowCardClientService.getRatesByChannelId(channelId, currency);
                }
            }
            
            // Fallback sur l'ancienne méthode si channelId n'est pas disponible ou si l'appel a échoué
            if (yellowCardRatesResponse == null || 
                yellowCardRatesResponse.getStatusCode() != HttpStatus.OK || 
                yellowCardRatesResponse.getBody() == null) {
                log.debug("Fallback: Getting YellowCard 'sell' rate without channelId");
                yellowCardRatesResponse = yellowCardClientService.getRates(currency);
            }
            
            if (yellowCardRatesResponse.getStatusCode() != HttpStatus.OK || yellowCardRatesResponse.getBody() == null) {
                log.error("Erreur lors de la récupération des taux YellowCard");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            // Parser la réponse YellowCard pour extraire le taux "sell"
            BigDecimal yellowCardSellRate = parseYellowCardSellRateBigDecimal(yellowCardRatesResponse.getBody(), currency);
            
            if (yellowCardSellRate.compareTo(BigDecimal.ZERO) <= 0) {
                log.error("Taux YellowCard 'sell' invalide ou non trouvé");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
            }

            log.debug("Step 1 result: YellowCard 'sell' rate = {}", yellowCardSellRate);

            // 2. Convertir XOF → USD via le taux "sell"
            // Montant en USD = Montant XOF / Taux sell
            // IMPORTANT: Ne pas arrondir ici - garder la valeur brute pour les calculs
            BigDecimal amountInUSD = amountXOF.divide(yellowCardSellRate, 10, RoundingMode.HALF_UP);
            log.debug("Step 2: Amount in USD = {} XOF / {} = {} USD - VALEUR BRUTE (non arrondie)", 
                    amountXOF, yellowCardSellRate, amountInUSD);

            // 3. Convertir USD → XOF via le taux "sell"
            // Montant après conversion = USD × Taux sell = (Montant XOF / Taux YC) × Taux YC
            // IMPORTANT: Ne pas arrondir ici - garder la valeur brute pour les calculs
            BigDecimal amountAfterConversion = amountInUSD.multiply(yellowCardSellRate);
            log.debug("Step 3: Amount after conversion = {} USD × {} = {} XOF (Montant XOF/Taux YC × Taux YC) - VALEUR BRUTE (non arrondie)", 
                    amountInUSD, yellowCardSellRate, amountAfterConversion);

            // 4. Calculer les frais Akuunda (3.5%)
            // Selon la procédure: Frais estimés = (Montant XOF/Taux YC × Taux YC) × 0.035
            // IMPORTANT: Utiliser la valeur brute (non arrondie) pour le calcul des frais
            BigDecimal feePercentageBD = BigDecimal.valueOf(AKUUNDA_FEE_PERCENTAGE_OFFRAMP);
            BigDecimal estimatedFees = amountAfterConversion.multiply(feePercentageBD);
            log.debug("Step 4: Estimated fees (brut) = {} XOF × 0.035 = {} XOF", amountAfterConversion, estimatedFees);

            // 5. Calculer le montant net que l'utilisateur recevra
            // Selon la procédure: Montant net = (Montant XOF/Taux YC × Taux YC) - Frais estimés
            // IMPORTANT: Utiliser les valeurs brutes (non arrondies) pour le calcul
            BigDecimal amountReceived = amountAfterConversion.subtract(estimatedFees);
            
            // Arrondir uniquement à la fin pour l'affichage (2 décimales)
            estimatedFees = estimatedFees.setScale(2, RoundingMode.HALF_UP);
            amountReceived = amountReceived.setScale(2, RoundingMode.HALF_UP);

            // Pourcentage de frais
            BigDecimal feePercentage = feePercentageBD.multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);

            log.info("YellowCard OffRamp fees calculation: Amount Sent={} {}, Fees={} {} (3.5%), Amount Received={} {}", 
                    amountXOF, currency, estimatedFees, currency, amountReceived, currency);

            FeesCalculationResponse.FeesBreakdown breakdown = FeesCalculationResponse.FeesBreakdown.builder()
                    .yellowCardRate(yellowCardSellRate.doubleValue())
                    .akuundaFeeRate(AKUUNDA_FEE_PERCENTAGE_OFFRAMP)
                    .amountInUsd(amountInUSD.doubleValue())
                    .amountAfterYellowCardRate(amountAfterConversion.doubleValue())
                    .build();

            double exchangeRateValue = yellowCardSellRate.doubleValue();
            FeesCalculationResponse response = FeesCalculationResponse.builder()
                    .amountSent(amountXOF.setScale(2, RoundingMode.HALF_UP).doubleValue()) // Montant envoyé
                    .currency(currency)
                    .estimatedFees(estimatedFees.doubleValue()) // Frais appliqués (3.5%)
                    .amountReceived(amountReceived.doubleValue()) // Montant reçu
                    .exchangeRate(exchangeRateValue)
                    .estimatedExchangeRate(exchangeRateValue) // Alias de exchangeRate pour compatibilité
                    .feePercentage(feePercentage.doubleValue())
                    .operator(OPERATOR_YELLOWCARD)
                    .breakdown(breakdown)
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Erreur lors du calcul des frais YellowCard OffRamp", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Calcule les frais pour Guardarian OffRamp (Europe/International)
     * 
     * Stratégie:
     * 1. Essayer d'abord d'utiliser l'endpoint /estimate de Guardarian (si disponible)
     * 2. Si l'endpoint n'est pas disponible (404), utiliser un calcul de frais fixe (0.5%)
     * 
     * Pour l'OffRamp, on inverse les devises par rapport à l'OnRamp:
     * - OnRamp: EUR → USDC (l'utilisateur envoie EUR, reçoit USDC)
     * - OffRamp: USDC → EUR (l'utilisateur envoie USDC, reçoit EUR)
     * 
     * Selon l'exemple fourni:
     * - Montant saisi: 30 USDC
     * - Frais de service: 0.15 USDC (0.5%)
     * - Montant reçu: 29.85 USDC (en EUR équivalent)
     * 
     * Utilise BigDecimal pour éviter les erreurs d'arrondi
     */
    private ResponseEntity<FeesCalculationResponse> calculateGuardarianOffRampFees(FeesCalculationRequest request) {
        try {
            BigDecimal amountSent = BigDecimal.valueOf(request.getAmount());
            String currency = request.getCurrency(); // Devise que l'utilisateur veut retirer (ex: USDC)

            log.info("Calculating Guardarian OffRamp fees for: currency={}, amount={}", 
                    currency, amountSent);

            // Essayer d'abord d'utiliser l'endpoint /estimate de Guardarian
            // Pour l'OffRamp, on convertit USDC → EUR (inverse de OnRamp)
            try {
                // Créer une EstimateRequest pour Guardarian
                // Pour l'OffRamp: from = USDC (crypto), to = EUR (fiat)
                // Si la devise n'est pas USDC, on essaie de détecter la devise fiat cible
                String fromCurrency = currency; // Devise source (crypto, ex: USDC)
                String toCurrency = "EUR"; // Devise destination (fiat, par défaut EUR)
                
                // Si la devise est déjà une devise fiat (EUR, USD, etc.), on inverse
                // Dans ce cas, l'utilisateur veut retirer de la crypto vers cette devise fiat
                if (currency.equals("EUR") || currency.equals("USD") || currency.equals("GBP")) {
                    // L'utilisateur veut retirer vers cette devise fiat
                    toCurrency = currency;
                    fromCurrency = "USDC"; // Par défaut, on retire de USDC
                }
                
                var estimateRequest = new org.akuunda.akuundawallet.wallet.api.dto.external.EstimateRequest(
                        fromCurrency,  // from (crypto, ex: USDC)
                        toCurrency,    // to (fiat, ex: EUR)
                        request.getAmount()
                );

                var guardarianResponse = guardarianClientService.getEstimate(estimateRequest);
                
                if (guardarianResponse != null && guardarianResponse.getStatusCode() == HttpStatus.OK 
                        && guardarianResponse.getBody() != null) {
                    var estimate = guardarianResponse.getBody();
                    
                    // Vérification supplémentaire pour éviter les null pointer
                    if (estimate == null) {
                        log.warn("EstimateResponse est null malgré le check précédent");
                        throw new RuntimeException("EstimateResponse is null");
                    }
                    
                    // Utiliser les données de Guardarian si disponibles
                    // Pour OffRamp (USDC → devise de destination):
                    // - estimatedAmount = value (montant reçu dans la devise de destination, ex: 44.75)
                    // - convertedAmount = converted_amount.amount (montant après frais dans la devise de destination)
                    // - amountReceived = toujours dans la devise de destination (toCurrency)
                    BigDecimal estimatedAmount = BigDecimal.valueOf(estimate.estimatedAmount());
                    BigDecimal estimatedFees;
                    BigDecimal amountReceived;
                    
                    // Variable pour stocker si on a utilisé convertedAmount (pour diagnostic)
                    boolean actuallyUsedConvertedAmount = false;
                    
                    // ⚠️ IMPORTANT : Pour OffRamp, si currency est en EUR (devise fiat), amountSent est déjà en EUR
                    // L'utilisateur veut retirer 50 EUR, donc les frais doivent être calculés en EUR directement
                    // Si convertedAmount est disponible, les frais = amountSent (EUR) - convertedAmount (EUR)
                    double rate = estimate.rate();
                    
                    log.info("🔍 Debug Guardarian OffRamp: amountSent={} {}, estimatedAmount={} ({}), convertedAmount={} ({}), rate={}", 
                            amountSent, currency, estimate.estimatedAmount(), toCurrency, estimate.convertedAmount(), toCurrency, rate);
                    
                    // ⚠️ CRITIQUE : Pour OffRamp Guardarian, vérifier la structure réelle de la réponse
                    // D'après les réponses réelles de Guardarian :
                    // - estimatedAmount (value) = montant brut avant frais (peut être dans différentes devises selon le contexte)
                    // - convertedAmount (converted_amount.amount) = montant réellement reçu après frais dans la devise de destination (EUR)
                    // ⚠️ IMPORTANT : convertedAmount semble être le montant réellement reçu en EUR, donc on doit l'utiliser si disponible
                    log.info("🔍 OffRamp - estimatedAmount (value)={} (en {}), convertedAmount (converted_amount)={} (en {})", 
                            estimate.estimatedAmount(), toCurrency, estimate.convertedAmount(), toCurrency);
                    
                    // ⚠️ CORRECTION : Utiliser convertedAmount si disponible, car il représente le montant réellement reçu
                    // Si convertedAmount n'est pas disponible, utiliser estimatedAmount (value) comme fallback
                    boolean useConvertedAmount = estimate.convertedAmount() != null && estimate.convertedAmount() > 0;
                    
                    if (useConvertedAmount) {
                        // Utiliser convertedAmount qui est le montant réellement reçu après frais
                        BigDecimal convertedAmountBD = BigDecimal.valueOf(estimate.convertedAmount());
                        amountReceived = convertedAmountBD.setScale(2, RoundingMode.HALF_UP);
                        actuallyUsedConvertedAmount = true;
                        
                        // Les frais = amountSent (EUR) - convertedAmount (EUR)
                        estimatedFees = amountSent.subtract(convertedAmountBD)
                                .setScale(2, RoundingMode.HALF_UP);
                        log.info("✅ ✅ ✅ OffRamp: Utilisation de convertedAmount (montant reçu après frais): {} {} - {} {} = {} {} (frais), amountReceived={} {}", 
                                amountSent, currency, convertedAmountBD, toCurrency, estimatedFees, currency, amountReceived, toCurrency);
                    } else {
                        // Fallback : utiliser estimatedAmount (value) si convertedAmount n'est pas disponible
                        amountReceived = estimatedAmount.setScale(2, RoundingMode.HALF_UP);
                        actuallyUsedConvertedAmount = false;
                        estimatedFees = amountSent.subtract(estimatedAmount)
                                .setScale(2, RoundingMode.HALF_UP);
                        log.warn("⚠️ OffRamp: convertedAmount non disponible, utilisation de estimatedAmount (value) comme fallback: {} {} - {} {} = {} {} (frais)", 
                                amountSent, currency, estimatedAmount, toCurrency, estimatedFees, currency);
                    }
                    
                    BigDecimal feePercentage = estimatedFees.divide(amountSent, 4, RoundingMode.HALF_UP)
                            .multiply(BigDecimal.valueOf(100))
                            .setScale(2, RoundingMode.HALF_UP);

                    log.info("✅ Guardarian API estimate utilisé (OffRamp): Amount Sent={} {}, Fees={} {}, Amount Received={} {}", 
                            amountSent, fromCurrency, estimatedFees, fromCurrency, amountReceived, toCurrency);

                    // Pour Guardarian OffRamp, le taux peut ne pas être disponible dans l'API
                    // Si le taux n'est pas disponible, on met 0.0 pour qu'il soit toujours affiché dans la réponse JSON
                    double guardarianRate = estimate.rate() > 0 ? estimate.rate() : 0.0;
                    
                    FeesCalculationResponse.FeesBreakdown breakdown = FeesCalculationResponse.FeesBreakdown.builder()
                            .guardarianExchangeRate(guardarianRate)
                            .guardarianConvertedAmount(amountReceived.doubleValue())
                            .guardarianServiceFee(estimatedFees.doubleValue())
                            .guardarianEstimatedAmount(estimatedAmount.doubleValue()) // Pour diagnostic
                            .guardarianConvertedAmountFromAPI(estimate.convertedAmount() != null ? estimate.convertedAmount() : null) // Pour diagnostic
                            .usedConvertedAmount(actuallyUsedConvertedAmount) // Pour diagnostic
                            .build();

                    // Si le taux n'est pas disponible, on met 0.0 pour qu'il soit toujours affiché
                    double exchangeRateValue = guardarianRate;
                    return ResponseEntity.ok(FeesCalculationResponse.builder()
                            .amountSent(amountSent.setScale(2, RoundingMode.HALF_UP).doubleValue())
                            .currency(currency) // Devise que l'utilisateur retire
                            .estimatedFees(estimatedFees.doubleValue())
                            .amountReceived(amountReceived.doubleValue())
                            .exchangeRate(exchangeRateValue)
                            .estimatedExchangeRate(exchangeRateValue) // 0.0 si non disponible, toujours affiché
                            .feePercentage(feePercentage.doubleValue())
                            .operator(OPERATOR_GUARDIAN)
                            .breakdown(breakdown)
                            .build());
                }
            } catch (Exception e) {
                log.warn("⚠️ Impossible d'utiliser l'endpoint Guardarian /estimate pour OffRamp: {}. Utilisation du calcul fixe (0.5%)", 
                        e.getMessage());
            }

            // Fallback: Calcul avec frais fixe (0.5%)
            log.info("Utilisation du calcul de frais fixe Guardarian (0.5%) pour OffRamp - Les endpoints API Guardarian ne sont pas disponibles");
            BigDecimal guardarianFeeRate = BigDecimal.valueOf(0.005); // 0.5%
            BigDecimal estimatedFees = amountSent.multiply(guardarianFeeRate)
                    .setScale(2, RoundingMode.HALF_UP);
            
            BigDecimal amountReceived = amountSent.subtract(estimatedFees)
                    .setScale(2, RoundingMode.HALF_UP);
            
            BigDecimal feePercentage = guardarianFeeRate.multiply(BigDecimal.valueOf(100))
                    .setScale(2, RoundingMode.HALF_UP);

            log.info("Guardarian OffRamp fees calculation (fallback): Amount Sent={} {}, Fees={} {} (0.5%), Amount Received={} {}", 
                    amountSent, currency, estimatedFees, currency, amountReceived, currency);

            // Pour le fallback, on ne renseigne que les champs pertinents
            FeesCalculationResponse.FeesBreakdown breakdown = FeesCalculationResponse.FeesBreakdown.builder()
                    .guardarianServiceFee(estimatedFees.doubleValue())
                    // Note: Les autres champs (guardarianExchangeRate, guardarianConvertedAmount) 
                    // ne sont pas disponibles car l'API Guardarian n'est pas accessible
                    .build();

            // En fallback, le taux n'est pas disponible, on met 0.0 pour qu'il soit toujours affiché dans la réponse JSON
            FeesCalculationResponse response = FeesCalculationResponse.builder()
                    .amountSent(amountSent.setScale(2, RoundingMode.HALF_UP).doubleValue())
                    .currency(currency)
                    .estimatedFees(estimatedFees.doubleValue())
                    .amountReceived(amountReceived.doubleValue())
                    .exchangeRate(0.0) // Non disponible en fallback (API Guardarian non accessible) - 0.0 pour toujours l'afficher
                    .estimatedExchangeRate(0.0) // Non disponible en fallback (API Guardarian non accessible) - 0.0 pour toujours l'afficher
                    .feePercentage(feePercentage.doubleValue())
                    .operator(OPERATOR_GUARDIAN)
                    .breakdown(breakdown)
                    .build();

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Erreur lors du calcul des frais Guardarian OffRamp pour amount={}, currency={}, countryCode={}", 
                    request.getAmount(), request.getCurrency(), request.getCountryCode(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(FeesCalculationResponse.builder()
                            .amountSent(request.getAmount())
                            .currency(request.getCurrency())
                            .operator(OPERATOR_GUARDIAN)
                            .build());
        }
    }
}



