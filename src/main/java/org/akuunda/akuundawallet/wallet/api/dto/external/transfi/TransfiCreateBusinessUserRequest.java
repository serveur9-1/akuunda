package org.akuunda.akuundawallet.wallet.api.dto.external.transfi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TransfiCreateBusinessUserRequest {
    /**
     * Akuunda wallet username (phone). If present, the resulting TransFi userId is
     * persisted to {@code transfi_users} as a business mapping. Never sent to TransFi.
     */
    @Schema(description = "Username wallet Akuunda — persiste le mapping username ↔ TransFi userId (business, non transmis à TransFi)",
            accessMode = Schema.AccessMode.WRITE_ONLY)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String username;

    private String businessName;
    private String regNo;
    private String date;
    private String email;
    private String phone;
    private String phoneCode;
    private String country;
    private String countryOfResidence;
    private TransfiAddressDto address;
}
