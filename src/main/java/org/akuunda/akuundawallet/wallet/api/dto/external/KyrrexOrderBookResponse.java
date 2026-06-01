package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class KyrrexOrderBookResponse {

    private String pair;
    private List<List<BigDecimal>> bids;
    private List<List<BigDecimal>> asks;
    private Long timestamp;

    public KyrrexOrderBookResponse() {}

    public String getPair() { return pair; }
    public void setPair(String pair) { this.pair = pair; }

    public List<List<BigDecimal>> getBids() { return bids; }
    public void setBids(List<List<BigDecimal>> bids) { this.bids = bids; }

    public List<List<BigDecimal>> getAsks() { return asks; }
    public void setAsks(List<List<BigDecimal>> asks) { this.asks = asks; }

    public Long getTimestamp() { return timestamp; }
    public void setTimestamp(Long timestamp) { this.timestamp = timestamp; }
}
