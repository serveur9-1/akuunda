-- =====================================================
-- V1001 : Table des tokens FCM des devices utilisateurs
-- =====================================================
-- Stocke le mapping username <-> fcmToken pour permettre
-- l'envoi de push notifications via Firebase Cloud Messaging.
-- Un utilisateur peut avoir plusieurs devices (multi-device).

CREATE TABLE IF NOT EXISTS user_device_tokens (
    id              BIGSERIAL PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL,
    fcm_token       VARCHAR(500) NOT NULL,
    device_name     VARCHAR(100),
    platform        VARCHAR(20),
    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_user_device_token UNIQUE (username, fcm_token)
);

-- Index pour retrouver rapidement tous les tokens d'un utilisateur
CREATE INDEX IF NOT EXISTS idx_user_device_tokens_username ON user_device_tokens (username);

-- Index pour retrouver un token spécifique (suppression quand token expiré)
CREATE INDEX IF NOT EXISTS idx_user_device_tokens_fcm_token ON user_device_tokens (fcm_token);

COMMENT ON TABLE user_device_tokens IS 'Tokens FCM des devices utilisateurs pour les push notifications';
COMMENT ON COLUMN user_device_tokens.username IS 'Numéro de téléphone / identifiant de l''utilisateur';
COMMENT ON COLUMN user_device_tokens.fcm_token IS 'Token Firebase Cloud Messaging du device';
COMMENT ON COLUMN user_device_tokens.device_name IS 'Nom du device (ex: iPhone 15, Samsung S24)';
COMMENT ON COLUMN user_device_tokens.platform IS 'Plateforme : ANDROID, IOS, WEB';
