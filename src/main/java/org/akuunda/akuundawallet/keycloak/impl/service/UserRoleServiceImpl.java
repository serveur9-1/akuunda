package org.akuunda.akuundawallet.keycloak.impl.service;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.constants.KeycloakConstants;
import org.akuunda.akuundawallet.common.utils.Types;
import org.akuunda.akuundawallet.common.utils.UserRoleType;
import org.akuunda.akuundawallet.keycloak.api.dto.UserRoleDto;
import org.akuunda.akuundawallet.keycloak.api.service.UserRoleService;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserRoleServiceImpl implements UserRoleService {

    private final Keycloak keycloak;

    public UserRoleServiceImpl(Keycloak keycloak) {
        this.keycloak = keycloak;
    }

    @Override
    public List<UserRoleDto> getListOfRoles(UserRoleType type, String realmName) {
        final var result = new ArrayList<UserRoleDto>();
        if (type == UserRoleType.ROLE || type == UserRoleType.ALL) {
            result.addAll(getAllRoles(realmName, KeycloakConstants.ROLE_PREFIX));
        }
        if (type == UserRoleType.SERVICE || type == UserRoleType.ALL) {
            result.addAll(getAllRoles(realmName, KeycloakConstants.SERVICE_PREFIX));
        }
        return result;
    }

    private List<UserRoleDto> getAllRoles(String realmName, String prefix) {
        List<UserRoleDto> roles = new ArrayList<>();
        final var realmResource = keycloak.realm(realmName);
        final var keycloakRoles = realmResource.roles().list(KeycloakConstants.LIST_PAGE_FIRST,
                        KeycloakConstants.LIST_PAGE_SIZE, false) //
                .stream() //
                .filter(role -> role.getName().startsWith(prefix)) //
                .collect(Collectors.toMap(RoleRepresentation::getName, Function.identity()));
        for (final var role : keycloakRoles.values()) {
            roles.add(buildDto(role, keycloakRoles, prefix));
        }
        return roles;
    }

    private UserRoleDto buildDto(RoleRepresentation role, Map<String, RoleRepresentation> allRoles, String prefix) {
        final var userRole = new UserRoleDto();
        userRole.setId(role.getName());
        userRole.setName(role.getName().substring(prefix.length()));
        userRole.setComment(role.getDescription());
        final var fragments = getParentFragments(role, allRoles);
        userRole.setFullPath(aggregateFullPath(fragments, prefix));
        if (role.getAttributes() != null) {
            userRole.setCreatedFrom(getFirstValueFromAttribute(role.getAttributes().get(KeycloakConstants.ATTRIBUTE_CREATED_FROM)));
            final var parentRole = getParent(role, allRoles);
            if (parentRole != null) {
                userRole.setNameParent(parentRole.getName().substring(prefix.length()));
                userRole.setIdParent(parentRole.getName());
            }
        }
        return userRole;
    }

    private String getFirstValueFromAttribute(List<String> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    private String aggregateFullPath(List<String> fragments, String prefix) {
        final var pathBuilder = new StringBuilder();
        for (int i = fragments.size() - 1; i >= 0; i--) {
            pathBuilder.append(fragments.get(i).substring(prefix.length()));
            if (i != 0) {
                pathBuilder.append('.');
            }
        }
        return pathBuilder.toString();
    }

    public List<String> getParentFragments(RoleRepresentation role, Map<String, RoleRepresentation> allRoles) {
        final List<String> pathFragments = new ArrayList<>();
        pathFragments.add(role.getName());
        final var parentRole = getParent(role, allRoles);
        if (parentRole != null) {
            pathFragments.addAll(getParentFragments(parentRole, allRoles));
        }
        return pathFragments;
    }

    private RoleRepresentation getParent(RoleRepresentation role, Map<String, RoleRepresentation> allRoles) {
        if (role.getAttributes() != null) {
            final var attributes = role.getAttributes();
            final var parentName = getFirstValueFromAttribute(attributes.get(KeycloakConstants.ATTRIBUTE_PARENT_NAME));
            if (parentName != null) {
                return allRoles.get(parentName);
            }
        }
        return null;
    }

    @Override
    public int deleteUserFromRole(String realmName, String roleName, String login, UserRoleType type) throws BadRequestException, NotFoundException{
        int rowEffect;
        final var realmResource = keycloak.realm(realmName);
        Optional<UserRepresentation> user = realmResource.users().search(login).stream().filter(u -> u.getUsername().equals(login)).findFirst();
        if (user.isPresent()) {
            UserRepresentation userRepresentation = user.get();
            UserResource userResource = realmResource.users().get(userRepresentation.getId());
            List<RoleRepresentation> listRoleRepresentation = getRoleRepresentationList(realmResource, roleName, type);
            if (!listRoleRepresentation.isEmpty()) {
                userResource.roles().realmLevel().remove(listRoleRepresentation);
                rowEffect = listRoleRepresentation.size();
            } else {
                throw new BadRequestException("Cannot find role or service by name= " + roleName + " and type= " + type);
            }
        } else {
            throw new NotFoundException("Cannot find user by " + login);
        }
        return rowEffect;
    }

    private List<RoleRepresentation> getRoleRepresentationList(RealmResource realmResource, String roleName, UserRoleType type) throws BadRequestException {
        List<RoleRepresentation> listRoleRepresentation = new ArrayList<>();
        if (type == UserRoleType.ALL) {
            throw new BadRequestException("The type must be ROLE or SERVICE");
        }

        if (type == UserRoleType.ROLE) {
            String kcRoleName = KeycloakConstants.ROLE_PREFIX + roleName;
            RoleRepresentation role = getRoleRepresentation(realmResource, kcRoleName);
            if (role != null) {
                listRoleRepresentation.add(role);
            }
        } else if (type == UserRoleType.SERVICE) {
            String kcRoleName = KeycloakConstants.SERVICE_PREFIX + roleName;
            RoleRepresentation role = getRoleRepresentation(realmResource, kcRoleName);
            if (role != null) {
                listRoleRepresentation.add(role);
            }
        }

        return listRoleRepresentation;
    }

    private RoleRepresentation getRoleRepresentation(RealmResource realmResource, String roleName) {
        RoleRepresentation role = null;
        try {
            role = realmResource.roles().get(roleName).toRepresentation();
        } catch (NotFoundException ex) {
            log.info("roleName: {} not found", roleName, ex);
        }
        return role;
    }

    @Override
    public void addRoleForUser(final String realmName, final String roleName, final String login, final Types type) throws NotFoundException, BadRequestException {
        final var realmResource = keycloak.realm(realmName);
        final var user = realmResource.users().search(login).stream().filter(u -> u.getUsername().equals(login)).findFirst();
        if (user.isPresent()) {
            final var userRepresentation = user.get();
            final var userResource = realmResource.users().get(userRepresentation.getId());
            final var userRoleType = type == Types.ROLE ? UserRoleType.ROLE : UserRoleType.SERVICE;
            final var listRoleRepresentationNeedToAdd = getRoleRepresentationList(realmResource, roleName, userRoleType);
            if (!listRoleRepresentationNeedToAdd.isEmpty()) {
                final var listRoleRepresentation = userResource.roles().realmLevel().listAvailable();
                final var roleNames = listRoleRepresentation.stream().map(RoleRepresentation::getName).toList();
                final var listRoleRepresentationCanAdd = listRoleRepresentationNeedToAdd.stream().filter(r -> roleNames.contains(r.getName())).collect(Collectors.toList());
                userResource.roles().realmLevel().add(listRoleRepresentationCanAdd);
            } else {
                throw new NotFoundException("Cannot find role or service by name = " + roleName + " and type = " + type);
            }
        } else {
            throw new BadRequestException("Cannot find user by " + login);
        }
    }
}
