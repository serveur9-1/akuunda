-- Comptes créés ou code de secours modifié dans les 4 derniers jours : nouveau format (pas de migration).
UPDATE user_emergency_codes uec
SET uses_phone_last5 = TRUE
FROM users u
WHERE u.user_id = uec.user_id
  AND uec.uses_phone_last5 = FALSE
  AND (
    u.created_at >= (NOW() AT TIME ZONE 'UTC') - INTERVAL '4 days'
    OR uec.created_at >= (NOW() AT TIME ZONE 'UTC') - INTERVAL '4 days'
  );
