package org.akuunda.akuundawallet.esim.api.dto;

import lombok.Data;

@Data
public class PurchaseFlowRequestDto {

    /** Deprecated: UUID Keycloak. Use username instead. */
    private String userId;

    /** Numéro de téléphone (ex: 0033612108828) — identifiant principal dans l'écosystème. */
    private String username;

    private String productId;
    private String orderType;
    private String msisdn;
    private String simSerial;
    private Double amount;

    /** headerPin pour le débit wallet : '$signingMethodId:$pin'. Optionnel. */
    private String headerPin;

    /** Devise du paiement wallet (ex: EUR, XOF). Optionnel. */
    private String devise;
}
