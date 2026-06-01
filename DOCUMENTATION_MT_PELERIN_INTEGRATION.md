# 📋 Documentation - Intégration MT Pelerin

## 🎯 Vue d'ensemble

Cette documentation décrit l'intégration complète de **MT Pelerin** dans l'application Akuunda Wallet. MT Pelerin est un service de paiement qui permet aux utilisateurs de convertir des devises fiat en cryptomonnaies (OnRamp) et vice versa (OffRamp).

**Date d'intégration :** Décembre 2024  
**Version API MT Pelerin :** v1

---

## 📦 Fonctionnalités implémentées

### ✅ Tâche 1 : Signature Wallet avec MT Pelerin
- Génération de signatures uniques pour authentification
- Intégration avec Venly pour signer les messages
- Sauvegarde des signatures en base de données
- Correction des bugs de gestion des null pointers

### ✅ Tâche 2 : Estimation de frais MT Pelerin
- Appel à l'API `/currency_rates/convert` pour obtenir des estimations de prix
- Calcul des frais et taux de change
- Support pour différentes devises et réseaux

### ✅ Tâche 3 : Historique des transactions MT Pelerin
- Récupération des transactions depuis l'API `/transactions/merchantReport`
- Sauvegarde automatique en base de données
- Support de la pagination (skip/limit)
- Filtrage par dates

### ✅ Tâche 4 : Retrait d'argent (OffRamp)
- Construction de liens de retrait vers le widget MT Pelerin
- Conversion crypto → fiat
- Paramètres pré-remplis pour une meilleure UX

### ✅ Tâche 5 : Dépôt d'argent (OnRamp)
- Construction de liens de dépôt vers le widget MT Pelerin
- Conversion fiat → crypto
- Support des codes de validation et signatures optionnelles

---

## 🔧 Configuration

### Variables d'environnement

Les configurations suivantes ont été ajoutées dans `application.properties` :

```properties
# MT Pelerin
mtpelerin.api.key=1c7a15d7-7243-4a77-a04e-c2b74061fa88
mtpelerin.api.base-url=https://api.mtpelerin.com
mtpelerin.api.report-auth-header=ZVg4JON1FvYoN1hkENj6PbQzFWdII4M94CrwGYrZ
mtpelerin.widget.base-url=https://securepay.akuunda-pay.io
mtpelerin.referrer=5qCpmK2X
```

### Base de données

Une nouvelle table `mtpelerin_transactions` a été créée pour stocker les transactions :

```sql
CREATE TABLE mtpelerin_transactions (
    id BIGSERIAL PRIMARY KEY,
    merchant_oid VARCHAR(255) UNIQUE NOT NULL,
    transaction_group_id VARCHAR(255),
    reference VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    paid VARCHAR(100),
    received VARCHAR(100),
    creation_date TIMESTAMP,
    last_update TIMESTAMP,
    synced_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    transaction_type VARCHAR(20),
    username VARCHAR(255),
    wallet_address VARCHAR(255)
);
```

**Migration SQL :** `V1002__create_mtpelerin_transactions_table.sql`

---

## 🏗️ Architecture

### Structure des fichiers

```
📁 wallet/service/infrastructure/
  ├── AkuundaMtPelerinServiceClient.java (interface)
  └── impl/
      └── AkuundaMtPelerinServiceClientImpl.java (implémentation)

📁 wallet/service/controller/
  └── AkuundaMtPelerinController.java (endpoints REST)

📁 wallet/api/dto/external/
  ├── PriceQuoteRequest.java
  ├── PriceQuoteResponse.java
  ├── MerchantTransactionResponse.java
  ├── MtPelerinOffRampRequest.java
  ├── MtPelerinOffRampResponse.java
  ├── MtPelerinOnRampRequest.java
  └── MtPelerinOnRampResponse.java

📁 wallet/api/entities/
  └── MtPelerinTransaction.java

📁 wallet/api/dao/
  └── MtPelerinTransactionRepository.java
```

### Flux de données

```
Frontend
    ↓
AkuundaMtPelerinController
    ↓
AkuundaMtPelerinServiceClient
    ↓
API MT Pelerin / Widget MT Pelerin
    ↓
Base de données (pour transactions)
```

---

## 📡 API Endpoints

### Base URL
```
/api/internal/v1/mtpelerin
```

### 1. Générer une signature wallet

**Endpoint :** `POST /generate-signature`

