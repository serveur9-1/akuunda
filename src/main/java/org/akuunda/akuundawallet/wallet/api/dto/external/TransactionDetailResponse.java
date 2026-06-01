package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransactionDetailResponse {
    private String id;
    private String status;
    private String email;
    private List<String> errors;

    @JsonProperty("status_details")
    private String statusDetails;

    @JsonProperty("from_currency")
    private String fromCurrency;

    @JsonProperty("initial_from_currency")
    private String initialFromCurrency;

    @JsonProperty("from_network")
    private String fromNetwork;

    @JsonProperty("from_currency_with_network")
    private String fromCurrencyWithNetwork;

    @JsonProperty("from_amount")
    private Double fromAmount;

    @JsonProperty("deposit_type")
    private String depositType;

    @JsonProperty("payout_type")
    private String payoutType;

    @JsonProperty("expected_from_amount")
    private Double expectedFromAmount;

    @JsonProperty("initial_expected_from_amount")
    private Double initialExpectedFromAmount;

    @JsonProperty("to_currency")
    private String toCurrency;

    @JsonProperty("to_network")
    private String toNetwork;

    @JsonProperty("to_currency_with_network")
    private String toCurrencyWithNetwork;

    @JsonProperty("to_amount")
    private Double toAmount;

    @JsonProperty("output_hash")
    private String outputHash;

    @JsonProperty("expected_to_amount")
    private Double expectedToAmount;

    private String location;

    @JsonProperty("created_at")
    private ZonedDateTime createdAt;

    @JsonProperty("updated_at")
    private ZonedDateTime updatedAt;

    @JsonProperty("partner_id")
    private String partnerId;

    @JsonProperty("external_partner_link_id")
    private String externalPartnerLinkId;

    @JsonProperty("from_amount_in_eur")
    private Double fromAmountInEur;

    @JsonProperty("deposit_payment_category")
    private String depositPaymentCategory;

    @JsonProperty("payout_payment_category")
    private String payoutPaymentCategory;

    @JsonProperty("estimate_breakdown")
    private EstimateBreakdown estimateBreakdown;

    private Payout payout;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EstimateBreakdown {
        @JsonProperty("toAmount")
        private Double toAmount;

        @JsonProperty("fromAmount")
        private Double fromAmount;

        @JsonProperty("serviceFees")
        private List<ServiceFee> serviceFees;

        @JsonProperty("convertedAmount")
        private ConvertedAmount convertedAmount;

        @JsonProperty("estimatedExchangeRate")
        private Double estimatedExchangeRate;

        @JsonProperty("networkFee")
        private Fee networkFee;

        @JsonProperty("partnerFee")
        private Fee partnerFee;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceFee {
        private String name;
        private Double amount;
        private String currency;
        private String percentage;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConvertedAmount {
        private Double amount;
        private String currency;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Fee {
        private Double amount;
        private String currency;
        private String percentage;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Payout {
        private String iban;

        @JsonProperty("full_name")
        private String fullName;

        private String address;
        private String extraId;
    }

}
