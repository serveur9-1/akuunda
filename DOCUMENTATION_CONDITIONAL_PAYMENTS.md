# 📋 Documentation - Paiements Conditionnels (Client → Smart contract directement, Polygon USDC)

## 🎯 Vue d'ensemble

Le système de **paiements conditionnels** permet aux clients de payer un service à l'avance, tout en garantissant au vendeur (hôtel, agence, établissement touristique, loueur, livreur…) que l'argent existe, mais ne sera libéré qu'au moment où la prestation démarre réellement.

**Contexte :** Hôtels, agences de voyage, tourisme, location, livraison.  
**Validation (version 1) :** Uniquement par scan de QR code.

---

## ❓ Paiement conditionnel : pas de choix de provider

**Le client ne choisit pas de provider** (Yellow Card, Guardarian, MT Pelerin, Meld, etc.) pour faire un paiement conditionnel.

| Concept | Rôle | Utilisé dans le paiement conditionnel ? |
|--------|------|----------------------------------------|
| **Provider** (Yellow Card, Guardarian, etc.) | Servent à **acheter** des USDC (on-ramp) ou **vendre** des USDC (off-ramp). | **Non** |
| **Vendeur** (`vendorUsername`) | Le marchand qui reçoit le paiement (hôtel, agence, loueur…). | **Oui** |

**Flow :**
1. Le client a déjà des USDC dans son wallet (achetés avant via un provider, ou reçus par virement).
2. Pour un paiement conditionnel, il choisit uniquement le **vendeur** (à qui il paie), le **montant**, le **type de service** et les **dates**.
3. Les USDC sont déposés **directement** depuis le wallet du client vers le smart contract (approve + deposit).

En résumé : **provider = pour acheter/vendre des crypto ; paiement conditionnel = pour payer un vendeur avec les USDC déjà en portefeuille.** Aucun choix de provider n'est demandé au client pour le paiement conditionnel.

---

## ✅ Flow simplifié (résumé) — sans wallet intermédiaire

| Étape | Rôle | Implémentation |
|-------|------|-----------------|
| **1. Dépôt** | Client paie → séquestre | Client `approve()` USDC + Client `deposit()` directement vers Smart contract. Statut `pending_condition`. |
| **2. Validation** | Début de prestation | Scan QR (réception hôtel, début voyage, remise clés, livraison…) → statut `condition_validated`. |
| **3. Libération** | Vendeur reçoit les fonds | Smart contract `release()` → directement au vendeur. Statut `released`. |
| **4. Annulation / No-show** | Remboursement | **A.** Smart contract `refund()` → client 100 % directement → `refunded`. **B.** Smart contract `partialRefund()` → part vendeur + part client directement → `refunded_partial`. |
| **5. Statuts** | Suivi | `pending_condition` → `condition_validated` → `released` \| `refunded` \| `refunded_partial`. |

**Architecture (flow direct) :**
- ✅ **Wallet admin** (ex-intermédiaire) : Signe uniquement les release/refund via le smart contract. **Ne reçoit plus de fonds.** (config : `akuunda.intermediate.wallet.id` ou `.address`, doit exister en base).
- ✅ **Smart contract Polygon USDC** : Séquestre des fonds (`akuunda.escrow.contract.wallet.address`).
- ✅ **Dépôt en 2 transactions Venly** : Client `approve()` USDC (PIN client) + Client `deposit()` (PIN client). Plus de TOKEN_TRANSFER intermédiaire.
- ✅ **Libération directe** : Smart contract `release()` → directement au vendeur (1 transaction au lieu de 2).
- ✅ **Remboursement direct** : Smart contract `refund()` → directement au client (1 transaction au lieu de 2).

### Contexte d'utilisation

- **Hôtels** : Réservation de chambre
- **Agences de voyage** : Réservation de voyage
- **Établissements touristiques** : Accès à des sites/activités
- **Location** : Location de véhicule, maison, matériel
- **Livraison** : Services de livraison

---

## 🔄 Workflow complet

### 1. Dépôt du paiement (client → séquestre directement)

