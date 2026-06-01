package org.akuunda.akuundawallet.keycloak.api.entities;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;


@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
@JsonIdentityInfo(
        generator = ObjectIdGenerators.PropertyGenerator.class,
        property = "id")
public class ReviewRequest implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String applicantId;
    private String inspectionId;
    private String correlationId;
    private String externalUserId;
    private String levelName;
    private String type;
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "review_result_id", referencedColumnName = "id")
    private ReviewResult reviewResult;
    private String reviewStatus;
    private String createdAtMs;
}
