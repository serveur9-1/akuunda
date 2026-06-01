package org.akuunda.akuundawallet.wallet.service.infrastructure;

/**
 * Aucune ligne active dans {@code kyrrex_user_credentials} pour cet utilisateur Akuunda.
 */
public class KyrrexCredentialMissingException extends RuntimeException {

    private final String requestedUsername;
    private final String credentialUsername;
    private final boolean revoked;

    public KyrrexCredentialMissingException(String requestedUsername, String credentialUsername, boolean revoked) {
        super(buildMessage(requestedUsername, credentialUsername, revoked));
        this.requestedUsername = requestedUsername;
        this.credentialUsername = credentialUsername;
        this.revoked = revoked;
    }

    public String getRequestedUsername() {
        return requestedUsername;
    }

    public String getCredentialUsername() {
        return credentialUsername;
    }

    public boolean isRevoked() {
        return revoked;
    }

    private static String buildMessage(String requested, String credential, boolean revoked) {
        if (revoked) {
            return "Credentials Kyrrex révoqués pour l'utilisateur: " + requested
                    + " (clé en base: " + credential + "). Importez via POST /api/internal/v1/kyrrex/credentials/{username}/import.";
        }
        return "Aucun credential Kyrrex trouvé pour l'utilisateur: " + requested
                + ". Importez les clés existantes via POST /api/internal/v1/kyrrex/credentials/{username}/import "
                + "(email, type, country_id).";
    }
}
