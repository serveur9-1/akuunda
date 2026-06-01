-- =============================================================================
-- INSERT wallet intermédiaire (paiements conditionnels) - pgAdmin
-- ID: 5192115a-51b7-4f4a-bbb3-ba2515d2e2ce | PIN: 482736 (Aman)
-- Remplacer 0xADRESSE_DU_WALLET_VENLY par l'adresse réelle du wallet Venly.
-- =============================================================================

INSERT INTO wallet (
    id,
    address,
    alias,
    archived,
    balance,
    created_at,
    custodial,
    decimals,
    description,
    devise_balance,
    gas_balance,
    gas_symbol,
    has_custom_pin,
    identifier,
    is_primary,
    raw_balance,
    raw_gas_balance,
    secret_type,
    symbol,
    wallet_type,
    currency,
    users
)
SELECT
    '5192115a-51b7-4f4a-bbb3-ba2515d2e2ce',
    '0xADRESSE_DU_WALLET_VENLY',
    NULL,
    false,
    0,
    NOW(),
    true,
    6,
    'Wallet intermédiaire Akuunda (séquestre)',
    0,
    0,
    NULL,
    true,
    NULL,
    false,
    '0',
    '0',
    'MATIC',
    'USDC',
    'API_WALLET',
    (SELECT id FROM country_currency LIMIT 1),
    NULL
WHERE NOT EXISTS (
    SELECT 1 FROM wallet WHERE id = '5192115a-51b7-4f4a-bbb3-ba2515d2e2ce'
);
