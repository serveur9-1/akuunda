package org.akuunda.akuundawallet.transfert.api.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;

@Entity
@Table(name = "media")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIdentityInfo(
        generator = ObjectIdGenerators.PropertyGenerator.class,
        property = "id")
public class Media implements Serializable {

    @Id
    private String id;
    private String mediaType;
    private String mediaValue;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    private Contract contract;
}
