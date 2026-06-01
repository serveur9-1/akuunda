package org.akuunda.akuundawallet.keycloak.api.service;

import org.springframework.validation.annotation.Validated;

@Validated
public interface OtpService {

    /**
     * {@inheritDoc}
     */
    Boolean verifyOtp(String otpCode, String userName);
}
