ALTER TABLE partner_contracts
    ADD COLUMN IF NOT EXISTS payment_link_type VARCHAR(20) NOT NULL DEFAULT 'BOTH';
