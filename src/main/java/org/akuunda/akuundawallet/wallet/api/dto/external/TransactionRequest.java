package org.akuunda.akuundawallet.wallet.api.dto.external;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Builder;

@Builder
public record TransactionRequest(

        // Symbole de la devise source (par exemple, « EUR », « BTC »)
        @NotBlank(message = "from_currency obligatoire") String from_currency,

        // Symbole de la devise de destination (par exemple, « BTC », « USDT »)
        @NotBlank(message = "to_currency obligatoire") String to_currency,

        // Adresse e-mail du client
        @NotBlank(message = "email obligatoire") String email,
        @NotBlank(message = "username obligatoire") String username,

        // String payment_category , // categorie de paiement

       // @NotBlank(message = "external_partner_link_id obligatoire") String external_partner_link_id, //  Votre identifiant de référence interne pour cette transaction

        //@NotBlank(message = "Le username est obligatoire") String username,

        @NotNull(message = "Le montant est obligatoire")
        @Min(value = 1, message = "Le montant doit être supérieur à 0")
        Double from_amount,

        // Utilisé uniquement pour OFFRAMP si tu fais des retraits fiat
        //String bank_account_number,
        String from_network,
        String to_network
        //String bank_routing_number,
        //String bank_holder_name,

        // Optionnel si Guardarian demande un payout crypto différent
        //String payout_address
) {}
