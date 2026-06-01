-- Mode opératoire de la clé (live / test) et libellé libre.
ALTER TABLE merchant_api_keys
    ADD COLUMN IF NOT EXISTS mode VARCHAR(10)  NOT NULL DEFAULT 'live',
    ADD COLUMN IF NOT EXISTS name VARCHAR(100) NULL;
