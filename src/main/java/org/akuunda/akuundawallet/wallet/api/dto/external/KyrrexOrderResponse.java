package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public class KyrrexOrderResponse {

    private String id;
    private String pair;
    private String side;
    private String type;
    private String status;
    private BigDecimal amount;
    private BigDecimal price;

    @JsonProperty("filled_amount")
    private BigDecimal filledAmount;

    @JsonProperty("remaining_amount")
    private BigDecimal remainingAmount;

    @JsonProperty("average_price")
    private BigDecimal averagePrice;

    @JsonProperty("stop_price")
    private BigDecimal stopPrice;

    @JsonProperty("time_in_force")
    private String timeInForce;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("updated_at")
    private String updatedAt;

    public KyrrexOrderResponse() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getPair() { return pair; }
    public void setPair(String pair) { this.pair = pair; }

    public String getSide() { return side; }
    public void setSide(String side) { this.side = side; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getFilledAmount() { return filledAmount; }
    public void setFilledAmount(BigDecimal filledAmount) { this.filledAmount = filledAmount; }

    public BigDecimal getRemainingAmount() { return remainingAmount; }
    public void setRemainingAmount(BigDecimal remainingAmount) { this.remainingAmount = remainingAmount; }

    public BigDecimal getAveragePrice() { return averagePrice; }
    public void setAveragePrice(BigDecimal averagePrice) { this.averagePrice = averagePrice; }

    public BigDecimal getStopPrice() { return stopPrice; }
    public void setStopPrice(BigDecimal stopPrice) { this.stopPrice = stopPrice; }

    public String getTimeInForce() { return timeInForce; }
    public void setTimeInForce(String timeInForce) { this.timeInForce = timeInForce; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(String updatedAt) { this.updatedAt = updatedAt; }
}
