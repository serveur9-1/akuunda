package org.akuunda.akuundawallet.keycloak.impl.service;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.constants.KeycloakConstants;
import org.akuunda.akuundawallet.common.utils.UserRoleType;
import org.akuunda.akuundawallet.keycloak.api.dao.AttachmentRepository;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.dto.AddUserCommand;
import org.akuunda.akuundawallet.keycloak.api.dto.UpdateUserCommand;
import org.akuunda.akuundawallet.keycloak.api.entities.Attachments;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.keycloak.api.service.KeycloakUserService;
import org.akuunda.akuundawallet.wallet.api.dao.*;
import org.akuunda.akuundawallet.wallet.api.entities.*;
import org.springframework.transaction.annotation.Transactional;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Slf4j
@RequiredArgsConstructor
public class KeycloakUserServiceImpl implements KeycloakUserService {

    @Value("${keycloak.realm}")
    private String realm;

    @Value("${keycloak.url.auth}")
    private String serverURL;
    @Value("${keycloak.clientId}")
    private String clientID;

    @Value("${keycloak.clientSecret}")
    private String clientSecret;
    private final Keycloak keycloak;

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PaymentLinkRepository paymentLinkRepository;
    private final PaymentLinkTransactionRepository paymentLinkTransactionRepository;
    private final AttachmentRepository attachmentRepository;
    private final UserPinRepository userPinRepository;
    private final UserEmergencyCodeRepository userEmergencyCodeRepository;
    private final OperationRepository operationRepository;
    private final ConditionalPaymentRepository conditionalPaymentRepository;
    private final QRCodeRepository qrCodeRepository;
    private final OneTimePaymentLinkRepository oneTimePaymentLinkRepository;

