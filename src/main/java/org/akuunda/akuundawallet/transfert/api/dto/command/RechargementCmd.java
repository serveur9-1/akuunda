package org.akuunda.akuundawallet.transfert.api.dto.command;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Classe représentant une commande de rechargement.
 * Elle contient les informations nécessaires pour effectuer un rechargement.
 */
@Data
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class RechargementCmd {

    @JsonProperty("amount")
    private String amount;

    @JsonProperty("devise")
    private String devise;

    @JsonProperty("username")
    private String username;

    @JsonProperty("type")
    private String type;
}
