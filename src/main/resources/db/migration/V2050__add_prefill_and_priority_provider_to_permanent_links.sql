ALTER TABLE permanent_links
    ADD COLUMN IF NOT EXISTS prefill_country   VARCHAR(5),
    ADD COLUMN IF NOT EXISTS prefill_email     VARCHAR(255),
    ADD COLUMN IF NOT EXISTS priority_provider VARCHAR(50);
