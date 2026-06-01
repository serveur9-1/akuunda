-- Client sans compte Akuunda : stocker phone + email directement sur le paiement
ALTER TABLE partner_contract_payments
    ADD COLUMN IF NOT EXISTS client_phone VARCHAR(30),
    ADD COLUMN IF NOT EXISTS client_email VARCHAR(255);

-- Rendre creator nullable sur one_time_payment_links (client sans compte)
ALTER TABLE one_time_payment_links
    ALTER COLUMN user_id DROP NOT NULL;
