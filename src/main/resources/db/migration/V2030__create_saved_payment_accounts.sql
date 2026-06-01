-- Comptes sources Yellow Card / Mobile Money sauvegardés par utilisateur (app mobile).

CREATE TABLE IF NOT EXISTS saved_payment_accounts (
    id                      VARCHAR(36) PRIMARY KEY,
    username                VARCHAR(50)  NOT NULL,
    operator_name           VARCHAR(255) NOT NULL,
    operator_type           VARCHAR(50)  NOT NULL,
    account_number          VARCHAR(120) NOT NULL,
    account_name            VARCHAR(255) NOT NULL,
    account_bank            VARCHAR(255),
    country_code            VARCHAR(10)  NOT NULL,
    currency_code           VARCHAR(10)  NOT NULL,
    network_id              VARCHAR(120) NOT NULL,
    id_type                 VARCHAR(50)  NOT NULL,
    id_number               VARCHAR(120) NOT NULL,
    additional_id_type      VARCHAR(50),
    additional_id_number    VARCHAR(120),
    date_of_birth           VARCHAR(32)  NOT NULL,
    address                 TEXT,
    email                   VARCHAR(255),
    is_default              BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at              TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_saved_payment_accounts_username
    ON saved_payment_accounts (username);

COMMENT ON TABLE saved_payment_accounts IS 'Comptes de paiement source enregistrés par l’utilisateur (MoMo, banque, manual input)';
