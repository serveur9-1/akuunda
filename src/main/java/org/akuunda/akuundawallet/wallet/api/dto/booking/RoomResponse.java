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
@Schema(description = "Réponse contenant les informations d'une chambre")
public class RoomResponse {

    private Long id;
    private String name;
    private String description;
    private Double pricePerNight;
    private String currency;
    private Integer maxGuests;
    private List<String> amenities;
    private List<String> images;
    private Boolean isAvailable;
    private Integer totalRooms;
}
