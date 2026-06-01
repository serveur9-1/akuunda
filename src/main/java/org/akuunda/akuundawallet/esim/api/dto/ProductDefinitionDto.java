package org.akuunda.akuundawallet.esim.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProductDefinitionDto {

    private String productId;
    private String productCategory;
    private JsonNode description;

    private ValidityPeriodDto validityPeriod;

    private List<String> countryList;
    private List<String> parentProductIds;

    private AllowancesDto allowances;
}

