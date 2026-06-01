ALTER TABLE permanent_links
    ADD COLUMN IF NOT EXISTS partner_contract_code VARCHAR(50);

ALTER TABLE permanent_link_sessions
    ADD COLUMN IF NOT EXISTS partner_contract_payment_code VARCHAR(50);
