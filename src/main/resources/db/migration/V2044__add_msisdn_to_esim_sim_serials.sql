-- Crée la table esim_sim_serials si elle n'existe pas encore (prod ne l'avait pas),
-- puis ajoute la colonne msisdn (no-op si la table vient d'être créée).

CREATE TABLE IF NOT EXISTS esim_sim_serials (
    id                   BIGSERIAL PRIMARY KEY,
    sim_serial           VARCHAR(255) NOT NULL UNIQUE,
    msisdn               VARCHAR(32),
    status               VARCHAR(50)  NOT NULL DEFAULT 'AVAILABLE',
    assigned_user_id     VARCHAR(255),
    last_product_id      VARCHAR(255),
    last_subscription_id VARCHAR(255),
    CONSTRAINT esim_sim_serials_status_check
        CHECK (status IN ('AVAILABLE', 'RESERVED', 'USED', 'SUSPENDED', 'TERMINATED'))
);

ALTER TABLE esim_sim_serials
    ADD COLUMN IF NOT EXISTS msisdn VARCHAR(32);
