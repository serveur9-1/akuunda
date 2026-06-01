package org.akuunda.akuundawallet.wallet.api.dto.external;

import lombok.Builder;

@Builder
public record GuadarianResponse(
        String status,
        String message,
        GuadarianData data
) {}

