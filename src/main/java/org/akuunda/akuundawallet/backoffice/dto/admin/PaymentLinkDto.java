package org.akuunda.akuundawallet.backoffice.dto.admin;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class PaymentLinkDto {
    private String id;
    private String merchantId;
    private String merchantName;
    private String title;
    private String description;
    private String slug;
    private String url;
    private Double amount;
    private String currency;
    private String status;
    private Integer maxUses;
    private Integer currentUses;
    private Double totalCollected;
    private String expiresAt;
    private List<CustomFieldDto> customFields;
    private String successUrl;
    private String cancelUrl;
    private String webhookUrl;
    private List<String> allowedMethods;
    private String createdAt;
    private String updatedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CustomFieldDto {
        private String name;
        private String type;
        private Boolean required;
    }
}
