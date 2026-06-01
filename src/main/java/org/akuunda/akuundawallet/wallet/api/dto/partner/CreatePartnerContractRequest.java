package org.akuunda.akuundawallet.wallet.api.dto.partner;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.util.List;

@Data
public class CreatePartnerContractRequest {

    @NotBlank(message = "Le nom du contrat est requis")
    private String name;

    private String description;

    @NotBlank(message = "Le type de service est requis")
    private String serviceType;

    /**
     * Mode de libération des fonds :
     * QR_CODE            – scan QR par le provider (présentiel)
     * MANUAL             – appel API explicite par le partenaire / opérateur
     * WEBHOOK            – callback entrant d'un système tiers (HMAC vérifié)
     * REMOTE_CONFIRMATION – lien/push envoyé au client → il confirme la réception
     * OTP                – code 6 chiffres envoyé au client → fourni au provider
     * DUAL_CONFIRMATION  – client ET provider doivent confirmer
     * TIME_BASED         – libération auto après N heures (configurer autoReleaseHours)
     * DISPUTE_WINDOW     – libération auto après fenêtre de dispute (configurer disputeWindowHours)
     * GEOLOCATION        – confirmation de présence GPS du client
     * ADMIN_APPROVAL     – approbation manuelle par un admin Akuunda
     */
    @Pattern(regexp = "QR_CODE|MANUAL|WEBHOOK|REMOTE_CONFIRMATION|OTP|DUAL_CONFIRMATION|TIME_BASED|DISPUTE_WINDOW|GEOLOCATION|ADMIN_APPROVAL|DYNAMIC_ASSIGNMENT",
             message = "triggerType invalide")
    private String triggerType = "QR_CODE";

    /**
     * ONE_TIME_ONLY  – pas de lien permanent public, le partner initie chaque paiement via API
     * PERMANENT_ONLY – lien permanent réutilisable (qr.akuunda-pay.io/m/{contractCode})
     * BOTH (défaut)  – les deux disponibles
     */
    @Pattern(regexp = "ONE_TIME_ONLY|PERMANENT_ONLY|BOTH",
             message = "paymentLinkType invalide (ONE_TIME_ONLY, PERMANENT_ONLY, BOTH)")
    private String paymentLinkType = "BOTH";

    @DecimalMin(value = "0.0", message = "Le taux de pénalité doit être >= 0")
    @DecimalMax(value = "1.0", message = "Le taux de pénalité doit être <= 1.0")
    private Double cancellationPenaltyRate = 0.0;

    @Min(0)
    private Integer freeCancellationHours = 24;

    /** TIME_BASED / DISPUTE_WINDOW : heures avant libération automatique */
    @Min(1)
    private Integer autoReleaseHours;

    /** DISPUTE_WINDOW : fenêtre en heures pendant laquelle le client peut contester */
    @Min(1)
    private Integer disputeWindowHours;

    /** GEOLOCATION : coordonnées attendues du lieu de service */
    private Double expectedLatitude;
    private Double expectedLongitude;

    /** GEOLOCATION : rayon de tolérance en mètres (défaut 200m) */
    @Min(10)
    private Double geolocationRadiusMeters;

    @Valid
    private List<BeneficiaryRequest> beneficiaries;
}
