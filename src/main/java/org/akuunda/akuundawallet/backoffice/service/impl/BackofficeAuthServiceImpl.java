package org.akuunda.akuundawallet.backoffice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.backoffice.config.BackofficeJwtConfig;
import org.akuunda.akuundawallet.backoffice.dto.auth.BackofficeLoginRequest;
import org.akuunda.akuundawallet.backoffice.dto.auth.BackofficeLoginResponse;
import org.akuunda.akuundawallet.backoffice.dto.auth.BackofficeMeResponse;
import org.akuunda.akuundawallet.backoffice.entity.BackofficeUser;
import org.akuunda.akuundawallet.backoffice.exception.BackofficeAuthException;
import org.akuunda.akuundawallet.backoffice.repository.BackofficeUserRepository;
import org.akuunda.akuundawallet.backoffice.service.BackofficeAuthService;
import org.akuunda.akuundawallet.backoffice.service.BackofficeTokenService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class BackofficeAuthServiceImpl implements BackofficeAuthService {

    private final BackofficeUserRepository backofficeUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final BackofficeTokenService backofficeTokenService;

    @Override
    public BackofficeLoginResponse login(BackofficeLoginRequest request) {
        String normalizedPortal = normalizePortal(request.getPortal());
        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);

        BackofficeUser user = backofficeUserRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new BackofficeAuthException("Invalid email or password"));

        if (!user.isEnabled()) {
            throw new BackofficeAuthException("Account is disabled");
        }
        if (!normalizedPortal.equals(user.getPortal())) {
            throw new BackofficeAuthException("Invalid email or password for this portal");
        }
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new BackofficeAuthException("Invalid email or password");
        }

        String accessToken = backofficeTokenService.createAccessToken(user);
        String displayName = buildDisplayName(user);

        BackofficeLoginResponse.BackofficeUserInfo userInfo = BackofficeLoginResponse.BackofficeUserInfo.builder()
                .id(user.getId().toString())
                .name(displayName)
                .email(user.getEmail())
                .role("admin".equals(normalizedPortal) ? "admin" : "pro")
                .permissions(Collections.emptyList())
                .portal(normalizedPortal)
                .twoFactorRequired(false)
                .build();

        return BackofficeLoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(null)
                .expiresIn(backofficeTokenService.getExpiresInSeconds())
                .tokenType("Bearer")
                .user(userInfo)
                .build();
    }

    private static String buildDisplayName(BackofficeUser user) {
        String fn = user.getFirstName() != null ? user.getFirstName().trim() : "";
        String ln = user.getLastName() != null ? user.getLastName().trim() : "";
        String both = (fn + " " + ln).trim();
        return both.isEmpty() ? user.getEmail() : both;
    }

    private String normalizePortal(String portal) {
        if (portal == null) {
            throw new BackofficeAuthException("Portal is required");
        }
        String normalized = portal.trim().toLowerCase(Locale.ROOT);
        if (!"admin".equals(normalized) && !"pro".equals(normalized)) {
            throw new BackofficeAuthException("Invalid portal. Expected 'admin' or 'pro'");
        }
        return normalized;
    }

    @Override
    public BackofficeMeResponse me() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth instanceof JwtAuthenticationToken)) {
            throw new BackofficeAuthException("Not authenticated");
        }
        Jwt jwt = ((JwtAuthenticationToken) auth).getToken();
        String iss = jwt.hasClaim("iss") ? Objects.toString(jwt.getClaim("iss"), null) : null;
        if (iss == null && jwt.getIssuer() != null) {
            iss = jwt.getIssuer().toString();
        }

        if (BackofficeJwtConfig.BACKOFFICE_ISSUER.equals(iss)) {
            String id = jwt.getSubject();
            String email = jwt.getClaimAsString("email");
            String portal = jwt.getClaimAsString("portal");
            String name = jwt.getClaimAsString("name");
            if (name == null) {
                name = email;
            }
            List<String> permissions = auth.getAuthorities().stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
            return BackofficeMeResponse.builder()
                    .id(id)
                    .name(name != null ? name : "")
                    .email(email != null ? email : "")
                    .role(portal != null ? portal : "user")
                    .permissions(permissions)
                    .portal(portal != null ? portal : "admin")
                    .twoFactorEnabled(false)
                    .build();
        }

        String sub = jwt.getSubject();
        String email = jwt.getClaimAsString("email");
        if (email == null) {
            email = jwt.getClaimAsString("preferred_username");
        }
        if (email == null) {
            email = sub;
        }
        String name = jwt.getClaimAsString("name");
        if (name == null) {
            name = email;
        }

        List<String> permissions = auth.getAuthorities().stream()
                .map(Object::toString)
                .collect(Collectors.toList());

        return BackofficeMeResponse.builder()
                .id(sub)
                .name(name)
                .email(email)
                .role(permissions.isEmpty() ? "user" : permissions.get(0))
                .permissions(permissions)
                .portal("admin")
                .twoFactorEnabled(false)
                .build();
    }
}
