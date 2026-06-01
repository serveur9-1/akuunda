package org.akuunda.akuundawallet.transfert.api.entities;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.util.ArrayList;

@Entity
@Table(name = "contract")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonIdentityInfo(
        generator = ObjectIdGenerators.PropertyGenerator.class,
        property = "id")
public class Contract implements Serializable {

    @Id
    public String id;

    public String name;
    public String description;
    public String address;
    public String chain;
    public String symbol;
    public String externalUrl;
    public String image;

    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    @OneToMany(mappedBy = "contract", cascade = CascadeType.PERSIST,
            orphanRemoval = true, fetch = FetchType.LAZY)
    public ArrayList<Media> media;
    public String transactionHash;
    public String status;
    public String owner;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    public Storage storage;
    public String contractUri;

    @JsonBackReference
    @ManyToOne(fetch = FetchType.LAZY)
    public Royalties royalties;
    public String external_link;
}
