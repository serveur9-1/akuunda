package org.akuunda.akuundawallet.wallet.api.entities;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import jakarta.persistence.*;
import jakarta.ws.rs.DefaultValue;
import lombok.*;

import java.io.Serial;
import java.io.Serializable;

@Entity
@Table(name = "country_currency")
@Builder
@Getter
@Setter
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIdentityInfo(
        generator = ObjectIdGenerators.PropertyGenerator.class,
        property = "id")
public class CountryCurrency implements Serializable {

    @Serial
    private static final long serialVersionUID = 11197655L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;
    private String countryCode;
    private String countryName;
    private String currencyCode;
    private int callingCode;
    private String capital;
    private String continentName;
    
    @Column(name = "flag_url", length = 500)
    private String flagUrl; // URL du drapeau du pays (PNG ou SVG)

    @DefaultValue("true")
    @Column(name = "is_activated", columnDefinition = "boolean default true")
    private boolean isActivated;

}