**Description :** Génère une signature unique pour un utilisateur afin d'interagir avec les services MT Pelerin.

**Requête :**
```json
{
  "username": "002250777832982",
  "signingPinId": "b4f6c2e9-9c32-4a13-bb57-12df4cd9a77a"
}
```

**Réponse (200 OK) :**
```json
{
  "userName": "002250777832982",
  "mtpHash": "dGVzdGhhc2gxMjM0NTY3ODkwYWJjZGVm",
  "mtpCode": "1234",
  "walletAddress": "0x090d3840A4eD99ED40Be741884b7f941A856A23a"
}
```

**Codes de réponse :**
- `200` : Signature générée avec succès
- `400` : Requête invalide
- `404` : Utilisateur introuvable
- `417` : Erreur lors de la génération (API Venly)
- `500` : Erreur interne

---

### 2. Obtenir une estimation de prix

**Endpoint :** `POST /price-quote`

**Description :** Récupère une estimation de prix depuis l'API MT Pelerin pour une conversion de devise.

**Requête :**
```json
{
  "sourceCurrency": "EUR",
  "destCurrency": "USDC",
  "sourceAmount": 100,
  "sourceNetwork": "fiat",
  "destNetwork": "matic_mainnet",
  "isCardPayment": false
}
```

**Réponse (200 OK) :**
```json
{
  "sourceCurrency": "EUR",
  "destCurrency": "USDC",
  "sourceAmount": 100.0,
  "destAmount": 108.5,
  "sourceNetwork": "fiat",
  "destNetwork": "matic_mainnet",
  "exchangeRate": 1.085,
  "fees": 2.5,
  "feesCurrency": "EUR"
}
```

**Codes de réponse :**
- `200` : Estimation récupérée avec succès
- `400` : Requête invalide
- `500` : Erreur interne

---

### 3. Récupérer l'historique des transactions

**Endpoint :** `GET /transactions`

**Description :** Récupère les transactions du marchand depuis l'API MT Pelerin. Les transactions sont automatiquement sauvegardées en base de données.

**Paramètres de requête :**
- `fromDate` (requis) : Date de début (format ISO: `yyyy-MM-ddTHH:mm:ss`)
- `toDate` (requis) : Date de fin (format ISO: `yyyy-MM-ddTHH:mm:ss`)
- `skip` (optionnel) : Nombre de transactions à ignorer (pagination, défaut: 0)
- `limit` (optionnel) : Nombre maximum de transactions (défaut: 100)

**Exemple d'appel :**
```
GET /api/internal/v1/mtpelerin/transactions?fromDate=2025-12-01T00:00:00&toDate=2026-01-01T00:00:00&skip=0&limit=100
```

**Réponse (200 OK) :**
```json
{
  "fromDate": "2025-12-01T00:00:00",
  "toDate": "2026-01-01T00:00:00",
  "txs": [
    {
      "status": "finished",
      "paid": "100 USDC",
      "received": "92 EUR",
      "merchant_oid": "user123-uuid-456",
      "transaction_group_id": "tx-group-789",
      "reference": "REF-123456",
      "creationDate": "2025-12-15T10:30:00",
      "lastUpdate": "2025-12-15T10:35:00"
    }
  ]
}
```

**Statuts de transaction :**
- `pending` : Paiement reçu, transfert en cours
- `failed` : Transaction échouée
- `finished` : Transaction terminée avec succès

**Note importante :** Seules les transactions payées sont retournées. Les commandes non complétées n'apparaissent pas.

**Codes de réponse :**
- `200` : Transactions récupérées avec succès
- `400` : Requête invalide
- `500` : Erreur interne

---

### 4. Créer un lien de retrait (OffRamp)

**Endpoint :** `POST /offramp`

**Description :** Crée un lien de retrait permettant à un utilisateur de convertir des cryptomonnaies en fiat.

**Paramètres de requête :**
- `username` (optionnel) : Nom d'utilisateur pour générer l'orderId

**Requête :**
```json
{
  "sdc": "EUR",
  "ssa": 100.0,
  "lang": "fr",
  "addr": "0x090d3840A4eD99ED40Be741884b7f941A856A23a",
  "phone": "33612108828",
  "ctry": "FR"
}
```

