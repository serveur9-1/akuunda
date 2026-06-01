package org.akuunda.akuundawallet.backoffice.util;

/**
 * Conversion des montants d'opération (devise locale affichée utilisateur) vers USD
 * pour les KPI admin (volume total, graphiques).
 */
public final class AdminVolumeUsdConverter {

    private static final String LOCAL_AMOUNT_TO_USD_O =
            "CASE UPPER(COALESCE(o.devise,'')) "
                    + "WHEN 'EUR'          THEN o.amount * 1.06 "
                    + "WHEN 'GBP'          THEN o.amount * 1.27 "
                    + "WHEN 'CHF'          THEN o.amount * 1.12 "
                    + "WHEN 'USDC'         THEN o.amount * 1.0 "
                    + "WHEN 'USDT'         THEN o.amount * 1.0 "
                    + "WHEN 'USD'          THEN o.amount * 1.0 "
                    + "WHEN 'USDC_POLYGON' THEN o.amount * 1.0 "
                    + "WHEN 'INR'          THEN o.amount * 0.012 "
                    + "WHEN 'XOF'          THEN o.amount * 0.00167 "
                    + "WHEN 'XAF'          THEN o.amount * 0.00167 "
                    + "WHEN 'NGN'          THEN o.amount * 0.00065 "
                    + "WHEN 'GHS'          THEN o.amount * 0.065 "
                    + "WHEN 'KES'          THEN o.amount * 0.0077 "
                    + "WHEN 'GNF'          THEN o.amount * 0.00012 "
                    + "WHEN 'MAD'          THEN o.amount * 0.10 "
                    + "WHEN 'DZD'          THEN o.amount * 0.0074 "
                    + "WHEN 'TND'          THEN o.amount * 0.32 "
                    + "WHEN 'ZAR'          THEN o.amount * 0.055 "
                    + "WHEN 'TZS'          THEN o.amount * 0.00038 "
                    + "WHEN 'CDF'          THEN o.amount * 0.00035 "
                    + "WHEN 'MGA'          THEN o.amount * 0.00022 "
                    + "ELSE o.amount * 0.001 END";

    /** Expression JPQL CASE (alias {@code o}) — constante pour {@code @Query}. */
    public static final String USD_VOLUME_CASE_O =
            "CASE "
                    + "WHEN o.amount IS NOT NULL AND o.amount > 0 AND ("
                    + "UPPER(COALESCE(o.designation,'')) LIKE '%ON_RAMP%' "
                    + "OR UPPER(COALESCE(o.designation,'')) LIKE '%ONRAMP%' "
                    + "OR UPPER(COALESCE(o.designation,'')) LIKE '%ON RAMP%' "
                    + "OR UPPER(COALESCE(o.designation,'')) LIKE '%OFF_RAMP%' "
                    + "OR UPPER(COALESCE(o.designation,'')) LIKE '%OFFRAMP%' "
                    + "OR UPPER(COALESCE(o.designation,'')) LIKE '%OFF RAMP%' "
                    + "OR (o.provider IS NOT NULL AND ("
                    + "UPPER(o.provider) LIKE '%YELLOW%' OR UPPER(o.provider) LIKE '%MELD%' "
                    + "OR UPPER(o.provider) LIKE '%GUARDARIAN%' OR UPPER(o.provider) LIKE '%KYRREX%' "
                    + "OR UPPER(o.provider) LIKE '%PELERIN%'))"
                    + ") THEN "
                    + LOCAL_AMOUNT_TO_USD_O
                    + " WHEN UPPER(COALESCE(o.devise,'')) IN ('USDC','USDT','USD','USDC_POLYGON') THEN o.amount "
                    + " WHEN o.providerAmount IS NOT NULL AND o.providerAmount > 0 "
                    + "     AND UPPER(COALESCE(o.providerDevise,'USDC')) IN ('USDC','USDT','USD') THEN o.providerAmount "
                    + " WHEN o.amount IS NOT NULL AND o.amount > 0 THEN " + LOCAL_AMOUNT_TO_USD_O
                    + " ELSE 0 END";

    private AdminVolumeUsdConverter() {
    }
}
