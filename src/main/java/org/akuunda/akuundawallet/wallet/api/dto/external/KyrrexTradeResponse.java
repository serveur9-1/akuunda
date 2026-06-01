package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public class KyrrexTradeResponse {

    private String id;
    private String pair;
    private String side;
    private BigDecimal price;
    private BigDecimal amount;
    private BigDecimal fee;

    @JsonProperty("fee_currency")
    private String feeCurrency;

    @JsonProperty("order_id")
    private String orderId;

    @JsonProperty("created_at")
    private String createdAt;

    public KyrrexTradeResponse() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPair() { return pair; }
    public void setPair(String pair) { this.pair = pair; }

    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getFee() { return fee; }
    public void setFee(BigDecimal fee) { this.fee = fee; }

    public String getFeeCurrency() { return feeCurrency; }
    public void setFeeCurrency(String feeCurrency) { this.feeCurrency = feeCurrency; }

    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