**Réponse (200 OK) :**
```json
{
  "redirectUrl": "https://securepay.akuunda-pay.io/?tab=sell&ssc=USDC&sdc=EUR&ssa=100&lang=fr&snet=matic_mainnet&net=matic_mainnet&addr=0x090d3840A4eD99ED40Be741884b7f941A856A23a&rfr=5qCpmK2X&phone=33612108828&ctry=FR&oid=user123-uuid&_ctkn=api-key",
  "orderId": "user123-uuid"
}
```

**Paramètres du lien généré :**

| Paramètre | Source | Description |
|-----------|--------|-------------|
| `tab` | Back | Toujours `sell` pour OffRamp |
| `ssc` | Back | Devise crypto source (toujours `USDC`) |
| `sdc` | Front | Devise fiat destination |
| `ssa` | Front | Montant fourni par l'utilisateur |
| `lang` | Front | Langue du widget |
| `snet` | Back | Réseau source (toujours `matic_mainnet`) |
| `net` | Back | Réseau destination (toujours `matic_mainnet`) |
| `addr` | Front | Adresse wallet utilisateur |
| `rfr` | Back | Référent (configuré) |
| `phone` | Front | Numéro de téléphone pour login pré-rempli |
| `ctry` | Front | Code pays |
| `oid` | Back | Order ID unique généré |
| `_ctkn` | Back | API Key MT Pelerin |

**Codes de réponse :**
- `200` : Lien créé avec succès
- `400` : Requête invalide
- `500` : Erreur interne

---

### 5. Créer un lien de dépôt (OnRamp)

**Endpoint :** `POST /onramp`

**Description :** Crée un lien de dépôt permettant à un utilisateur de convertir des fiat en cryptomonnaies.

**Paramètres de requête :**
- `username` (optionnel) : Nom d'utilisateur pour générer l'orderId

**Requête :**
```json
{
  "bsc": "EUR",
  "bsa": 100.0,
  "phone": "33612108828",
  "ctry": "FR",
  "lang": "fr",
  "addr": "0x090d3840A4eD99ED40Be741884b7f941A856A23a",
  "code": "1234",
  "hash": ""
}
```

**Réponse (200 OK) :**
```json
{
  "redirectUrl": "https://securepay.akuunda-pay.io/?tab=buy&bsc=EUR&bdc=USDC&bsa=100&dnet=matic_mainnet&net=matic_mainnet&rfr=5qCpmK2X&phone=33612108828&ctry=FR&lang=fr&addr=0x090d3840A4eD99ED40Be741884b7f941A856A23a&code=1234&hash=&oid=user123-uuid&_ctkn=api-key",
  "orderId": "user123-uuid"
}
```

**Paramètres du lien généré :**

| Paramètre | Source | Description |
|-----------|--------|-------------|
| `tab` | Back | Toujours `buy` pour OnRamp |
| `bsc` | Front | Devise fiat source |
| `bdc` | Back | Devise crypto destination (toujours `USDC`) |
| `bsa` | Front | Montant fiat |
| `dnet` | Back | Réseau destination (toujours `matic_mainnet`) |
| `net` | Back | Réseau (toujours `matic_mainnet`) |
| `rfr` | Back | Référent (configuré) |
| `phone` | Front | Numéro de téléphone pour login pré-rempli |
| `ctry` | Front | Code pays |
| `lang` | Front | Langue du widget |
| `addr` | Front | Adresse wallet utilisateur |
| `code` | Front | Code de validation (optionnel) |
| `hash` | Front | Signature optionnelle |
| `oid` | Back | Order ID unique généré |
| `_ctkn` | Back | API Key MT Pelerin |

**Codes de réponse :**
- `200` : Lien créé avec succès
- `400` : Requête invalide
- `500` : Erreur interne

---

## 🔄 Workflows

### Workflow 1 : Dépôt d'argent (OnRamp)

```
1. Utilisateur sélectionne montant et devise fiat
   ↓
2. Frontend appelle POST /api/internal/v1/mtpelerin/onramp
   ↓
3. Backend génère un orderId unique
   ↓
4. Backend construit l'URL du widget avec tous les paramètres
   ↓
5. Frontend redirige l'utilisateur vers l'URL générée
   ↓
6. Utilisateur complète le paiement sur le widget MT Pelerin
   ↓
7. MT Pelerin traite le paiement
   ↓
8. Transaction apparaît dans GET /transactions (statut: pending → finished)
```

### Workflow 2 : Retrait d'argent (OffRamp)

