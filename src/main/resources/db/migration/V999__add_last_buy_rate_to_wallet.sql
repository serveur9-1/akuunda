-- Migration : Ajout des champs pour sauvegarder le taux d'achat du partenaire
-- Selon la proposition de David : sauvegarder le taux lors du dépôt et l'utiliser pour l'affichage

ALTER TABLE wallet ADD COLUMN IF NOT EXISTS last_buy_rate DOUBLE PRECISION NULL;
ALTER TABLE wallet ADD COLUMN IF NOT EXISTS last_buy_rate_updated_at TIMESTAMP NULL;
ALTER TABLE wallet ADD COLUMN IF NOT EXISTS last_provider VARCHAR(50) NULL;

-- Index pour améliorer les performances des requêtes
CREATE INDEX IF NOT EXISTS idx_wallet_last_buy_rate_updated_at ON wallet(last_buy_rate_updated_at);

-- Commentaires pour documentation
COMMENT ON COLUMN wallet.last_buy_rate IS 'Taux d''achat du partenaire sauvegardé lors du dernier dépôt. Utilisé pour convertir le solde USDC en devise locale pour l''affichage. Format : 1 USD = last_buy_rate XOF (ex: 588.08)';
COMMENT ON COLUMN wallet.last_buy_rate_updated_at IS 'Date de dernière mise à jour du taux d''achat';
COMMENT ON COLUMN wallet.last_provider IS 'Partenaire utilisé pour le dernier dépôt (yellowcard ou guardarian)';

