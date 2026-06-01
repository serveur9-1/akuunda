package org.akuunda.akuundawallet.backoffice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.akuunda.akuundawallet.backoffice.dto.ApiSuccess;
import org.akuunda.akuundawallet.backoffice.dto.PaginatedResponse;
import org.akuunda.akuundawallet.backoffice.dto.admin.PaymentLinkDto;
import org.akuunda.akuundawallet.backoffice.service.BackofficeAdminService;
import org.akuunda.akuundawallet.backoffice.service.BackofficeProMerchantResolver;
import org.akuunda.akuundawallet.wallet.api.dto.CreateOneTimePaymentLinkRequest;
import org.akuunda.akuundawallet.wallet.api.dto.CreatePermanentLinkRequest;
import org.akuunda.akuundawallet.wallet.api.dto.OneTimePaymentLinkResponse;
import org.akuunda.akuundawallet.wallet.api.dto.PermanentLinkResponse;
import org.akuunda.akuundawallet.wallet.service.OneTimePaymentLinkService;
import org.akuunda.akuundawallet.wallet.service.PermanentLinkService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(path = "/api/v1/pro/payment-links", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Backoffice - Pro Payment Links")
@RequiredArgsConstructor
public class BackofficeProPaymentLinksController {

    private final BackofficeAdminService backofficeAdminService;
    private final BackofficeProMerchantResolver merchantResolver;
    private final PermanentLinkService permanentLinkService;
    private final OneTimePaymentLinkService oneTimePaymentLinkService;

    @GetMapping
    @Operation(summary = "Liste des payment links (pro)")
    public ResponseEntity<ApiSuccess<PaginatedResponse<PaymentLinkDto>>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int limit) {
        PaginatedResponse<PaymentLinkDto> data = backofficeAdminService.getPaymentLinks(
                PageRequest.of(Math.max(0, page - 1), Math.min(100, limit)), null);
        return ResponseEntity.ok(ApiSuccess.of(data));
    }

    @GetMapping("/{linkId}")
    @Operation(summary = "Détail payment link")
    public ResponseEntity<ApiSuccess<PaymentLinkDto>> get(@PathVariable String linkId) {
        PaymentLinkDto data = backofficeAdminService.getPaymentLinkById(linkId);
        if (data == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(ApiSuccess.of(data));
    }

    @PostMapping("/permanent")
    @Operation(summary = "Créer un lien permanent / QR (username depuis JWT)")
    public ResponseEntity<ApiSuccess<PermanentLinkResponse>> createPermanent(
            @RequestBody @Valid CreatePermanentLinkRequest request) {
        String username = merchantResolver.resolveWalletUsername();
        ResponseEntity<?> upstream = permanentLinkService.createPermanentLink(username, request);
        if (!upstream.getStatusCode().is2xxSuccessful()) {
            return ResponseEntity.status(upstream.getStatusCode()).build();
        }
        Object body = upstream.getBody();
        if (body instanceof PermanentLinkResponse pl) {
            return ResponseEntity.status(upstream.getStatusCode()).body(ApiSuccess.of(pl));
        }
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @GetMapping("/permanent")
    @Operation(summary = "Lister les liens permanents du marchand connecté")
    public ResponseEntity<ApiSuccess<List<PermanentLinkResponse>>> listPermanent() {
        String username = merchantResolver.resolveWalletUsername();
        ResponseEntity<List<PermanentLinkResponse>> upstream =
                permanentLinkService.getUserPermanentLinks(username);
        return ResponseEntity.status(upstream.getStatusCode())
                .body(ApiSuccess.of(upstream.getBody() != null ? upstream.getBody() : List.of()));
    }

    @PostMapping("/one-time")
    @Operation(summary = "Créer un lien de paiement ponctuel (username depuis JWT)")
    public ResponseEntity<ApiSuccess<OneTimePaymentLinkResponse>> createOneTime(
            @RequestBody @Valid CreateOneTimePaymentLinkRequest request) {
        String username = merchantResolver.resolveWalletUsername();
        ResponseEntity<OneTimePaymentLinkResponse> upstream =
                oneTimePaymentLinkService.createOneTimePaymentLink(username, request);
        if (!upstream.getStatusCode().is2xxSuccessful() || upstream.getBody() == null) {
            return ResponseEntity.status(upstream.getStatusCode()).build();
        }
        return ResponseEntity.status(upstream.getStatusCode()).body(ApiSuccess.of(upstream.getBody()));
    }

    @GetMapping("/one-time")
    @Operation(summary = "Lister les liens ponctuels du marchand connecté")
    public ResponseEntity<ApiSuccess<List<OneTimePaymentLinkResponse>>> listOneTime() {
        String username = merchantResolver.resolveWalletUsername();
        ResponseEntity<List<OneTimePaymentLinkResponse>> upstream =
                oneTimePaymentLinkService.getUserOneTimePaymentLinks(username);
        return ResponseEntity.status(upstream.getStatusCode())
                .body(ApiSuccess.of(upstream.getBody() != null ? upstream.getBody() : List.of()));
    }
}
