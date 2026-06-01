package org.akuunda.akuundawallet.transfert.api.dto.command;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class TransactionCommand {

    private String statuses;
    private String userId;
    private int page;
    private int size;
    private boolean includeUsers;
    private String sortOn;
    private String sortOrder;

    public TransactionCommand(String statuses, String userId, int page, int size, boolean includeUsers, String sortOn, String sortOrder) {
        this.statuses = statuses;
        this.userId = userId;
        this.page = page;
        this.size = size;
        this.includeUsers = includeUsers;
        this.sortOn = sortOn;
        this.sortOrder = sortOrder;
    }


}
