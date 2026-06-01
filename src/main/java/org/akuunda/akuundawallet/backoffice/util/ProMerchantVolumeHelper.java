package org.akuunda.akuundawallet.backoffice.util;

import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.entities.Operation;
import org.akuunda.akuundawallet.wallet.service.infrastructure.CurrencyFreaksClientService;

import java.util.Locale;
import java.util.Set;

/**
 * Montants agrégés pour l'espace Pro marchand : conversion cohérente vers la
 * devise d'affichage du wallet (sans additionner XOF + EUR + USDC bruts).
 */
@Slf4j
public final class ProMerchantVolumeHelper {

    private static final Set<String> USD_PEGGED = Set.of(
            "USDC", "USDT", "USDP", "DAI", "BUSD", "TUSD", "FDUSD", "PYUSD", "USDD", "USD"
    );

    private ProMerchantVolumeHelper() {
    }

    /**
     * Montant d'une opération dans la devise d'affichage du marchand.
     * N'utilise {@code convertedAmount} que si la devise source correspond ;
     * sinon conversion FX (API puis taux de repli).
     */
    public static double amountInMerchantCurrency(
            Operation o, String displayCur, CurrencyFreaksClientService fx) {
        if (o == null || displayCur == null || displayCur.isBlank()) return 0d;
        String target = normalize(displayCur);
        if (target == null) return 0d;

        if (o.getAmount() != null && o.getAmount() > 0 && o.getDevise() != null) {
            String opCur = normalize(o.getDevise());
            if (opCur != null) {
                if (opCur.equals(target)) return o.getAmount();
                Double converted = convert(opCur, target, o.getAmount(), fx);
                if (converted != null && converted > 0) return converted;
            }
        }

        if (o.getProviderAmount() != null && o.getProviderAmount() > 0
                && o.getProviderDevise() != null) {
            String pCur = normalize(o.getProviderDevise());
            if (pCur != null) {
                if (pCur.equals(target)) return o.getProviderAmount();
                Double converted = convert(pCur, target, o.getProviderAmount(), fx);
                if (converted != null && converted > 0) return converted;
            }
        }

        // Legacy : convertedAmount seulement si devise opération = devise cible
        if (o.getConvertedAmount() != null && o.getConvertedAmount() != 0
                && o.getDevise() != null) {
            String opCur = normalize(o.getDevise());
            if (opCur != null && opCur.equals(target)) {
                return o.getConvertedAmount();
            }
        }

        return 0d;
    }

    private static Double convert(String from, String to, double amount, CurrencyFreaksClientService fx) {
        if (from == null || to == null || amount <= 0 || from.equalsIgnoreCase(to)) {
            return amount;
        }
        if (fx != null) {
            try {
                var response = fx.convertCurrency(from, to, amount);
                if (response != null && response.getStatusCode().is2xxSuccessful()
                        && response.getBody() != null) {
                    String str = response.getBody().getConvertedAmount();
                    if (str != null && !str.isBlank()) {
                        return Double.parseDouble(str);
                    }
                }
            } catch (Exception e) {
                log.debug("FX {}→{} via API: {}", from, to, e.getMessage());
            }
        }
        return fallbackConvert(from, to, amount);
    }

    /** Taux fixes USD (alignés sur {@link AdminVolumeUsdConverter}). */
    private static Double fallbackConvert(String from, String to, double amount) {
        double usd = toUsd(from, amount);
        if (usd <= 0) return null;
        return fromUsd(to, usd);
    }

    private static double toUsd(String currency, double amount) {
        return switch (currency) {
            case "EUR" -> amount * 1.06;
            case "GBP" -> amount * 1.27;
            case "CHF" -> amount * 1.12;
            case "USD", "USDC", "USDT" -> amount;
            case "INR" -> amount * 0.012;
            case "XOF", "XAF", "FCFA" -> amount * 0.00167;
            case "NGN" -> amount * 0.00065;
            case "GHS" -> amount * 0.065;
            case "KES" -> amount * 0.0077;
            case "MAD" -> amount * 0.10;
            default -> amount * 0.001;
        };
    }

    private static double fromUsd(String currency, double usd) {
        return switch (currency) {
            case "EUR" -> usd / 1.06;
            case "GBP" -> usd / 1.27;
            case "CHF" -> usd / 1.12;
            case "USD", "USDC", "USDT" -> usd;
            case "INR" -> usd / 0.012;
            case "XOF", "XAF", "FCFA" -> usd / 0.00167;
            case "NGN" -> usd / 0.00065;
            case "GHS" -> usd / 0.065;
            case "KES" -> usd / 0.0077;
            case "MAD" -> usd / 0.10;
            default -> usd / 0.001;
        };
    }

    private static String normalize(String code) {
        if (code == null || code.isBlank()) return null;
        String c = code.trim().toUpperCase(Locale.ROOT);
        if ("FCFA".equals(c)) return "XOF";
        if (USD_PEGGED.contains(c)) return "USD";
        return c;
    }
}
