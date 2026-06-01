package org.akuunda.akuundawallet.wallet.api.dto;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class CountryDto {

    private int id;
    private String countryCode;
    private String countryName;
    private int callingCode;
    private String capital;
    private String continentName;

}
