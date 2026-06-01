package org.akuunda.akuundawallet.transfert.api.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.*;
import java.io.Serial;
import java.io.Serializable;

@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transactions implements Serializable {

    @Serial
    private static final long serialVersionUID = 1027655L;

    @Id
    private String id;
    private String type;
    private String status;
    private String tokenAddress;
    private String walletId;
    private String replacedBy;
    private String originId;
    private double transactionAmount;
    private String transactionHash;
    private String expiresAt;
    private String createdAt;

}
