package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;

@JsonIgnoreProperties(ignoreUnknown = true)
public class KyrrexTickerResponse {

    private String pair;

    @JsonProperty("last_price")
    private BigDecimal lastPrice;

    @JsonProperty("high_24h")
    private BigDecimal high24h;

    @JsonProperty("low_24h")
    private BigDecimal low24h;

    @JsonProperty("volume_24h")
    private BigDecimal volume24h;

    @JsonProperty("price_change_24h")
    private BigDecimal priceChange24h;

    @JsonProperty("price_change_percent_24h")
    private BigDecimal priceChangePercent24h;

    private BigDecimal bid;
    private BigDecimal ask;

    public KyrrexTickerResponse() {}

    public String getPair() { return pair; }
    public void setPair(String pair) { this.pair = pair; }

    public BigDecimal getLastPrice() { return lastPrice; }
    public void setLastPrice(BigDecimal lastPrice) { this.lastPrice = lastPrice; }

    public BigDecimal getHigh24h() { return high24h; }
    public void setHigh24h(BigDecimal high24h) { this.high24h = high24h; }

    public BigDecimal getLow24h() { return low24h; }
    public void setLow24h(BigDecimal low24h) { this.low24h = low24h; }

    public BigDecimal getVolume24h() { return volume24h; }
    public void setVolume24h(BigDecimal volume24h) { this.volume24h = volume24h; }

    public BigDecimal getPriceChange24h() { return priceChange24h; }
    public void setPriceChange24h(BigDecimal priceChange24h) { this.priceChange24h = priceChange24h; }

    public BigDecimal getPriceChangePercent24h() { return priceChangePercent24h; }
    public void setPriceChangePercent24h(BigDecimal priceChangePercent24h) { this.priceChangePercent24h = priceChangePercent24h; }

    public BigDecimal getBid() { return bid; }
    public void setBid(BigDecimal bid) { this.bid = bid; }

    public BigDecimal getAsk() { return ask; }
    public void setAsk(BigDecimal ask) { this.ask = ask; }
}
