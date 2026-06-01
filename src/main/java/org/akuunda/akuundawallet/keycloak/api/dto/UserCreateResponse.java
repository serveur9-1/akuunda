package org.akuunda.akuundawallet.keycloak.api.dto;

import lombok.Builder;

@Builder
public record UserCreateResponse(
        String status,
        String message,
        String data
) {}
