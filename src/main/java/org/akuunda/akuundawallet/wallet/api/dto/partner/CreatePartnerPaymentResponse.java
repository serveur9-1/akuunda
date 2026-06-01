package org.akuunda.akuundawallet.wallet.api.dto.partner;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CreatePartnerPaymentResponse {
    private String paymentCode;
    private String provider;
    /** Lien direct vers le widget/page provider — à ouvrir par le client */
    private String providerUrl;
    /** Infos virement bancaire YellowCard (si bank transfer) */
    private Object bankInfo;
    private String reference;
    /** Statut du lien de paiement */
    private String status;
}
