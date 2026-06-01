-- DYNAMIC_ASSIGNMENT : les distributions n'ont pas de bénéficiaire contrat fixe
ALTER TABLE partner_payment_distributions
    ALTER COLUMN beneficiary_id DROP NOT NULL;
