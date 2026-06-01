package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class KyrrexCurrencyResponse {

    private String code;
    private String name;
    private String type;
    private Integer precision;

    @JsonProperty("min_deposit")
    private BigDecimal minDeposit;

    @JsonProperty("min_withdrawal")
    private BigDecimal minWithdrawal;

    @JsonProperty("withdrawal_fee")
    private BigDecimal withdrawalFee;

    private List<String> networks;
    private boolean active;

    public KyrrexCurrencyResponse() {}

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public Integer getPrecision() { return precision; }
    public void setPrecision(Integer precision) { this.precision = precision; }

    public BigDecimal getMinDeposit() { return minDeposit; }
    public void setMinDeposit(BigDecimal minDeposit) { this.minDeposit = minDeposit; }

    public BigDecimal getMinWithdrawal() { return minWithdrawal; }
    public void setMinWithdrawal(BigDecimal minWithdrawal) { this.minWithdrawal = minWithdrawal; }

    public BigDecimal getWithdrawalFee() { return withdrawalFee; }
    public void setWithdrawalFee(BigDecimal withdrawalFee) { this.withdrawalFee = withdrawalFee; }

    public List<String> getNetworks() { return networks; }
    public void setNetworks(List<String> networks) { this.networks = networks; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
