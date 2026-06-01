package org.akuunda.akuundawallet.wallet.api.dto;

import lombok.*;

import java.util.Date;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class WalletUserDto {

    private String id;
    private String walletType;
    private Date createdAt;
    private String description;
    private double balance; // Montant converti en devise locale (deviseBalance)
    private Double userBalance; // Solde USDC brut (balance en USDC)
    private boolean merchandAkunnda;
    private String currencyCode;
    private String countryCode;
    private String continentName;
    private String countryName;
    private String walletAddress;

}
