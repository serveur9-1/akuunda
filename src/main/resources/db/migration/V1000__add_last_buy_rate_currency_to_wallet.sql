-- Migration : Ajout de la colonne pour sauvegarder la devise associée au taux d'achat
-- Permet de vérifier que le taux sauvegardé correspond bien à la devise du wallet

ALTER TABLE wallet 
ADD COLUMN IF NOT EXISTS last_buy_rate_currency VARCHAR(10) NULL;

-- Index pour améliorer les performances des requêtes
CREATE INDEX IF NOT EXISTS idx_wallet_last_buy_rate_currency ON wallet(last_buy_rate_currency);

-- Commentaire pour documentation
COMMENT ON COLUMN wallet.last_buy_rate_currency IS 'Devise associée au taux d''achat sauvegardé (ex: XOF, EUR, USD). Permet de vérifier que le taux sauvegardé correspond bien à la devise du wallet avant de l''utiliser pour la conversion.';

