package org.akuunda.akuundawallet.keycloak.impl.service;

import lombok.RequiredArgsConstructor;
import org.akuunda.akuundawallet.keycloak.api.dto.UserDto;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.keycloak.api.service.UserHelperService;
import org.akuunda.akuundawallet.keycloak.api.service.UserReadOnlyService;
import org.akuunda.akuundawallet.keycloak.impl.service.mapper.UserMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserReadOnlyServiceImpl implements UserReadOnlyService {

    private final UserHelperService userHelperService;

    @Override
    public ResponseEntity<UserDto> getUser(String username) {
        Users user = userHelperService.getUserEntity(username);
        if (user == null) return ResponseEntity.notFound().build();
        var wallets = userHelperService.getWalletsByUser(user);
        UserDto dto = UserMapper.mapUserToUserDto(user, wallets);
        return ResponseEntity.ok(dto);
    }

    @Override
    public UserDto getUserEntity(String username) {
        Users user = userHelperService.getUserEntity(username);
        if (user == null) return null;
        var wallets = userHelperService.getWalletsByUser(user);
        return UserMapper.mapUserToUserDto(user, wallets);
    }

}
