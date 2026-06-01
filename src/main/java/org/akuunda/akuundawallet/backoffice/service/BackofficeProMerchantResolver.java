package org.akuunda.akuundawallet.backoffice.service;

import lombok.RequiredArgsConstructor;
import org.akuunda.akuundawallet.backoffice.repository.BackofficeUserRepository;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

/** Résout le username wallet Akuunda du marchand connecté (JWT email → backoffice_user / users). */
@Component
@RequiredArgsConstructor
public class BackofficeProMerchantResolver {

    private final BackofficeUserRepository backofficeUserRepository;
    private final UserRepository userRepository;

    public String resolveWalletUsername() {
        JwtAuthenticationToken auth = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = auth.getToken();
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email absent du token JWT");
        }
        Optional<String> fromBackoffice = backofficeUserRepository.findByEmailIgnoreCase(email.trim())
                .map(u -> u.getWalletUsername())
                .filter(w -> w != null && !w.isBlank());
        if (fromBackoffice.isPresent()) {
            return fromBackoffice.get();
        }
        Optional<Users> akuundaUser = userRepository.findFirstByEmailOrderByCreatedAtAsc(email.trim());
        if (akuundaUser.isPresent()) {
            String username = akuundaUser.get().getUsername();
            if (username != null && !username.isBlank()) {
                return username;
            }
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Aucun compte Akuunda Pay associé à cet email (" + email + ")");
    }
}
