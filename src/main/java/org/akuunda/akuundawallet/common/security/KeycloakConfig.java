package org.akuunda.akuundawallet.common.security;

import org.keycloak.OAuth2Constants;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeycloakConfig {

    @Value("${keycloak.clientId}")
    private String adminClientId;

    @Value("${keycloak.clientSecret}")
    private String adminClientSecret;

    @Value("${keycloak.url.auth}")
    private String authServerUrl;

    @Value("${keycloak.realm}")
    private String realm;

    @Bean
    public Keycloak keycloak() {
        // Éviter les espaces accidentels dans application*.properties ("keycloak.realm= akuunda-realm")
        String r = realm != null ? realm.trim() : "";
        String url = authServerUrl != null ? authServerUrl.trim() : "";
        String cid = adminClientId != null ? adminClientId.trim() : "";
        String secret = adminClientSecret != null ? adminClientSecret.trim() : "";
        return KeycloakBuilder.builder()
                .serverUrl(url)
                .realm(r)
                .grantType(OAuth2Constants.CLIENT_CREDENTIALS)
                .clientId(cid)
                .clientSecret(secret)
                .build();
    }
}
