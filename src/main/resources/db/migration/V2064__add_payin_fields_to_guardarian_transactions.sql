-- Ajout des colonnes payin_address et payin_extra_id à guardarian_transactions.
-- Nécessaire pour stocker l'adresse USDC de dépôt Guardarian (payinAddress)
-- et le memo/tag associé (payinExtraId), utilisés lors du flux off-ramp.

ALTER TABLE guardarian_transactions
    ADD COLUMN IF NOT EXISTS payin_address  VARCHAR(255) NULL,
    ADD COLUMN IF NOT EXISTS payin_extra_id VARCHAR(255) NULL;
