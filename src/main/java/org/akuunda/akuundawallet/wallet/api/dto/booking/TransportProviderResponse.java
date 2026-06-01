package org.akuunda.akuundawallet.wallet.api.dto.booking;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Réponse contenant les informations d'un prestataire de transport")
public class TransportProviderResponse {

    private Long id;
    private String name;
    private String description;
    private String photoUrl;
    private String phone;
    private String email;
    private String city;
    private String country;
    private Double rating;
    private Integer reviewCount;
    private Integer completedTrips;
    private List<String> serviceAreas;
    private List<String> serviceTypes;
    private List<VehicleResponse> vehicles;
    private Boolean isVerified;
    private Boolean isAvailable;
    private String ownerUsername;
}
