-- TransFi orders tracking table
CREATE TABLE IF NOT EXISTS transfi_orders (
    id                  BIGSERIAL       PRIMARY KEY,
    order_id            VARCHAR(100)    UNIQUE,
    username            VARCHAR(100)    NOT NULL,
    transfi_user_id     VARCHAR(100),
    order_type          VARCHAR(50),
    status              VARCHAR(100),
    source_currency     VARCHAR(20),
    source_amount       NUMERIC(20, 8),
    destination_currency VARCHAR(20),
    destination_amount  NUMERIC(20, 8),
    fee_amount          NUMERIC(20, 8),
    fee_currency        VARCHAR(20),
    exchange_rate       VARCHAR(100),
    payment_url         TEXT,
    purpose_code        VARCHAR(100),
    created_at          TIMESTAMPTZ     DEFAULT now(),
    updated_at          TIMESTAMPTZ     DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_transfi_orders_order_id    ON transfi_orders(order_id);
CREATE INDEX IF NOT EXISTS idx_transfi_orders_username    ON transfi_orders(username);
CREATE INDEX IF NOT EXISTS idx_transfi_orders_status      ON transfi_orders(status);
CREATE INDEX IF NOT EXISTS idx_transfi_orders_order_type  ON transfi_orders(order_type);
CREATE INDEX IF NOT EXISTS idx_transfi_orders_created_at  ON transfi_orders(created_at);

-- TransFi user mapping (Akuunda username ↔ TransFi userId)
CREATE TABLE IF NOT EXISTS transfi_users (
    id                  BIGSERIAL       PRIMARY KEY,
    username            VARCHAR(100)    NOT NULL UNIQUE,
    transfi_user_id     VARCHAR(100)    NOT NULL UNIQUE,
    user_type           VARCHAR(20)     NOT NULL DEFAULT 'individual',
    kyc_status          VARCHAR(50),
    kyb_status          VARCHAR(50),
    created_at          TIMESTAMPTZ     DEFAULT now(),
    updated_at          TIMESTAMPTZ     DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_transfi_users_username        ON transfi_users(username);
CREATE INDEX IF NOT EXISTS idx_transfi_users_transfi_user_id ON transfi_users(transfi_user_id);

-- TransFi virtual IBANs
CREATE TABLE IF NOT EXISTS transfi_ibans (
    id                  BIGSERIAL       PRIMARY KEY,
    iban_id             VARCHAR(100)    UNIQUE,
    username            VARCHAR(100)    NOT NULL,
    transfi_user_id     VARCHAR(100),
    iban                VARCHAR(50),
    bic                 VARCHAR(20),
    currency            VARCHAR(10),
    bank_name           VARCHAR(200),
    account_holder_name VARCHAR(200),
    status              VARCHAR(50),
    created_at          TIMESTAMPTZ     DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_transfi_ibans_username ON transfi_ibans(username);
CREATE INDEX IF NOT EXISTS idx_transfi_ibans_iban_id  ON transfi_ibans(iban_id);
