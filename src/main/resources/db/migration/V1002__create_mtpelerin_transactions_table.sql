-- Migration pour créer la table mtpelerin_transactions
-- Cette table stocke les transactions synchronisées depuis l'API MT Pelerin

CREATE TABLE IF NOT EXISTS mtpelerin_transactions (
    id BIGSERIAL PRIMARY KEY,
    merchant_oid VARCHAR(255) UNIQUE NOT NULL,
    transaction_group_id VARCHAR(255),
    reference VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    paid VARCHAR(100),
    received VARCHAR(100),
    creation_date TIMESTAMP,
    last_update TIMESTAMP,
    synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    transaction_type VARCHAR(20),
    username VARCHAR(255),
    wallet_address VARCHAR(255)
);

-- Index pour améliorer les performances des requêtes
CREATE INDEX IF NOT EXISTS idx_mtpelerin_transactions_merchant_oid ON mtpelerin_transactions(merchant_oid);
CREATE INDEX IF NOT EXISTS idx_mtpelerin_transactions_status ON mtpelerin_transactions(status);
CREATE INDEX IF NOT EXISTS idx_mtpelerin_transactions_transaction_type ON mtpelerin_transactions(transaction_type);
CREATE INDEX IF NOT EXISTS idx_mtpelerin_transactions_username ON mtpelerin_transactions(username);
CREATE INDEX IF NOT EXISTS idx_mtpelerin_transactions_creation_date ON mtpelerin_transactions(creation_date);

