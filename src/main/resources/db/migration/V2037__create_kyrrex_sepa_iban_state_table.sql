CREATE TABLE IF NOT EXISTS kyrrex_sepa_iban_state (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(255) NOT NULL,
    provider_id VARCHAR(100) NOT NULL,
    instrument VARCHAR(50) NOT NULL,
    instrument_id VARCHAR(255),
    iban VARCHAR(64),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    raw_response TEXT
);

CREATE INDEX IF NOT EXISTS idx_kyrrex_sepa_iban_state_username
    ON kyrrex_sepa_iban_state(username);
CREATE INDEX IF NOT EXISTS idx_kyrrex_sepa_iban_state_status
    ON kyrrex_sepa_iban_state(status);
CREATE INDEX IF NOT EXISTS idx_kyrrex_sepa_iban_state_provider_instrument
    ON kyrrex_sepa_iban_state(provider_id, instrument);
