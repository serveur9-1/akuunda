-- Add column to store the service provider widget URL (Mercuryo, etc.)
-- This URL is sensitive and will be served via a short redirect endpoint
ALTER TABLE permanent_link_sessions ADD COLUMN IF NOT EXISTS provider_widget_url VARCHAR(2000);

-- Also add to one_time_payment_links for one-time payment flows
ALTER TABLE one_time_payment_links ADD COLUMN IF NOT EXISTS provider_widget_url VARCHAR(2000);
