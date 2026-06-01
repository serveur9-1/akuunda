-- ====================================================================
-- Migration: Ajouter les colonnes de paiement conditionnel
-- Table: one_time_payment_links
-- Date: 2026-03-13
-- Description: Ajoute les colonnes nécessaires pour le support des
--              paiements conditionnels (cas d'usage réservation hôtel)
-- Note: Utilise IF NOT EXISTS pour être idempotent (peut être rejoué
--       sans erreur si les colonnes existent déjà, ex. depuis V1005)
-- ====================================================================

-- Type de lien : SIMPLE (paiement direct) ou CONDITIONAL (escrow/séquestre)
ALTER TABLE one_time_payment_links ADD COLUMN IF NOT EXISTS link_type VARCHAR(20) NOT NULL DEFAULT 'SIMPLE';

-- Type de service pour les liens conditionnels (HOTEL, TRAVEL_AGENCY, TOURISM, RENTAL, DELIVERY)
ALTER TABLE one_time_payment_links ADD COLUMN IF NOT EXISTS service_type VARCHAR(50);

-- Date de début du service (ex: date d'arrivée à l'hôtel)
ALTER TABLE one_time_payment_links ADD COLUMN IF NOT EXISTS service_start_date TIMESTAMP;

-- Date limite d'annulation (après cette date, pas de remboursement)
ALTER TABLE one_time_payment_links ADD COLUMN IF NOT EXISTS cancellation_deadline TIMESTAMP;

-- Référence vers l'entrée ConditionalPayment créée après validation du paiement
ALTER TABLE one_time_payment_links ADD COLUMN IF NOT EXISTS conditional_payment_id BIGINT;

-- URL du QR code de confirmation (généré après paiement conditionnel validé)
ALTER TABLE one_time_payment_links ADD COLUMN IF NOT EXISTS qr_code_url VARCHAR(500);

-- Token du QR code (pour vérification lors du scan à l'arrivée)
ALTER TABLE one_time_payment_links ADD COLUMN IF NOT EXISTS qr_code_token VARCHAR(100);

-- Index pour accélérer les recherches par type de lien
CREATE INDEX IF NOT EXISTS idx_otpl_link_type ON one_time_payment_links(link_type);

-- Index pour accélérer les recherches par conditional_payment_id
CREATE INDEX IF NOT EXISTS idx_otpl_conditional_payment_id ON one_time_payment_links(conditional_payment_id);

-- Index pour rechercher les liens conditionnels par date de service
CREATE INDEX IF NOT EXISTS idx_otpl_service_start_date ON one_time_payment_links(service_start_date);
