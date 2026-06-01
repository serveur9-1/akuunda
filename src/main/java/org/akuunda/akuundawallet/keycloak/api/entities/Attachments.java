package org.akuunda.akuundawallet.keycloak.api.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
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
public class Attachments implements Serializable {

    @Id
    @GeneratedValue(generator = "uuid")
    private String id;
    private String fileName;
    private String fileExtension;
    private String typePiece;
    private String dateCreate;
    private byte[] fileData;


    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY, cascade=CascadeType.REMOVE)
    private Users users;
}
