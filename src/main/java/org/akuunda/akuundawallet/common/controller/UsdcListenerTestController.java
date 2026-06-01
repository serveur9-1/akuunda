package org.akuunda.akuundawallet.common.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dao.PartnerContractPaymentRepository;
import org.akuunda.akuundawallet.wallet.service.listener.UsdcTransferEventListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoints de test pour le listener WebSocket USDC.
 * Disponible uniquement avec le profil "dev" ou "test".
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/internal/v1/test/usdc-listener")
@Tag(name = "Tests - USDC Listener", description = "Endpoints de test du listener WebSocket Polygon")
@Profile({"dev", "test", "local"})
@ConditionalOnProperty(name = "akuunda.usdc.listener.enabled", havingValue = "true", matchIfMissing = false)
public class UsdcListenerTestController {

    private final UsdcTransferEventListener listener;
    private final PartnerContractPaymentRepository paymentRepository;

    /**
     * Simule la réception d'un Transfer USDC vers une adresse CREATE2.
     *
     * <p>Exemple : POST /api/internal/v1/test/usdc-listener/simulate
     * <pre>{ "create2Address": "0xABC123...", "txHash": "0xFAKE" }</pre>
     */
    @PostMapping("/simulate")
    @Operation(summary = "Simuler un Transfer USDC vers une adresse CREATE2")
    public ResponseEntity<Map<String, Object>> simulateTransfer(
            @RequestBody Map<String, String> body) {

        String create2Address = body.get("create2Address");
        String txHash = body.getOrDefault("txHash", "0xSIMULATED_" + System.currentTimeMillis());

        if (create2Address == null || create2Address.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "create2Address est requis"));
        }

        boolean isWatched = paymentRepository
                .findByCreate2WalletAddressIgnoreCase(create2Address)
                .isPresent();

        log.warn("[TEST] Simulation Transfer USDC — create2={}, isWatched={}", create2Address, isWatched);
        listener.simulateTransfer(create2Address, txHash);

        return ResponseEntity.ok(Map.of(
                "message", "Simulation envoyée",
                "create2Address", create2Address,
                "txHash", txHash,
                "paymentFound", isWatched
        ));
    }

    /**
     * Enregistre manuellement une adresse dans le listener (pour tests sans créer de vrai paiement).
     */
    @PostMapping("/register")
    @Operation(summary = "Enregistrer une adresse CREATE2 dans le listener")
    public ResponseEntity<Map<String, String>> registerAddress(
            @RequestParam String address) {
        listener.registerPayment(address);
        return ResponseEntity.ok(Map.of(
                "message", "Adresse enregistrée",
                "address", address
        ));
    }
}
