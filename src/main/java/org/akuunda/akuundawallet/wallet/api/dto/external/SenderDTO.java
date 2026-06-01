package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SenderDTO {
    private String name;
    private String country;
    private String address;
    private String dob; // Format "dd/MM/yyyy" - peut être changé en `LocalDate` si besoin
    private String email;
    private String idNumber;
    private String idType;
    private String phone;
}
