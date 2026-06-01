package org.akuunda.akuundawallet.keycloak.api.service;

import jakarta.ws.rs.NotFoundException;
import org.akuunda.akuundawallet.common.utils.UserRoleType;
import org.akuunda.akuundawallet.keycloak.api.dto.AddUserCommand;
import org.akuunda.akuundawallet.keycloak.api.dto.UpdateUserCommand;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface KeycloakUserService {

    /**
     * {@inheritDoc}
     */
    UserRepresentation getUserById(String userId);

    /**
     * {@inheritDoc}
     */
    List<UserRepresentation> searchByEmail(String realmname, String email, boolean exact);


    /**
     * {@inheritDoc}
     */
    List<UserRepresentation> getUsers(String realmName, String login, String roleName, UserRoleType type);

    /**
     * {@inheritDoc}
     */
    ResponseEntity<UserRepresentation> addUser(String realmName, AddUserCommand command);

    /**
     * {@inheritDoc}
     */
    boolean updateUser(String realmName, UpdateUserCommand command);

    /**
     * {@inheritDoc}
     */
    UserRepresentation changePassword(String realmName, String password, String userName);

    /**
     * {@inheritDoc}
     */
    Boolean updateUserStatus(String realmName, String login, boolean b);

    /**
     * {@inheritDoc}
     */
    void deleteUser(String realmName, String login);

    /**
     * Supprime complètement un utilisateur de Keycloak et de la base de données locale en cascade
     * @param realmName Le nom du realm Keycloak
     * @param login Le username (login) de l'utilisateur à supprimer
     * @throws NotFoundException Si l'utilisateur n'est pas trouvé
     */
    void deleteUserPermanently(String realmName, String login) throws NotFoundException;

}