Quand un client réserve ou commande une prestation :

```
Client → Crée un paiement conditionnel
  ↓
Montant débité du wallet client (base de données)
  ↓
ÉTAPE 1 : Client approve() USDC vers le smart contract (PIN client)
  ↓
ÉTAPE 2 : Client deposit() vers le smart contract (PIN client)
  ↓
Les fonds sont séquestrés dans le smart contract
  ↓
QR code généré
  ↓
Statut : "pending_condition"
```

- ➡️ **Le vendeur voit que le client a payé**, mais ne reçoit pas encore les fonds.
- ➡️ **Le paiement est sécurisé et bloqué** jusqu'à validation.

**Note :**
- Le client signe directement les transactions approve() et deposit() avec son PIN
- Pas de wallet intermédiaire pour recevoir des fonds (le wallet admin signe uniquement release/refund)
- Le smart contract génère son propre wallet/adresse lors du déploiement

### 2. Validation au début de la prestation : Scan du QR code

La validation dépend du type d'activité, mais **toujours via scan du QR code** :

| Contexte | Moment de validation |
|----------|----------------------|
| **Hôtel** | Client arrive à la réception → QR scanné |
| **Agence de voyage** | Début du voyage → QR scanné |
| **Établissement touristique** | Accès au site/activité → QR scanné |
| **Location** (véhicule, maison, matériel) | Remise des clés / remise du matériel → QR scanné |
| **Livraison** | Livreur arrive → client scanne / livreur scanne |

Le scan du QR code confirme :
- ✓ la présence du client
- ✓ que la prestation commence
- ✓ que le vendeur peut recevoir les fonds

**Flux technique :**
```
QR code scanné (client ou vendeur)
  ↓
Statut : "condition_validated"
  ↓
Wallet admin signe smart contract release()
  ↓
Smart contract envoie directement les fonds au vendeur (vendorAddress enregistré lors du deposit())
  ↓
Statut : "released"
```

**Note :**
- Le wallet admin (ex-intermédiaire) signe la transaction release() sans recevoir de fonds
- Les fonds vont directement du smart contract au wallet du vendeur (1 seule transaction)

### 3. Libération automatique des fonds

Une fois validé (QR scanné) :
- Le smart contract libère les fonds **directement** vers le wallet du vendeur (hôtel, agence, etc.).
- Le statut devient **`released`**.
- Le client reçoit une confirmation de paiement libéré.

---

### 4. Annulation / No-show : remboursement OU retenue partielle

Si la prestation n'a pas lieu (QR non scanné), les fonds restent dans le smart contract ; l'application déclenche le remboursement **directement depuis le smart contract**.

**Deux scénarios :**

#### A. Remboursement total (processus général par défaut)

- Le smart contract `refund()` envoie **100 % du montant directement** au wallet du client (le depositor).
- **Statut :** `refunded`.

#### B. Remboursement partiel + pénalités (cas hôtel, location, voyage, etc.)

Certains prestataires peuvent engager des dépenses avant le début (ex. : préparation chambre, mobilisation équipe, véhicule réservé). Selon les règles définies par le prestataire (paramétrage en version 1) :
- Retenue partielle (ex. 10 %, 20 %, 30 %, montant fixe, etc.)
- Pénalité d'annulation selon délai (ex. annulation &lt; 24 h = 20 % retenu)
- Frais de non-présentation (« no-show fee »)

**Processus :**
- Le smart contract `partialRefund()` distribue **directement** la part vendeur au wallet du vendeur et le reste au wallet du client (une seule transaction blockchain).
- **Statut final :** `refunded_partial`.

**Exemples :**

| Situation | Règle | Paiement |
|-----------|-------|----------|
| Annulation 48 h avant | 0 % pénalité | 100 % remboursé |
| Annulation &lt; 24 h | 20 % retenu | 80 % remboursé |

**Flux technique :**
```
Annulation demandée
  ↓
Calcul des pénalités selon les règles (géré par l'application Java)
  ↓
Le smart contract rembourse de façon autonome
  ↓
Transfert Venly : Wallet du smart contract → Wallet du client (remboursement total ou partiel)
  ↓
OU Transferts partiels via Venly : Wallet du smart contract → vendeur + client (si pénalités)
  ↓
Statut : "refunded" ou "refunded_partial"
```

