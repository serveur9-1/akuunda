package org.akuunda.akuundawallet.keycloak.api.dto;

import lombok.Builder;
@Builder
public record UserResponse(
        String status,
        String message,
        UserDto data
) {}
