-- Ajouter original_amount seulement si elle n'existe pas
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'operation' AND column_name = 'original_amount'
    ) THEN
        ALTER TABLE operation ADD COLUMN original_amount DOUBLE PRECISION DEFAULT NULL;
    END IF;
END $$;

-- Ajouter original_devise seulement si elle n'existe pas
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'operation' AND column_name = 'original_devise'
    ) THEN
        ALTER TABLE operation ADD COLUMN original_devise VARCHAR(10) DEFAULT NULL;
    END IF;
END $$;
