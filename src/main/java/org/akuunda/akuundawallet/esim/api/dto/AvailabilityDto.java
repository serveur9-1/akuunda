package org.akuunda.akuundawallet.esim.api.dto;

import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class AvailabilityDto {

    private boolean available;
    private OffsetDateTime startDate;
    private OffsetDateTime endDate;
}
