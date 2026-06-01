-- =====================================================================
-- V2016 : Nettoyage kyrrex_user_credentials
-- 1. Copier session_expires_at → session_expire_at (si données existent)
-- 2. Supprimer la colonne doublon session_expires_at
-- =====================================================================

-- Récupérer les données du doublon avant suppression
UPDATE kyrrex_user_credentials
SET    session_expire_at = session_expires_at
WHERE  session_expire_at IS NULL
  AND  session_expires_at IS NOT NULL;

-- Supprimer le doublon
ALTER TABLE kyrrex_user_credentials
    DROP COLUMN IF EXISTS session_expires_at;
