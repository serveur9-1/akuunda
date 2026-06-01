ALTER TABLE esim_sim_serials
    ADD COLUMN IF NOT EXISTS msisdn VARCHAR(32);
