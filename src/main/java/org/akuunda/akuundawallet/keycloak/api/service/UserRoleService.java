package org.akuunda.akuundawallet.keycloak.api.service;

import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import org.akuunda.akuundawallet.common.utils.Types;
import org.akuunda.akuundawallet.common.utils.UserRoleType;
import org.akuunda.akuundawallet.keycloak.api.dto.UserRoleDto;
import java.util.List;

public interface UserRoleService {

    List<UserRoleDto> getListOfRoles(UserRoleType type, String realmName);

    int deleteUserFromRole(String realmName, String roleName, String login, UserRoleType type) throws BadRequestException, NotFoundException;

    void addRoleForUser(String realmName, String roleName, String login, Types type);

}
