-- Colonnes ajoutées au module Merchant API qui peuvent manquer
-- si la table checkout_sessions a été créée sans elles.
ALTER TABLE checkout_sessions
    ADD COLUMN IF NOT EXISTS idempotency_key    VARCHAR(100),
    ADD COLUMN IF NOT EXISTS mode               VARCHAR(10),
    ADD COLUMN IF NOT EXISTS refunded_amount    DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS refunded_at        TIMESTAMP,
    ADD COLUMN IF NOT EXISTS refund_reason      VARCHAR(500),
    ADD COLUMN IF NOT EXISTS created_at         TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at         TIMESTAMP,
    ADD COLUMN IF NOT EXISTS webhook_sent_at    TIMESTAMP,
    ADD COLUMN IF NOT EXISTS paid_at            TIMESTAMP,
    ADD COLUMN IF NOT EXISTS description        VARCHAR(500),
    ADD COLUMN IF NOT EXISTS callback_url       VARCHAR(500),
    ADD COLUMN IF NOT EXISTS cancel_url         VARCHAR(500),
    ADD COLUMN IF NOT EXISTS webhook_url        VARCHAR(500),
    ADD COLUMN IF NOT EXISTS metadata_json      TEXT;

-- Index d'idempotence (peut déjà exister)
CREATE UNIQUE INDEX IF NOT EXISTS uk_checkout_idempotency
    ON checkout_sessions (api_key_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
