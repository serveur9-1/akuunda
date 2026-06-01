package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // <-- ignore les champs inconnus
public class MeldQuote {

    private String transactionType;
    private Double sourceAmount;
    private Double sourceAmountWithoutFees;
    private Double fiatAmountWithoutFees;
    private Double destinationAmountWithoutFees;
    private String sourceCurrencyCode;
    private String countryCode;
    private Double totalFee;
    private Double networkFee;
    private Double transactionFee;
    private Double destinationAmount;
    private String destinationCurrencyCode;
    private Double exchangeRate;
    private String paymentMethodType;
    private Double customerScore;
    private String serviceProvider;
    private String institutionName;
    private Boolean lowKyc;
    private Double partnerFee;

    /**
     * Indique si ce provider est recommandé par Akuunda
     * en fonction du montant et de la stabilité testée.
     */
    private Boolean recommended;
}
