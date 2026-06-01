package org.akuunda.akuundawallet.wallet.api.dto.partner;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

/**
 * Requête d'assignation dynamique des bénéficiaires par l'admin après paiement.
 * Utilisé pour le mode DYNAMIC_ASSIGNMENT.
 */
@Data
public class AssignPaymentBeneficiariesRequest {

    /** Username de l'admin qui effectue l'assignation (pour audit) */
    @NotBlank(message = "adminUsername requis")
    private String adminUsername;

    /**
     * Liste des professionnels qui effectueront le service.
     * La somme des percentage doit être exactement 1.0.
     */
    @NotEmpty(message = "Au moins un bénéficiaire est requis")
    @Valid
    private List<BeneficiaryRequest> beneficiaries;
}
