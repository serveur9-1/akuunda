package org.akuunda.akuundawallet.wallet.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Requête pour créer un lien de paiement permanent")
public class CreatePermanentLinkRequest {

    @NotBlank(message = "Le slug est obligatoire")
    @Pattern(regexp = "^[a-z0-9]+(-[a-z0-9]+)*$", message = "Le slug doit être URL-safe (ex: boutique-mama-coco)")
    @Schema(description = "Slug URL-safe unique du marchand", example = "boutique-mama-coco", required = true)
    private String merchantSlug;

    @NotBlank(message = "La description est obligatoire")
    @Schema(description = "Description/libellé du lien permanent", example = "Paiement boutique", required = true)
    private String description;

    @Schema(description = "Montant fixe (null = montant libre choisi par le payeur)", example = "5000.0", required = false)
    private Double amount;

    @Schema(description = "Devise (null = devise choisie par le payeur)", example = "XOF", required = false)
    private String currency;
}
