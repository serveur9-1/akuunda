package org.akuunda.akuundawallet.backoffice.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.annotation.Order;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * JWT HMAC pour le backoffice (issuer {@link #BACKOFFICE_ISSUER}), distinct des JWT Keycloak.
 */
@Configuration
@Order(1)
public class BackofficeJwtConfig {

    public static final String BACKOFFICE_ISSUER = "akuunda-backoffice";
    public static final String SOCIAL_ISSUER     = "akuunda-social";

    private static byte[] deriveHmacKey(String secret) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return md.digest(secret.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Bean
    JwtEncoder backofficeJwtEncoder(@Value("${backoffice.jwt.secret}") String secret) {
        byte[] key = deriveHmacKey(secret);
        try {
            OctetSequenceKey jwk = new OctetSequenceKey.Builder(key)
                .algorithm(JWSAlgorithm.HS256)
                .build();
            JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(jwk));
            return new NimbusJwtEncoder(jwkSource);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot build backoffice JWT encoder", e);
        }
    }

    @Bean(name = "backofficeSymmetricJwtDecoder")
    JwtDecoder backofficeSymmetricJwtDecoder(@Value("${backoffice.jwt.secret}") String secret) {
        byte[] key = deriveHmacKey(secret);
        SecretKey sk = new SecretKeySpec(key, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(sk).build();
    }

    @Bean(name = "keycloakIssuerJwtDecoder")
    JwtDecoder keycloakIssuerJwtDecoder(
            @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String issuerUri) {
        String iss = issuerUri != null ? issuerUri.trim() : "";
        if (iss.isEmpty()) {
            throw new IllegalStateException("spring.security.oauth2.resourceserver.jwt.issuer-uri is required");
        }
        return JwtDecoders.fromIssuerLocation(iss);
    }

    @Bean
    JwtEncoder socialJwtEncoder(@Value("${social.jwt.secret:akuunda-social-login-secret-min-32-chars}") String secret) {
        byte[] key = deriveHmacKey(secret);
        try {
            OctetSequenceKey jwk = new OctetSequenceKey.Builder(key)
                .algorithm(JWSAlgorithm.HS256)
                .build();
            JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(jwk));
            return new NimbusJwtEncoder(jwkSource);
        } catch (Exception e) {
            throw new IllegalStateException("Cannot build social JWT encoder", e);
        }
    }

    @Bean(name = "socialSymmetricJwtDecoder")
    JwtDecoder socialSymmetricJwtDecoder(@Value("${social.jwt.secret:akuunda-social-login-secret-min-32-chars}") String secret) {
        byte[] key = deriveHmacKey(secret);
        SecretKey sk = new SecretKeySpec(key, "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(sk).build();
    }

    @Bean
    @Primary
    JwtDecoder compositeJwtDecoder(
            @org.springframework.beans.factory.annotation.Qualifier("keycloakIssuerJwtDecoder")
            JwtDecoder keycloakDecoder,
            @org.springframework.beans.factory.annotation.Qualifier("backofficeSymmetricJwtDecoder")
            JwtDecoder backofficeDecoder,
            @org.springframework.beans.factory.annotation.Qualifier("socialSymmetricJwtDecoder")
            JwtDecoder socialDecoder) {
        ObjectMapper mapper = new ObjectMapper();
        return token -> {
            String iss = peekIss(token, mapper);
            if (BACKOFFICE_ISSUER.equals(iss)) {
                return backofficeDecoder.decode(token);
            }
            if (SOCIAL_ISSUER.equals(iss)) {
                return socialDecoder.decode(token);
            }
            return keycloakDecoder.decode(token);
        };
    }

    private static String peekIss(String token, ObjectMapper mapper) {
        try {
            String[] p = token.split("\\.");
            if (p.length < 2) {
                return null;
            }
            byte[] payload = Base64.getUrlDecoder().decode(p[1]);
            JsonNode root = mapper.readTree(payload);
            JsonNode iss = root.get("iss");
            return iss != null && iss.isTextual() ? iss.asText() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