**Note :** Le smart contract gère le remboursement de façon autonome. Les transferts réels se font via Venly depuis le wallet du smart contract.

---

## 📊 5. Statuts globaux à gérer

| Statut | Description |
|--------|-------------|
| `pending_condition` | Paiement bloqué, prestation non commencée |
| `condition_validated` | QR code scanné |
| `released` | Paiement envoyé au vendeur |
| `refunded` | Remboursement total au client |
| `refunded_partial` | Remboursement partiel + retenue pour le vendeur |

---

## 🔵 API Endpoints

### 1. Créer un paiement conditionnel

**Endpoint :**
```
POST /api/internal/v1/conditional-payments/create?clientUsername={username}
```

**Body :**
```json
{
  "vendorUsername": "0033612108828",
  "serviceType": "HOTEL",
  "description": "Réservation chambre d'hôtel - 2 nuits",
  "amount": 100.0,
  "serviceStartDate": "2025-12-25T14:00:00",
  "cancellationDeadline": "2025-12-23T14:00:00"
}
```

**Réponse :**
```json
{
  "id": 1,
  "paymentCode": "CP-1704067200000-ABC12345",
  "clientUsername": "0033612108828",
  "vendorUsername": "hotel123",
  "serviceType": "HOTEL",
  "description": "Réservation chambre d'hôtel - 2 nuits",
  "amount": 100.0,
  "currency": "USDC",
  "status": "pending_condition",
  "escrowContractAddress": "0x...",
  "depositTransactionHash": "ESCROW-DEPOSIT-...",
  "qrCodeUrl": "https://akuunda-pay.io/qr/validate/qr-abc123...",
  "qrCodeToken": "qr-abc123...",
  "serviceStartDate": "2025-12-25T14:00:00",
  "cancellationDeadline": "2025-12-23T14:00:00",
  "createdAt": "2025-01-01T10:00:00",
  "updatedAt": "2025-01-01T10:00:00"
}
```

### 2. Valider un QR code

**Endpoint :**
```
POST /api/internal/v1/conditional-payments/validate-qr
```

**Body :**
```json
{
  "qrCodeToken": "qr-abc123...",
  "scannedBy": "0033612108828"
}
```

**Important :** 
- Le champ `scannedBy` identifie la personne qui a scanné le QR code (peut être le client ou le vendeur)
- Les fonds seront envoyés au wallet de cette personne (pas forcément le vendeur)
- Si `scannedBy` n'est pas fourni, le système utilisera celui sauvegardé dans le QR code

**Réponse :**
```json
{
  "id": 1,
  "paymentCode": "CP-1704067200000-ABC12345",
  "status": "released",
  "releaseTransactionHash": "0x1234567890abcdef...",
  "serviceActualStartDate": "2025-01-01T15:30:00",
  ...
}
```

**Note :** 
- Le `releaseTransactionHash` est le hash de la transaction Venly
- Les fonds sont envoyés au wallet de la personne qui a scanné le QR code (identifiée via `scannedBy`)

**Note :** Le `releaseTransactionHash` est le hash de la transaction Venly.

### 3. Annuler un paiement conditionnel

**Endpoint :**
```
POST /api/internal/v1/conditional-payments/{paymentCode}/cancel
```

**Body (optionnel) :**
```json
{
  "reason": "Client n'a pas pu se présenter"
}
```

**Réponse (remboursement partiel) :**
```json
{
  "id": 1,
  "paymentCode": "CP-1704067200000-ABC12345",
  "status": "refunded_partial",
  "refundedAmount": 80.0,
  "retainedAmount": 20.0,
  "cancellationReason": "Client n'a pas pu se présenter",
  "refundTransactionHash": "PARTIAL-0x...-0x...",
  ...
}
```

**Note :** Le `refundTransactionHash` contient les hashs des transactions Venly.

### 4. Récupérer un paiement par code

