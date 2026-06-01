package org.akuunda.akuundawallet.wallet.api.dto.external;

import lombok.Builder;

@Builder
public record GuadarianData(
        Long id,
        String redirect_url,
        String username,
        String status
) {}
