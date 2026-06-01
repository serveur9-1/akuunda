package org.akuunda.akuundawallet.backoffice.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.backoffice.dto.auth.BackofficeRegisterRequest;
import org.akuunda.akuundawallet.backoffice.dto.auth.BackofficeRegisterResponse;
import org.akuunda.akuundawallet.backoffice.entity.BackofficeUser;
import org.akuunda.akuundawallet.backoffice.exception.BackofficeRequestException;
import org.akuunda.akuundawallet.backoffice.repository.BackofficeUserRepository;
import org.akuunda.akuundawallet.backoffice.service.BackofficeRegistrationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class BackofficeRegistrationServiceImpl implements BackofficeRegistrationService {

    private final BackofficeUserRepository backofficeUserRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${backoffice.registration.enabled:true}")
    private boolean registrationEnabled;

    @Value("${backoffice.registration.allow-admin:false}")
    private boolean allowAdminRegistration;

    @Override
    @Transactional
    public BackofficeRegisterResponse register(BackofficeRegisterRequest request) {
        if (!registrationEnabled) {
            throw new BackofficeRequestException(HttpStatus.FORBIDDEN, "REGISTRATION_DISABLED",
                    "Backoffice registration is disabled (backoffice.registration.enabled=false)");
        }

        String portal = request.getPortal() == null ? "" : request.getPortal().trim().toLowerCase(Locale.ROOT);
        if (!"admin".equals(portal) && !"pro".equals(portal)) {
            throw new BackofficeRequestException(HttpStatus.BAD_REQUEST, "INVALID_PORTAL",
                    "portal must be 'admin' or 'pro'");
        }
        if ("admin".equals(portal) && !allowAdminRegistration) {
            throw new BackofficeRequestException(HttpStatus.FORBIDDEN, "ADMIN_REGISTRATION_DISABLED",
                    "Creating admin accounts via API is disabled (backoffice.registration.allow-admin=false)");
        }

        String email = request.getEmail().trim().toLowerCase(Locale.ROOT);
        if (backofficeUserRepository.existsByEmailIgnoreCase(email)) {
            throw new BackofficeRequestException(HttpStatus.CONFLICT, "EMAIL_EXISTS",
                    "An account with this email already exists");
        }

        BackofficeUser user = BackofficeUser.builder()
                .id(UUID.randomUUID())
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(trimToNull(request.getFirstName()))
                .lastName(trimToNull(request.getLastName()))
                .portal(portal)
                .enabled(true)
                .createdAt(Instant.now())
                .build();
        backofficeUserRepository.save(user);

        return BackofficeRegisterResponse.builder()
                .id(user.getId().toString())
                .email(email)
                .portal(portal)
                .message("Account created. You can sign in with email and password.")
                .build();
    }

    private static String trimToNull(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }
}
