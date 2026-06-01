package org.akuunda.akuundawallet.backoffice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Réponse standard backoffice : { "success": true, "data": T }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiSuccess<T> {
    private boolean success = true;
    private T data;
    private String message;

    public static <T> ApiSuccess<T> of(T data) {
        return new ApiSuccess<>(true, data, null);
    }

    public static <T> ApiSuccess<T> of(T data, String message) {
        return new ApiSuccess<>(true, data, message);
    }
}
