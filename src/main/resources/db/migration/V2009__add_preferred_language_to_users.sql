-- V2006 : Ajout du champ preferred_language à la table users
-- Valeur par défaut 'fr' (français). L'utilisateur pourra changer dans l'app.
-- Idempotent : utilise DO $$ ... IF NOT EXISTS

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'users' AND column_name = 'preferred_language'
    ) THEN
        ALTER TABLE users ADD COLUMN preferred_language VARCHAR(5) DEFAULT 'fr';
    END IF;
END $$;
