-- Stocke l'identifiant on-chain de la config dynamique enregistrée sur le smart contract
-- lors de l'assignation des bénéficiaires DYNAMIC_ASSIGNMENT
ALTER TABLE partner_contract_payments
    ADD COLUMN IF NOT EXISTS dynamic_on_chain_config_id VARCHAR(100);
