package org.akuunda.akuundawallet.common.security;

import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    // ═══════════════════════════════════════════════════════════════
    // BOOKING - Hotels & Transport
    // ═══════════════════════════════════════════════════════════════
    @Bean
    public GroupedOpenApi bookingApi() {
        return GroupedOpenApi.builder()
                .group("Booking - Hotels & Transport")
                .pathsToMatch("/api/internal/v1/booking/**")
                .build();
    }

    @Bean
    public GroupedOpenApi smsApi() {
        return GroupedOpenApi.builder()
                .group("Akuunda Services - Sms")
                .pathsToMatch("/api/internal/v1/sms/**")
                .build();
    }

    @Bean
    public GroupedOpenApi meldApi() {
        return GroupedOpenApi.builder()
                .group("Akuunda Services - Meld")
                .pathsToMatch("/api/internal/v1/meld/**")
                .build();
    }

    @Bean
    public GroupedOpenApi esimApi() {
        return GroupedOpenApi.builder()
                .group("Akuunda Services - Transatel - eSIM")
                .packagesToScan("org.akuunda.akuundawallet.esim.impl.controller")
                .pathsToMatch("/api/internal/v1/esim/**")
                .build();
    }

    @Bean
    public GroupedOpenApi authenticationApi() {
        return GroupedOpenApi.builder()
                .group("Akuunda Services - Authentication")
                .pathsToMatch("/api/internal/v1/auth/**")
                .build();
    }

    @Bean
    public GroupedOpenApi guardarianApi() {
        return GroupedOpenApi.builder()
                .group("Akuunda Services - Guadarian")
                .pathsToMatch("/api/internal/v1/guardarian/**")
                .build();
    }

    @Bean
    public GroupedOpenApi sumsubApi() {
        return GroupedOpenApi.builder()
                .group("Akuunda Services - Sumsub")
                .pathsToMatch("/api/internal/v1/sumsub/**")
                .build();
    }

    @Bean
    public GroupedOpenApi reviewRequestApi() {
        return GroupedOpenApi.builder()
                .group("Akunnda - Review - Identity")
                .pathsToMatch("/api/public/v1/review/**")
                .build();
    }

    @Bean
    public GroupedOpenApi akunndaUserApi() {
        return GroupedOpenApi.builder()
                .group("Akuunda Services - Users")
                .pathsToMatch("/api/internal/v1/users/**")
                .build();
    }

    @Bean
    public GroupedOpenApi signingMethodApi() {
        return GroupedOpenApi.builder()
                .group("Akuunda Services - Signing Methods")
                .pathsToMatch("/api/internal/v1/signing-methods/**")
                .build();
    }

    @Bean
    public GroupedOpenApi databaseBackupApi() {
        return GroupedOpenApi.builder()
                .group("Akuunda Services - DatabaseBackup")
                .pathsToMatch("/api/database-backup/**")
                .build();
    }

    @Bean
    public GroupedOpenApi countryApi() {
        return GroupedOpenApi.builder()
                .group("Akuunda Services - Country")
                .pathsToMatch("/api/internal/v1/country/**")
                .build();
    }

    @Bean
    public GroupedOpenApi countryCurrencyApi() {
        return GroupedOpenApi.builder()
                .group("Akuunda Services - Currency-Country")
                .pathsToMatch("/api/internal/v1/currency/**")
                .build();
    }

    @Bean
    public GroupedOpenApi otpApi() {
        return GroupedOpenApi.builder()
                .group("Akuunda Services - Otp")
                .pathsToMatch("/api/internal/v1/otp/**")
                .build();
    }

    @Bean
    public GroupedOpenApi transferApi() {
        return GroupedOpenApi.builder()
                .group("Akunnda - Transfert")
                .pathsToMatch("/api/internal/v1/transfer/**")
                .build();
    }

    @Bean
    public GroupedOpenApi currencyFreaksApi() {
        return GroupedOpenApi.builder()
                .group("Akunnda - Currency Conversion")
                .pathsToMatch("/api/internal/v1/currency/convert/**")
                .build();
    }

    @Bean
    public GroupedOpenApi transactionStatusApi() {
        return GroupedOpenApi.builder()
                .group("Akunnda - Transactions - Paiment")
                .pathsToMatch("/api/internal/v1/transactions/**")
                .build();
    }

    @Bean
    public GroupedOpenApi akunndaOperationsApi() {
        return GroupedOpenApi.builder()
                .group("Akuunda Services - Operations")
                .pathsToMatch("/api/internal/v1/operations/**")
                .build();
    }

    @Bean
    public GroupedOpenApi akunndaYellowCardsApi() {
        return GroupedOpenApi.builder()
                .group("Akuunda Services - Yellow cards")
                .pathsToMatch("/api/internal/v1/yellow-card/**")
                .build();
    }

    @Bean
    public GroupedOpenApi akunndaMtPelerinApi() {
        return GroupedOpenApi.builder()
                .group("Akuunda Services - MtPelerin")
                .pathsToMatch("/api/internal/v1/mtpelerin/**")
                .build();
    }

    @Bean
    public GroupedOpenApi akunndaOnRampMoneyApi() {
        return GroupedOpenApi.builder()
                .group("Akuunda Services - OnRamp Money")
                .pathsToMatch("/api/internal/v1/onramp-money/**")
                .build();
    }

    @Bean
    public GroupedOpenApi pinApi() {
        return GroupedOpenApi.builder()
                .group("Akuunda Services - PIN")
                .pathsToMatch("/api/internal/v1/pin/**")
                .build();
    }

    @Bean
    public GroupedOpenApi emergencyCodeApi() {
        return GroupedOpenApi.builder()
                .group("Akuunda Services - Emergency Code")
                .pathsToMatch("/api/internal/v1/emergency-code/**")
                .build();
    }

    @Bean
    public GroupedOpenApi paymentLinksApi() {
        return GroupedOpenApi.builder()
                .group("Akuunda Services - Payment Links")
                .pathsToMatch("/api/internal/v1/payment-links/**")
                .build();
    }

    @Bean
    public GroupedOpenApi oneTimePaymentLinksApi() {
        return GroupedOpenApi.builder()
                .group("Akuunda Services - One-Time Payment Links")
                .pathsToMatch("/api/internal/v1/one-time-payment-links/**")
                .build();
    }

    @Bean
    public GroupedOpenApi permanentPaymentLinksApi() {
        return GroupedOpenApi.builder()
                .group("Akuunda Services - Permanent Payment Links")
                .pathsToMatch("/api/internal/v1/permanent-links/**")
                .build();
    }

    @Bean
    public GroupedOpenApi feesCalculationApi() {
        return GroupedOpenApi.builder()
                .group("Akuunda Services - Fees Calculation")
                .pathsToMatch("/api/internal/v1/fees/**")
                .build();
    }

    @Bean
    public GroupedOpenApi conditionalPaymentsApi() {
        return GroupedOpenApi.builder()
                .group("Akuunda Services - Conditional Payments")
                .pathsToMatch("/api/internal/v1/conditional-payments/**")
                .build();
    }

    @Bean
    public GroupedOpenApi partnerContractsApi() {
        return GroupedOpenApi.builder()
                .group("Akuunda Services - Partner Smart Contracts")
                .pathsToMatch("/api/internal/v1/partner-contracts/**")
                .build();
    }

    @Bean
    public GroupedOpenApi kyrrexApi() {
        return GroupedOpenApi.builder()
                .group("Akuunda Services - Kyrrex Malta Business")
                .pathsToMatch("/api/internal/v1/kyrrex/**")
                .build();
    }

    @Bean
    public GroupedOpenApi transfiApi() {
        return GroupedOpenApi.builder()
                .group("Akuunda Services - TransFi")
                .pathsToMatch("/api/internal/v1/transfi/**")
                .build();
    }

    @Bean
    public GroupedOpenApi kyrrexWebhooksApi() {
        return GroupedOpenApi.builder()
                .group("Akuunda Services - Kyrrex Webhooks")
                .pathsToMatch("/api/webhooks/v1/kyrrex/**")
                .build();
    }

    // ═══════════════════════════════════════════════════════════════
    // Integration marchand (nouveau parcours simplifié)
    // ═══════════════════════════════════════════════════════════════

    /** API publique consommée par les intégrateurs marchand (clé API). */
    @Bean
    public GroupedOpenApi paymentsApi() {
        return GroupedOpenApi.builder()
                .group("Merchant API - Payments")
                .pathsToMatch("/api/v1/payments/**")
                .build();
    }

    /** Page de paiement hébergée (lecture publique d'un checkout par son code). */
    @Bean
    public GroupedOpenApi checkoutPublicApi() {
        return GroupedOpenApi.builder()
                .group("Merchant API - Checkout (public)")
                .pathsToMatch("/api/v1/checkout/**")
                .build();
    }

    /** Gestion des clés API marchand côté dashboard (JWT). */
    @Bean
    public GroupedOpenApi merchantKeysApi() {
        return GroupedOpenApi.builder()
                .group("Merchant API - Keys (dashboard)")
                .pathsToMatch("/api/v1/merchant/keys/**", "/api/v1/merchant/keys")
                .build();
    }

    // -------------------------------------------------------------------------
    // Backoffice (Admin & Pro)
    // -------------------------------------------------------------------------

    @Bean
    public GroupedOpenApi backofficeAuthApi() {
        return GroupedOpenApi.builder()
                .group("Backoffice - Auth")
                .pathsToMatch("/api/v1/auth/**")
                .build();
    }

    @Bean
    public GroupedOpenApi backofficeAdminApi() {
        return GroupedOpenApi.builder()
                .group("Backoffice - Admin")
                .pathsToMatch("/api/v1/admin/**")
                .build();
    }

    @Bean
    public GroupedOpenApi backofficeProApi() {
        return GroupedOpenApi.builder()
                .group("Backoffice - Pro")
                .pathsToMatch("/api/v1/pro/**")
                .build();
    }

    // -------------------------------------------------------------------------
    // FCM Push Notifications
    // -------------------------------------------------------------------------

    @Bean
    public GroupedOpenApi deviceTokenApi() {
        return GroupedOpenApi.builder()
                .group("Akuunda Services - Device Token (FCM)")
                .pathsToMatch("/api/v1/devices/**")
                .build();
    }

    @Bean
    public GroupedOpenApi notificationsAdminApi() {
        return GroupedOpenApi.builder()
                .group("Akuunda Services - Notifications Admin (FCM)")
                .pathsToMatch("/api/admin/v1/notifications/**")
                .build();
    }
}
