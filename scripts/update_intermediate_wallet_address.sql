-- =============================================================================
-- MISE À JOUR de l'adresse du wallet intermédiaire
-- ID: 5192115a-51b7-4f4a-bbb3-ba2515d2e2ce
-- =============================================================================
-- 
-- INSTRUCTIONS :
-- 1. Remplacer '0xVOTRE_ADRESSE_VENLY_REELLE' par l'adresse réelle du wallet Venly
-- 2. Exécuter ce script dans pgAdmin ou psql
-- 3. Vérifier la mise à jour avec la requête SELECT ci-dessous
-- =============================================================================

-- Mettre à jour l'adresse du wallet intermédiaire
UPDATE wallet 
SET address = '0xVOTRE_ADRESSE_VENLY_REELLE'
WHERE id = '5192115a-51b7-4f4a-bbb3-ba2515d2e2ce';

-- Vérifier la mise à jour
SELECT 
    id,
    address,
    wallet_type,
    description,
    balance,
    devise_balance,
    symbol,
    created_at
FROM wallet 
WHERE id = '5192115a-51b7-4f4a-bbb3-ba2515d2e2ce';

-- Si le wallet n'existe pas, vous verrez 0 lignes.
-- Dans ce cas, exécutez d'abord : scripts/insert_intermediate_wallet_pgadmin.sql
