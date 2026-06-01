package org.akuunda.akuundawallet.keycloak.api.service;

import org.akuunda.akuundawallet.keycloak.api.dto.UserDto;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

@Validated
public interface UserReadOnlyService {

    ResponseEntity<UserDto> getUser(String username);
    UserDto getUserEntity(String username);
}
