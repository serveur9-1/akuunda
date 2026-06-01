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
@Schema(description = "Réponse contenant les informations d'un hôtel")
public class HotelResponse {

    private Long id;
    private String name;
    private String description;
    private String address;
    private String city;
    private String country;
    private Double latitude;
    private Double longitude;
    private String imageUrl;
    private List<String> images;
    private Double rating;
    private Integer reviewCount;
    private List<String> amenities;
    private List<RoomResponse> rooms;
    private String phone;
    private String email;
    private Boolean isVerified;
    private String ownerUsername;
}
