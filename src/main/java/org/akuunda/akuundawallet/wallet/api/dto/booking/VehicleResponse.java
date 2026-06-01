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
@Schema(description = "Réponse contenant les informations d'un véhicule")
public class VehicleResponse {

    private Long id;
    private String type;
    private String brand;
    private String model;
    private String plateNumber;
    private Integer maxPassengers;
    private Integer maxLuggage;
    private Double pricePerKm;
    private Double basePrice;
    private String currency;
    private List<String> images;
    private Boolean hasAirConditioning;
    private Boolean hasWifi;
    private Boolean isAvailable;
}
