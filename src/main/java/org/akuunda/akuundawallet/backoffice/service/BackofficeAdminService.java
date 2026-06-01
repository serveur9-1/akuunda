package org.akuunda.akuundawallet.backoffice.service;

import org.akuunda.akuundawallet.backoffice.dto.PaginatedResponse;
import org.akuunda.akuundawallet.backoffice.dto.admin.*;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service backoffice Admin : dashboard, users, transactions, conditional payments, payment links.
 */
public interface BackofficeAdminService {

    DashboardStatsDto getDashboardStats(String period);

    List<ChartDataPointDto> getVolumeChart(String period, String granularity, String currency);

    List<ChartDataPointDto> getDistributionChart(String period);

    PaginatedResponse<UserProfileDto> getUsers(Pageable pageable, String search, String status);

    UserProfileDto getUserById(String userId);

    PaginatedResponse<ConditionalPaymentDto> getConditionalPayments(Pageable pageable, String status);

    ConditionalPaymentDto getConditionalPaymentById(String paymentId);

    PaginatedResponse<PaymentLinkDto> getPaymentLinks(Pageable pageable, String status);

    PaymentLinkDto getPaymentLinkById(String linkId);

    List<GeoCountryDto> getGeoCountries();

    List<EvolutionPointDto> getEvolution(String period);

    List<TransactionTypeDto> getTransactionTypes();

    List<VolumeDayDto> getVolume(String period);

    List<TopUserDto> getTopUsers(int limit, String sort);

    List<GeoTransactionDto> getGeoTransactionsByCountry();
}
