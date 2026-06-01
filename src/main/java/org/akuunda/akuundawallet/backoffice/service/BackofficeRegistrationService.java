package org.akuunda.akuundawallet.backoffice.service;

import org.akuunda.akuundawallet.backoffice.dto.auth.BackofficeRegisterRequest;
import org.akuunda.akuundawallet.backoffice.dto.auth.BackofficeRegisterResponse;

public interface BackofficeRegistrationService {

    BackofficeRegisterResponse register(BackofficeRegisterRequest request);
}
