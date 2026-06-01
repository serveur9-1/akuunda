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
public class TransfiKycRequest {
    /**
     * Akuunda wallet username. If {@code userId} is blank, it is resolved from {@code transfi_users}.
     * Never forwarded to TransFi (WRITE_ONLY).
     */
    @Schema(description = "Username wallet Akuunda — résout userId via transfi_users si userId vide",
            accessMode = Schema.AccessMode.WRITE_ONLY)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String username;

    private String userId;
    private String redirectUrl;
}
