package org.akuunda.akuundawallet.wallet.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.backoffice.config.BackofficeJwtConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
@Slf4j
public class SocialTokenService {

    private final JwtEncoder socialJwtEncoder;

    @Value("${social.jwt.expiration-seconds:86400}")
    private int expirationSeconds;

    public SocialTokenService(@Qualifier("socialJwtEncoder") JwtEncoder socialJwtEncoder) {
        this.socialJwtEncoder = socialJwtEncoder;
    }

    public String generateToken(String username, String provider) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(BackofficeJwtConfig.SOCIAL_ISSUER)
                .subject(username)
                .issuedAt(now)
                .expiresAt(now.plus(expirationSeconds, ChronoUnit.SECONDS))
                .claim("provider", provider)
                .claim("preferred_username", username)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = socialJwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        log.info("Social JWT generated for username={} provider={}", username, provider);
        return token;
    }
}
