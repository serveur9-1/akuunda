-- ====================================================================
-- Migration: Élargir la colonne status de VARCHAR(20) à VARCHAR(50)
-- Table: one_time_payment_links
-- Date: 2026-03-14
-- Description: Le statut ESCROW_FUNDED_PENDING_DB (24 caractères) dépasse
--              la limite actuelle de VARCHAR(20), provoquant un crash
--              PSQLException en production.
--              Cette migration élargit la colonne à VARCHAR(50) pour
--              supporter tous les statuts actuels et futurs.
-- Note: ALTER COLUMN TYPE est idempotent si la colonne est déjà plus large.
-- ====================================================================

ALTER TABLE one_time_payment_links ALTER COLUMN status TYPE VARCHAR(50);
