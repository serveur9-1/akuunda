package org.akuunda.akuundawallet.keycloak.api.service;

import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.springframework.validation.annotation.Validated;

import java.util.List;

@Validated
public interface UserHelperService {

    Users getUserEntity(String username);

    List<Users> getAllUsers();

    List<Wallet> getWalletsByUser(Users user);
}
