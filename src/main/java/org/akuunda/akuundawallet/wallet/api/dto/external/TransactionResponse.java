package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionResponse(
        Long id,
        String status,
        String email,
        List<Map<String, String>> errors,
        String status_details,
        String from_currency,
        String initial_from_currency,
        String from_network,
        String from_currency_with_network,
        Double from_amount,
        String deposit_type,
        String payout_type,
        Double expected_from_amount,
        Double initial_expected_from_amount,
        String to_currency,
        String to_network,
        String to_currency_with_network,
        Double to_amount,
        String output_hash,
        Double expected_to_amount,
        String location,
        String refund_reason,
        Instant created_at,
        Instant updated_at,
        Long partner_id,
        String external_partner_link_id,
        Double from_amount_in_eur,
        Boolean customer_payout_address_changeable,
        Map<String, Object> estimate_breakdown,
        Map<String, String> payout,
        String deposit_payment_category,
        String payout_payment_category,
        String redirect_url,
        String preauth_token,
        Boolean skip_choose_payout_address,
        Boolean skip_choose_payment_category
) {}
