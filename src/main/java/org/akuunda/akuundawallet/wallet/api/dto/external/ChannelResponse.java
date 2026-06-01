package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.ZonedDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChannelResponse {

    private List<Channel> channels;
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Channel {
        private long max;
        private String currency;
        private String countryCurrency;
        private String status;
        private int feeLocal;
        private String vendorId;
        private String country;
        private int feeUSD;
        private String apiStatus;
        private int estimatedSettlementTime;
        private String id;
        private int successThreshold;
        private Integer widgetMin;
        private String commercialStatus;
        private String createdAt;
        private String widgetStatus;
        private long min;
        private String channelType;
        private String rampType;
        private Integer widgetMax;
        private String updatedAt;
        private String settlementType;
        private Integer apiMax;
        private Integer apiMin;
    }
}
