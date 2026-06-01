-- Date pivot corrigée : mise en prod /emergency-code/change le 20/05/2026 (et non le 24).

UPDATE user_emergency_codes uec
SET uses_phone_last5 = FALSE
FROM users u
WHERE u.user_id = uec.user_id
  AND uec.uses_phone_last5 = TRUE
  AND u.created_at < TIMESTAMPTZ '2026-05-20 00:00:00+00'
  AND uec.created_at < TIMESTAMPTZ '2026-05-20 00:00:00+00';

UPDATE user_emergency_codes uec
SET uses_phone_last5 = TRUE
FROM users u
WHERE u.user_id = uec.user_id
  AND uec.uses_phone_last5 = FALSE
  AND (
    u.created_at >= TIMESTAMPTZ '2026-05-20 00:00:00+00'
    OR uec.created_at >= TIMESTAMPTZ '2026-05-20 00:00:00+00'
  );
