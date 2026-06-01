package org.akuunda.akuundawallet.wallet.service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dto.CheckoutRequest;
import org.akuunda.akuundawallet.wallet.api.dto.CheckoutResponse;
import org.akuunda.akuundawallet.wallet.api.dto.CheckoutSessionResponse;
import org.akuunda.akuundawallet.wallet.service.CheckoutService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/checkout")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Checkout", description = "API pour l'intégration e-commerce (checkout hébergé)")
public class CheckoutController {

    private final CheckoutService checkoutService;

    @PostMapping(value = "/create", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Créer une session de checkout",
            description = "Crée une session de paiement pour une boutique en ligne. Authentifié par API key.")
    public ResponseEntity<CheckoutResponse> createCheckout(
            @RequestHeader("X-API-Key") String apiKey,
            @RequestBody @Valid CheckoutRequest request) {
        return checkoutService.createCheckout(apiKey, request);
    }

    @GetMapping("/{checkoutCode}")
    @Operation(summary = "Récupérer les infos d'un checkout",
            description = "Récupère les informations publiques d'un checkout par son code.")
    public ResponseEntity<CheckoutSessionResponse> getCheckout(@PathVariable String checkoutCode) {
        return checkoutService.getCheckoutByCode(checkoutCode);
    }
}