```
1. Utilisateur sélectionne montant crypto à retirer
   ↓
2. Frontend appelle POST /api/internal/v1/mtpelerin/offramp
   ↓
3. Backend génère un orderId unique
   ↓
4. Backend construit l'URL du widget avec tous les paramètres
   ↓
5. Frontend redirige l'utilisateur vers l'URL générée
   ↓
6. Utilisateur complète le retrait sur le widget MT Pelerin
   ↓
7. MT Pelerin traite le retrait
   ↓
8. Transaction apparaît dans GET /transactions (statut: pending → finished)
```

### Workflow 3 : Synchronisation des transactions

```
1. Backend appelle périodiquement GET /transactions
   ↓
2. API MT Pelerin retourne les transactions payées
   ↓
3. Backend sauvegarde chaque transaction en base de données
   ↓
4. Si transaction existe déjà (merchant_oid), mise à jour du statut
   ↓
5. Les transactions sont disponibles pour consultation
```

---

## 📊 Modèle de données

### Table : `mtpelerin_transactions`

| Colonne | Type | Description |
|---------|------|-------------|
| `id` | BIGSERIAL | Identifiant unique (PK) |
| `merchant_oid` | VARCHAR(255) | Identifiant de commande marchand (UNIQUE) |
| `transaction_group_id` | VARCHAR(255) | ID du groupe de transactions |
| `reference` | VARCHAR(255) | Référence de la transaction |
| `status` | VARCHAR(50) | Statut : pending, failed, finished |
| `paid` | VARCHAR(100) | Montant payé (ex: "100 USDC") |
| `received` | VARCHAR(100) | Montant reçu (ex: "92 EUR") |
| `creation_date` | TIMESTAMP | Date de création de la transaction |
| `last_update` | TIMESTAMP | Dernière mise à jour |
| `synced_at` | TIMESTAMP | Date de synchronisation depuis l'API |
| `transaction_type` | VARCHAR(20) | Type : ONRAMP ou OFFRAMP |
| `username` | VARCHAR(255) | Nom d'utilisateur (optionnel) |
| `wallet_address` | VARCHAR(255) | Adresse wallet (optionnel) |

**Index créés :**
- `idx_mtpelerin_transactions_merchant_oid` sur `merchant_oid`
- `idx_mtpelerin_transactions_status` sur `status`
- `idx_mtpelerin_transactions_transaction_type` sur `transaction_type`
- `idx_mtpelerin_transactions_username` sur `username`
- `idx_mtpelerin_transactions_creation_date` sur `creation_date`

---

## 🔐 Sécurité

### Authentification

Tous les endpoints nécessitent une authentification Bearer Token (JWT).

### Headers requis

- `Authorization: Bearer {token}`
- `Content-Type: application/json`

### API Key MT Pelerin

L'API Key est stockée dans les configurations et utilisée automatiquement pour :
- Les appels à l'API MT Pelerin (`_ctkn` dans les URLs)
- L'authentification pour récupérer les transactions (`x-report-auth`)

---

## 🧪 Tests

### Exemples de requêtes cURL

#### 1. Générer une signature
```bash
curl -X POST "http://localhost:8089/api/internal/v1/mtpelerin/generate-signature" \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "username": "002250777832982",
    "signingPinId": "b4f6c2e9-9c32-4a13-bb57-12df4cd9a77a"
  }'
```

#### 2. Obtenir une estimation de prix
```bash
curl -X POST "http://localhost:8089/api/internal/v1/mtpelerin/price-quote" \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "sourceCurrency": "EUR",
    "destCurrency": "USDC",
    "sourceAmount": 100,
    "sourceNetwork": "fiat",
    "destNetwork": "matic_mainnet",
    "isCardPayment": false
  }'
```

#### 3. Récupérer les transactions
```bash
curl -X GET "http://localhost:8089/api/internal/v1/mtpelerin/transactions?fromDate=2025-12-01T00:00:00&toDate=2026-01-01T00:00:00&skip=0&limit=100" \
  -H "Authorization: Bearer {token}"
```

#### 4. Créer un lien de retrait
```bash
curl -X POST "http://localhost:8089/api/internal/v1/mtpelerin/offramp?username=user123" \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "sdc": "EUR",
    "ssa": 100.0,
    "lang": "fr",
    "addr": "0x090d3840A4eD99ED40Be741884b7f941A856A23a",
    "phone": "33612108828",
    "ctry": "FR"
  }'
```

