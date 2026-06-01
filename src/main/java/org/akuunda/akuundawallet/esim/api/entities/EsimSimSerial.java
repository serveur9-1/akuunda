package org.akuunda.akuundawallet.esim.api.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "esim_sim_serials")
@Getter
@Setter
@NoArgsConstructor
public class EsimSimSerial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String simSerial;

    @Column
    private String msisdn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EsimSimSerialStatus status = EsimSimSerialStatus.AVAILABLE;

    @Column
    private String assignedUserId;

    @Column
    private String lastProductId;

    @Column
    private String lastSubscriptionId;

    /** IDs de toutes les souscriptions de cet utilisateur, séparés par des virgules. */
    @Column(columnDefinition = "TEXT")
    private String subscriptionIds;

    public EsimSimSerial(String simSerial) {
        this.simSerial = simSerial;
        this.status = EsimSimSerialStatus.AVAILABLE;
    }

    public EsimSimSerial(String simSerial, String msisdn) {
        this.simSerial = simSerial;
        this.msisdn = msisdn;
        this.status = EsimSimSerialStatus.AVAILABLE;
    }
}
