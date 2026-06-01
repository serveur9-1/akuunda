package org.akuunda.akuundawallet.wallet.api.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import jakarta.persistence.*;
import lombok.*;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Date;

@Entity
@Builder
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIdentityInfo(
        generator = ObjectIdGenerators.PropertyGenerator.class,
        property = "id")
public class Wallet implements Serializable {

    @Serial
    private static final long serialVersionUID = 1027655L;

    @Id
    private String id;
    private String address;
    private String walletType;
    private String secretType;
    private Date createdAt;
    private Boolean archived;
    private String alias;
    private String description;
    private Boolean isPrimary;
    private Boolean hasCustomPin;
    private double balance;
    private double deviseBalance;
    private double gasBalance;
    private boolean custodial;
    private String symbol;
    private String gasSymbol;
    private String rawBalance;
    private String rawGasBalance;
    private int decimals;
    @JsonBackReference
    @ManyToOne(fetch = FetchType.EAGER)
    private CountryCurrency currency;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(nullable = true)
    private Users users;

    /**
     * Taux d'achat du partenaire sauvegardé lors du dernier dépôt
     * Utilisé pour convertir le solde USDC en devise locale pour l'affichage
     * Format : 1 USD = lastBuyRate XOF (ex: 588.08)
     */
    @Column(name = "last_buy_rate")
    private Double lastBuyRate;

    /**
     * Date de dernière mise à jour du taux d'achat
     */
    @Column(name = "last_buy_rate_updated_at")
    private LocalDateTime lastBuyRateUpdatedAt;

    /**
     * Partenaire utilisé pour le dernier dépôt (yellowcard ou guardarian)
     */
    @Column(name = "last_provider")
    private String lastProvider;

    /**
     * Devise associée au taux d'achat sauvegardé
     * Permet de vérifier que le taux sauvegardé correspond bien à la devise du wallet
     */
    @Column(name = "last_buy_rate_currency")
    private String lastBuyRateCurrency;
}