#### 5. Créer un lien de dépôt
```bash
curl -X POST "http://localhost:8089/api/internal/v1/mtpelerin/onramp?username=user123" \
  -H "Authorization: Bearer {token}" \
  -H "Content-Type: application/json" \
  -d '{
    "bsc": "EUR",
    "bsa": 100.0,
    "phone": "33612108828",
    "ctry": "FR",
    "lang": "fr",
    "addr": "0x090d3840A4eD99ED40Be741884b7f941A856A23a",
    "code": "1234",
    "hash": ""
  }'
```

---

## 📝 Notes importantes

### Order ID (merchant_oid)

- L'`orderId` (merchant_oid) est généré automatiquement par le backend
- Format : `{username}-{uuid}` si username fourni, sinon `{uuid}`
- Cet ID permet de faire correspondre vos commandes internes avec les transactions MT Pelerin
- Utilisez le paramètre `oid` dans l'URL du widget pour passer votre propre ID

### Statuts de transaction

- **pending** : Le paiement a été reçu mais le transfert est encore en cours
- **failed** : La transaction a échoué
- **finished** : La transaction est terminée avec succès

### Transactions non payées

Le endpoint `/merchantReport` ne retourne que les transactions qui ont été **effectivement payées**. Si un utilisateur crée une commande mais ne complète pas le paiement, elle n'apparaîtra pas dans les résultats.

### Réseaux supportés

Actuellement, l'intégration utilise principalement :
- **matic_mainnet** (Polygon) pour les cryptomonnaies
- **fiat** pour les devises traditionnelles

D'autres réseaux peuvent être supportés en modifiant les paramètres `snet`, `net`, `dnet` dans les liens générés.

---

## 🐛 Dépannage

### Problèmes courants

#### 1. Erreur 400 sur `/generate-signature`
- Vérifier que l'utilisateur existe et a un wallet
- Vérifier que le `signingPinId` est valide
- Vérifier les logs pour plus de détails

#### 2. Erreur 500 sur `/price-quote`
- Vérifier la connectivité avec l'API MT Pelerin
- Vérifier que les paramètres sont valides (devises, réseaux)
- Vérifier les logs pour les erreurs API

#### 3. Aucune transaction retournée
- Vérifier que les dates sont correctes
- Vérifier qu'il y a des transactions payées dans la période
- Les transactions non payées n'apparaissent pas

#### 4. Lien widget ne fonctionne pas
- Vérifier que tous les paramètres requis sont présents
- Vérifier que l'API Key est correcte
- Vérifier que l'URL du widget est correcte dans la configuration

---

## 📚 Références

- **Documentation MT Pelerin :** https://developers.mtpelerin.com/
- **API Price Quote :** https://developers.mtpelerin.com/integration-guides/apis/price-quote-api
- **Widget MT Pelerin :** https://securepay.akuunda-pay.io

---

## ✅ Checklist de déploiement

- [ ] Configurer les variables d'environnement (API key, URLs)
- [ ] Exécuter la migration SQL `V1002__create_mtpelerin_transactions_table.sql`
- [ ] Vérifier la connectivité avec l'API MT Pelerin
- [ ] Tester tous les endpoints avec Swagger UI
- [ ] Vérifier que les transactions sont bien sauvegardées
- [ ] Tester les liens OnRamp et OffRamp dans un environnement de test
- [ ] Configurer les webhooks MT Pelerin si nécessaire (optionnel)

---

## 🔄 Évolutions futures

### Améliorations possibles

1. **Webhooks MT Pelerin**
   - Implémenter les webhooks pour recevoir les notifications de transactions en temps réel
   - Éviter de devoir poller l'API régulièrement

2. **Intégration dans FeesCalculationService**
   - Ajouter MT Pelerin comme opérateur dans le calcul de frais
   - Permettre la sélection de MT Pelerin pour les calculs de frais

3. **Support de multiples réseaux**
   - Permettre la sélection du réseau blockchain (Ethereum, Polygon, etc.)
   - Configurer les réseaux supportés par pays/devise

4. **Dashboard de suivi**
   - Créer une interface pour visualiser les transactions MT Pelerin
   - Statistiques et rapports sur les conversions

---

**Documentation créée le :** Décembre 2024  
**Dernière mise à jour :** Décembre 2024  
**Version :** 1.0

