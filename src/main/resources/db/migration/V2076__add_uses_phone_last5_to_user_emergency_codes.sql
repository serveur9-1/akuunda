-- Indique si le code de secours utilise les 5 derniers chiffres du téléphone Akuunda Pay.
-- Les 5 caractères du code ne sont jamais stockés ; seul ce booléen permet de savoir si migration requise.
ALTER TABLE user_emergency_codes
    ADD COLUMN IF NOT EXISTS uses_phone_last5 BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN user_emergency_codes.uses_phone_last5 IS
    'true = code de secours basé sur les 5 derniers chiffres du mobile_phone ; false = ancien code personnalisé à migrer';
