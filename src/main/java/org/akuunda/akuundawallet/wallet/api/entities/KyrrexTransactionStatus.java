package org.akuunda.akuundawallet.wallet.api.entities;

public enum KyrrexTransactionStatus {
    INITIATED,
    PENDING,
    PROCESSING,
    SUCCESS,
    FAILED,
    CANCELLED,
    EXPIRED,
    UNKNOWN;

    public static KyrrexTransactionStatus fromKyrrexStatus(String kyrrexStatus) {
        if (kyrrexStatus == null) return UNKNOWN;
        return switch (kyrrexStatus.toLowerCase().trim()) {
            case "submitted", "new", "created", "initiated" -> INITIATED;
            case "pending", "wait", "confirming", "processing_by_provider" -> PENDING;
            case "processing", "accepted" -> PROCESSING;
            case "done", "completed", "succeed", "success", "collected" -> SUCCESS;
            case "failed", "rejected", "errored", "error" -> FAILED;
            case "cancelled", "canceled", "cancel" -> CANCELLED;
            case "expired" -> EXPIRED;
            default -> UNKNOWN;
        };
    }

    public boolean isTerminal() {
        return this == SUCCESS || this == FAILED || this == CANCELLED || this == EXPIRED;
    }
}