    /**
     * {@inheritDoc}
     */
    @Override
    public UserRepresentation getUserById(String userId) {
        return  getUsersResource().get(userId).toRepresentation();
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<UserRepresentation> searchByEmail(String realmName, String email, boolean exact) {
        log.info("Searching by email: {} exact {} in realm {} )", email, exact, realmName);
        UsersResource usersResource = getRealmResource(realmName).users();
        List<UserRepresentation> users = usersResource.searchByEmail(email, exact);
        log.info("Users found by email {}", users.stream()
                .map(UserRepresentation::getEmail)
                .collect(Collectors.toList()));
        return users;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public List<UserRepresentation> getUsers(String realmName, String login, String roleName, UserRoleType roleType) {
        final var realmResource = keycloak.realm(realmName);
        final Stream<UserRepresentation> users;
        if (roleName != null && !roleName.isEmpty() && roleType != null){
            //filter on role
            var usersForRole = getUsersForRole(realmResource, roleName, roleType);
            if (login != null && !login.isEmpty()) {
                // filter on role and login
                usersForRole = usersForRole.filter(u -> u.getUsername().equals(login));
            }
            users = usersForRole;
        } else if (login != null && !login.isEmpty()) {
            // filter on login only
            users = getUsersForLogin(realmResource, login);
        } else {
            // no filter
            users = realmResource.users().list(KeycloakConstants.LIST_PAGE_FIRST,
                    KeycloakConstants.LIST_PAGE_SIZE).stream();
        }
        return users.collect(Collectors.toList());
    }

    @Override
    public ResponseEntity<UserRepresentation> addUser(String realmName, AddUserCommand command) throws NotFoundException, BadRequestException {
        final var realmResource = getRealmResource(realmName);
        final var usersResource = realmResource.users();

        log.debug("Search username in KeyCloak {}", command.getUsername());

        var user = realmResource
                .users()
                .searchByUsername(command.getUsername(), true)
                .stream().filter(r -> r.getUsername()
                        .equals(command.getUsername()))
                .findFirst().orElse(null);
        if (user != null) {
            log.info("The user by login '" + command.getUsername() + "' in realm '" + realmName + "' is existed");
            log.error("The user by login '" + command.getUsername() + "' in realm '" + realmName + "' is existed");
            return new ResponseEntity<>(user, HttpStatus.CONFLICT);
        }
        user = new UserRepresentation();
        user.setUsername(command.getUsername());
        this.mapUserFromCreateCommand(user, command);

        log.debug("Keycloak User Info {} ", user.getUsername() + user.getFirstName());

        Response response = usersResource.create(user);

        log.debug("Keycloak Create User Response {} ", response.getStatusInfo());

        if (response.getStatus() == 201) {
            return ResponseEntity.ok(user);
        } else {
            return ResponseEntity.status(response.getStatus()).body(null);
        }

    }

    @Override
    public boolean updateUser(String realmName, UpdateUserCommand command) throws NotFoundException {

        final var realmResource = getRealmResource(realmName);
        final var usersResource = realmResource.users();
        var user = realmResource.users().search(command.getUserName(), true)
                .stream().filter(r -> r.getUsername().equals(command.getUserName()))
                .findFirst().orElse(null);
        if (user != null) {
            this.mapUserFromUpdateCommand(user, command);
            // Update the user
            final var userResource = usersResource.get(user.getId());
            userResource.update(user);

            return true;

        } else {
            log.error("User " + command.getUserName() + " not found in realm " + realmName);
            log.info("User " + command.getUserName() + " not found in realm " + realmName);
            return false;
        }
    }

    @Override
    public UserRepresentation changePassword(String realmName, String password, String userName) {

                final var realmResource = getRealmResource(realmName);
                final var usersResource = realmResource.users();
                var user = realmResource.users().search(userName, true)
                        .stream().filter(r -> r.getUsername().equals(userName))
                        .findFirst().orElse(null);

                if (user != null ) {
                    user.setCredentials(Collections.singletonList(createPasswordCredentials(password)));
                    // Update the user
                    final var userResource = usersResource.get(user.getId());
                    userResource.update(user);
                    log.info("user update in success ");
                    return user;
                } else {
                    log.info("user not found in realm {} ", realmName);
                    return null;
                }

    }

    @Override
    public Boolean updateUserStatus(String realmName, String login, boolean available) throws NotFoundException {
        final var realmResource = getRealmResource(realmName);
        UsersResource usersResource = realmResource.users();
        UserRepresentation userRepresentation = realmResource
                .users()
                .search(login, true)
                .stream().filter(r -> r.getUsername().equals(login))
                .findFirst().orElse(null);
        if (userRepresentation != null) {
            if (!userRepresentation.isEnabled().equals(available)) {
                userRepresentation.setEnabled(available);
                UserResource userResource = usersResource.get(userRepresentation.getId());
                userResource.update(userRepresentation);

                log.info("update user {} with status {} ", login, available);
                new Users();
                Users localUser = userRepository.getUsersByUsername(userRepresentation.getUsername());
                if (localUser != null) {
                    localUser.setEnabled(available);
                    userRepository.saveAndFlush(localUser);
                }
                return Boolean.TRUE;
            }
        } else {
            log.error("User " + login + " not found in realm " + realmName);
            log.info("User " + login + " not found in realm " + realmName);
            return Boolean.FALSE;
        }
        return Boolean.TRUE;
    }

    @Override
    public void deleteUser(String realmName, String login) throws NotFoundException {
        final var realmResource = getRealmResource(realmName);
        UsersResource usersResource = realmResource.users();
        UserRepresentation userRepresentation = realmResource.users().search(login, true)
                .stream()
                .filter(r -> r.getUsername().equals(login))
                .findFirst().orElse(null);
        if (userRepresentation != null) {
            usersResource.get(userRepresentation.getId()).remove();

            log.info("delete user {} in realm {} ", login, realmName);
            Users localUser = userRepository.findById(userRepresentation.getId()).orElse(null);
            if (localUser != null) {
                localUser.setEnabled(false);
                userRepository.saveAndFlush(localUser);
            }
        } else  {
            throw new NotFoundException("User " + login + " not found in realm " + realmName);
        }
    }

    @Override
    @Transactional
    public void deleteUserPermanently(String realmName, String login) throws NotFoundException {
        log.info("Starting permanent deletion of user: {} from realm: {}", login, realmName);

        // 1. Essayer de récupérer l'utilisateur depuis Keycloak (optionnel)
        String userId = null;
        UserRepresentation userRepresentation = null;
        UsersResource usersResource = null;
        try {
            final var realmResource = getRealmResource(realmName);
            usersResource = realmResource.users();
            userRepresentation = realmResource.users().search(login, true)
                    .stream()
                    .filter(r -> r.getUsername().equals(login))
                    .findFirst()
                    .orElse(null);

            if (userRepresentation != null) {
                userId = userRepresentation.getId();
                log.info("Found user in Keycloak with ID: {}", userId);
            } else {
                log.warn("User {} not found in Keycloak realm {}, but will continue with local database deletion", login, realmName);
            }
        } catch (Exception e) {
            log.warn("Error searching user in Keycloak: {}, but will continue with local database deletion", e.getMessage());
        }

        // 2. Récupérer l'utilisateur local (chercher d'abord par username, puis par userId si disponible)
        Users localUser = userRepository.getUsersByUsername(login);
        if (localUser == null && userId != null) {
            log.debug("User {} not found by username, trying by userId: {}", login, userId);
            localUser = userRepository.findById(userId).orElse(null);
        }
        
        // Si l'utilisateur n'existe ni en local ni dans Keycloak, lever une exception
        if (localUser == null && userRepresentation == null) {
            throw new NotFoundException("User " + login + " not found in realm " + realmName + " and local database");
        }
        
        if (localUser == null) {
            log.warn("User {} not found in local database (searched by username and userId: {}), but will continue with Keycloak deletion", login, userId);
        } else {
            log.info("Found user in local database with userId: {}, starting cascade deletion", localUser.getUserId());

            // 3. Supprimer les PaymentLinkTransactions où l'utilisateur est payeur
            List<PaymentLinkTransaction> payerTransactions = paymentLinkTransactionRepository.findByPayer(localUser);
            if (!payerTransactions.isEmpty()) {
                log.info("Deleting {} payment link transactions where user is payer", payerTransactions.size());
                paymentLinkTransactionRepository.deleteAll(payerTransactions);
            }

            // 4. Supprimer les PaymentLinks créés par l'utilisateur
            List<PaymentLink> userPaymentLinks = paymentLinkRepository.findByCreatorOrderByCreatedAtDesc(localUser);
            if (!userPaymentLinks.isEmpty()) {
                log.info("Deleting {} payment links created by user", userPaymentLinks.size());
                // Supprimer d'abord les transactions liées à ces liens
                for (PaymentLink link : userPaymentLinks) {
                    List<PaymentLinkTransaction> linkTransactions = 
                            paymentLinkTransactionRepository.findByPaymentLinkOrderByCreatedAtDesc(link);
                    if (!linkTransactions.isEmpty()) {
                        paymentLinkTransactionRepository.deleteAll(linkTransactions);
                    }
                }
                paymentLinkRepository.deleteAll(userPaymentLinks);
            }

            // 5. Paiements conditionnels (escrow) puis wallets
            List<Wallet> userWallets = walletRepository.findWalletByUsers(localUser);
            cleanupConditionalPaymentsBeforeWalletDeletion(localUser, userWallets);

            // Mettre currency à null pour chaque wallet avant suppression
            // (car CountryCurrency est une entité partagée du système)
            if (!userWallets.isEmpty()) {
                log.info("Deleting {} wallets for user", userWallets.size());
                // Mettre currency à null pour éviter la suppression en cascade de CountryCurrency
                for (Wallet wallet : userWallets) {
                    if (wallet.getCurrency() != null) {
                        wallet.setCurrency(null);
                    }
                }
                walletRepository.saveAll(userWallets);
                walletRepository.flush();
                walletRepository.deleteAll(userWallets);
            }

            // 6. Supprimer les Attachments
            List<Attachments> userAttachments = attachmentRepository.findAttachmentsByUsers(localUser);
            if (!userAttachments.isEmpty()) {
                log.info("Deleting {} attachments for user", userAttachments.size());
                attachmentRepository.deleteAll(userAttachments);
            }

            // 7. Supprimer les UserPins (utiliser le userId local, pas celui de Keycloak)
            String localUserId = localUser.getUserId();
            List<UserPin> userPins = userPinRepository.findByUserId(localUserId);
            if (!userPins.isEmpty()) {
                log.info("Deleting {} user pins for user", userPins.size());
                userPinRepository.deleteAll(userPins);
            }

            // 8. Supprimer les UserEmergencyCodes (utiliser le userId local, pas celui de Keycloak)
            List<UserEmergencyCode> userEmergencyCodes = userEmergencyCodeRepository.findByUserId(localUserId);
            if (!userEmergencyCodes.isEmpty()) {
                log.info("Deleting {} emergency codes for user", userEmergencyCodes.size());
                userEmergencyCodeRepository.deleteAll(userEmergencyCodes);
            }

            // 9. Supprimer les Operations
            List<Operation> userOperations = operationRepository.findByUsername(login, org.springframework.data.domain.Pageable.unpaged()).getContent();
            if (!userOperations.isEmpty()) {
                log.info("Deleting {} operations for user", userOperations.size());
                operationRepository.deleteAll(userOperations);
            }

            // 10. Supprimer l'utilisateur local
            // Mettre countryCurrency à null pour éviter la suppression en cascade
            // (car CountryCurrency est une entité partagée du système, utilisée par d'autres utilisateurs)
            if (localUser.getCountryCurrency() != null) {
                log.debug("Setting countryCurrency to null before deletion to avoid cascade constraint violation");
                localUser.setCountryCurrency(null);
                userRepository.saveAndFlush(localUser);
            }
            log.info("Deleting user from local database");
            userRepository.delete(localUser);
        }

        // 11. Supprimer l'utilisateur de Keycloak (seulement s'il existe)
        if (userRepresentation != null && userId != null && usersResource != null) {
            try {
                log.info("Deleting user from Keycloak");
                usersResource.get(userId).remove();
                log.info("User {} deleted from Keycloak", login);
            } catch (Exception e) {
                log.error("Error deleting user from Keycloak: {}", e.getMessage(), e);
                // Ne pas lever d'exception, la suppression locale a déjà été effectuée
            }
        } else {
            log.info("User {} not found in Keycloak, skipping Keycloak deletion", login);
        }

        log.info("User {} permanently deleted from realm {} and local database", login, realmName);
    }

    /**
     * Supprime les escrow de l'utilisateur et détache les FK wallet sur conditional_payments
     * avant {@code delete from wallet}.
     */
    private void cleanupConditionalPaymentsBeforeWalletDeletion(Users localUser, List<Wallet> userWallets) {
        List<ConditionalPayment> userPayments =
                conditionalPaymentRepository.findByClientOrVendor(localUser);
        if (!userPayments.isEmpty()) {
            log.info("Deleting {} conditional payments for user", userPayments.size());
            for (ConditionalPayment payment : userPayments) {
                qrCodeRepository.findByConditionalPayment(payment)
                        .ifPresent(qrCodeRepository::delete);
                oneTimePaymentLinkRepository.findByConditionalPaymentId(payment.getId())
                        .ifPresent(link -> {
                            link.setConditionalPaymentId(null);
                            oneTimePaymentLinkRepository.save(link);
                        });
            }
            conditionalPaymentRepository.deleteAll(userPayments);
            conditionalPaymentRepository.flush();
        }

        if (userWallets == null || userWallets.isEmpty()) {
            return;
        }
        List<String> walletIds = userWallets.stream()
                .map(Wallet::getId)
                .filter(Objects::nonNull)
                .toList();
        if (walletIds.isEmpty()) {
            return;
        }

        List<ConditionalPayment> walletLinked =
                conditionalPaymentRepository.findByWalletIds(walletIds);
        if (walletLinked.isEmpty()) {
            return;
        }

        log.info("Detaching {} conditional payment wallet references", walletLinked.size());
        for (ConditionalPayment payment : walletLinked) {
            boolean changed = false;
            if (payment.getClientWallet() != null
                    && walletIds.contains(payment.getClientWallet().getId())) {
                payment.setClientWallet(null);
                changed = true;
            }
            if (payment.getVendorWallet() != null
                    && walletIds.contains(payment.getVendorWallet().getId())) {
                payment.setVendorWallet(null);
                changed = true;
            }
            if (payment.getIntermediateWallet() != null
                    && walletIds.contains(payment.getIntermediateWallet().getId())) {
                payment.setIntermediateWallet(null);
                changed = true;
            }
            if (changed) {
                conditionalPaymentRepository.save(payment);
            }
        }
        conditionalPaymentRepository.flush();
    }

    private Stream<UserRepresentation> getUsersForLogin(RealmResource realmResource, String login) {
        return realmResource.users().search(login, true).stream();
    }

    private Stream<UserRepresentation> getUsersForRole(RealmResource realmResource, String roleName, UserRoleType roleType) {
        final Stream<UserRepresentation> userForRoles;
        if (roleType == UserRoleType.ALL || roleType == UserRoleType.ROLE){
            final var fullRoleName = KeycloakConstants.ROLE_PREFIX + roleName;
            userForRoles = getUsersForRole(realmResource, fullRoleName);
        } else {
            userForRoles = Stream.empty();
        }
        final Stream<UserRepresentation> userForServices;
        if (roleType == UserRoleType.ALL || roleType == UserRoleType.SERVICE){
            final var fullRoleName = KeycloakConstants.ROLE_PREFIX + roleName;
            userForServices = getUsersForRole(realmResource, fullRoleName);
        } else {
            userForServices = Stream.empty();
        }
        return Stream.concat(userForRoles, userForServices);
    }

    private Stream<UserRepresentation> getUsersForRole(RealmResource realmResource, String fullRoleName) {
        try {
            return realmResource.roles()
                    .get(fullRoleName).getUserMembers(KeycloakConstants.LIST_PAGE_FIRST, KeycloakConstants.LIST_PAGE_SIZE)
                    .stream()
                    .distinct();
        } catch (NotFoundException e){
            log.info("No role found fo {}", fullRoleName, e);
            return Stream.empty();
        }
    }

    private UsersResource getUsersResource() {
        RealmResource realm1 = keycloak.realm(realm);
        return realm1.users();
    }

    private RealmResource getRealmResource(String realmName) throws NotFoundException {
        final var realmResource = keycloak.realm(realmName);
        if (realmResource == null) {
            throw new NotFoundException("realm " + realmName + "not found!, please check!");
        }
        return realmResource;
    }

    private void mapUserFromUpdateCommand(UserRepresentation user, UpdateUserCommand command) {
        if(!command.getFirstName().isEmpty()) {
            user.setFirstName(command.getFirstName());
        }
        if(!command.getLastName().isEmpty()) {
            user.setLastName(command.getLastName());
        }

        if(!command.getEmail().isEmpty()) {
            user.setEmail(command.getEmail());
        }
    }

    private void mapUserFromCreateCommand(UserRepresentation user, AddUserCommand command) {
        user.setFirstName(command.getFirstName());
        user.setUsername(command.getUsername());
        user.setLastName(command.getLastName());
        //user.setCredentials(Collections.singletonList(createPasswordCredentials(hashedPassword)));
        if (!command.getEmail().isEmpty()) {
            user.setEmail(command.getEmail());
        }
        user.setEnabled(Boolean.FALSE);
        user.setEmailVerified(Boolean.FALSE);
        user.singleAttribute(KeycloakConstants.ATTRIBUTE_CREATED_FROM, "AKUNNDA_WALLET");
        user.singleAttribute(KeycloakConstants.ATTRIBUTE_HABILITATION, String.valueOf(9));
    }

    private static CredentialRepresentation createPasswordCredentials(String password) {
        CredentialRepresentation passwordCredentials = new CredentialRepresentation();
        passwordCredentials.setTemporary(false);
        passwordCredentials.setType(CredentialRepresentation.PASSWORD);
        passwordCredentials.setValue(password);
        return passwordCredentials;
    }
}
