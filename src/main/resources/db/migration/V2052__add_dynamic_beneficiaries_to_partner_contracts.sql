ALTER TABLE partner_contracts
    ADD COLUMN IF NOT EXISTS dynamic_beneficiaries BOOLEAN NOT NULL DEFAULT FALSE;
