package org.akuunda.akuundawallet.keycloak.api.entities;

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;
import java.sql.Timestamp;

@Entity
@Getter
@Setter
@Table(name = "otp")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Otp implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String otpCode;

    private Timestamp dateCreate;
    private Timestamp expiredDate;
    private Timestamp dateValidated;

    private String status;

    private String userName;

}
