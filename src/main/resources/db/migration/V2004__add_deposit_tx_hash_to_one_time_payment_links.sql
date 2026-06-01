-- ====================================================================
-- Migration: Ajouter deposit_tx_hash à one_time_payment_links
-- Date: 2026-03-13
-- Description: Stocke le hash de la transaction depositToEscrow() pour
--              permettre la récupération (retry) si la persistance BDD
--              échoue après la réussite des appels blockchain.
--              Le statut ESCROW_FUNDED_PENDING_DB est utilisé pour signaler
--              qu'un retry DB est nécessaire.
-- ====================================================================

-- Hash de la transaction de dépôt dans le smart contract escrow
ALTER TABLE one_time_payment_links ADD COLUMN IF NOT EXISTS deposit_tx_hash VARCHAR(66);
