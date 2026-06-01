package org.akuunda.akuundawallet.wallet.service.mapper;

import lombok.experimental.UtilityClass;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.dto.WalletUserDto;
import org.akuunda.akuundawallet.wallet.api.dto.external.Result;
import org.akuunda.akuundawallet.wallet.api.entities.CountryCurrency;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;

import java.math.BigDecimal;
import java.math.RoundingMode;

@UtilityClass
public class WalletMapper {

    public static WalletUserDto mapWalletToWalletUserDto(Wallet wallet) {
        final var walletUserDto = new WalletUserDto();
        walletUserDto.setWalletType(wallet.getWalletType());
        walletUserDto.setId(wallet.getId());
        
        // ⚠️ IMPORTANT : Arrondir UNIQUEMENT pour l'affichage, pas pour les calculs
        // Les montants en base de données (wallet.balance et wallet.deviseBalance) gardent leur précision complète
        // L'arrondi est appliqué uniquement lors du mapping vers le DTO pour l'affichage dans l'application mobile
        
        // Arrondir le balance (deviseBalance) à 2 décimales pour l'affichage
        // deviseBalance = montant en devise locale (EUR, XOF, etc.) - affiché sur la capture
        double balance = roundToTwoDecimals(wallet.getDeviseBalance());
        walletUserDto.setBalance(balance); // Montant converti en devise locale (EUR, XOF, etc.)
        
        // Arrondir le userBalance (balance USDC) à 2 décimales pour l'affichage
        // balance = montant en USDC (stocké en base de données avec précision complète)
        double userBalance = roundToTwoDecimals(wallet.getBalance());
        walletUserDto.setUserBalance(userBalance); // Solde USDC brut
        
        walletUserDto.setDescription(wallet.getDescription());
        walletUserDto.setCreatedAt(wallet.getCreatedAt());
        walletUserDto.setCurrencyCode(wallet.getCurrency().getCurrencyCode());
        walletUserDto.setCountryCode(wallet.getCurrency().getCountryCode());
        walletUserDto.setContinentName(wallet.getCurrency().getContinentName());
        walletUserDto.setCountryName(wallet.getCurrency().getCountryName());
        walletUserDto.setWalletAddress(wallet.getAddress());
        return walletUserDto;
    }

    /**
     * Arrondit un montant à 2 décimales pour l'affichage.
     * 
     * @param value Montant à arrondir
     * @return Montant arrondi à 2 décimales
     */
    private static double roundToTwoDecimals(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return BigDecimal.valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    public static Wallet mapResultToWallet(Result result, CountryCurrency currency, Users user) {
        final var wallet = new Wallet();
        wallet.setCurrency(currency);
        wallet.setBalance(result.getBalance().getBalance());
        wallet.setDeviseBalance(convertToDevise(result.getBalance().getBalance()));
        wallet.setWalletType(result.getWalletType());
        wallet.setId(result.getId());
        wallet.setAddress(result.getAddress());
        wallet.setArchived(result.isArchived());
        wallet.setDescription(result.getDescription());
        wallet.setUsers(user);
        wallet.setWalletType(result.getWalletType());
        wallet.setDecimals(result.getBalance().getDecimals());
        wallet.setGasBalance(result.getBalance().getGasBalance());
        wallet.setSecretType(result.getBalance().getSecretType());
        wallet.setRawBalance(result.getBalance().getRawBalance());
        wallet.setRawGasBalance(result.getBalance().getRawGasBalance());
        wallet.setIsPrimary(result.isPrimary());
        wallet.setHasCustomPin(result.isHasCustomPin());
        wallet.setSymbol(result.getBalance().getSymbol());
        wallet.setCustodial(result.isCustodial());
        return wallet;
    }

    private static double convertToDevise(int balance) {
        // call service to convert to devise
        return Double.parseDouble(String.valueOf(balance));
    }
}
