ALTER TABLE esim_sim_serials
    DROP CONSTRAINT IF EXISTS esim_sim_serials_status_check;

ALTER TABLE esim_sim_serials
    ADD CONSTRAINT esim_sim_serials_status_check
        CHECK (status IN ('AVAILABLE', 'RESERVED', 'USED', 'SUSPENDED', 'TERMINATED'));
