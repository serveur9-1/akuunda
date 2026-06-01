package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class RecipientDto {

    private String name;
    private String phone;
    private String email;
    private String country;
    private String address;
    private String dob;
    private String idNumber;
    private String idType;
    private String additionalIdType;
    private String additionalIdNumber;

    // Institution fields
    private String businessName;
    private String businessId;
}
