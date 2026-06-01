-- Migration : automatisation Kyrrex -> Akuunda Pay (Issue 2026-05)
-- Ajoute les colonnes d'idempotence et de tracabilité du règlement (settlement)
-- d'un dépôt Kyrrex vers le wallet Akuunda Pay de l'utilisateur.
--
-- Schéma cible :
--   * settled_at          : horodatage du crédit du wallet (NULL = pas encore réglé)
--   * settlement_amount   : montant crédité dans l'unité de l'asset cible (ex: USDC)
--   * settlement_asset    : asset crédité (ex: USDC)
--   * settlement_network  : réseau cible pour le sweep on-chain (ex: Polygon)
--
-- Une contrainte d'unicité sur kyrrex_id est requise pour éviter tout double-crédit
-- en cas de retries / webhooks dupliqués (la colonne avait déjà un index, on
-- promeut en UNIQUE en complétant si nécessaire).

ALTER TABLE kyrrex_transactions
    ADD COLUMN IF NOT EXISTS settled_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS settlement_amount NUMERIC(20, 8),
    ADD COLUMN IF NOT EXISTS settlement_asset VARCHAR(20),
    ADD COLUMN IF NOT EXISTS settlement_network VARCHAR(50);

-- Index d'aide à la réconciliation : on doit pouvoir lister vite les
-- transactions avec status terminal (SUCCESS) non encore réglées.
CREATE INDEX IF NOT EXISTS idx_kyrrex_transactions_settled_at
    ON kyrrex_transactions (settled_at);

CREATE INDEX IF NOT EXISTS idx_kyrrex_transactions_status_settled
    ON kyrrex_transactions (status, settled_at);

-- Garantir l'unicité de kyrrex_id (un dépôt = un règlement)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_indexes
        WHERE schemaname = current_schema()
          AND tablename = 'kyrrex_transactions'
          AND indexname = 'uq_kyrrex_transactions_kyrrex_id'
    ) THEN
        EXECUTE 'CREATE UNIQUE INDEX uq_kyrrex_transactions_kyrrex_id
                 ON kyrrex_transactions (kyrrex_id)
                 WHERE kyrrex_id IS NOT NULL';
    END IF;
END$$;
