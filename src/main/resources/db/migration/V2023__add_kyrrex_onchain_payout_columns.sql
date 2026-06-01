-- Migration : traçabilité payout on-chain Kyrrex -> wallet utilisateur
-- Ajoute les colonnes de suivi du retrait crypto exécuté par Kyrrex
-- après crédit ledger Akuunda (modèle ledger-first + payout réel).

ALTER TABLE kyrrex_transactions
    ADD COLUMN IF NOT EXISTS payout_withdrawal_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS payout_status VARCHAR(40),
    ADD COLUMN IF NOT EXISTS payout_started_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS payout_confirmed_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_kyrrex_transactions_payout_withdrawal_id
    ON kyrrex_transactions (payout_withdrawal_id);
