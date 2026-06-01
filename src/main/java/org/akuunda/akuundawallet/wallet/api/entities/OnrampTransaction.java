package org.akuunda.akuundawallet.wallet.api.entities;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Data
@Table(name = "onramp_transaction")
public class OnrampTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String coinId;
    private String chainId;
    private String network;
    private Double fiatAmount;
    private String fiatType;
    private String type;
    private String walletId;
    private String userName;
    private String codePays;
    private String paymentMethod;
    private String phoneNumber;
    private String lang;
    private String merchantRecognitionId;
    private String link;
    private String urlHash;
    private LocalDateTime createdAt;
}
