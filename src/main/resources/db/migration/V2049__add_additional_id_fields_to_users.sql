ALTER TABLE users
    ADD COLUMN IF NOT EXISTS additional_id_number VARCHAR(100),
    ADD COLUMN IF NOT EXISTS additional_id_type   VARCHAR(50);
