package org.akuunda.akuundawallet.backoffice.service.impl;

import lombok.RequiredArgsConstructor;
import org.akuunda.akuundawallet.backoffice.dto.PaginatedResponse;
import org.akuunda.akuundawallet.backoffice.dto.admin.*;
import org.akuunda.akuundawallet.backoffice.service.BackofficeAdminService;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.dao.ConditionalPaymentRepository;
import org.akuunda.akuundawallet.wallet.api.dao.PaymentLinkRepository;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.api.dto.WalletUserDto;
import org.akuunda.akuundawallet.wallet.api.entities.ConditionalPayment;
import org.akuunda.akuundawallet.wallet.api.entities.PaymentLink;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.wallet.service.mapper.WalletMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BackofficeAdminServiceImpl implements BackofficeAdminService {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_DATE_TIME;

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final ConditionalPaymentRepository conditionalPaymentRepository;
    private final PaymentLinkRepository paymentLinkRepository;
    private final org.akuunda.akuundawallet.wallet.api.dao.OperationRepository operationRepository;

    @Override
    public DashboardStatsDto getDashboardStats(String period) {
        // Users
        long totalUsers = userRepository.count();
        long activeUsers = userRepository.countByEnabledTrue();
        long pendingKYC = userRepository.countByIdentyVerifyFalse();

        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime endOfDay = startOfDay.plusDays(1);
        long newUsersToday = userRepository.countByCreatedAtBetween(
                Timestamp.valueOf(startOfDay), Timestamp.valueOf(endOfDay));

        // Operations
        long totalTransactions = operationRepository.count();

        long failedOps = operationRepository.countByStatusIn(List.of("REJETEE", "ECHOUEE"));
        double failureRate = totalTransactions > 0
                ? Math.round(((double) failedOps / totalTransactions) * 1000.0) / 10.0
                : 0.0;

        // Volume par devise (somme des montants)
        Map<String, Double> transactionVolume = new HashMap<>();
        try {
            List<Object[]> volRows = operationRepository.sumAmountGroupByDevise();
            for (Object[] row : volRows) {
                String devise = (String) row[0];
                Double sum = row[1] != null ? ((Number) row[1]).doubleValue() : 0.0;
                if (devise != null) transactionVolume.put(devise, sum);
            }
        } catch (Exception ignored) {}

        // Escrow & payment links
        long totalCp = conditionalPaymentRepository.count();
        long totalPl = paymentLinkRepository.count();
        Map<String, Double> escrowBalance = new HashMap<>();
        escrowBalance.put("USDC", (double) totalCp);

        double totalVolumeUsd = 0.0;
        try {
            Double volUsd = operationRepository.sumTotalVolumeUsdSuccessful();
            if (volUsd != null && volUsd > 0) {
                totalVolumeUsd = volUsd;
            }
        } catch (Exception ignored) {}
        totalVolumeUsd = Math.round(totalVolumeUsd * 100.0) / 100.0;

        return DashboardStatsDto.builder()
                .totalUsers(totalUsers)
                .activeUsers(activeUsers)
                .newUsersToday(newUsersToday)
                .totalTransactions(totalTransactions)
                .transactionVolume(transactionVolume)
                .totalVolumeUsd(totalVolumeUsd)
                .pendingKYC(pendingKYC)
                .activeESIMs(0L)
                .activePaymentLinks(totalPl)
                .escrowBalance(escrowBalance)
                .revenueToday(Map.of())
                .revenueThisMonth(Map.of())
                .failureRate(failureRate)
                .averageProcessingTime(0.0)
                .build();
    }

    @Override
    public List<ChartDataPointDto> getVolumeChart(String period, String granularity, String currency) {
        int days = "7d".equals(period) ? 7 : "30d".equals(period) ? 30 : 7;
        LocalDateTime since = LocalDateTime.now().minusDays(days).withHour(0).withMinute(0).withSecond(0);
        String[] dayLabels = {"Dim", "Lun", "Mar", "Mer", "Jeu", "Ven", "Sam"};
        Map<String, Double> volumeByDate = new HashMap<>();
        try {
            for (Object[] row : operationRepository.sumVolumeUsdByDay(since)) {
                int y = ((Number) row[0]).intValue();
                int m = ((Number) row[1]).intValue();
                int d = ((Number) row[2]).intValue();
                double vol = row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;
                volumeByDate.put(y + "-" + m + "-" + d, vol);
            }
        } catch (Exception ignored) {}
        List<ChartDataPointDto> result = new ArrayList<>();
        LocalDate cursor = LocalDate.now().minusDays(days - 1);
        for (int i = 0; i < days; i++) {
            String key = cursor.getYear() + "-" + cursor.getMonthValue() + "-" + cursor.getDayOfMonth();
            double vol = volumeByDate.getOrDefault(key, 0.0);
            String label = "month".equals(granularity)
                    ? cursor.toString()
                    : dayLabels[cursor.getDayOfWeek().getValue() % 7] + " " + cursor.getDayOfMonth();
            result.add(new ChartDataPointDto(label, Math.round(vol * 100.0) / 100.0, null, null));
            cursor = cursor.plusDays(1);
        }
        return result;
    }

    @Override
    public List<ChartDataPointDto> getDistributionChart(String period) {
        return List.of(
                new ChartDataPointDto("Conditional", 1.0, null, null),
                new ChartDataPointDto("Payment link", 1.0, null, null)
        );
    }

    @Override
    public PaginatedResponse<UserProfileDto> getUsers(Pageable pageable, String search, String status) {
        boolean hasSearch = search != null && !search.isBlank();
        boolean hasStatus = status != null && !status.isBlank() && !"inactive".equals(status);

        Page<Users> page;
        if (hasStatus && hasSearch) {
            boolean enabled = "active".equals(status);
            page = userRepository.findByEnabledAndSearch(enabled, search.toLowerCase(), pageable);
        } else if (hasStatus) {
            page = userRepository.findByEnabled("active".equals(status), pageable);
        } else if (hasSearch) {
            page = userRepository.findBySearch(search.toLowerCase(), pageable);
        } else {
            page = userRepository.findAll(pageable);
        }

        List<Users> users = page.getContent();
        if (users.isEmpty()) {
            return PaginatedResponse.of(List.of(), page.getNumber() + 1, page.getSize(), page.getTotalElements());
        }
        // Bulk-load : 1 SELECT wallet pour toute la page au lieu de 1 par user
        Map<String, Wallet> walletByUserId = walletRepository.findByUsersIn(users).stream()
                .collect(Collectors.toMap(w -> w.getUsers().getUserId(), w -> w, (a, b) -> a));

        List<UserProfileDto> list = users.stream()
                .map(u -> toUserProfileDto(u, walletByUserId.get(u.getUserId())))
                .collect(Collectors.toList());
        return PaginatedResponse.of(list, page.getNumber() + 1, page.getSize(), page.getTotalElements());
    }

    @Override
    public UserProfileDto getUserById(String userId) {
        return userRepository.findById(userId)
                .map(u -> toUserProfileDto(u, walletRepository.findByUsers(u)))
                .orElse(null);
    }

    @Override
    public PaginatedResponse<ConditionalPaymentDto> getConditionalPayments(Pageable pageable, String status) {
        Page<ConditionalPayment> page = conditionalPaymentRepository.findAllWithFetch(pageable);
        List<ConditionalPaymentDto> list = page.getContent().stream()
                .map(this::toConditionalPaymentDto)
                .collect(Collectors.toList());
        return PaginatedResponse.of(list, page.getNumber() + 1, page.getSize(), page.getTotalElements());
    }

    @Override
    public ConditionalPaymentDto getConditionalPaymentById(String paymentId) {
        try {
            long id = Long.parseLong(paymentId);
            return conditionalPaymentRepository.findById(id)
                    .map(this::toConditionalPaymentDto)
                    .orElse(null);
        } catch (NumberFormatException e) {
            return conditionalPaymentRepository.findByPaymentCode(paymentId)
                    .map(this::toConditionalPaymentDto)
                    .orElse(null);
        }
    }

    @Override
    public PaginatedResponse<PaymentLinkDto> getPaymentLinks(Pageable pageable, String status) {
        Page<PaymentLink> page = paymentLinkRepository.findAll(pageable);
        List<PaymentLinkDto> list = page.getContent().stream()
                .map(this::toPaymentLinkDto)
                .collect(Collectors.toList());
        return PaginatedResponse.of(list, page.getNumber() + 1, page.getSize(), page.getTotalElements());
    }

    @Override
    public PaymentLinkDto getPaymentLinkById(String linkId) {
        try {
            long id = Long.parseLong(linkId);
            return paymentLinkRepository.findById(id).map(this::toPaymentLinkDto).orElse(null);
        } catch (NumberFormatException e) {
            return paymentLinkRepository.findByUniqueCode(linkId).map(this::toPaymentLinkDto).orElse(null);
        }
    }

    private UserProfileDto toUserProfileDto(Users u, Wallet wallet) {
        WalletUserDto walletDto = wallet != null ? WalletMapper.mapWalletToWalletUserDto(wallet) : null;

        return UserProfileDto.builder()
                .id(u.getUserId())
                .firstName(u.getFirstname())
                .lastName(u.getLastname())
                .email(u.getEmail())
                .phone(u.getMobilePhone())
                .status(u.isEnabled() ? "active" : "suspended")
                .createdAt(u.getCreatedAt() != null ? u.getCreatedAt().toInstant().toString() : null)
                .typeCompte(u.getAccountType())
                .kycVerified(u.isIdentyVerify())
                .cguAcceptation(u.isCguAcceptation())
                .wallets(walletDto != null ? List.of(walletDto) : List.of())
                .countryCode(walletDto != null ? walletDto.getCountryCode() : null)
                .countryName(walletDto != null ? walletDto.getCountryName() : null)
                .currencyCode(walletDto != null ? walletDto.getCurrencyCode() : null)
                .build();
    }

    private ConditionalPaymentDto toConditionalPaymentDto(ConditionalPayment cp) {
        return ConditionalPaymentDto.builder()
                .id(String.valueOf(cp.getId()))
                .reference(cp.getPaymentCode())
                .status(cp.getStatus())
                .amount(cp.getAmount())
                .currency(cp.getCurrency())
                .sender(cp.getClient() != null ? Map.of("id", cp.getClient().getUserId(), "name", cp.getClient().getUsername()) : null)
                .receiver(cp.getVendor() != null ? Map.of("id", cp.getVendor().getUserId(), "name", cp.getVendor().getUsername()) : null)
                .conditions(Collections.emptyList())
                .escrowWalletId(cp.getEscrowContractAddress())
                .releaseSchedule("full")
                .expiresAt(cp.getCancellationDeadline() != null ? cp.getCancellationDeadline().format(ISO) : null)
                .createdAt(cp.getCreatedAt() != null ? cp.getCreatedAt().format(ISO) : null)
                .updatedAt(cp.getUpdatedAt() != null ? cp.getUpdatedAt().format(ISO) : null)
                .build();
    }

    @Override
    public List<GeoCountryDto> getGeoCountries() {
        // Step 1: user counts per country (from Wallet → CountryCurrency)
        Map<String, GeoCountryDto> byCode = new LinkedHashMap<>();
        try {
            for (Object[] r : walletRepository.countUsersByCountry()) {
                String code = (String) r[0];
                if (code == null) continue;
                byCode.put(code, GeoCountryDto.builder()
                        .code(code)
                        .name((String) r[1])
                        .continentName((String) r[2])
                        .currencyCode((String) r[3])
                        .userCount(r[4] != null ? ((Number) r[4]).longValue() : 0L)
                        .build());
            }
        } catch (Exception ignored) {}

        // Step 2: build username → countryCode map (via Wallet)
        // Keys: both mobilePhone and username to maximise match coverage
        Map<String, String> usernameToCountry = new HashMap<>();
        try {
            for (Object[] r : walletRepository.getUsersToCountry()) {
                String phone    = r[0] != null ? (String) r[0] : null;
                String username = r[1] != null ? (String) r[1] : null;
                String code     = r[2] != null ? (String) r[2] : null;
                if (code == null) continue;
                if (phone    != null) usernameToCountry.put(phone, code);
                if (username != null) usernameToCountry.put(username, code);
            }
        } catch (Exception ignored) {}

        // Step 3: aggregate operations per country via username → countryCode
        Map<String, long[]> opsByCountry = new HashMap<>(); // countryCode → [txCount, volume*100]
        try {
            for (Object[] r : operationRepository.countAndSumByUsername()) {
                String opUsername = (String) r[0];
                long   txCount   = r[1] != null ? ((Number) r[1]).longValue() : 0L;
                double volume    = r[2] != null ? ((Number) r[2]).doubleValue() : 0.0;
                String countryCode = usernameToCountry.get(opUsername);
                if (countryCode == null) continue;
                long[] agg = opsByCountry.computeIfAbsent(countryCode, k -> new long[2]);
                agg[0] += txCount;
                agg[1] += Math.round(volume * 100.0);
            }
        } catch (Exception ignored) {}

        // Step 4: merge tx counts + volumes into the byCode map
        for (Map.Entry<String, long[]> e : opsByCountry.entrySet()) {
            GeoCountryDto g = byCode.get(e.getKey());
            if (g == null) continue;
            long[] agg = e.getValue();
            byCode.put(e.getKey(), GeoCountryDto.builder()
                    .code(g.getCode()).name(g.getName())
                    .continentName(g.getContinentName())
                    .currencyCode(g.getCurrencyCode())
                    .userCount(g.getUserCount())
                    .transactionCount(agg[0])
                    .totalVolume(agg[1] / 100.0)
                    .build());
        }

        // Sort by userCount desc
        List<GeoCountryDto> result = new ArrayList<>(byCode.values());
        result.sort((a, b) -> Long.compare(b.getUserCount(), a.getUserCount()));
        return result;
    }

    @Override
    public List<EvolutionPointDto> getEvolution(String period) {
        String[] MONTH_LABELS = {"Jan","Fév","Mar","Avr","Mai","Jun","Jul","Aoû","Sep","Oct","Nov","Déc"};
        LocalDateTime since = LocalDateTime.now().minusMonths(12);
        Timestamp tsSince = Timestamp.valueOf(since);
        Map<String, Long> txByYearMonth = new HashMap<>();
        try {
            for (Object[] row : operationRepository.countByMonth(since)) {
                int year = ((Number) row[0]).intValue();
                int month = ((Number) row[1]).intValue();
                long count = ((Number) row[2]).longValue();
                txByYearMonth.put(year + "-" + month, count);
            }
        } catch (Exception ignored) {}
        Map<String, Long> usersByYearMonth = new HashMap<>();
        try {
            for (Object[] row : userRepository.countByMonth(tsSince)) {
                int year = ((Number) row[0]).intValue();
                int month = ((Number) row[1]).intValue();
                long count = ((Number) row[2]).longValue();
                usersByYearMonth.put(year + "-" + month, count);
            }
        } catch (Exception ignored) {}
        List<EvolutionPointDto> result = new ArrayList<>();
        LocalDate cursor = LocalDate.now().minusMonths(11);
        for (int i = 0; i < 12; i++) {
            String key = cursor.getYear() + "-" + cursor.getMonthValue();
            result.add(EvolutionPointDto.builder()
                    .mois(MONTH_LABELS[cursor.getMonthValue() - 1])
                    .utilisateurs(usersByYearMonth.getOrDefault(key, 0L))
                    .transactions(txByYearMonth.getOrDefault(key, 0L))
                    .build());
            cursor = cursor.plusMonths(1);
        }
        return result;
    }

    @Override
    public List<TransactionTypeDto> getTransactionTypes() {
        Map<String, String> labelMap = new LinkedHashMap<>();
        labelMap.put("INTERNAL_TRANSFER", "Transfert");
        labelMap.put("CONDITIONAL_PAYMENT", "Séquestre");
        labelMap.put("ON_RAMP", "Dépôt");
        labelMap.put("OFF_RAMP", "Retrait");
        labelMap.put("NFC", "NFC");
        labelMap.put("MOBILE_MONEY", "Mobile Money");
        Map<String, String> colorMap = new HashMap<>();
        colorMap.put("Transfert", "#3b82f6");
        colorMap.put("Séquestre", "#10b981");
        colorMap.put("Dépôt", "#22c55e");
        colorMap.put("Retrait", "#ef4444");
        colorMap.put("NFC", "#8b5cf6");
        colorMap.put("Mobile Money", "#ec4899");
        Map<String, Long> countByLabel = new LinkedHashMap<>();
        try {
            for (Object[] row : operationRepository.countByDesignation()) {
                String designation = row[0] != null ? ((String) row[0]).toUpperCase() : "";
                long count = ((Number) row[1]).longValue();
                String label = "Autre";
                for (Map.Entry<String, String> entry : labelMap.entrySet()) {
                    if (designation.contains(entry.getKey())) {
                        label = entry.getValue();
                        break;
                    }
                }
                countByLabel.merge(label, count, Long::sum);
            }
        } catch (Exception ignored) {}
        List<TransactionTypeDto> result = new ArrayList<>();
        for (Map.Entry<String, Long> entry : countByLabel.entrySet()) {
            result.add(TransactionTypeDto.builder()
                    .name(entry.getKey())
                    .value(entry.getValue())
                    .color(colorMap.getOrDefault(entry.getKey(), "#6b7280"))
                    .build());
        }
        result.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        return result;
    }

    @Override
    public List<VolumeDayDto> getVolume(String period) {
        String[] DAY_LABELS = {"Dim","Lun","Mar","Mer","Jeu","Ven","Sam"};
        LocalDateTime since = LocalDateTime.now().minusDays(7).withHour(0).withMinute(0).withSecond(0);
        Map<String, Double> volumeByDate = new HashMap<>();
        try {
            for (Object[] row : operationRepository.sumVolumeUsdByDay(since)) {
                int year = ((Number) row[0]).intValue();
                int month = ((Number) row[1]).intValue();
                int day = ((Number) row[2]).intValue();
                double vol = row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;
                volumeByDate.put(year + "-" + month + "-" + day, vol);
            }
        } catch (Exception ignored) {}
        List<VolumeDayDto> result = new ArrayList<>();
        LocalDate cursor = LocalDate.now().minusDays(6);
        for (int i = 0; i < 7; i++) {
            String key = cursor.getYear() + "-" + cursor.getMonthValue() + "-" + cursor.getDayOfMonth();
            double vol = volumeByDate.getOrDefault(key, 0.0);
            result.add(VolumeDayDto.builder()
                    .jour(DAY_LABELS[cursor.getDayOfWeek().getValue() % 7])
                    .volume(Math.round(vol * 100.0) / 100.0)
                    .build());
            cursor = cursor.plusDays(1);
        }
        return result;
    }

    @Override
    public List<TopUserDto> getTopUsers(int limit, String sort) {
        int effectiveLimit = Math.min(Math.max(1, limit), 50);
        org.springframework.data.domain.Pageable pageable =
                org.springframework.data.domain.PageRequest.of(0, effectiveLimit * 3); // over-fetch to deduplicate

        // username → (volume, nbTx, devise) — keep best devise per user
        Map<String, TopUserDto> byUser = new LinkedHashMap<>();
        try {
            for (Object[] row : operationRepository.topUsersByVolumeWithDevise(pageable)) {
                String username = (String) row[0];
                long   nbTx    = ((Number) row[1]).longValue();
                double volume  = row[2] != null ? ((Number) row[2]).doubleValue() : 0.0;
                String devise  = row[3] != null ? (String) row[3] : null;
                if (byUser.containsKey(username)) {
                    // merge: add tx count and volume from other devises
                    TopUserDto existing = byUser.get(username);
                    long   totalNb  = existing.getNbTx() + nbTx;
                    double totalVol = existing.getVolume() + volume;
                    byUser.put(username, TopUserDto.builder()
                            .nom(username)
                            .volume(Math.round(totalVol * 100.0) / 100.0)
                            .nbTx(totalNb)
                            .avgTx(totalNb > 0 ? Math.round((totalVol / totalNb) * 100.0) / 100.0 : 0.0)
                            .devise(existing.getDevise()) // keep first devise (highest volume)
                            .build());
                } else {
                    double avgTx = nbTx > 0 ? Math.round((volume / nbTx) * 100.0) / 100.0 : 0.0;
                    byUser.put(username, TopUserDto.builder()
                            .nom(username)
                            .volume(Math.round(volume * 100.0) / 100.0)
                            .nbTx(nbTx)
                            .avgTx(avgTx)
                            .devise(devise)
                            .build());
                }
            }
        } catch (Exception ignored) {}

        List<TopUserDto> result = new ArrayList<>(byUser.values());
        result.sort((a, b) -> Double.compare(b.getVolume(), a.getVolume()));
        return result.stream().limit(effectiveLimit).collect(Collectors.toList());
    }

    @Override
    public List<GeoTransactionDto> getGeoTransactionsByCountry() {
        Map<String, long[]> byDevise = new LinkedHashMap<>();
        try {
            for (Object[] row : operationRepository.countByDeviseAndType()) {
                String devise = (String) row[0];
                String type = (String) row[1];
                long count = ((Number) row[2]).longValue();
                byDevise.computeIfAbsent(devise, k -> new long[2]);
                if ("DEBIT".equalsIgnoreCase(type)) byDevise.get(devise)[0] += count;
                else if ("CREDIT".equalsIgnoreCase(type)) byDevise.get(devise)[1] += count;
            }
        } catch (Exception ignored) {}
        List<GeoTransactionDto> result = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : byDevise.entrySet()) {
            result.add(GeoTransactionDto.builder()
                    .pays(entry.getKey())
                    .envois(entry.getValue()[0])
                    .receptions(entry.getValue()[1])
                    .build());
        }
        result.sort((a, b) -> Long.compare(b.getEnvois() + b.getReceptions(), a.getEnvois() + a.getReceptions()));
        return result.stream().limit(8).collect(Collectors.toList());
    }

    private PaymentLinkDto toPaymentLinkDto(PaymentLink pl) {
        return PaymentLinkDto.builder()
                .id(String.valueOf(pl.getId()))
                .merchantId(pl.getCreator() != null ? pl.getCreator().getUserId() : null)
                .merchantName(pl.getCreator() != null ? pl.getCreator().getUsername() : null)
                .title(pl.getDescription())
                .description(pl.getDescription())
                .slug(pl.getUniqueCode())
                .url("/pay/" + pl.getUniqueCode())
                .amount(pl.getAmount())
                .currency(pl.getCurrency())
                .status(Boolean.TRUE.equals(pl.getIsActive()) ? "active" : "inactive")
                .currentUses(pl.getTotalPayments() != null ? pl.getTotalPayments() : 0)
                .totalCollected(pl.getTotalAmountReceived() != null ? pl.getTotalAmountReceived() : 0.0)
                .expiresAt(pl.getExpiresAt() != null ? pl.getExpiresAt().format(ISO) : null)
                .createdAt(pl.getCreatedAt() != null ? pl.getCreatedAt().format(ISO) : null)
                .updatedAt(pl.getUpdatedAt() != null ? pl.getUpdatedAt().format(ISO) : null)
                .build();
    }
}