**Endpoint :**
```
GET /api/internal/v1/conditional-payments/{paymentCode}
```

### 5. Récupérer les paiements d'un utilisateur

**Endpoint :**
```
GET /api/internal/v1/conditional-payments/user/{username}
```

### 6. Libérer manuellement les fonds

**Endpoint :**
```
POST /api/internal/v1/conditional-payments/{paymentCode}/release
```

---

## 🎨 Types de services

| Type | Description | Exemple |
|------|-------------|---------|
| `HOTEL` | Réservation d'hôtel | Chambre d'hôtel |
| `TRAVEL_AGENCY` | Agence de voyage | Voyage organisé |
| `TOURISM` | Établissement touristique | Accès musée, parc |
| `RENTAL` | Location | Véhicule, maison, matériel |
| `DELIVERY` | Livraison | Service de livraison |

---

## ⚙️ Règles de remboursement

Les règles de remboursement permettent de définir des pénalités d'annulation selon les délais (cas hôtel, location, voyage, etc.).

### Exemples : remboursement partiel + pénalités

| Situation | Règle | Paiement |
|-----------|-------|----------|
| Annulation 48 h avant | 0 % pénalité | 100 % remboursé |
| Annulation &lt; 24 h | 20 % retenu | 80 % remboursé |

### Configuration des règles

Les règles sont stockées dans la table `conditional_payment_rules` et peuvent être :
- **Globales** : Appliquées à tous les vendeurs d'un type de service
- **Spécifiques** : Appliquées à un vendeur particulier

**Champs d'une règle :**
- `serviceType` : Type de prestation
- `hoursBeforeService` : Nombre d'heures avant le début
- `penaltyPercentage` : Pourcentage de pénalité (ex: 0.20 = 20%)
- `fixedPenaltyAmount` : Montant fixe de pénalité (alternative au pourcentage)
- `vendor` : Vendeur spécifique (null = règle globale)

---

## 🔧 Configuration

### Propriétés application.properties

```properties
# ===============================
# = Paiements Conditionnels (Smart Contract Déployé via Venly)
# ===============================

# Wallet intermédiaire (OBLIGATOIRE - au moins une des deux)
# Ce wallet est un wallet de service Akuunda Pay qui sert de pont
# pour recevoir les fonds des partenaires (YellowCard, Guardarian, etc.)
# Les partenaires ne peuvent pas envoyer directement au wallet du smart contract
# Option 1 : Par ID du wallet
akuunda.intermediate.wallet.id=wallet-service-akuunda-001
# Option 2 : Par adresse du wallet
# akuunda.intermediate.wallet.address=0xB6f7b717403B9d07b582a741a1689d1aAFF6957C

# Adresse du wallet du smart contract (OBLIGATOIRE)
# Le smart contract génère son propre wallet/adresse lors du déploiement
# Cette adresse est récupérée après le déploiement du smart contract via Venly
# Format: 0x...
akuunda.escrow.contract.wallet.address=0x...

# PIN du wallet intermédiaire pour les transferts Venly (OBLIGATOIRE)
# Format: "PIN:000000" ou "signingMethodId:pinValue"
# Ce PIN doit être configuré dans Venly pour le wallet intermédiaire
# Utilisé pour signer les transferts depuis le wallet intermédiaire vers le smart contract
akuunda.escrow.service.pin=PIN:000000

# Adresse du token USDC sur Polygon (déjà configuré, peut être personnalisé)
akuunda.escrow.token.address=0x3c499c542cEF5E3811e1192ce70d8cC03d5c3359

# Configuration QR code (OPTIONNEL - valeurs par défaut)
# URL de base pour générer les URLs des QR codes
akuunda.qrcode.base-url=https://akuunda-pay.io/qr/validate
# Durée d'expiration par défaut des QR codes (en heures)
akuunda.qrcode.default-expiration-hours=24
```

### Configuration Venly (déjà en place)

Le système utilise la configuration Venly existante :
- `venly.url.base` : URL de l'API Venly
- `venly.clientId` : Client ID Venly
- `venly.clientSecret` : Client Secret Venly

