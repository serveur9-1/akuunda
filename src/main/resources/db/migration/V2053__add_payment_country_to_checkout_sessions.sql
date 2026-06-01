-- Pays cible défini par le marchand lors de la création du checkout.
-- Si renseigné, la page de paiement sélectionne ce pays automatiquement.
ALTER TABLE checkout_sessions
    ADD COLUMN IF NOT EXISTS payment_country VARCHAR(5) NULL;
