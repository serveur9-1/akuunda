-- Le bon wallet (1b850978) existe déjà. On met à jour toutes les références
-- vers l'ancien ID (4115d888 = User ID Venly, pas le Wallet ID).
-- L'ancien enregistrement wallet est conservé car il est référencé par d'anciennes données.

UPDATE partner_contract_payments
SET intermediate_wallet_id = '1b850978-f0d1-479d-8dd4-1ec9b6b2d4f7'
WHERE intermediate_wallet_id = '4115d888-90eb-418b-a4f4-52afb87afd78';

UPDATE conditional_payments
SET intermediate_wallet_id = '1b850978-f0d1-479d-8dd4-1ec9b6b2d4f7'
WHERE intermediate_wallet_id = '4115d888-90eb-418b-a4f4-52afb87afd78';
