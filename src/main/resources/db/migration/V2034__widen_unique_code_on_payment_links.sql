-- unique_code était VARCHAR(8), trop court pour les paymentCode partenaires (ex: PP-003361-1777890838698)
ALTER TABLE one_time_payment_links
    ALTER COLUMN unique_code TYPE VARCHAR(60);
