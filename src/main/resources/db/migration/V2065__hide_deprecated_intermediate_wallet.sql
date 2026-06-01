-- L'ancien wallet (4115d888 = User ID Venly) doit être ignoré par findByAddress.
-- On préfixe son adresse pour qu'il ne soit plus retourné lors des lookups par adresse.
-- Le bon wallet (1b850978 = Wallet ID correct) sera le seul trouvé.
UPDATE wallet
SET address = 'DEPRECATED_' || address
WHERE id = '4115d888-90eb-418b-a4f4-52afb87afd78'
  AND address NOT LIKE 'DEPRECATED_%';
