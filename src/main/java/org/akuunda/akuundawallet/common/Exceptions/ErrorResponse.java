package org.akuunda.akuundawallet.common.Exceptions;

import lombok.*;

import java.util.ArrayList;

@Data
@Builder
public class ErrorResponse {

    public boolean success;
    public ArrayList<Error> errors;

    public String traceCode;

    public ErrorResponse() {
    }

    public ErrorResponse(boolean success, ArrayList<Error> errors, String traceCode) {
        this.success = success;
        this.errors = errors;
        this.traceCode = traceCode;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public ArrayList<Error> getErrors() {
        return errors;
    }

    public void setErrors(ArrayList<Error> errors) {
        this.errors = errors;
    }

    public String getTraceCode() {
        return traceCode;
    }

    public void setTraceCode(String traceCode) {
        this.traceCode = traceCode;
    }

    @Data
    @Builder
    public static class Error {
        public String code;
        public String message;
        public String field;

        public Error() {
        }

        public Error(String code, String message, String field) {
            this.code = code;
            this.message = message;
            this.field = field;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getField() {
            return field;
        }

        public void setField(String field) {
            this.field = field;
        }
    }

    @Override
    public String toString() {
        return "ErrorResponse{" +
                "success=" + success +
                ", errors=" + errors +
                ", traceCode='" + traceCode + '\'' +
                '}';
    }


}
