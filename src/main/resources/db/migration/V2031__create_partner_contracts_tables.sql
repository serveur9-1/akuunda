-- ============================================================
-- V2022 : Service de smart contract modulable pour partenaires
-- ============================================================

-- Table principale : configuration d'un contrat partenaire
CREATE TABLE IF NOT EXISTS partner_contracts (
    id                  BIGSERIAL PRIMARY KEY,
    contract_code       VARCHAR(50)  NOT NULL UNIQUE,
    partner_username    VARCHAR(255) NOT NULL,
    name                VARCHAR(255) NOT NULL,
    description         TEXT,
    service_type        VARCHAR(100) NOT NULL,

    -- Déclenchement de la redistribution
    -- QR_CODE | MANUAL | WEBHOOK
    trigger_type        VARCHAR(50)  NOT NULL DEFAULT 'QR_CODE',

    -- Remboursement en cas d'annulation
    -- 0.0 = remboursement total, 0.2 = 20% retenu, etc.
    cancellation_penalty_rate DECIMAL(5,4) NOT NULL DEFAULT 0.0,

    -- Délai avant la prestation (heures) pour annulation sans pénalité
    free_cancellation_hours INTEGER DEFAULT 24,

    -- La somme des pourcentages des bénéficiaires doit être <= 1.0
    -- Le reste revient au client
    active              BOOLEAN NOT NULL DEFAULT TRUE,

    created_at          TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Bénéficiaires d'un contrat (règles de redistribution)
CREATE TABLE IF NOT EXISTS partner_contract_beneficiaries (
    id                  BIGSERIAL PRIMARY KEY,
    contract_id         BIGINT NOT NULL REFERENCES partner_contracts(id),

    -- Identifiant du bénéficiaire
    beneficiary_type    VARCHAR(50)  NOT NULL,  -- VENDOR | PARTNER | OPERATOR | CUSTOM
    label               VARCHAR(255) NOT NULL,  -- Nom lisible (ex: "Hôpital Principal")
    wallet_address      VARCHAR(255),           -- Adresse wallet Polygon (prioritaire)
    username            VARCHAR(255),           -- Username Akuunda Pay (fallback)

    -- Pourcentage du montant total à redistribuer (0.0 à 1.0)
    percentage          DECIMAL(5,4) NOT NULL,

    -- Ordre d'exécution des transferts
    execution_order     INTEGER NOT NULL DEFAULT 1,

    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Paiements effectués sous un contrat partenaire
CREATE TABLE IF NOT EXISTS partner_contract_payments (
    id                      BIGSERIAL PRIMARY KEY,
    payment_code            VARCHAR(50)  NOT NULL UNIQUE,
    contract_id             BIGINT NOT NULL REFERENCES partner_contracts(id),

    -- Payer
    client_username         VARCHAR(255) NOT NULL,
    client_wallet_id        VARCHAR(255),

    -- Escrow
    escrow_wallet_id        VARCHAR(255),
    intermediate_wallet_id  VARCHAR(255),

    -- Montants
    amount                  DECIMAL(18,8) NOT NULL,
    currency                VARCHAR(10)   NOT NULL DEFAULT 'USDC',
    local_amount            DECIMAL(18,4),
    local_currency          VARCHAR(10),

    -- Statut : pending_condition | condition_validated | distributed | refunded | refunded_partial | failed
    status                  VARCHAR(50) NOT NULL DEFAULT 'pending_condition',

    -- Hashes blockchain
    deposit_tx_hash         VARCHAR(255),
    distribution_tx_hashes  TEXT,   -- JSON array des hashes de redistribution
    refund_tx_hash          VARCHAR(255),

    -- QR code (si trigger_type = QR_CODE)
    qr_token                VARCHAR(255) UNIQUE,
    qr_url                  VARCHAR(500),
    qr_expires_at           TIMESTAMP,
    qr_scanned_at           TIMESTAMP,
    qr_scanned_by           VARCHAR(255),

    -- Webhook (si trigger_type = WEBHOOK)
    webhook_url             VARCHAR(500),
    webhook_secret          VARCHAR(255),
    webhook_validated_at    TIMESTAMP,

    -- Dates
    service_start_date      TIMESTAMP,
    cancellation_deadline   TIMESTAMP,
    distributed_at          TIMESTAMP,
    cancellation_reason     VARCHAR(500),

    -- Montants remboursement
    refunded_amount         DECIMAL(18,8),
    retained_amount         DECIMAL(18,8),

    created_at              TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Détail de chaque transfert de redistribution exécuté
CREATE TABLE IF NOT EXISTS partner_payment_distributions (
    id                  BIGSERIAL PRIMARY KEY,
    payment_id          BIGINT NOT NULL REFERENCES partner_contract_payments(id),
    beneficiary_id      BIGINT NOT NULL REFERENCES partner_contract_beneficiaries(id),
    wallet_address      VARCHAR(255) NOT NULL,
    amount              DECIMAL(18,8) NOT NULL,
    percentage          DECIMAL(5,4)  NOT NULL,
    tx_hash             VARCHAR(255),
    status              VARCHAR(50) NOT NULL DEFAULT 'pending',   -- pending | success | failed
    executed_at         TIMESTAMP,
    error_message       TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_partner_payments_contract  ON partner_contract_payments(contract_id);
CREATE INDEX IF NOT EXISTS idx_partner_payments_status    ON partner_contract_payments(status);
CREATE INDEX IF NOT EXISTS idx_partner_payments_client    ON partner_contract_payments(client_username);
CREATE INDEX IF NOT EXISTS idx_partner_distributions_pay  ON partner_payment_distributions(payment_id);
