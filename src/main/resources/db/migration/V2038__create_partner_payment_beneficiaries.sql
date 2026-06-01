-- Bénéficiaires assignés dynamiquement par l'admin après paiement
-- Utilisé pour le mode DYNAMIC_ASSIGNMENT où le professionnel est inconnu à la création du contrat
CREATE TABLE IF NOT EXISTS partner_payment_beneficiaries (
    id                  BIGSERIAL PRIMARY KEY,
    payment_id          BIGINT NOT NULL REFERENCES partner_contract_payments(id),
    beneficiary_type    VARCHAR(50)  NOT NULL,
    label               VARCHAR(255) NOT NULL,
    wallet_address      VARCHAR(255),
    username            VARCHAR(255),
    percentage          DOUBLE PRECISION NOT NULL,
    execution_order     INTEGER NOT NULL DEFAULT 1,
    created_at          TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ppb_payment ON partner_payment_beneficiaries(payment_id);

-- Colonnes ajoutées sur partner_contract_payments pour DYNAMIC_ASSIGNMENT
ALTER TABLE partner_contract_payments
    ADD COLUMN IF NOT EXISTS beneficiaries_assigned_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS assigned_by              VARCHAR(255);
