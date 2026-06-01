-- Ajout des champs pour le mode conditionnel dans one_time_payment_links
ALTER TABLE one_time_payment_links ADD COLUMN IF NOT EXISTS link_type VARCHAR(20) NOT NULL DEFAULT 'SIMPLE';
ALTER TABLE one_time_payment_links ADD COLUMN IF NOT EXISTS service_type VARCHAR(50);
ALTER TABLE one_time_payment_links ADD COLUMN IF NOT EXISTS service_start_date TIMESTAMP;
ALTER TABLE one_time_payment_links ADD COLUMN IF NOT EXISTS cancellation_deadline TIMESTAMP;
ALTER TABLE one_time_payment_links ADD COLUMN IF NOT EXISTS conditional_payment_id BIGINT;
ALTER TABLE one_time_payment_links ADD COLUMN IF NOT EXISTS qr_code_url VARCHAR(500);
ALTER TABLE one_time_payment_links ADD COLUMN IF NOT EXISTS qr_code_token VARCHAR(100);

-- Index pour retrouver facilement les liens conditionnels
CREATE INDEX IF NOT EXISTS idx_otpl_link_type ON one_time_payment_links (link_type);
CREATE INDEX IF NOT EXISTS idx_otpl_conditional_payment_id ON one_time_payment_links (conditional_payment_id);
