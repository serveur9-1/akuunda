package org.akuunda.akuundawallet.keycloak.api.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import org.akuunda.akuundawallet.wallet.api.entities.CountryCurrency;
import java.io.Serial;
import java.io.Serializable;
import java.sql.Timestamp;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Builder
public class Users implements Serializable {

    @Serial
    private static final long serialVersionUID = 1097655L;

    @Id
    private String userId;
    @NotNull(message = "Username cannot be null")
    String username;
    @NotNull(message = "Mobile phone cannot be null")
    String mobilePhone;
    String password;
    String email;
    String firstname;
    String reference;
    String lastname;
    private Timestamp createdAt;
    private String dateNaissance;
    @NotNull(message = "Type Compte cannot be null")
    private String accountType;
    private String siret;
    private String dateCreation;
    private String raisonSociale;
    private String adresse;
    private boolean emailVerified;
    private boolean enabled;

    @NotNull(message = "Identify Verify cannot be null")
    @Column(name = "identy_verify", columnDefinition = "boolean default false")
    private boolean identyVerify = false;

    @NotNull(message = "Cgu acceptation cannot be null")
    @Column(name = "cgu_acceptation", columnDefinition = "boolean default false")
    private boolean cguAcceptation = false;

    private Timestamp dateCguAcceptation;

    private String facebookId; // to user facebook
    private String googleId;  // to user google

    @Column(name = "apple_id")
    private String appleId;

    /** KYC — document d'identité */
    private String idNumber;
    private String idType;
    private String additionalIdNumber;
    private String additionalIdType;

    /**
     * Langue préférée de l'utilisateur : "fr" ou "en".
     * Utilisée pour les notifications push.
     * Valeur par défaut : "fr".
     */
    @Column(name = "preferred_language", length = 5)
    @Builder.Default
    private String preferredLanguage = "fr";

    @JsonBackReference
    // EAGER conservé : open-in-view=false en prod — accès hors transaction dans les contrôleurs.
    @ManyToOne(fetch = FetchType.EAGER, cascade = CascadeType.REMOVE)
    private CountryCurrency countryCurrency;

}
