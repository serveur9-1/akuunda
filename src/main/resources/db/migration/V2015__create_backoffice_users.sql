-- Comptes backoffice (auth locale, sans Keycloak)
CREATE TABLE IF NOT EXISTS backoffice_user (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(255),
    last_name VARCHAR(255),
    portal VARCHAR(16) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_backoffice_user_email UNIQUE (email),
    CONSTRAINT ck_backoffice_user_portal CHECK (portal IN ('admin', 'pro'))
);

CREATE INDEX IF NOT EXISTS idx_backoffice_user_email_lower ON backoffice_user (LOWER(email));
