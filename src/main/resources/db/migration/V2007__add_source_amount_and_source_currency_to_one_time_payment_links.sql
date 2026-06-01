-- ====================================================================
-- Migration: Ajouter source_amount et source_currency
-- Table: one_time_payment_links
-- Date: 2026-03-18
-- Description: Stocke le montant et la devise source (USDC) du paiement
--              crypto on-chain, distinct du montant fiat (amount/currency).
-- ====================================================================

ALTER TABLE one_time_payment_links ADD COLUMN IF NOT EXISTS source_amount DOUBLE PRECISION;
ALTER TABLE one_time_payment_links ADD COLUMN IF NOT EXISTS source_currency VARCHAR(10);
