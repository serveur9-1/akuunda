package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class MerchantTransactionResponse {
    
    @JsonProperty("fromDate")
    private LocalDateTime fromDate;
    
    @JsonProperty("toDate")
    private LocalDateTime toDate;
    
    @JsonProperty("txs")
    private List<MerchantTransaction> txs;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MerchantTransaction {
        @JsonProperty("status")
        private String status; // "pending" | "failed" | "finished"
        
        @JsonProperty("paid")
        private String paid; // e.g. "1 BTC"
        
        @JsonProperty("received")
        private String received; // e.g. "100000 EUR"
        
        @JsonProperty("merchant_oid")
        private String merchantOid; // e.g. "1234" - Utilise camelCase en Java mais mappe depuis merchant_oid
        
        @JsonProperty("transaction_group_id")
        private String transactionGroupId;
        
        @JsonProperty("reference")
        private String reference;
        
        @JsonProperty("creationDate")
        private LocalDateTime creationDate;
        
        @JsonProperty("lastUpdate")
        private LocalDateTime lastUpdate;
    }
}

