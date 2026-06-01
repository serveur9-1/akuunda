-- Import manuel des credentials membre Kyrrex dans kyrrex_user_credentials.
-- À exécuter sur la base wallet (prod / preprod), PAS dans application.properties.
--
-- Remplacer les placeholders par les valeurs Kyrrex (dashboard ou réponse sign-up).
-- username = identifiant Akuunda (ex. téléphone 0033612108828).
--
-- Après déploiement avec chiffrement activé, préférer l'API :
--   POST /api/internal/v1/kyrrex/credentials/{username}/import
-- (les clés seront chiffrées automatiquement à l'enregistrement).

INSERT INTO kyrrex_user_credentials (
    username,
    kyrrex_member_uid,
    access_key,
    secret_key,
    revoked_at
) VALUES (
    '0033612108828',
    'mltz9a44d',
    '<ACCESS_KEY>',
    '<SECRET_KEY>',
    NULL
)
ON CONFLICT (username) DO UPDATE SET
    kyrrex_member_uid = EXCLUDED.kyrrex_member_uid,
    access_key = EXCLUDED.access_key,
    secret_key = EXCLUDED.secret_key,
    revoked_at = NULL,
    session_access_key = NULL,
    session_secret_key = NULL,
    session_expire_at = NULL,
    updated_at = now();

-- Vérification
SELECT username, kyrrex_member_uid, revoked_at IS NULL AS active, created_at
FROM kyrrex_user_credentials
WHERE username = '0033612108828';
