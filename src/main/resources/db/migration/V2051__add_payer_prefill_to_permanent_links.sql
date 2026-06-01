ALTER TABLE permanent_links
    ADD COLUMN IF NOT EXISTS prefill_payer_name  VARCHAR(255),
    ADD COLUMN IF NOT EXISTS prefill_payer_phone VARCHAR(50);
