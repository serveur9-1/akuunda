package org.akuunda.akuundawallet.wallet.service.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.constants.ApiConstants;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkunndaWalletClientService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Endpoint de récupération : resynchronise les adresses des wallets depuis l'API Venly.
 * À utiliser si la colonne address de la table wallet a été écrasée par erreur.
 * Venly conserve l'association walletId → address ; on appelle GET /api/wallets/{id} pour chaque wallet.
 */
@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping(path = "/api/internal/v1/wallets", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Wallet Recovery", description = "Récupération des adresses wallets depuis Venly (à utiliser en cas d'écrasement en base)")
@SecurityRequirement(name = ApiConstants.SWAGGER_BASIC_SECURITY_SCHEME)
@RequiredArgsConstructor
public class WalletRecoveryController {

    private final WalletRepository walletRepository;
    private final AkunndaWalletClientService walletClientService;

    @Value("${akuunda.intermediate.wallet.id:}")
    private String intermediateWalletId;

    @PostMapping("/sync-addresses-from-venly")
    @Operation(
            operationId = "syncWalletAddressesFromVenly",
            summary = "Resynchroniser les adresses des wallets depuis Venly",
            description = """
        Parcourt tous les wallets en base et met à jour leur adresse en appelant l'API Venly (GET /api/wallets/{id}).
        À utiliser si les adresses ont été écrasées par erreur (ex: UPDATE sans WHERE correct).
        
        - Les wallets dont l'ID existe chez Venly sont mis à jour avec l'adresse retournée.
        - Le wallet intermédiaire (config akuunda.intermediate.wallet.id) n'est pas modifié si son adresse est déjà correcte en config.
        - Les wallets non reconnus par Venly (ex: smart contract, anciens) sont ignorés (pas d'écrasement).
        """
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Résultat de la synchronisation (updated, failed, skipped)"),
            @ApiResponse(responseCode = "500", description = "Erreur serveur")
    })
    public ResponseEntity<Map<String, Object>> syncAddressesFromVenly() {
        log.warn("⚠️ Appel sync-addresses-from-venly : récupération des adresses depuis Venly");
        
        List<Wallet> allWallets = walletRepository.findAll();
        List<Map<String, Object>> updated = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();
        List<Map<String, Object>> skipped = new ArrayList<>();

        for (Wallet wallet : allWallets) {
            String id = wallet.getId();
            if (id == null || id.isBlank()) {
                skipped.add(Map.of("id", "", "reason", "id vide"));
                continue;
            }
            
            try {
                var response = walletClientService.getWallet(id, false);
                var body = response != null ? response.getBody() : null;
                if (response != null && response.getStatusCode().is2xxSuccessful() 
                        && body != null && body.getResult() != null) {
                    String addressFromVenly = body.getResult().getAddress();
                    if (addressFromVenly != null && !addressFromVenly.isBlank()) {
                        wallet.setAddress(addressFromVenly);
                        walletRepository.save(wallet);
                        updated.add(Map.<String, Object>of(
                                "id", id,
                                "address", addressFromVenly
                        ));
                        log.info("✅ Wallet {} mis à jour avec adresse {}", id, addressFromVenly);
                    } else {
                        skipped.add(Map.<String, Object>of("id", id, "reason", "Venly n'a pas retourné d'adresse"));
                    }
                } else {
                    // Venly n'a pas retourné ce wallet (ex: wallet système, smart contract)
                    skipped.add(Map.<String, Object>of(
                            "id", id,
                            "reason", "Non trouvé chez Venly ou réponse invalide (possible pour wallet système/smart contract)"
                    ));
                    log.debug("Skip wallet {} (non trouvé ou réponse invalide)", id);
                }
            } catch (Exception e) {
                log.error("Erreur pour wallet {}: {}", id, e.getMessage());
                failed.add(Map.<String, Object>of(
                        "id", id,
                        "error", e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()
                ));
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("total", allWallets.size());
        result.put("updated", updated.size());
        result.put("failed", failed.size());
        result.put("skipped", skipped.size());
        result.put("detailsUpdated", updated);
        result.put("detailsFailed", failed);
        result.put("detailsSkipped", skipped);
        
        log.info("✅ Sync Venly terminée: {} mis à jour, {} en échec, {} ignorés", updated.size(), failed.size(), skipped.size());
        return ResponseEntity.ok(result);
    }
}
