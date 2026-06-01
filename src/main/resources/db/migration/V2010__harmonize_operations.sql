-- ═══════════════════════════════════════════════════════════════════
-- Migration : Harmonisation des opérations
-- ═══════════════════════════════════════════════════════════════════

-- 1. Ajouter les nouvelles colonnes
ALTER TABLE operation ADD COLUMN IF NOT EXISTS transaction_type VARCHAR(50);
ALTER TABLE operation ADD COLUMN IF NOT EXISTS provider VARCHAR(50);
ALTER TABLE operation ADD COLUMN IF NOT EXISTS provider_amount DOUBLE PRECISION;
ALTER TABLE operation ADD COLUMN IF NOT EXISTS provider_devise VARCHAR(20);

-- 2. Harmoniser les designations existantes

-- Dépôt Meld / Guardarian ONRAMP → MELD_ON_RAMP
UPDATE operation SET designation = 'MELD_ON_RAMP', transaction_type = 'Dépôt', provider = 'Meld'
WHERE UPPER(designation) IN ('MELD', 'MELD_ONRAMP', 'GUADARIAN_ONRAMP', 'GUARDARIAN_ONRAMP')
  AND UPPER(type) = 'CREDIT';

-- Dépôt YellowCard → YELLOWCARD_ON_RAMP
UPDATE operation SET designation = 'YELLOWCARD_ON_RAMP', transaction_type = 'Dépôt', provider = 'YellowCard'
WHERE (UPPER(designation) LIKE '%YELLOWCARD%' OR UPPER(designation) LIKE '%YELLOW_CARD%')
  AND (UPPER(designation) LIKE '%ONRAMP%' OR UPPER(designation) LIKE '%ON_RAMP%')
  AND UPPER(type) = 'CREDIT';

-- Retrait Guardarian → GUARDARIAN_OFF_RAMP
UPDATE operation SET designation = 'GUARDARIAN_OFF_RAMP', transaction_type = 'Retrait', provider = 'Guardarian'
WHERE UPPER(designation) IN ('GUADARIAN_OFFRAMP', 'GUARDARIAN_OFFRAMP', 'GUADARIAN_OFF_RAMP')
  AND UPPER(type) = 'DEBIT';

-- Retrait YellowCard → YELLOWCARD_OFF_RAMP
UPDATE operation SET designation = 'YELLOWCARD_OFF_RAMP', transaction_type = 'Retrait', provider = 'YellowCard'
WHERE (UPPER(designation) LIKE '%YELLOWCARD%' OR UPPER(designation) LIKE '%YELLOW_CARD%')
  AND (UPPER(designation) LIKE '%OFFRAMP%' OR UPPER(designation) LIKE '%OFF_RAMP%')
  AND UPPER(type) = 'DEBIT';

-- Retrait Meld → MELD_OFF_RAMP
UPDATE operation SET designation = 'MELD_OFF_RAMP', transaction_type = 'Retrait', provider = 'Meld'
WHERE UPPER(designation) IN ('MELD_OFFRAMP', 'MELD_OFF_RAMP')
  AND UPPER(type) = 'DEBIT';

-- Ancien ON_RAMP générique → MELD_ON_RAMP (credit)
UPDATE operation SET designation = 'MELD_ON_RAMP', transaction_type = 'Dépôt', provider = 'Meld'
WHERE UPPER(designation) = 'ON_RAMP' AND UPPER(type) = 'CREDIT';

-- Ancien OFF_RAMP générique → GUARDARIAN_OFF_RAMP (debit)
UPDATE operation SET designation = 'GUARDARIAN_OFF_RAMP', transaction_type = 'Retrait', provider = 'Guardarian'
WHERE UPPER(designation) = 'OFF_RAMP' AND UPPER(type) = 'DEBIT';

-- Transferts internes (DEBIT)
UPDATE operation SET designation = 'INTERNAL_TRANSFER_SENT', transaction_type = 'Envoi', provider = 'Interne'
WHERE UPPER(designation) = 'TRANSFERT' AND UPPER(type) = 'DEBIT';

-- Transferts internes (CREDIT)
UPDATE operation SET designation = 'INTERNAL_TRANSFER_RECEIVED', transaction_type = 'Reçu', provider = 'Interne'
WHERE UPPER(designation) = 'TRANSFERT' AND UPPER(type) = 'CREDIT';

-- Anciennes designations longues (texte libre "Transfert de X EUR de A vers B")
UPDATE operation SET designation = 'INTERNAL_TRANSFER_SENT', transaction_type = 'Envoi', provider = 'Interne'
WHERE designation LIKE 'Transfert de%' AND UPPER(type) = 'DEBIT';

UPDATE operation SET designation = 'INTERNAL_TRANSFER_RECEIVED', transaction_type = 'Reçu', provider = 'Interne'
WHERE designation LIKE 'Transfert de%' AND UPPER(type) = 'CREDIT';

-- MT_PELERIN → MELD_ON_RAMP (credit) ou MELD_OFF_RAMP (debit)
UPDATE operation SET designation = 'MELD_ON_RAMP', transaction_type = 'Dépôt', provider = 'MtPelerin'
WHERE UPPER(designation) = 'MT_PELERIN' AND UPPER(type) = 'CREDIT';

UPDATE operation SET designation = 'MELD_OFF_RAMP', transaction_type = 'Retrait', provider = 'MtPelerin'
WHERE UPPER(designation) = 'MT_PELERIN' AND UPPER(type) = 'DEBIT';

-- 3. Normaliser les statuts
UPDATE operation SET status = 'VALIDEE' WHERE UPPER(status) IN ('COMPLETED', 'FINISHED', 'SUCCESS');
UPDATE operation SET status = 'EN_COURS' WHERE UPPER(status) IN ('NEW', 'PENDING', 'EN ATTENTE', 'PROCESSING');
UPDATE operation SET status = 'REJETEE' WHERE UPPER(status) IN ('FAILED', 'ANNULEE', 'CANCELLED');
UPDATE operation SET status = 'EXPIREE' WHERE UPPER(status) IN ('EXPIRED');

-- 4. Remplir transaction_type et provider pour les opérations non migrées
UPDATE operation SET transaction_type = 'Inconnu' WHERE transaction_type IS NULL;
UPDATE operation SET provider = 'Inconnu' WHERE provider IS NULL;

-- 5. Copier convertedAmount vers provider_amount pour les anciens ON_RAMP/OFF_RAMP
UPDATE operation SET provider_amount = converted_amount, provider_devise = 'USDC'
WHERE provider_amount IS NULL
  AND converted_amount IS NOT NULL
  AND UPPER(designation) IN ('MELD_ON_RAMP', 'YELLOWCARD_ON_RAMP', 'GUARDARIAN_OFF_RAMP', 'YELLOWCARD_OFF_RAMP', 'MELD_OFF_RAMP');
