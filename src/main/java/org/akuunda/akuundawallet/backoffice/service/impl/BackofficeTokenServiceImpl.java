package org.akuunda.akuundawallet.backoffice.service.impl;

import org.akuunda.akuundawallet.backoffice.config.BackofficeJwtConfig;
import org.akuunda.akuundawallet.backoffice.entity.BackofficeUser;
import org.akuunda.akuundawallet.backoffice.service.BackofficeTokenService;
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
public class BackofficeTokenServiceImpl implements BackofficeTokenService {

    private final JwtEncoder jwtEncoder;

    @Value("${backoffice.jwt.expiration-seconds:28800}")
    private int expirationSeconds;

    public BackofficeTokenServiceImpl(@Qualifier("backofficeJwtEncoder") JwtEncoder jwtEncoder) {
        this.jwtEncoder = jwtEncoder;
    }

    @Override
    public String createAccessToken(BackofficeUser user) {
        Instant now = Instant.now();
        String name = buildDisplayName(user);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(BackofficeJwtConfig.BACKOFFICE_ISSUER)
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(expirationSeconds, ChronoUnit.SECONDS))
                .claim("email", user.getEmail())
                .claim("portal", user.getPortal())
                .claim("name", name)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    @Override
    public int getExpiresInSeconds() {
        return expirationSeconds;
    }

    private static String buildDisplayName(BackofficeUser user) {
        String fn = user.getFirstName() != null ? user.getFirstName().trim() : "";
        String ln = user.getLastName() != null ? user.getLastName().trim() : "";
        String both = (fn + " " + ln).trim();
        return both.isEmpty() ? user.getEmail() : both;
    }
}
