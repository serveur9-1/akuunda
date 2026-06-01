-- V2017 : Ajouter les colonnes manquantes à kyrrex_transactions

ALTER TABLE kyrrex_transactions
    ADD COLUMN IF NOT EXISTS amount NUMERIC(20,8),
    ADD COLUMN IF NOT EXISTS asset VARCHAR(20),
    ADD COLUMN IF NOT EXISTS raw_response TEXT;