---

## 📝 Exemples d'utilisation

### Exemple 1 : Réservation d'hôtel

**1. Client crée le paiement :**
```bash
POST /api/internal/v1/conditional-payments/create?clientUsername=0033612108828
{
  "vendorUsername": "hotel123",
  "serviceType": "HOTEL",
  "description": "Réservation chambre - 2 nuits",
  "amount": 200.0,
  "serviceStartDate": "2025-12-25T14:00:00"
}
```

**Résultat :**
- Client débité de 200 USDC (base de données)
- Wallet de séquestre crédité de 200 USDC (base de données)
- QR code généré
- Statut : `pending_condition`

**2. Client arrive à l'hôtel, réceptionniste scanne le QR code :**
```bash
POST /api/internal/v1/conditional-payments/validate-qr
{
  "qrCodeToken": "qr-abc123...",
  "scannedBy": "hotel123"
}
```

**Résultat :**
- Transfert Venly : Wallet de séquestre → Wallet de l'hôtel (200 USDC)
- Wallet de l'hôtel crédité de 200 USDC
- Statut : `released`

### Exemple 2 : Annulation avec pénalité

**1. Client annule < 24h avant :**
```bash
POST /api/internal/v1/conditional-payments/CP-1704067200000-ABC12345/cancel
{
  "reason": "Imprévu"
}
```

**2. Système applique la règle :**
- 20% retenu pour l'hôtel (40 USDC)
- 80% remboursé au client (160 USDC)

**3. Transferts Venly :**
- Transfert 1 : Wallet de séquestre → Wallet de l'hôtel (40 USDC)
- Transfert 2 : Wallet de séquestre → Wallet du client (160 USDC)

**4. Résultat :**
- Statut : `refunded_partial`
- `retainedAmount` : 40.0
- `refundedAmount` : 160.0

---

## 🏗️ Architecture technique

### Architecture : Smart Contract Déployé

Le **smart contract** est un contrat déployé qui génère son propre wallet/adresse. Le **wallet intermédiaire** sert de pont :

```
┌─────────────────┐
│  Wallet Client  │
│  Balance: 1000  │
└────────┬──────────┘
       │ ÉTAPE 1 : Transfert Venly 100 USDC
       ▼
┌─────────────────────────────┐
│ Wallet Intermédiaire         │ ← Pont pour recevoir les fonds
│ (Wallet de service Akuunda) │   des partenaires (YellowCard, etc.)
│  Balance temporaire         │
└──────────┬────────────────────┘
           │ ÉTAPE 2 : Transfert Venly
           ▼
┌─────────────────────────────┐
│ Smart Contract Déployé       │ ← Génère son propre wallet/adresse
│ (Wallet du Smart Contract) │   Gère la logique de façon autonome
│  Balance: 100 USDC          │
└──────────┬────────────────────┘
           │
           ├─→ Validation QR → Venly → Wallet de la personne qui a scanné
           │
           └─→ Annulation → Venly → Wallet(s) Client/Vendeur
```

**Caractéristiques :**
- ✅ **Wallet Intermédiaire** : Pont pour recevoir les fonds des partenaires
- ✅ **Smart Contract** : Contrat déployé qui génère son propre wallet/adresse
- ✅ **Workflow** : Client → Wallet intermédiaire → Wallet du smart contract
- ✅ **Autonomie** : Le smart contract gère la logique de façon autonome
- ✅ **Venly** : Tous les transferts passent par Venly (blockchain réelle)

### Services utilisés

| Service | Utilisation | Statut |
|---------|-------------|--------|
| **Venly** | Tous les transferts de fonds (dépôt, libération, remboursement) | ✅ Déjà intégré |
| **Base de données** | Gestion des soldes et traçabilité | ✅ Existant |
| **QR Code** | Génération locale (UUID) | ✅ Local |
| **Wallet Intermédiaire** | Pont pour recevoir les fonds des partenaires | ✅ Existant |
| **Smart Contract** | Contrat déployé qui gère la logique de façon autonome | ✅ À déployer via Venly |

