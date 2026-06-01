package org.akuunda.akuundawallet.esim.api.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class EsimSimStockImportResult {

    private int total;
    private int inserted;
    private int updated;
    private int skipped;
    private List<String> errors = new ArrayList<>();

    public void addError(String error) {
        errors.add(error);
    }
}
