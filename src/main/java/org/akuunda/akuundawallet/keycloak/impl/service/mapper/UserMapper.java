package org.akuunda.akuundawallet.keycloak.impl.service.mapper;


import org.akuunda.akuundawallet.keycloak.api.dto.UserDto;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.dto.WalletUserDto;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.wallet.service.mapper.WalletMapper;
import java.util.ArrayList;
import java.util.List;

public class UserMapper {

    public static UserDto mapUserToUserDto(Users users, List<Wallet> wallets) {
       UserDto userDto = new UserDto();
       final var walletDto =  new ArrayList<WalletUserDto>();
       userDto.setUserId(users.getUserId());
       userDto.setEmail(users.getEmail());
       userDto.setEnabled(users.isEnabled());
       userDto.setFirstname(users.getFirstname());
       userDto.setLastname(users.getLastname());
       userDto.setCreatedAt(users.getCreatedAt());
       userDto.setDateNaissance(users.getDateNaissance());
       userDto.setTypeCompte(users.getAccountType());
       userDto.setMobilePhone(users.getMobilePhone());
       userDto.setEmailVerified(users.isEmailVerified());
       userDto.setUsername(users.getUsername());
       userDto.setSiret(users.getSiret());
       userDto.setAdresse(users.getAdresse());
       userDto.setDateCreation(users.getDateCreation());
       userDto.setRaisonSociale(users.getRaisonSociale());
       userDto.setIdentityVerify(users.isIdentyVerify());
       userDto.setCguAcceptation(users.isCguAcceptation());
       userDto.setCguAcceptationDate(users.getDateCguAcceptation());
       userDto.setIdType(users.getIdType());
       userDto.setIdNumber(users.getIdNumber());
       userDto.setAdditionalIdType(users.getAdditionalIdType());
       userDto.setAdditionalIdNumber(users.getAdditionalIdNumber());
       wallets.forEach(wallet -> walletDto.add(WalletMapper.mapWalletToWalletUserDto(wallet)));
       userDto.setWallets(walletDto);

       return userDto;
    }

}
