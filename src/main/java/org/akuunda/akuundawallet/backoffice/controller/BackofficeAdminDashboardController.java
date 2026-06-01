package org.akuunda.akuundawallet.backoffice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.akuunda.akuundawallet.backoffice.dto.ApiSuccess;
import org.akuunda.akuundawallet.backoffice.dto.admin.ChartDataPointDto;
import org.akuunda.akuundawallet.backoffice.dto.admin.DashboardStatsDto;
import org.akuunda.akuundawallet.backoffice.dto.admin.EvolutionPointDto;
import org.akuunda.akuundawallet.backoffice.dto.admin.GeoCountryDto;
import org.akuunda.akuundawallet.backoffice.dto.admin.GeoTransactionDto;
import org.akuunda.akuundawallet.backoffice.dto.admin.TopUserDto;
import org.akuunda.akuundawallet.backoffice.dto.admin.TransactionTypeDto;
import org.akuunda.akuundawallet.backoffice.dto.admin.VolumeDayDto;
import org.akuunda.akuundawallet.backoffice.service.BackofficeAdminService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping(path = "/api/v1/admin/dashboard", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Backoffice - Admin Dashboard")
@RequiredArgsConstructor
public class BackofficeAdminDashboardController {

    private final BackofficeAdminService backofficeAdminService;

    @GetMapping("/stats")
    @Operation(summary = "Statistiques globales")
    public ResponseEntity<ApiSuccess<DashboardStatsDto>> getStats(@RequestParam(required = false) String period) {
        String p = period != null ? period : "30d";
        return ResponseEntity.ok(ApiSuccess.of(backofficeAdminService.getDashboardStats(p)));
    }

    @GetMapping("/evolution")
    @Operation(summary = "Courbe évolution")
    public ResponseEntity<ApiSuccess<List<EvolutionPointDto>>> evolution(@RequestParam(required = false) String period) {
        return ResponseEntity.ok(ApiSuccess.of(backofficeAdminService.getEvolution(period != null ? period : "12m")));
    }

    @GetMapping("/transaction-types")
    @Operation(summary = "Répartition par type")
    public ResponseEntity<ApiSuccess<List<TransactionTypeDto>>> transactionTypes() {
        return ResponseEntity.ok(ApiSuccess.of(backofficeAdminService.getTransactionTypes()));
    }

    @GetMapping("/volume")
    @Operation(summary = "Volume hebdomadaire")
    public ResponseEntity<ApiSuccess<List<VolumeDayDto>>> volume(@RequestParam(required = false) String period) {
        return ResponseEntity.ok(ApiSuccess.of(backofficeAdminService.getVolume(period != null ? period : "7d")));
    }

    @GetMapping("/top-users")
    @Operation(summary = "Top utilisateurs par volume")
    public ResponseEntity<ApiSuccess<List<TopUserDto>>> topUsers(
            @RequestParam(required = false, defaultValue = "8") Integer limit,
            @RequestParam(required = false) String sort) {
        return ResponseEntity.ok(ApiSuccess.of(backofficeAdminService.getTopUsers(limit != null ? limit : 8, sort)));
    }

    @GetMapping("/geo/countries")
    @Operation(summary = "Données géographiques par pays")
    public ResponseEntity<ApiSuccess<List<GeoCountryDto>>> geoCountries() {
        return ResponseEntity.ok(ApiSuccess.of(backofficeAdminService.getGeoCountries()));
    }

    @GetMapping("/geo/evolution")
    @Operation(summary = "Évolution par pays (stub)")
    public ResponseEntity<ApiSuccess<Object>> geoEvolution(@RequestParam(required = false) String period) {
        return ResponseEntity.ok(ApiSuccess.of(java.util.Collections.emptyList()));
    }

    @GetMapping("/geo/transactions-by-country")
    @Operation(summary = "Transactions par devise/pays")
    public ResponseEntity<ApiSuccess<List<GeoTransactionDto>>> txByCountry() {
        return ResponseEntity.ok(ApiSuccess.of(backofficeAdminService.getGeoTransactionsByCountry()));
    }

    @GetMapping("/charts/volume")
    @Operation(summary = "Graphique volume")
    public ResponseEntity<ApiSuccess<List<ChartDataPointDto>>> getVolumeChart(
            @RequestParam String period, @RequestParam String granularity, @RequestParam(required = false) String currency) {
        return ResponseEntity.ok(ApiSuccess.of(backofficeAdminService.getVolumeChart(period, granularity, currency)));
    }

    @GetMapping("/charts/distribution")
    @Operation(summary = "Distribution")
    public ResponseEntity<ApiSuccess<List<ChartDataPointDto>>> getDistribution(@RequestParam String period) {
        return ResponseEntity.ok(ApiSuccess.of(backofficeAdminService.getDistributionChart(period)));
    }

    @GetMapping("/activity")
    public ResponseEntity<ApiSuccess<java.util.List<?>>> getActivity(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int limit) {
        return ResponseEntity.ok(ApiSuccess.of(java.util.Collections.emptyList()));
    }

    @GetMapping("/alerts")
    public ResponseEntity<ApiSuccess<java.util.List<?>>> getAlerts() {
        return ResponseEntity.ok(ApiSuccess.of(java.util.Collections.emptyList()));
    }
}
