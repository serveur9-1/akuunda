package org.akuunda.akuundawallet.wallet.api.dto;

import lombok.Data;

@Data
public class OnrampMoneyResponse {
    private String status;
    private String message;
    private DataResponse data;

    @Data
    public static class DataResponse {
        private String link;
        private String urlHash;
    }
}
