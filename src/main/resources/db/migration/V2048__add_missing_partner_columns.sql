-- Colonnes présentes dans les entités Java mais absentes des migrations initiales.
-- Toutes sont nullable : aucune donnée existante n'est affectée.

-- ── partner_contracts ────────────────────────────────────────────────────────
ALTER TABLE partner_contracts
    ADD COLUMN IF NOT EXISTS on_chain_config_id      VARCHAR(66),
    ADD COLUMN IF NOT EXISTS auto_release_hours      INTEGER,
    ADD COLUMN IF NOT EXISTS dispute_window_hours    INTEGER,
    ADD COLUMN IF NOT EXISTS expected_latitude       DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS expected_longitude      DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS geolocation_radius_meters DOUBLE PRECISION DEFAULT 200.0;

-- ── partner_contract_payments ────────────────────────────────────────────────
ALTER TABLE partner_contract_payments
    -- Smart-contract / CREATE2
    ADD COLUMN IF NOT EXISTS on_chain_payment_id             VARCHAR(255),
    ADD COLUMN IF NOT EXISTS create2_wallet_address          VARCHAR(255),
    ADD COLUMN IF NOT EXISTS create2_status                  VARCHAR(50) DEFAULT 'waiting_payment',
    -- REMOTE_CONFIRMATION
    ADD COLUMN IF NOT EXISTS confirmation_token              VARCHAR(255),
    ADD COLUMN IF NOT EXISTS confirmation_url                VARCHAR(500),
    ADD COLUMN IF NOT EXISTS confirmation_token_expires_at   TIMESTAMP,
    ADD COLUMN IF NOT EXISTS confirmed_by_client_at          TIMESTAMP,
    -- OTP
    ADD COLUMN IF NOT EXISTS otp_code                        VARCHAR(10),
    ADD COLUMN IF NOT EXISTS otp_expires_at                  TIMESTAMP,
    ADD COLUMN IF NOT EXISTS otp_used_at                     TIMESTAMP,
    ADD COLUMN IF NOT EXISTS otp_used_by                     VARCHAR(255),
    -- DUAL_CONFIRMATION
    ADD COLUMN IF NOT EXISTS provider_confirmed_at           TIMESTAMP,
    ADD COLUMN IF NOT EXISTS provider_confirmed_by           VARCHAR(255),
    ADD COLUMN IF NOT EXISTS client_dual_confirmed_at        TIMESTAMP,
    -- TIME_BASED / auto-release
    ADD COLUMN IF NOT EXISTS auto_release_scheduled_at       TIMESTAMP,
    -- DISPUTE_WINDOW
    ADD COLUMN IF NOT EXISTS disputed_at                     TIMESTAMP,
    ADD COLUMN IF NOT EXISTS disputed_by                     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS dispute_reason                  TEXT,
    -- GEOLOCATION
    ADD COLUMN IF NOT EXISTS client_latitude                 DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS client_longitude                DOUBLE PRECISION,
    ADD COLUMN IF NOT EXISTS location_confirmed_at           TIMESTAMP,
    -- ADMIN_APPROVAL
    ADD COLUMN IF NOT EXISTS admin_approval_requested_at     TIMESTAMP,
    ADD COLUMN IF NOT EXISTS admin_approved_at               TIMESTAMP,
    ADD COLUMN IF NOT EXISTS admin_approved_by               VARCHAR(255);
