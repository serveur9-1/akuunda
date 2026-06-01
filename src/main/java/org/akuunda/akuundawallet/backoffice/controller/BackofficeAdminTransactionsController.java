package org.akuunda.akuundawallet.backoffice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.akuunda.akuundawallet.backoffice.dto.ApiSuccess;
import org.akuunda.akuundawallet.backoffice.dto.PaginatedResponse;
import org.akuunda.akuundawallet.backoffice.dto.admin.TransactionDto;
import org.akuunda.akuundawallet.wallet.api.dao.OperationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping(path = "/api/v1/admin/transactions", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Backoffice - Admin Transactions")
@RequiredArgsConstructor
public class BackofficeAdminTransactionsController {

    private final OperationRepository operationRepository;

    @GetMapping
    @Operation(summary = "Liste des transactions")
    public ResponseEntity<ApiSuccess<PaginatedResponse<TransactionDto>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search) {

        PageRequest pageable = PageRequest.of(
                Math.max(0, page - 1),
                Math.min(100, limit),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        String d = (type != null && !type.isBlank()) ? type : null;
        String s = (status != null && !status.isBlank()) ? status : null;
        String q = (search != null && !search.isBlank()) ? search.toLowerCase() : null;

        Page<org.akuunda.akuundawallet.wallet.api.entities.Operation> p;
        if (d != null && s != null && q != null)      p = operationRepository.findByDesignationAndStatusAndSearch(d, s, q, pageable);
        else if (d != null && s != null)              p = operationRepository.findByDesignationAndStatus(d, s, pageable);
        else if (d != null && q != null)              p = operationRepository.findByDesignationAndSearch(d, q, pageable);
        else if (s != null && q != null)              p = operationRepository.findByStatusAndSearch(s, q, pageable);
        else if (d != null)                           p = operationRepository.findByDesignationContainingIgnoreCase(d, pageable);
        else if (s != null)                           p = operationRepository.findByStatusIgnoreCase(s, pageable);
        else if (q != null)                           p = operationRepository.findBySearch(q, pageable);
        else                                          p = operationRepository.findAll(pageable);

        List<TransactionDto> list = p.getContent().stream()
                .map(op -> TransactionDto.builder()
                        .id(op.getId())
                        .reference(op.getOperationHash())
                        .designation(op.getDesignation())
                        .transactionType(op.getTransactionType())
                        .type(op.getType())
                        .status(op.getStatus())
                        .expediteur(op.getUsername())
                        .destinataire(op.getCounterpartDisplayName() != null
                                ? op.getCounterpartDisplayName()
                                : op.getCounterpartUsername())
                        .amount(op.getAmount())
                        .currency(op.getDevise())
                        .convertedAmount(op.getConvertedAmount())
                        .originalAmount(op.getOriginalAmount())
                        .originalCurrency(op.getOriginalDevise())
                        .providerAmount(op.getProviderAmount())
                        .providerCurrency(op.getProviderDevise())
                        .provider(op.getProvider())
                        .createdAt(op.getCreatedAt() != null ? op.getCreatedAt().toString() : null)
                        .updatedAt(op.getUpdatedAt() != null ? op.getUpdatedAt().toString() : null)
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiSuccess.of(
                PaginatedResponse.of(list, page, limit, p.getTotalElements())
        ));
    }

    @GetMapping("/stats")
    @Operation(summary = "Statistiques transactions")
    public ResponseEntity<ApiSuccess<Object>> getStats(@RequestParam(required = false) String period) {
        long total = operationRepository.count();
        return ResponseEntity.ok(ApiSuccess.of(Map.of(
                "totalCount", total,
                "totalVolume", 0,
                "currency", "USDC",
                "successRate", 0
        )));
    }
}
