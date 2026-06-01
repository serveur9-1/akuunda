-- ═══════════════════════════════════════════════════════════════════════════
-- V10023 : Correction des provider, transaction_type et counterpart_display_name
-- Date : 2026-03-29
-- ═══════════════════════════════════════════════════════════════════════════

-- ═══════════════════════════════════════════════════════════════════════════
-- 1. CORRIGER LES PROVIDERS
-- ═══════════════════════════════════════════════════════════════════════════

-- 1a. "Yellow Card OnRamp" (avec espace) → provider = YellowCard
UPDATE operation
SET provider = 'YellowCard', transaction_type = 'Dépôt'
WHERE designation = 'Yellow Card OnRamp';

-- 1b. GUADARIAN_ONRAMP → provider = Guardarian
UPDATE operation
SET provider = 'Guardarian', transaction_type = 'Dépôt'
WHERE designation = 'GUADARIAN_ONRAMP';

-- 1c. GUADARIAN_OFFRAMP → provider = Guardarian
UPDATE operation
SET provider = 'Guardarian', transaction_type = 'Retrait'
WHERE designation = 'GUADARIAN_OFFRAMP';

-- 1d. OnRamp MT Pelerin → provider = MtPelerin
UPDATE operation
SET provider = 'MtPelerin', transaction_type = 'Dépôt'
WHERE designation = 'OnRamp MT Pelerin';

-- 1e. OffRamp MT Pelerin → provider = MtPelerin
UPDATE operation
SET provider = 'MtPelerin', transaction_type = 'Retrait'
WHERE designation = 'OffRamp MT Pelerin';

-- 1f. MELD_ON_RAMP avec provider=Inconnu
UPDATE operation
SET provider = 'Meld', transaction_type = 'Dépôt'
WHERE designation = 'MELD_ON_RAMP' AND (provider = 'Inconnu' OR provider IS NULL);

-- 1g. MELD_OFF_RAMP avec provider=Inconnu
UPDATE operation
SET provider = 'Meld', transaction_type = 'Retrait'
WHERE designation = 'MELD_OFF_RAMP' AND (provider = 'Inconnu' OR provider IS NULL);

-- 1h. YELLOWCARD_ON_RAMP avec provider=Inconnu
UPDATE operation
SET provider = 'YellowCard', transaction_type = 'Dépôt'
WHERE designation = 'YELLOWCARD_ON_RAMP' AND (provider = 'Inconnu' OR provider IS NULL);

-- 1i. YELLOWCARD_OFF_RAMP avec provider=Inconnu
UPDATE operation
SET provider = 'YellowCard', transaction_type = 'Retrait'
WHERE designation = 'YELLOWCARD_OFF_RAMP' AND (provider = 'Inconnu' OR provider IS NULL);

-- 1j. GUARDARIAN_OFF_RAMP avec provider=Inconnu
UPDATE operation
SET provider = 'Guardarian', transaction_type = 'Retrait'
WHERE designation = 'GUARDARIAN_OFF_RAMP' AND (provider = 'Inconnu' OR provider IS NULL);

-- 1k. OFF_RAMP générique
UPDATE operation
SET provider = 'Inconnu', transaction_type = 'Retrait'
WHERE designation = 'OFF_RAMP' AND (transaction_type IS NULL OR transaction_type = 'Inconnu');

-- 1l. UNKNOWN
UPDATE operation
SET transaction_type = CASE WHEN type = 'CREDIT' THEN 'Dépôt' ELSE 'Retrait' END
WHERE designation = 'UNKNOWN' AND (transaction_type IS NULL OR transaction_type = 'Inconnu');

-- ═══════════════════════════════════════════════════════════════════════════
-- 2. CORRIGER LES ONE_TIME_PAYMENT
-- ═══════════════════════════════════════════════════════════════════════════

UPDATE operation
SET provider = 'Externe',
    transaction_type = CASE WHEN type = 'CREDIT' THEN 'Encaissement' ELSE 'Paiement' END
WHERE designation = 'ONE_TIME_PAYMENT'
  AND (provider IS NULL OR provider = 'Inconnu');

-- ═════════════════════════════════���═════════════════════════════════════════
-- 3. CORRIGER LES CONDITIONAL_PAYMENT
-- ═══════════════════════════════════════════════════════════════════════════

