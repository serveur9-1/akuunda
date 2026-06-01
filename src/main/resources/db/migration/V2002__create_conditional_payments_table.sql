-- Migration pour créer la table conditional_payments
-- Cette table gère les paiements conditionnels (escrow) avec validation par QR code
-- L'entrée est créée après qu'un lien conditionnel (one_time_payment_links) a été payé

CREATE TABLE IF NOT EXISTS conditional_payments (
    id                        BIGSERIAL PRIMARY KEY,

    -- Identifiant unique du paiement conditionnel
    payment_code              VARCHAR(50)      NOT NULL UNIQUE,

    -- Participants (client nullable pour les payeurs externes sans compte)
    client_id                 VARCHAR(255),
    vendor_id                 VARCHAR(255)     NOT NULL,

    -- Description de la prestation
    service_type              VARCHAR(50)      NOT NULL,
    description               VARCHAR(1000)    NOT NULL,

    -- Montant et devise (USDC par défaut)
    amount                    DOUBLE PRECISION NOT NULL,
    currency                  VARCHAR(10)      NOT NULL,

    -- Statut du paiement conditionnel
    -- Valeurs : pending_condition, condition_validated, released, refunded, refunded_partial
    status                    VARCHAR(50)      NOT NULL,

    -- Données blockchain (smart contract escrow)
    escrow_contract_address   VARCHAR(255),
    deposit_transaction_hash  VARCHAR(255),
    release_transaction_hash  VARCHAR(255),
    refund_transaction_hash   VARCHAR(255),

    -- Dates liées à la prestation
    service_start_date        TIMESTAMP,
    service_actual_start_date TIMESTAMP,
    cancellation_deadline     TIMESTAMP,

    -- Montants en cas de remboursement partiel
    retained_amount           DOUBLE PRECISION,
    refunded_amount           DOUBLE PRECISION,
    cancellation_reason       VARCHAR(500),

    -- Références vers les opérations comptables internes
    client_operation_id       BIGINT,
    vendor_operation_id       BIGINT,
    refund_operation_id       BIGINT,

    -- Wallets associés
    intermediate_wallet_id    VARCHAR(255),
    client_wallet_id          VARCHAR(255),
    vendor_wallet_id          VARCHAR(255),

    -- Horodatage
    created_at                TIMESTAMP        NOT NULL,
    updated_at                TIMESTAMP        NOT NULL
);

-- Index définis dans l'entité ConditionalPayment
CREATE INDEX IF NOT EXISTS idx_conditional_payment_status ON conditional_payments (status);
CREATE INDEX IF NOT EXISTS idx_conditional_payment_client ON conditional_payments (client_id);
CREATE INDEX IF NOT EXISTS idx_conditional_payment_vendor ON conditional_payments (vendor_id);
