-- Colonnes marchand pour les comptes backoffice Pro
ALTER TABLE backoffice_user
    ADD COLUMN IF NOT EXISTS wallet_username     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS business_name       VARCHAR(255),
    ADD COLUMN IF NOT EXISTS registration_number VARCHAR(255),
    ADD COLUMN IF NOT EXISTS business_type       VARCHAR(64),
    ADD COLUMN IF NOT EXISTS address             TEXT,
    ADD COLUMN IF NOT EXISTS contact_phone       VARCHAR(64);