---

## 🔍 Dépannage

### Erreur : "Wallet intermédiaire non configuré"

**Cause :** `akuunda.intermediate.wallet.id` et `akuunda.intermediate.wallet.address` sont vides ou le wallet n'existe pas

**Solution :**
1. Vérifier que le wallet existe en base de données
2. Configurer soit `id` soit `address` dans `application.properties`
3. Redémarrer l'application

### Erreur : "Adresse du wallet du smart contract non configurée"

**Cause :** `akuunda.escrow.contract.wallet.address` est vide ou non configurée

**Solution :**
1. Déployer le smart contract via Venly
2. Récupérer l'adresse du wallet généré par le smart contract
3. Configurer `akuunda.escrow.contract.wallet.address` dans `application.properties`
4. Redémarrer l'application

### Erreur : "Échec du transfert Venly"

**Cause :** Le PIN du wallet intermédiaire est incorrect ou le wallet n'a pas de PIN configuré dans Venly

**Solution :**
1. Vérifier que le wallet intermédiaire a un PIN configuré dans Venly
2. Vérifier le format du PIN dans `akuunda.escrow.service.pin` (format: `PIN:000000`)
3. Vérifier que le wallet intermédiaire a suffisamment de fonds pour transférer vers le smart contract
4. Vérifier que l'adresse du wallet du smart contract est correcte

### Erreur : "QR code expiré"

**Cause :** Le QR code a dépassé sa durée de validité

