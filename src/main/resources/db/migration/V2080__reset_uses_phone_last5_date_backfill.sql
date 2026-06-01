-- Annule les flags uses_phone_last5=TRUE posés à tort par V2077/V2078/V2079
-- sur des comptes legacy (créés et code antérieurs au 20/05/2026).
-- La décision de migration repose sur users.created_at et user_emergency_codes.created_at,
-- pas sur ce flag ; ce nettoyage évite un affichage erroné dans l'app (réglages).

UPDATE user_emergency_codes uec
SET uses_phone_last5 = FALSE
FROM users u
WHERE u.user_id = uec.user_id
  AND uec.uses_phone_last5 = TRUE
  AND u.created_at < TIMESTAMPTZ '2026-05-20 00:00:00+00'
  AND uec.created_at < TIMESTAMPTZ '2026-05-20 00:00:00+00';
