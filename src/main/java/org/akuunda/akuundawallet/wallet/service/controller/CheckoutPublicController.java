package org.akuunda.akuundawallet.wallet.service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dto.CheckoutCountryOption;
import org.akuunda.akuundawallet.wallet.api.dto.CheckoutSessionResponse;
import org.akuunda.akuundawallet.wallet.service.PaymentsService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoint public consommé par la page de paiement hébergée (frontend-web-pay).
 * Aucune authentification : le code de checkout fait office de jeton d'accès.
 */
@Slf4j
@RestController
@RequestMapping(path = "/api/v1/checkout", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
@Tag(name = "Checkout (public)", description = "Lecture publique d'un checkout par son code (page de paiement)")
public class CheckoutPublicController {

    private final PaymentsService paymentsService;

    @GetMapping("/{code}")
    @Operation(summary = "Lire un checkout par son code (sans authentification)")
    public ResponseEntity<CheckoutSessionResponse> get(@PathVariable String code) {
        return paymentsService.getPublicCheckout(code);
    }

    @GetMapping("/{code}/countries")
    @Operation(summary = "Pays de paiement disponibles pour ce checkout, avec le provider associé (sans auth)")
    public ResponseEntity<List<CheckoutCountryOption>> getCountries(@PathVariable String code) {
        return paymentsService.getCheckoutCountries(code);
    }
}
