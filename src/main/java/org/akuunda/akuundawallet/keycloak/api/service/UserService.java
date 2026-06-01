package org.akuunda.akuundawallet.keycloak.api.service;

import org.akuunda.akuundawallet.keycloak.api.dto.*;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import java.util.List;

@Validated
public interface UserService {

    /**
     * {@inheritDoc}
     */
    void updateUserLocal(final String realmName, final UpdateUserCommand command);

    /**
     * {@inheritDoc}
     */
    ResponseEntity<UserCreateResponse> createUserPart(final AddUserPartCommand command, final String realmName);

    /**
     * {@inheritDoc}
     */
    ResponseEntity<UserCreateResponse> createUserEnterprise(final AddUserEntCommand command, final String realmName);

    /**
     * {@inheritDoc}
     */
    ResponseEntity<List<UserDto>> getUsers();

    /**
     * {@inheritDoc}
     */
    ResponseEntity<UserDto> activeUser(final String userName, String realmName);

    /**
     * {@inheritDoc}
     */
    ResponseEntity<UserResponse> getUser(final String userName);

    /**
     * {@inheritDoc}
     */
    ResponseEntity<UserResponse> sendOtpToUser(final String userName, final String type, final String action);

    /**
     * {@inheritDoc}
     */
    ResponseEntity<UserResponse> login(final LoginRequest loginRequest, final String realmName); // update to add facebbok and google

    /**
     * {@inheritDoc}
     */
    ResponseEntity<UserResponse> loginSocial(final LoginSocialRequest loginRequest, final String realmName); // update to add facebbok and google

    ResponseEntity<AfricanDto> checkIfUserIsAfrican(final String username, final String realmName);

    ResponseEntity<WalletResponse> getUserWalletBalance(String username);

    ResponseEntity<UserDto> handleExistingUser(Users user);

    ResponseEntity<String> updateUserCguAcceptation();

}
