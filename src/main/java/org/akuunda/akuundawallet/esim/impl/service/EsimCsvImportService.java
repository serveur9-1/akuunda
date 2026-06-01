package org.akuunda.akuundawallet.esim.impl.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.esim.api.dao.EsimSimSerialRepository;
import org.akuunda.akuundawallet.esim.api.dto.EsimSimStockImportResult;
import org.akuunda.akuundawallet.esim.api.entities.EsimSimSerial;
import org.akuunda.akuundawallet.esim.api.entities.EsimSimSerialStatus;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * Parses Transatel SIM delivery CSV files (semicolon-separated, UTF-8 BOM)
 * and upserts the data into the esim_sim_serials table.
 *
 * CSV column indices (0-based):
 *   0 → ICCID / SIM serial
 *   7 → MSISDN (with leading '+', stripped on import)
 *   8 → Statut (Active, Pré-activée, Suspendue, Résiliée)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EsimCsvImportService {

    private static final int COL_SIM_SERIAL = 0;
    private static final int COL_MSISDN     = 7;
    private static final int COL_STATUS     = 8;
    private static final String SEPARATOR   = ";";

    private final EsimSimSerialRepository simSerialRepository;

    /**
     * Imports SIM cards from a Transatel CSV InputStream.
     * Skips the header line automatically.
     */
    public EsimSimStockImportResult importFromStream(InputStream inputStream) {
        EsimSimStockImportResult result = new EsimSimStockImportResult();
        int lineNumber = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;

                // Strip UTF-8 BOM on first line
                if (lineNumber == 1) {
                    if (line.startsWith("﻿")) {
                        line = line.substring(1);
                    }
                    continue; // skip header
                }

                if (line.isBlank()) continue;

                try {
                    processLine(line, result);
                } catch (Exception e) {
                    result.addError("Ligne " + lineNumber + ": " + e.getMessage());
                    log.warn("Erreur import CSV ligne {}: {}", lineNumber, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Erreur lecture CSV: {}", e.getMessage(), e);
            result.addError("Erreur lecture fichier: " + e.getMessage());
        }

        log.info("Import CSV terminé — total={} inserted={} updated={} skipped={} errors={}",
                result.getTotal(), result.getInserted(), result.getUpdated(),
                result.getSkipped(), result.getErrors().size());
        return result;
    }

    private void processLine(String line, EsimSimStockImportResult result) {
        String[] cols = line.split(SEPARATOR, -1);
        if (cols.length <= COL_STATUS) {
            result.addError("Ligne ignorée — colonnes insuffisantes: " + line.substring(0, Math.min(40, line.length())));
            return;
        }

        String simSerial = cols[COL_SIM_SERIAL].trim();
        String rawMsisdn = cols[COL_MSISDN].trim();
        String rawStatus = cols[COL_STATUS].trim();

        if (simSerial.isEmpty()) return;

        String msisdn = normalizeMsisdn(rawMsisdn);
        EsimSimSerialStatus transatelStatus = mapStatus(rawStatus);

        result.setTotal(result.getTotal() + 1);

        Optional<EsimSimSerial> existing = simSerialRepository.findBySimSerial(simSerial);

        if (existing.isEmpty()) {
            // New SIM — insert
            EsimSimSerial sim = new EsimSimSerial(simSerial, msisdn);
            sim.setStatus(transatelStatus);
            simSerialRepository.save(sim);
            result.setInserted(result.getInserted() + 1);
            log.debug("Inserted SIM {} msisdn={} status={}", simSerial, msisdn, transatelStatus);
        } else {
            // Existing SIM — selective update
            EsimSimSerial sim = existing.get();
            boolean changed = false;

            // Always update MSISDN if it changed and is not blank
            if (!msisdn.isEmpty() && !msisdn.equals(sim.getMsisdn())) {
                sim.setMsisdn(msisdn);
                changed = true;
            }

            // Update status only if Transatel reports a terminal/negative state
            // or if our DB has AVAILABLE and Transatel confirms it's still valid
            EsimSimSerialStatus current = sim.getStatus();
            if (shouldUpdateStatus(current, transatelStatus)) {
                sim.setStatus(transatelStatus);
                changed = true;
            }

            if (changed) {
                simSerialRepository.save(sim);
                result.setUpdated(result.getUpdated() + 1);
                log.debug("Updated SIM {} msisdn={} status={}", simSerial, msisdn, transatelStatus);
            } else {
                result.setSkipped(result.getSkipped() + 1);
            }
        }
    }

    /**
     * Determines whether to update the local status based on Transatel's reported status.
     * Rules:
     * - TERMINATED always wins (Transatel terminated = terminate locally too)
     * - SUSPENDED wins unless we already have a terminal state
     * - AVAILABLE from Transatel only updates if local is already AVAILABLE
     *   (don't reset a USED/ASSIGNED SIM back to AVAILABLE)
     */
    private boolean shouldUpdateStatus(EsimSimSerialStatus current, EsimSimSerialStatus incoming) {
        if (incoming == EsimSimSerialStatus.TERMINATED && current != EsimSimSerialStatus.TERMINATED) {
            return true;
        }
        if (incoming == EsimSimSerialStatus.SUSPENDED
                && current != EsimSimSerialStatus.TERMINATED
                && current != EsimSimSerialStatus.SUSPENDED) {
            return true;
        }
        if (incoming == EsimSimSerialStatus.AVAILABLE && current == EsimSimSerialStatus.AVAILABLE) {
            return false; // already correct, no change
        }
        return false;
    }

    /**
     * Maps Transatel French status strings to local enum values.
     * Handles both properly encoded (UTF-8) and mis-encoded variants.
     */
    EsimSimSerialStatus mapStatus(String raw) {
        if (raw == null || raw.isBlank()) return EsimSimSerialStatus.AVAILABLE;
        String s = raw.toLowerCase().trim();

        // "Résiliée" / "Résiliée" / mis-encoded variants
        if (s.contains("résili") || s.contains("resili") || s.contains("termin")) {
            return EsimSimSerialStatus.TERMINATED;
        }
        // "Suspendue"
        if (s.contains("suspen")) {
            return EsimSimSerialStatus.SUSPENDED;
        }
        // "Active" or "Pré-activée" / "Pre-activée"
        return EsimSimSerialStatus.AVAILABLE;
    }

    /** Strips leading '+' from MSISDN. */
    private String normalizeMsisdn(String raw) {
        if (raw == null) return "";
        String s = raw.trim();
        if (s.startsWith("+")) {
            s = s.substring(1);
        }
        return s;
    }
}
