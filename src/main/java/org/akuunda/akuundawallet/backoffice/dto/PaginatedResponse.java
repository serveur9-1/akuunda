package org.akuunda.akuundawallet.backoffice.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Réponse paginée backoffice : { "data": [], "meta": { page, limit, total, ... } }
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PaginatedResponse<T> {
    private List<T> data;
    private PaginatedMeta meta;

    public static <T> PaginatedResponse<T> of(List<T> data, int page, int limit, long total) {
        int totalPages = limit > 0 ? (int) Math.ceil((double) total / limit) : 0;
        PaginatedMeta meta = new PaginatedMeta(page, limit, total, totalPages, page < totalPages, page > 1);
        return new PaginatedResponse<>(data, meta);
    }
}
