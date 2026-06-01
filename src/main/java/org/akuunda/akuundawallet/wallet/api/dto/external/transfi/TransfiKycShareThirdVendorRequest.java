package org.akuunda.akuundawallet.wallet.api.dto.external.transfi;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.web.multipart.MultipartFile;

/**
 * Body de POST /v3/kyc/share/third-vendor (multipart/form-data).
 *
 * Champs obligatoires côté TransFi : userId, country, nationality, idDocIssuerCountry,
 * idDocType, dob, gender, idDocUserName, idDocExpiryDate, street, city, postalCode,
 * idDocFrontSide, idDocBackSide, selfie.
 *
 * Optionnels : phoneNo, state.
 *
 * - idDocType : PASSPORT | ID_CARD | DRIVERS
 * - gender    : male | female
 * - dob, idDocExpiryDate : YYYY-MM-DD
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransfiKycShareThirdVendorRequest {
    private String userId;
    private String country;
    private String nationality;
    private String idDocIssuerCountry;
    private String idDocType;
    private String dob;
    private String gender;
    private String idDocUserName;
    private String idDocExpiryDate;
    private String phoneNo;
    private String street;
    private String city;
    private String state;
    private String postalCode;

    private MultipartFile idDocFrontSide;
    private MultipartFile idDocBackSide;
    private MultipartFile selfie;
}
