package org.akuunda.akuundawallet.esim.api.dto;

import lombok.Data;

import java.util.List;

@Data
public class ProductsResponseDto {

    private String cos;
    private String currentLocale;
    private List<ProductDto> products;
    private List<LinkDto> links;
}
