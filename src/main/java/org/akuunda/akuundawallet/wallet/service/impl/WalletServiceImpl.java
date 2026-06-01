package org.akuunda.akuundawallet.wallet.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.api.dto.external.WalletCreateResponse;
import org.akuunda.akuundawallet.wallet.api.entities.CountryCurrency;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.wallet.api.service.WalletService;
import org.akuunda.akuundawallet.wallet.service.PaymentFactoryContractService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkunndaWalletClientService;
import org.akuunda.akuundawallet.wallet.service.mapper.WalletMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;


@Service
@Slf4j
@RequiredArgsConstructor
public class WalletServiceImpl implements WalletService {

    private final AkunndaWalletClientService walletClientService;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PaymentFactoryContractService paymentFactoryContractService;

    @Override
    public ResponseEntity<String> createWallet(final String userName, final String signingPin,
                                               final CountryCurrency countryCurrency,
                                               final String reference) {

        final var localUser = userRepository.getUsersByUsername(userName);

        final String secretType = "MATIC";

        if (localUser.getUserId() != null) {

            final String userId = localUser.getUserId();
            final String description = reference.replace("User", "Wallet");

            String requestBody = String.format(
                    "{\"secretType\":\"%s\",\"userId\":\"%s\",\"description\":\"%s\"}",
                    secretType, userId, description
            );

            final var walletResponse = walletClientService.createWallet(requestBody, signingPin);
            if (walletResponse.getStatusCode().is2xxSuccessful()) {
                assert walletResponse.getBody() != null;
                walletRepository.save(WalletMapper.mapResultToWallet(walletResponse.getBody().getResult(),
                        countryCurrency, localUser));
                return new ResponseEntity<>("Wallet created successfully - " + userName, HttpStatus.OK);
            } else {
                return new ResponseEntity<>("Wallet create failed - " + userName,
                        HttpStatus.EXPECTATION_FAILED);
            }
        } else {
            return new ResponseEntity<>("User not found - " + userName, HttpStatus.NOT_FOUND);
        }
    }

    @Override
    public WalletCreateResponse updateWallet(String userName, String currencyCode) {
        return null;
    }

    @Override
    public ResponseEntity<String> activateWallet(String userName, String signingPin) {
        return null;
    }

    @Override
    public ResponseEntity<String> getWalletBalance(String userName) {
        Users user = userRepository.getUsersByUsername(userName);
        if (user == null) {
            return new ResponseEntity<>("User not found - " + userName, HttpStatus.NOT_FOUND);
        }

        Wallet wallet = walletRepository.findByUsers(user);
        if (wallet == null) {
            return new ResponseEntity<>("Wallet not found - " + userName, HttpStatus.NOT_FOUND);
        }

        // Lecture directe sur la blockchain — pas d'appel Venly nécessaire (balanceOf public)
        if (wallet.getAddress() != null && !wallet.getAddress().isBlank()) {
            Double balance = paymentFactoryContractService.getUsdcBalance(wallet.getAddress());
            if (balance != null) {
                return new ResponseEntity<>(String.valueOf(balance), HttpStatus.OK);
            }
        }

        // Fallback sur le solde en base si le RPC est indisponible (balance est un double primitif)
        return new ResponseEntity<>(String.valueOf(wallet.getBalance()), HttpStatus.OK);
    }

}
