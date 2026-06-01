-- ====================================================================
-- Migration: Ajouter received_amount, received_currency et released_at
-- Table: conditional_payments
-- Date: 2026-03-19
-- Description: Stocke le montant effectivement reçu par le prestataire
--              (en devise fiat locale), la devise correspondante,
--              et la date/heure de libération des fonds.
-- ====================================================================

ALTER TABLE conditional_payments ADD COLUMN IF NOT EXISTS received_amount DOUBLE PRECISION;
ALTER TABLE conditional_payments ADD COLUMN IF NOT EXISTS received_currency VARCHAR(10);
ALTER TABLE conditional_payments ADD COLUMN IF NOT EXISTS released_at TIMESTAMP;
