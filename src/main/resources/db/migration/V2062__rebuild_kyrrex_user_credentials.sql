-- V2018 : Rebuild kyrrex_user_credentials to match KyrrexUserCredential entity
-- Table vide (0 rows) → safe to drop & recreate

DROP TABLE IF EXISTS kyrrex_user_credentials;

CREATE TABLE kyrrex_user_credentials (
    id                  BIGSERIAL       PRIMARY KEY,
    username            VARCHAR(100)    NOT NULL UNIQUE,
    kyrrex_member_uid   VARCHAR(100),
    access_key          VARCHAR(512)    NOT NULL,
    secret_key          VARCHAR(512)    NOT NULL,
    session_access_key  VARCHAR(512),
    session_secret_key  VARCHAR(512),
    session_expire_at   TIMESTAMPTZ,
    kyc_verified_at     TIMESTAMPTZ,
    created_at          TIMESTAMPTZ     DEFAULT now(),
    updated_at          TIMESTAMPTZ     DEFAULT now(),
    revoked_at          TIMESTAMPTZ
);
