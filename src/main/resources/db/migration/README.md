# Migrations SQL — Flyway

Ce projet utilise **Flyway** pour les migrations de base de données. Les scripts SQL dans ce dossier sont **exécutés automatiquement** au démarrage de l'application.

---

## Convention de nommage

Les scripts SQL suivent la convention :

```
V<version>__<description>.sql
```

- `V` : préfixe obligatoire
- `<version>` : numéro de version (ex. `999`, `1000`, `2001`)
- `__` : double underscore comme séparateur
- `<description>` : description en snake_case

**Exemples :**
- `V999__add_last_buy_rate_to_wallet.sql`
- `V1000__add_last_buy_rate_currency_to_wallet.sql`
- `V2001__add_conditional_payment_columns_to_one_time_payment_links.sql`

---

## Ordre d'exécution

Flyway applique les scripts **automatiquement dans l'ordre croissant des numéros de version** au démarrage de l'application. L'historique des migrations appliquées est conservé dans la table `flyway_schema_history`.

| Version | Fichier | Description |
|---------|---------|-------------|
| V999    | `V999__add_last_buy_rate_to_wallet.sql` | Ajout du taux d'achat au wallet |
| V1000   | `V1000__add_last_buy_rate_currency_to_wallet.sql` | Ajout de la devise du taux d'achat |
| V1001   | `V1001__add_flag_url_to_country_currency.sql` | Ajout de l'URL du drapeau |
| V1002   | `V1002__create_mtpelerin_transactions_table.sql` | Création de la table MT Pelerin |
| V1003   | `V1003__make_amount_currency_nullable.sql` | Rendre amount/currency nullable |
| V1004   | `V1004__create_kyrrex_transactions_table.sql` | Création de la table Kyrrex |
| V1005   | `V1005__add_conditional_fields_to_one_time_payment_links.sql` | Ajout des champs conditionnels à `one_time_payment_links` (idempotent, avec IF NOT EXISTS) |
| V2001   | `V2001__add_conditional_payment_columns_to_one_time_payment_links.sql` | Ajout des colonnes de paiement conditionnel (idempotent, avec IF NOT EXISTS) |
| V2002   | `V2002__create_conditional_payments_table.sql` | Création de la table `conditional_payments` (paiements escrow conditionnels) |
| V2003   | `V2003__add_smart_contract_columns_to_one_time_payment_links.sql` | Ajout des colonnes smart contract CREATE2 et informations payeur à `one_time_payment_links` (idempotent, avec IF NOT EXISTS) |
| V2004   | `V2004__add_deposit_tx_hash_to_one_time_payment_links.sql` | Ajout de la colonne `deposit_tx_hash` à `one_time_payment_links` (idempotent, avec IF NOT EXISTS) |
| V2005   | `V2005__widen_status_column_one_time_payment_links.sql` | Élargir la colonne `status` de VARCHAR(20) à VARCHAR(50) pour supporter `ESCROW_FUNDED_PENDING_DB` (24 chars) |

> **Note :** Tous les scripts utilisent `IF NOT EXISTS` — ils sont idempotents et peuvent être rejoués sans erreur.

---

## Configuration Flyway

Flyway est configuré dans `application.properties` avec les paramètres suivants :

```properties
spring.flyway.baseline-on-migrate=true
spring.flyway.baseline-version=0
spring.flyway.locations=classpath:db/migration
```

- `baseline-on-migrate=true` : Permet à Flyway de fonctionner sur les bases de données existantes (créées avant l'ajout de Flyway). Flyway crée la table `flyway_schema_history` et applique toutes les migrations en attente.
- `baseline-version=0` : Toutes les migrations (V999 et supérieures) seront appliquées sur une base existante sans historique Flyway.

---

## Environnements et `ddl-auto`

| Environnement | `spring.jpa.hibernate.ddl-auto` | Comportement |
|---------------|----------------------------------|--------------|
| **dev**       | `update` | Flyway applique les migrations d'abord, puis Hibernate ajoute les colonnes nullable manquantes |
| **preprod**   | `update` | Même situation que dev |
| **prod**      | `validate` | Flyway applique les migrations d'abord, puis Hibernate **vérifie** que le schéma correspond aux entités |

> ⚠️ **Important :** Flyway s'exécute **avant** Hibernate au démarrage. Les migrations sont appliquées automatiquement — aucune action manuelle n'est nécessaire.

---

## Ajout d'un nouveau script de migration

1. Créer un fichier nommé `V<prochaine_version>__<description>.sql` dans ce dossier
2. Utiliser `IF NOT EXISTS` sur les `ALTER TABLE` et `CREATE INDEX` pour rendre le script idempotent
3. Ajouter l'entrée dans le tableau ci-dessus
4. Documenter la migration dans le fichier de documentation approprié (ex. `DOCUMENTATION_CONDITIONAL_PAYMENTS.md`)
5. Au démarrage suivant de l'application, Flyway appliquera automatiquement le nouveau script
