-- Règle du 24/05/2026 (mise en prod /emergency-code/change, fin de déploiement 10:38:18 UTC).
-- Pas de migration si compte créé ou code modifié (user_emergency_codes.created_at) à partir de cette date.

-- Annule un éventuel backfill « 4 jours » (V2077) sur des comptes encore legacy.
UPDATE user_emergency_codes uec
SET uses_phone_last5 = FALSE
FROM users u
WHERE u.user_id = uec.user_id
  AND uec.uses_phone_last5 = TRUE
  AND u.created_at < TIMESTAMPTZ '2026-05-24 10:38:18+00'
  AND uec.created_at < TIMESTAMPTZ '2026-05-24 10:38:18+00';

UPDATE user_emergency_codes uec
SET uses_phone_last5 = TRUE
FROM users u
WHERE u.user_id = uec.user_id
  AND uec.uses_phone_last5 = FALSE
  AND (
    u.created_at >= TIMESTAMPTZ '2026-05-24 10:38:18+00'
    OR uec.created_at >= TIMESTAMPTZ '2026-05-24 10:38:18+00'
  );
