-- Track subscription IDs per user to filter display in mobile app
ALTER TABLE esim_sim_serials ADD COLUMN IF NOT EXISTS subscription_ids TEXT;
