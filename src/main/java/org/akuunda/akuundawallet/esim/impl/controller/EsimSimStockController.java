package org.akuunda.akuundawallet.esim.impl.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.constants.ApiConstants;
import org.akuunda.akuundawallet.esim.api.dao.EsimSimSerialRepository;
import org.akuunda.akuundawallet.esim.api.dto.EsimSimStockImportResult;
import org.akuunda.akuundawallet.esim.api.dto.SftpSyncRequestDto;
import org.akuunda.akuundawallet.esim.api.entities.EsimSimSerial;
import org.akuunda.akuundawallet.esim.api.entities.EsimSimSerialStatus;
import org.akuunda.akuundawallet.esim.impl.service.EsimCsvImportService;
import org.akuunda.akuundawallet.esim.impl.service.EsimSftpSyncService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping(path = "/api/internal/v1/esim/sim-cards",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Akuunda Services - Transatel - Stock SIM",
        description = "Gestion du stock eSIM Transatel : import CSV, sync SFTP, consultation.")
@SecurityRequirement(name = ApiConstants.SWAGGER_BASIC_SECURITY_SCHEME)
@RequiredArgsConstructor
public class EsimSimStockController {

    private final EsimSimSerialRepository simSerialRepository;
    private final EsimCsvImportService csvImportService;
    private final EsimSftpSyncService sftpSyncService;

    @Value("${esim.sftp.sync-enabled:false}")
    private boolean sftpSyncEnabled;

    // -------------------------------------------------------------------------
    // GET — Liste du stock
    // -------------------------------------------------------------------------

    @GetMapping
    @Operation(
            summary = "Lister le stock de SIM cards",
            description = """
                    Retourne toutes les SIM cards en base avec leur MSISDN, statut et assignation.
                    Filtrer par statut optionnel : AVAILABLE, ASSIGNED, USED, SUSPENDED, TERMINATED.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Stock récupéré"),
                    @ApiResponse(responseCode = "400", description = "Statut invalide")
            }
    )
    public ResponseEntity<List<EsimSimSerial>> listSimCards(
            @RequestParam(required = false) String status) {

        if (status != null && !status.isBlank()) {
            try {
                EsimSimSerialStatus s = EsimSimSerialStatus.valueOf(status.toUpperCase());
                return ResponseEntity.ok(simSerialRepository.findAllByStatusOrderByIdAsc(s));
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest().build();
            }
        }
        return ResponseEntity.ok(simSerialRepository.findAllByOrderByIdAsc());
    }

    // -------------------------------------------------------------------------
    // POST — Import manuel CSV
    // -------------------------------------------------------------------------

    @PostMapping(value = "/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Importer un fichier CSV Transatel",
            description = """
                    Upload du fichier CSV exporté depuis le portail Transatel (format ';', UTF-8 BOM).
                    Les SIM cards sont insérées ou mises à jour automatiquement en base.
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Import effectué"),
                    @ApiResponse(responseCode = "400", description = "Fichier invalide"),
                    @ApiResponse(responseCode = "500", description = "Erreur serveur")
            }
    )
    public ResponseEntity<EsimSimStockImportResult> importCsv(
            @RequestParam("file") MultipartFile file) {

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        log.info("Import CSV manuel — fichier: {} ({} octets)", file.getOriginalFilename(), file.getSize());
        try {
            EsimSimStockImportResult result = csvImportService.importFromStream(file.getInputStream());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Erreur import CSV: {}", e.getMessage(), e);
            EsimSimStockImportResult r = new EsimSimStockImportResult();
            r.addError("Erreur lecture fichier: " + e.getMessage());
            return ResponseEntity.internalServerError().body(r);
        }
    }

    // -------------------------------------------------------------------------
    // POST — Sync SFTP manuel
    // -------------------------------------------------------------------------

    @PostMapping("/sync-sftp")
    @Operation(
            summary = "Synchroniser le stock depuis le SFTP Transatel",
            description = """
                    Se connecte au SFTP Transatel, télécharge le dernier fichier CSV de stock
                    et importe les SIM cards en base.
                    Les credentials peuvent être passés dans le body (override des properties serveur)
                    ou configurés via les variables d'environnement ESIM_SFTP_*.
                    Exemple body :
                    {
                      "host": "sftp-public.transatel.com",
                      "username": "m2m-tsl-akuundapay",
                      "password": "...",
                      "remoteDir": "/",
                      "filePattern": "_park.csv"
                    }
                    """,
            responses = {
                    @ApiResponse(responseCode = "200", description = "Sync effectué"),
                    @ApiResponse(responseCode = "200", description = "Erreur dans errors[] si SFTP échoue")
            }
    )
    public ResponseEntity<EsimSimStockImportResult> syncSftp(
            @RequestBody(required = false) SftpSyncRequestDto req) {
        log.info("Sync SFTP manuel déclenché");
        EsimSimStockImportResult result;
        if (req != null) {
            result = sftpSyncService.syncFromSftp(
                    req.getHost(), req.getPort(), req.getUsername(),
                    req.getPassword(), req.getRemoteDir(), req.getFilePattern());
        } else {
            result = sftpSyncService.syncFromSftp();
        }
        return ResponseEntity.ok(result);
    }

    // -------------------------------------------------------------------------
    // Tâche planifiée — Sync SFTP automatique quotidien
    // -------------------------------------------------------------------------

    /**
     * Synchronisation automatique du stock eSIM depuis le SFTP Transatel.
     * Cron configurable via esim.sftp.sync-cron (défaut: 6h00 chaque matin).
     * Activée uniquement si esim.sftp.sync-enabled=true.
     */
    @Scheduled(cron = "${esim.sftp.sync-cron:0 0 6 * * *}")
    public void scheduledSftpSync() {
        if (!sftpSyncEnabled) {
            return;
        }
        log.info("Déclenchement automatique sync SFTP Transatel (cron)");
        EsimSimStockImportResult result = sftpSyncService.syncFromSftp();
        log.info("Sync SFTP automatique terminé — inserted={} updated={} skipped={} errors={}",
                result.getInserted(), result.getUpdated(),
                result.getSkipped(), result.getErrors().size());
    }
}