UPDATE operation
SET provider = 'Interne',
    transaction_type = CASE WHEN type = 'DEBIT' THEN 'Paiement' ELSE 'Encaissement' END
WHERE designation = 'CONDITIONAL_PAYMENT'
  AND (provider IS NULL OR provider = 'Inconnu');

UPDATE operation
SET provider = 'Externe',
    transaction_type = CASE WHEN type = 'DEBIT' THEN 'Paiement' ELSE 'Encaissement' END
WHERE designation = 'CONDITIONAL_PAYMENT_EXTERNAL'
  AND (provider IS NULL OR provider = 'Inconnu');

UPDATE operation
SET provider = 'Interne',
    transaction_type = 'Encaissement'
WHERE designation = 'CONDITIONAL_PAYMENT_REFUND'
  AND (provider IS NULL OR provider = 'Inconnu');

-- ═══════════════════════════════════════════════════════════════════════════
-- 4. CORRIGER LES INTERNAL_TRANSFER avec provider=Inconnu
-- ═══════════════════════════════════════════════════════════════════════════

UPDATE operation
SET provider = 'Interne'
WHERE designation = 'INTERNAL_TRANSFER_SENT' AND (provider = 'Inconnu' OR provider IS NULL);

UPDATE operation
SET provider = 'Interne'
WHERE designation = 'INTERNAL_TRANSFER_RECEIVED' AND (provider = 'Inconnu' OR provider IS NULL);

-- ═══════════════════════════════════════════════════════════════════════════
-- 5. CORRIGER LES designation=NULL ou vides
-- ═══════════════════════════════════════════════════════════════════════════

UPDATE operation
SET designation = CASE WHEN type = 'CREDIT' THEN 'UNKNOWN_CREDIT' ELSE 'UNKNOWN_DEBIT' END,
    transaction_type = CASE WHEN type = 'CREDIT' THEN 'Dépôt' ELSE 'Retrait' END
WHERE designation IS NULL OR designation = '';

-- ═══════════════════════════════════════════════════════════════════════════
-- 6. CORRIGER counterpart_display_name POUR LES TRANSFERTS INTERNES
-- ═══════════════════════════════════════════════════════════════════════════

-- 6a. INTERNAL_TRANSFER_SENT : la contrepartie est le receveur
UPDATE operation o_sent
SET counterpart_username = o_recv.username,
    counterpart_display_name = COALESCE(
        NULLIF(CONCAT_WS(' ', u.firstname, u.lastname), ''),
        o_recv.username
    )
FROM operation o_recv
LEFT JOIN users u ON u.username = o_recv.username
WHERE o_sent.designation = 'INTERNAL_TRANSFER_SENT'
  AND o_recv.designation = 'INTERNAL_TRANSFER_RECEIVED'
  AND o_sent.operation_hash = o_recv.operation_hash
  AND o_sent.counterpart_display_name IS NULL;

-- 6b. INTERNAL_TRANSFER_RECEIVED : la contrepartie est l'envoyeur
UPDATE operation o_recv
SET counterpart_username = o_sent.username,
    counterpart_display_name = COALESCE(
        NULLIF(CONCAT_WS(' ', u.firstname, u.lastname), ''),
        o_sent.username
    )
FROM operation o_sent
LEFT JOIN users u ON u.username = o_sent.username
WHERE o_recv.designation = 'INTERNAL_TRANSFER_RECEIVED'
  AND o_sent.designation = 'INTERNAL_TRANSFER_SENT'
  AND o_recv.operation_hash = o_sent.operation_hash
  AND o_recv.counterpart_display_name IS NULL;

-- ═══════════════════════════════════════════════════════════════════════════
-- 7. CATCH-ALL : tout ce qui a encore provider NULL ou transaction_type NULL
-- ═══════════════════════════════════════════════════════════════════════════

UPDATE operation
SET provider = 'Inconnu'
WHERE provider IS NULL;

UPDATE operation
SET transaction_type = CASE WHEN type = 'CREDIT' THEN 'Dépôt' ELSE 'Retrait' END
WHERE transaction_type IS NULL OR transaction_type = 'Inconnu';
