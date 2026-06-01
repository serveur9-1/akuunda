package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Élément de liste d'une clé API marchand (sans secret).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Clé API marchand (vue liste, sans secret)")
public class MerchantKeyItem {
    private Long id;
    private String name;
    private String mode;
    private String apiKey;
    private Boolean active;
    private String webhookUrl;
    private String returnUrl;
    private String cancelUrl;

    @Schema(description = "Username marchand Akuunda Pay (login, mapping providers).", example = "002250759146858")
    private String merchantUsername;

    @Schema(description = "Identifiant technique marchand (Keycloak sub / users.user_id).",
            example = "8d3a1c52-7e91-4dbb-b9e0-3fa1c2c1d0ff")
    private String merchantUserId;

    @Schema(description = "Slug marchand de référence (lien permanent par défaut)")
    private String merchantSlug;

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
}
