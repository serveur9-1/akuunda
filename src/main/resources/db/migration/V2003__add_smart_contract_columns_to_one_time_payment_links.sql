-- ====================================================================
-- Migration: Ajouter les colonnes smart contract et autres champs optionnels
-- Table: one_time_payment_links
-- Date: 2026-03-13
-- Description: Ajoute les colonnes liées aux smart contracts CREATE2 et aux
--              informations payeur qui peuvent être absentes dans les anciennes
--              bases de données (avant l'implémentation des smart contracts).
-- Note: Utilise IF NOT EXISTS pour être idempotent (peut être rejoué
--       sans erreur si les colonnes existent déjà)
-- ====================================================================

-- Identifiant du paiement sur la blockchain (bytes32 encodé en hex)
ALTER TABLE one_time_payment_links ADD COLUMN IF NOT EXISTS payment_id_bytes32 VARCHAR(66);

-- Adresse du wallet CREATE2 temporaire calculée avant le paiement
ALTER TABLE one_time_payment_links ADD COLUMN IF NOT EXISTS create2_wallet_address VARCHAR(42);

-- Hash de la transaction de création du wallet CREATE2 on-chain
ALTER TABLE one_time_payment_links ADD COLUMN IF NOT EXISTS create_tx_hash VARCHAR(66);

-- Hash de la transaction d'exécution (libération des fonds) on-chain
ALTER TABLE one_time_payment_links ADD COLUMN IF NOT EXISTS execute_tx_hash VARCHAR(66);

-- Identifiant de transaction Yellow Card (paiement MoMo/carte)
ALTER TABLE one_time_payment_links ADD COLUMN IF NOT EXISTS yellow_card_transaction_id VARCHAR(200);

-- Informations sur le payeur (renseignées lors du paiement)
ALTER TABLE one_time_payment_links ADD COLUMN IF NOT EXISTS payer_phone VARCHAR(50);
ALTER TABLE one_time_payment_links ADD COLUMN IF NOT EXISTS payer_name VARCHAR(200);
ALTER TABLE one_time_payment_links ADD COLUMN IF NOT EXISTS payer_email VARCHAR(200);

-- Date d'expiration du lien de paiement
ALTER TABLE one_time_payment_links ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP;

-- Date à laquelle le paiement a été effectué
ALTER TABLE one_time_payment_links ADD COLUMN IF NOT EXISTS paid_at TIMESTAMP;

-- Index pour accélérer les recherches par adresse CREATE2
CREATE INDEX IF NOT EXISTS idx_otpl_create2_wallet_address ON one_time_payment_links(create2_wallet_address);

-- Index pour accélérer les recherches par payment_id_bytes32
CREATE INDEX IF NOT EXISTS idx_otpl_payment_id_bytes32 ON one_time_payment_links(payment_id_bytes32);
