package org.akuunda.akuundawallet.wallet.service.yellowcard;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Frais Yellow Card locaux — même logique que le mobile
 * (YellowCardDisbursementFeeCalculator / YellowCardFeeCalculator).
 */
public final class YellowCardLocalFeeCalculator {

    private static final double AKUUNDA_MARGIN = 0.005;

    private YellowCardLocalFeeCalculator() {
    }

    public static String normalizePaymentMethod(String type) {
        if (type == null || type.isBlank()) {
            return "mobile_money";
        }
        String lower = type.trim().toLowerCase();
        return switch (lower) {
            case "momo", "mobile_money", "mobilemoney", "p2p" -> "mobile_money";
            case "bank", "bank_transfer", "banktransfer", "eft" -> "bank_transfer";
            default -> lower;
        };
    }

    public static double calculateFee(String countryCode, String paymentMethod, double amount, boolean offRamp) {
        if (amount <= 0) {
            return 0;
        }
        FeeRule rule = findRule(countryCode, paymentMethod, amount, offRamp);
        double ycFee = rule != null ? rule.calculate(amount) : 0;
        return ycFee + amount * AKUUNDA_MARGIN;
    }

    private static FeeRule findRule(String countryCode, String paymentMethod, double amount, boolean offRamp) {
        Map<String, List<FeeRule>> methods = SCHEDULE.get(countryCode.toUpperCase());
        if (methods == null) {
            return null;
        }
        String key = normalizePaymentMethod(paymentMethod);
        List<FeeRule> rules = methods.get(key);
        if (rules == null) {
            return null;
        }
        for (FeeRule rule : rules) {
            if (rule.matches(amount)) {
                return rule;
            }
        }
        return null;
    }

    private record FeeRule(FeeType type, double value, double minimumFee, Double minAmount, Double maxAmount) {
        boolean matches(double amount) {
            boolean aboveMin = minAmount == null || amount >= minAmount;
            boolean belowMax = maxAmount == null || amount < maxAmount;
            return aboveMin && belowMax;
        }

        double calculate(double amount) {
            double raw = type == FeeType.PERCENTAGE ? amount * value : value;
            return raw < minimumFee ? minimumFee : raw;
        }
    }

    private enum FeeType { PERCENTAGE, FIXED }

    private static FeeRule pct(double rate, double minFee) {
        return new FeeRule(FeeType.PERCENTAGE, rate, minFee, null, null);
    }

    private static FeeRule fixed(double amount, double minFee) {
        return new FeeRule(FeeType.FIXED, amount, minFee, null, null);
    }

    private static Map<String, Map<String, List<FeeRule>>> buildSchedule() {
        Map<String, Map<String, List<FeeRule>>> raw = new HashMap<>();
        raw.put("BJ", Map.of("bank_transfer", List.of(pct(0.0225, 1)), "mobile_money", List.of(pct(0.0225, 1))));
        raw.put("BW", Map.of("bank_transfer", List.of(pct(0.005, 10)), "mobile_money", List.of(pct(0.022, 1))));
        raw.put("BF", Map.of("bank_transfer", List.of(pct(0.0225, 1)), "mobile_money", List.of(pct(0.0225, 1))));
        raw.put("CM", Map.of("bank_transfer", List.of(pct(0.015, 1)), "mobile_money", List.of(pct(0.015, 1))));
        raw.put("CD", Map.of("bank_transfer", List.of(pct(0.015, 1)), "mobile_money", List.of(pct(0.015, 1))));
        raw.put("CI", Map.of("bank_transfer", List.of(pct(0.0225, 1)), "mobile_money", List.of(pct(0.0225, 1))));
        raw.put("KE", Map.of("bank_transfer", List.of(pct(0.005, 200)), "mobile_money", List.of(pct(0.02, 1))));
        raw.put("MW", Map.of("bank_transfer", List.of(pct(0.005, 750)), "mobile_money", List.of(pct(0.01, 1))));
        raw.put("ML", Map.of("bank_transfer", List.of(pct(0.02, 1)), "mobile_money", List.of(pct(0.02, 1))));
        raw.put("NG", Map.of("bank_transfer", List.of(fixed(100, 55)), "mobile_money", List.of(fixed(100, 55))));
        raw.put("RW", Map.of("bank_transfer", List.of(pct(0.005, 1000)), "mobile_money", List.of(pct(0.005, 1000))));
        raw.put("SN", Map.of("bank_transfer", List.of(pct(0.015, 1)), "mobile_money", List.of(pct(0.015, 1))));
        raw.put("ZA", Map.of("bank_transfer", List.of(pct(0.005, 20)), "mobile_money", List.of(pct(0.005, 20)), "eft", List.of(pct(0.005, 20))));
        raw.put("TZ", Map.of("bank_transfer", List.of(pct(0.005, 12500)), "mobile_money", List.of(pct(0.01, 1))));
        raw.put("TG", Map.of("bank_transfer", List.of(pct(0.0225, 1)), "mobile_money", List.of(pct(0.0225, 1))));
        raw.put("UG", Map.of("bank_transfer", List.of(pct(0.005, 5000)), "mobile_money", List.of(pct(0.015, 1))));
        raw.put("ZM", Map.of("bank_transfer", List.of(pct(0.005, 100)), "mobile_money", List.of(pct(0.015, 1))));
        for (Map<String, List<FeeRule>> methods : raw.values()) {
            if (methods.containsKey("bank_transfer")) {
                methods.put("bank", methods.get("bank_transfer"));
            }
            if (methods.containsKey("mobile_money")) {
                methods.put("momo", methods.get("mobile_money"));
            }
        }
        return raw;
    }

    private static final Map<String, Map<String, List<FeeRule>>> SCHEDULE = buildSchedule();
}
