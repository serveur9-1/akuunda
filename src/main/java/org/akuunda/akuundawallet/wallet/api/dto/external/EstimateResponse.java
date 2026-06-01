package org.akuunda.akuundawallet.wallet.api.dto.external;

public record EstimateResponse(
        String from,
        String to,
        double amount,
        double estimatedAmount,
        double rate,
        Double convertedAmount  // Montant après frais dans la devise d'origine (ex: converted_amount.amount en EUR)
) {
    public EstimateResponse(String from, String to, double amount, double estimatedAmount, double rate) {
        this(from, to, amount, estimatedAmount, rate, null);
    }
}
