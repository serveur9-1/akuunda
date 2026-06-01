package org.akuunda.akuundawallet.keycloak.impl.service;


import lombok.RequiredArgsConstructor;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.keycloak.api.service.UserHelperService;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserHelperServiceImpl implements UserHelperService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    @Override
    public Users getUserEntity(String username) {
        return userRepository.getUsersByUsername(username);
    }

    @Override
    public List<Users> getAllUsers() {
        return userRepository.findAll();
    }

    @Override
    public List<Wallet> getWalletsByUser(Users user) {
        return walletRepository.findWalletByUsers(user);
    }
}
