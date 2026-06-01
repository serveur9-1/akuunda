package org.akuunda.akuundawallet.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class WebSecurityConfig {

    private final JwtAuthConverter jwtAuthConverter;

    public WebSecurityConfig(JwtAuthConverter jwtAuthConverter) {
        this.jwtAuthConverter = jwtAuthConverter;
    }

    // ================================================================
    //  WHITELIST — endpoints publics (pas besoin de JWT)
    // ================================================================
    private static final String[] AUTH_WHITELIST = {
            // -- Swagger UI v2
            "/v2/api-docs",
            "v2/api-docs",
            "/swagger-resources",
            "swagger-resources",
            "/swagger-resources/**",
            "swagger-resources/**",
            "/configuration/ui",
            "configuration/ui",
            "/configuration/security",
            "configuration/security",
            "/swagger-ui.html",
            "swagger-ui.html",
            "webjars/**",
            // -- Swagger UI v3
            "/v3/api-docs/**",
            "/v3/api-docs",
            "v3/api-docs/**",
            "/v3/api-docs**",
            "/swagger-ui/**",
            "swagger-ui/**",
            // CSA Controllers
            "/csa/api/token",
            // Actuators
            "/actuator/**",
            "/health/**",
            // Payment Links Web Endpoints (Public, no authentication required)
            "/api/internal/v1/payment-links/web/**",
            // One-Time Payment Link public endpoints (quotes and pay)
            "/api/internal/v1/one-time-payment-links/*/quotes",
            "/api/internal/v1/one-time-payment-links/*/pay",
            "/api/internal/v1/one-time-payment-links/*/pay/yellowcard",
            "/api/internal/v1/one-time-payment-links/confirmation/*",
            "/api/internal/v1/auth/**",
            // Social login (sans JWT — retourne un social JWT)
            "/api/v1/*/users/social-login",
            // Backoffice Auth (sans JWT)
            "/api/v1/auth/login",
            "/api/v1/auth/login/email",
            "/api/v1/auth/login/admin",
            "/api/v1/auth/login/pro",
            "/api/v1/auth/register",
            "/api/v1/auth/register/admin",
            "/api/v1/auth/register/pro",
            "/api/v1/auth/login/google",
            "/api/v1/auth/login/microsoft",
            "/api/v1/auth/refresh",
            "/api/v1/auth/logout",
            "/api/v1/auth/logout/all",
            "/api/internal/v1/meld/webhook",
            "/api/internal/v1/yellowcard-webhook/**",
            // Permanent Payment Links public endpoints
            "/api/internal/v1/permanent-links/m/**",
            "/api/internal/v1/permanent-links/session/**",
            "/api/internal/v1/permanent-links/yellow-card/**",
            "/api/internal/v1/permanent-links/meld/**",
            "/api/internal/v1/permanent-links/pay-redirect/**",
            // Pay redirect endpoint (public)
            "/api/internal/v1/pay-redirect/**",
            // Currency public endpoints (read-only reference data)
            "/api/internal/v1/currency/**",
            // Checkout public endpoints (GET by code, no auth required) — lu par la page de paiement hébergée
            "/api/v1/checkout/**",
            // Payments API (auth par X-API-Key / Bearer dans le contrôleur, pas de JWT)
            "/api/v1/payments",
            "/api/v1/payments/**",
            // Partner contract — endpoints publics (page web + paiement)
            "/api/internal/v1/partner-contracts/payments/*/web",
            "/api/internal/v1/partner-contracts/payments/*/quotes",
            "/api/internal/v1/partner-contracts/payments/*/pay/meld",
            "/api/internal/v1/partner-contracts/payments/*/pay/yellowcard",
            // Booking endpoints (hotels and transport)
            "/api/internal/v1/booking/**",

            // eSIM activation page — public (Safari ne peut pas envoyer de JWT)
            "/api/internal/v1/esim/activate-page",

            // ═══════════════════════════════════════════════════════
            //  KYRREX — Webhooks (appelés par Kyrrex, pas de JWT)
            //  La vérification se fait via HMAC dans le contrôleur
            // ═══════════════════════════════════════════════════════
            "/api/v1/kyrrex/webhook",
            "/api/webhooks/v1/kyrrex/**",

            // ═══════════════════════════════════════════════════════
            //  KYRREX — Données publiques de référence (read-only)
            // ═══════════════════════════════════════════════════════
            "/api/v1/kyrrex/markets",
            "/api/v1/kyrrex/markets/**",
            "/api/v1/kyrrex/fiat/providers",
            "/api/v1/kyrrex/fiat/provider-methods",
            "/api/v1/kyrrex/fiat/provider-currencies"
    };

    // ================================================================
    //  1. CORS — mêmes domaines + header webhook Kyrrex
    // ================================================================
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowedOrigins(Arrays.asList(
                "https://wallet.akuunda-pay.io",
                "https://walletdev.akuunda-pay.io",
                "https://walletpreprod.akuunda-pay.io",
                "https://panel.akuunda-pay.io",
                "https://localhost:8089",
                "https://qr.akuunda-pay.io",
                "https://qr-api.akuunda-pay.io",
                "http://localhost:5173"
        ));

        config.setAllowedMethods(Arrays.asList(
                "GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"
        ));

        config.setAllowedHeaders(Arrays.asList(
                "Authorization",
                "Content-Type",
                "Origin",
                "Accept",
                "X-Requested-With",
                "Access-Control-Request-Method",
                "Access-Control-Request-Headers",
                "Cache-Control",
                // ── Kyrrex Webhook signature header ──
                "X-Webhook-Signature",
                // ── Merchant Payments API (clé API + idempotence) ──
                "X-API-Key",
                "Idempotency-Key"
        ));

        config.setExposedHeaders(Arrays.asList(
                "Authorization",
                "Content-Disposition",
                "X-Total-Count"
        ));

        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // ================================================================
    //  2. SECURITY FILTER CHAIN
    // ================================================================
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
            // ── CORS avec source explicite ──
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // ── CSRF désactivé (API stateless) ──
            .csrf(AbstractHttpConfigurer::disable)

            // ── Authorization ──
            .authorizeHttpRequests(auth -> auth
                // Preflight OPTIONS : TOUJOURS autoriser sur toutes les routes
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()

                // ═══════════════════════════════════════════════
                //  KYRREX — Webhook POST uniquement (sans JWT)
                //  La vérification se fait via HMAC dans le code
                // ═══════════════════════════════════════════════
                .requestMatchers(HttpMethod.POST, "/api/v1/kyrrex/webhook").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/webhooks/v1/kyrrex/**").permitAll()

                // ═══════════════════════════════════════════════
                //  KYRREX — Markets & Fiat Providers (GET public)
                // ═══════════════════════════════════════════════
                .requestMatchers(HttpMethod.GET, "/api/v1/kyrrex/markets").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/kyrrex/markets/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/kyrrex/fiat/providers").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/kyrrex/fiat/provider-methods").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/kyrrex/fiat/provider-currencies").permitAll()

                // ═══════════════════════════════════════════════
                //  KYRREX — Tout le reste : JWT obligatoire
                //  (wallets, deposits, withdrawals, swap, orders,
                //   cards, bank accounts, KYC, sessions, etc.)
                // ═══════════════════════════════════════════════
                .requestMatchers("/api/v1/kyrrex/**").authenticated()

                // Whitelist publique existante
                .requestMatchers(AUTH_WHITELIST).permitAll()

                // Tout le reste nécessite un JWT valide
                .anyRequest().authenticated()
            )

            // ── OAuth2 Resource Server (JWT) ──
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthConverter))
            )

            // ── Session stateless ──
            .sessionManagement(sess ->
                sess.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            );

        return http.build();
    }
}