**Solution :** Générer un nouveau QR code si nécessaire (via l'endpoint de création)

### Erreur : "Solde insuffisant"

**Cause :** Le client n'a pas suffisamment de USDC dans son wallet

**Solution :** Le client doit créditer son wallet avant de créer un paiement conditionnel

---

## 📚 Structure de la base de données

### Tables créées

1. **conditional_payments** : Paiements conditionnels
   - `id` : ID unique
   - `payment_code` : Code unique du paiement
   - `client_id` : Référence au client
   - `vendor_id` : Référence au vendeur
   - `amount` : Montant en USDC
   - `status` : Statut du paiement
   - `escrow_contract_address` : Adresse du wallet de séquestre
   - `deposit_transaction_hash` : Hash de dépôt
   - `release_transaction_hash` : Hash de libération (Venly)
   - `refund_transaction_hash` : Hash de remboursement (Venly)
   - `intermediate_wallet_id` : Référence au wallet de séquestre
   - `client_wallet_id` : Référence au wallet du client
   - `vendor_wallet_id` : Référence au wallet du vendeur

2. **qr_codes** : QR codes de validation
   - `id` : ID unique
   - `token` : Token unique du QR code
   - `conditional_payment_id` : Référence au paiement
   - `status` : Statut (generated, scanned, expired)
   - `generated_at` : Date de génération
   - `scanned_at` : Date de scan
   - `expires_at` : Date d'expiration

3. **conditional_payment_rules** : Règles de remboursement
   - `id` : ID unique
   - `service_type` : Type de prestation
   - `hours_before_service` : Nombre d'heures avant le début
   - `penalty_percentage` : Pourcentage de pénalité
   - `fixed_penalty_amount` : Montant fixe de pénalité
   - `vendor_id` : Vendeur spécifique (null = global)
   - `is_active` : Si la règle est active

---

## 🗄️ Migration de base de données

### Colonnes ajoutées à `one_time_payment_links`

Le support des paiements conditionnels nécessite les colonnes suivantes dans la table `one_time_payment_links` :

| Colonne | Type | Contrainte | Description |
|---------|------|------------|-------------|
| `link_type` | `VARCHAR(20)` | `NOT NULL DEFAULT 'SIMPLE'` | Type de lien : `SIMPLE` (paiement direct) ou `CONDITIONAL` (escrow/séquestre) |
| `service_type` | `VARCHAR(50)` | nullable | Type de service : `HOTEL`, `TRAVEL_AGENCY`, `TOURISM`, `RENTAL`, `DELIVERY` |
| `service_start_date` | `TIMESTAMP` | nullable | Date de début du service (ex : date d'arrivée à l'hôtel) |
| `cancellation_deadline` | `TIMESTAMP` | nullable | Date limite d'annulation (après cette date, pas de remboursement) |
| `conditional_payment_id` | `BIGINT` | nullable | Référence vers l'entrée `ConditionalPayment` créée après validation du paiement |
| `qr_code_url` | `VARCHAR(500)` | nullable | URL du QR code de confirmation (généré après paiement conditionnel validé) |
| `qr_code_token` | `VARCHAR(100)` | nullable | Token du QR code (pour vérification lors du scan à l'arrivée) |

### Script de migration

Le fichier de migration à appliquer est :

```
src/main/resources/db/migration/V2001__add_conditional_payment_columns_to_one_time_payment_links.sql
```

Ce script utilise `IF NOT EXISTS` sur chaque `ALTER TABLE` et `CREATE INDEX`, ce qui le rend **idempotent** (peut être rejoué sans erreur si les colonnes existent déjà).

### Exécution selon l'environnement

> **⚠️ Important :** Le projet n'utilise ni Flyway ni Liquibase — les migrations sont manuelles.

| Environnement | `ddl-auto` | Action requise |
|---------------|-----------|----------------|
| **dev** | `update` | Hibernate ajoute les colonnes nullable automatiquement, mais **échoue pour `link_type` (NOT NULL)**. Exécuter le script manuellement. |
| **preprod** | `update` | Même situation que dev. Exécuter le script manuellement. |
| **prod** | `validate` | Le script **DOIT** être exécuté **AVANT le déploiement**. Sinon l'application ne démarrera pas (`validate` vérifie que le schéma correspond aux entités JPA). |

### Commande d'exécution

```bash
psql -h <HOST> -U <USER> -d <DATABASE> \
  -f src/main/resources/db/migration/V2001__add_conditional_payment_columns_to_one_time_payment_links.sql
```

Remplacer `<HOST>`, `<USER>` et `<DATABASE>` par les valeurs du fichier `application-<env>.properties` de l'environnement cible.

Pour plus de détails sur la gestion des migrations manuelles, voir [`src/main/resources/db/migration/README.md`](src/main/resources/db/migration/README.md).

---

## ✅ Checklist de déploiement

- [ ] **Exécuter le script de migration SQL** (`V2001__add_conditional_payment_columns_to_one_time_payment_links.sql`) sur la base de données cible **avant** le déploiement
- [ ] Configurer le wallet intermédiaire dans `application.properties`
- [ ] Configurer le PIN du wallet de séquestre pour Venly
- [ ] Vérifier que le wallet de séquestre existe dans Venly
- [ ] Vérifier que le wallet de séquestre a un PIN configuré dans Venly
- [ ] Créer les règles de remboursement par défaut (optionnel)
- [ ] Tester le workflow complet :
  - [ ] Création d'un paiement conditionnel
  - [ ] Validation par QR code
  - [ ] Annulation avec remboursement
- [ ] Configurer les notifications (optionnel, à implémenter)

---

## 🚀 Avantages de cette architecture

✅ **Smart Contract Autonome** : Le smart contract gère la logique de façon autonome  
✅ **Wallet Intermédiaire comme Pont** : Permet de recevoir les fonds des partenaires qui ne peuvent pas envoyer directement au smart contract  
✅ **Blockchain réelle** : Tous les transferts passent par Venly (blockchain Polygon)  
✅ **Système autonome** : Le smart contract exécute le programme de façon autonome sans intervention manuelle  
✅ **Utilise les services existants** : Venly déjà intégré pour les transferts et le déploiement  
✅ **Traçabilité complète** : Tous les transferts sont enregistrés avec hash Venly  
✅ **Sécurité** : Les fonds sont réellement séquestrés dans le wallet du smart contract via Venly  

---

## 📞 Support

Pour toute question ou problème :
1. Vérifier les logs de l'application
2. Vérifier les transactions Venly dans l'interface Venly
3. Vérifier les soldes des wallets en base de données
4. Consulter la documentation Swagger : `/swagger-ui.html`

