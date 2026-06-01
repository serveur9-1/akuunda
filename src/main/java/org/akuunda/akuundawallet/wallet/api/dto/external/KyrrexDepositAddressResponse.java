package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class KyrrexDepositAddressResponse {

    private String address;
    private String currency;
    private String network;
    private String memo;

    @JsonProperty("destination_tag")
    private String destinationTag;

    public KyrrexDepositAddressResponse() {}

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getNetwork() { return network; }
    public void setNetwork(String network) { this.network = network; }

    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }

    public String getDestinationTag() { return destinationTag; }
    public void setDestinationTag(String destinationTag) { this.destinationTag = destinationTag; }
}
