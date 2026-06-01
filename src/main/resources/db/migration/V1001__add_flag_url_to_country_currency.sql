-- Migration : Ajout de la colonne flag_url pour stocker l'URL du drapeau du pays
-- Les drapeaux sont récupérés depuis l'API REST Countries (format PNG ou SVG)

-- Vérifier si la colonne n'existe pas déjà avant de l'ajouter
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_name = 'country_currency' 
        AND column_name = 'flag_url'
    ) THEN
        ALTER TABLE country_currency 
        ADD COLUMN flag_url VARCHAR(500) NULL;
        
        -- Commentaire pour documentation
        COMMENT ON COLUMN country_currency.flag_url IS 'URL directe du drapeau du pays (format PNG ou SVG) récupérée depuis l''API REST Countries. Exemple: https://flagcdn.com/w320/sn.png';
    END IF;
END $$;

