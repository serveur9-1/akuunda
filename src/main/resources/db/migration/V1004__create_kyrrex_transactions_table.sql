-- Migration pour créer la table kyrrex_transactions
-- Cette table stocke les transactions Kyrrex Malta Business

CREATE TABLE IF NOT EXISTS kyrrex_transactions (
    id BIGSERIAL PRIMARY KEY,
    kyrrex_id VARCHAR(255),
    username VARCHAR(255) NOT NULL,
    type VARCHAR(50),
    status VARCHAR(100),
    fiat_currency VARCHAR(20),
    crypto_asset VARCHAR(20),
    fiat_amount NUMERIC(20, 8),
    crypto_amount NUMERIC(20, 8),
    rate NUMERIC(20, 8),
    fee NUMERIC(20, 8),
    provider_id VARCHAR(255),
    payment_method VARCHAR(100),
    instrument VARCHAR(100),
    redirect_url VARCHAR(1024),
    sequence_id VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- Index pour améliorer les performances des requêtes
CREATE INDEX IF NOT EXISTS idx_kyrrex_transactions_kyrrex_id ON kyrrex_transactions(kyrrex_id);
CREATE INDEX IF NOT EXISTS idx_kyrrex_transactions_username ON kyrrex_transactions(username);
CREATE INDEX IF NOT EXISTS idx_kyrrex_transactions_status ON kyrrex_transactions(status);
CREATE INDEX IF NOT EXISTS idx_kyrrex_transactions_type ON kyrrex_transactions(type);
CREATE INDEX IF NOT EXISTS idx_kyrrex_transactions_sequence_id ON kyrrex_transactions(sequence_id);
CREATE INDEX IF NOT EXISTS idx_kyrrex_transactions_created_at ON kyrrex_transactions(created_at);
