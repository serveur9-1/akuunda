# Documentation Complète : Système de Liens de Paiement en Masse

## Table des matières

1. [Vue d'ensemble](#vue-densemble)
2. [Cas d'usage](#cas-dusage)
3. [Architecture technique](#architecture-technique)
4. [Modèle de données](#modèle-de-données)
5. [Endpoints API](#endpoints-api)
6. [Flux de paiement](#flux-de-paiement)
7. [Génération de codes uniques](#génération-de-codes-uniques)
8. [Sécurité et validations](#sécurité-et-validations)
9. [Exemples d'utilisation](#exemples-dutilisation)

---

## Vue d'ensemble

Le système de liens de paiement en masse permet aux utilisateurs de créer des liens uniques et partageables pour recevoir des paiements de plusieurs personnes. Inspiré de services comme [Djamo Pay](https://pay.djamo.com/gn1mb), cette fonctionnalité permet :

- **Création de liens uniques** : Génération automatique d'un code court (ex: "gn1mb")
- **Paiements multiples** : Un même lien peut être utilisé par plusieurs payeurs
- **Montant fixe ou libre** : Possibilité de définir un montant fixe ou laisser le payeur choisir lors du paiement
- **Devise fixe ou libre** : Possibilité de définir une devise fixe ou laisser le payeur choisir lors du paiement
- **Expiration optionnelle** : Les liens peuvent avoir une date d'expiration
- **Suivi des paiements** : Statistiques en temps réel (nombre de paiements, montant total reçu)
- **Interface web** : Les paiements se font via une interface web (comme Djamo Pay) suivant un flux en 5 étapes : sélection du pays, choix du moyen de paiement, informations utilisateur (optionnel), saisie du montant, et validation

### Principe fondamental

Un **lien de paiement** est un identifiant unique court qui permet à n'importe qui de payer directement vers le wallet du créateur du lien. Le système :
1. Génère un code unique court (5 caractères alphanumériques)
2. Associe ce code à un utilisateur (créateur) et ses paramètres (description, montant optionnel, devise optionnelle)
3. Permet à plusieurs payeurs d'utiliser le même lien
4. Le payeur accède au lien via une interface web suivant un flux en 5 étapes :
   - **Étape 1** : Sélection du pays → Les moyens de paiement s'affichent automatiquement
   - **Étape 2** : Choix du moyen de paiement (Wave, Orange, MTN, Moov, Virement, Carte, etc.)
   - **Étape 3** : Saisie des informations utilisateur (nom, email) - optionnel
   - **Étape 4** : Saisie du montant (si non fixe)
   - **Étape 5** : Validation du paiement
     - **Mobile Money (Afrique)** : Saisie du numéro de téléphone
     - **Carte/Virement (Europe/Diaspora)** : Redirection vers le partenaire (Guardarian, etc.)
5. Traite chaque paiement via YellowCard (mobile money) ou Guardarian (carte/virement)
6. Met à jour les statistiques du lien en temps réel

---

## Cas d'usage

### 1. Facturation
**Scénario :** Un commerçant veut recevoir le paiement d'une facture
- Crée un lien avec montant fixe (ex: 5000 XOF)
- Partage le lien avec le client
- Le client paie via le lien
- Le commerçant reçoit le paiement instantanément

### 2. Cagnotte / Collecte de fonds
**Scénario :** Organisation d'un événement, besoin de collecter des contributions
- Crée un lien avec montant libre
- Partage le lien sur les réseaux sociaux
- Plusieurs personnes contribuent avec des montants différents
- Suivi en temps réel des contributions

### 3. Paiement récurrent
**Scénario :** Abonnement mensuel
- Crée un lien avec montant fixe et expiration mensuelle
- Partage le lien avec les abonnés
- Chaque mois, les abonnés utilisent le même lien pour payer

### 4. Collecte d'argent pour un groupe
**Scénario :** Groupe d'amis collecte de l'argent pour un cadeau
- Un membre crée un lien avec montant libre
- Partage le lien dans le groupe
- Chaque membre contribue selon ses moyens
- Le créateur voit le total collecté

---

## Architecture technique

### Composants principaux

```
┌─────────────────────────────────────────────────────────────┐
│                     Couche Contrôleurs                        │
├─────────────────────────────────────────────────────────────┤
│ • PaymentLinkController                                       │
│   - POST /api/internal/v1/payment-links/create              │
│   - GET /api/internal/v1/payment-links/{uniqueCode}          │
│   - GET /api/internal/v1/payment-links/user/{username}       │
│   - PUT /api/internal/v1/payment-links/{uniqueCode}/deactivate│
│   - POST /api/internal/v1/payment-links/process-payment      │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    Couche Services                            │
├─────────────────────────────────────────────────────────────┤
│ • PaymentLinkService / PaymentLinkServiceImpl                 │
│   - createPaymentLink()                                       │
│   - getPaymentLinkByCode()                                    │
│   - getUserPaymentLinks()                                     │
│   - deactivatePaymentLink()                                   │
│   - processPayment()                                          │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    Couche Accès Données                       │
├─────────────────────────────────────────────────────────────┤
│ • PaymentLinkRepository                                       │
│   - findByUniqueCode()                                        │
│   - findByCreator()                                           │
│   - existsByUniqueCode()                                      │
│                                                              │
│ • PaymentLinkTransactionRepository                           │
│   - findByPaymentLink()                                       │
│                                                              │
│ • WalletRepository                                            │
│ • OperationRepository                                         │
│ • UserRepository                                              │
└─────────────────────────────────────────────────────────────┘
```

### Relations entre composants

```
PaymentLinkController
    │
    └─► PaymentLinkService
            │
            ├─► PaymentLinkRepository
            ├─► PaymentLinkTransactionRepository
            ├─► UserRepository
            ├─► WalletRepository
            └─► OperationRepository
```

---

## Modèle de données

### Table : `payment_links`

Stoque les informations des liens de paiement.

| Colonne | Type | Description | Contraintes |
|---------|------|-------------|-------------|
| `id` | BIGINT | Identifiant unique | PRIMARY KEY, AUTO GENERATED |
| `unique_code` | VARCHAR(20) | Code unique court (ex: "gn1mb") | NOT NULL, UNIQUE, INDEX |
| `user_id` | VARCHAR | ID utilisateur créateur | NOT NULL, FOREIGN KEY |
| `description` | VARCHAR(500) | Description/libellé | NOT NULL |
| `amount` | DOUBLE | Montant fixe (null = montant libre choisi par le payeur) | NULLABLE |
| `currency` | VARCHAR(10) | Code devise (null = devise choisie par le payeur, ex: XOF, EUR, USD) | NULLABLE |
| `is_active` | BOOLEAN | Si le lien est actif | NOT NULL, DEFAULT true |
| `expires_at` | TIMESTAMP | Date d'expiration | NULLABLE |
| `total_payments` | INTEGER | Nombre total de paiements | NOT NULL, DEFAULT 0 |
| `total_amount_received` | DOUBLE | Montant total reçu | NOT NULL, DEFAULT 0.0 |
| `created_at` | TIMESTAMP | Date de création | NOT NULL |
| `updated_at` | TIMESTAMP | Date de mise à jour | NOT NULL |

### Table : `payment_link_transactions`

Stoque les transactions individuelles effectuées via les liens.

| Colonne | Type | Description | Contraintes |
|---------|------|-------------|-------------|
| `id` | BIGINT | Identifiant unique | PRIMARY KEY, AUTO GENERATED |
| `payment_link_id` | BIGINT | Référence au lien | NOT NULL, FOREIGN KEY |
| `payer_id` | VARCHAR | ID utilisateur payeur | NOT NULL, FOREIGN KEY |
| `amount` | DOUBLE | Montant payé | NOT NULL |
| `currency` | VARCHAR(10) | Devise | NOT NULL |
| `status` | VARCHAR(50) | Statut (PENDING, COMPLETED, FAILED) | NOT NULL |
| `note` | VARCHAR(500) | Note optionnelle | NULLABLE |
| `operation_id` | BIGINT | Référence à l'opération | NULLABLE |
| `created_at` | TIMESTAMP | Date de création | NOT NULL |
| `updated_at` | TIMESTAMP | Date de mise à jour | NOT NULL |

### Schéma relationnel

```
┌─────────────────────┐
│      Users           │
│  (Créateur/Payeur)   │
│                     │
│  id: String         │
│  username: String   │
└──────────┬──────────┘
           │
           │ 1:N
           │
    ┌──────┴──────────────────────┐
    │                               │
    ▼                               ▼
┌──────────────┐          ┌─────────────────────┐
│ payment_links│          │payment_link_transactions│
├──────────────┤          ├─────────────────────┤
│ id           │          │ id                   │
│ unique_code  │          │ payment_link_id ─────┼──┐
│ user_id ─────┼──────────┤ payer_id ───────────┼──┤
│ description  │          │ amount               │ │
│ amount       │          │ currency              │ │
│ currency     │          │ status                │ │
│ is_active    │          │ note                  │ │
│ expires_at   │          │ operation_id          │ │
│ total_*      │          │ created_at            │ │
│ created_at   │          └─────────────────────┘ │
│ updated_at   │                                  │
└──────────────┘                                  │
                                                  │
                                                  │
┌─────────────────────────────────────────────────┘
│
│ 1:N
│
▼
┌──────────────┐
│  operations  │
├──────────────┤
│ id           │
│ type         │
│ amount       │
│ username     │
│ designation  │
│ ...
└──────────────┘
```

---

## Endpoints API

### Base URL

```
http://localhost:8089/api/internal/v1/payment-links
```

### Endpoints publics (interface web)

Les endpoints suivants sont **publics** et ne nécessitent **pas d'authentification**. Ils sont utilisés par l'interface web de paiement.

#### 1. Récupérer les informations publiques d'un lien de paiement

**Endpoint :** `GET /api/internal/v1/payment-links/web/{uniqueCode}`

**Description :** Récupère les informations publiques d'un lien de paiement pour l'interface web.

**Path Parameters :**
- `uniqueCode` (requis) : Code unique du lien (ex: "gn1mb")

**Réponse succès (200) :**
```json
{
  "uniqueCode": "gn1mb",
  "description": "Paiement facture électricité - Janvier 2024",
  "amount": null,
  "currency": null,
  "isActive": true,
  "expiresAt": null,
  "totalPayments": 5,
  "totalAmountReceived": 25000.0,
  "creatorName": "Jean Dupont"
}
```

**Réponses d'erreur :**
- `404` : Lien de paiement non trouvé
- `410` : Lien de paiement expiré ou inactif

---

#### 2. Récupérer la liste des pays disponibles (Étape 1)

**Endpoint :** `GET /api/internal/v1/payment-links/web/countries`

**Description :** Récupère la liste de tous les pays activés disponibles pour les paiements. Utilisé lors de l'étape 1 du flux de paiement web.

**Réponse succès (200) :**
```json
[
  {
    "id": 1,
    "countryCode": "CI",
    "countryName": "Côte d'Ivoire",
    "currencyCode": "XOF",
    "callingCode": 225,
    "capital": "Abidjan",
    "continentName": "Afrique"
  },
  {
    "id": 2,
    "countryCode": "SN",
    "countryName": "Sénégal",
    "currencyCode": "XOF",
    "callingCode": 221,
    "capital": "Dakar",
    "continentName": "Afrique"
  },
  {
    "id": 3,
    "countryCode": "FR",
    "countryName": "France",
    "currencyCode": "EUR",
    "callingCode": 33,
    "capital": "Paris",
    "continentName": "Europe"
  }
]
```

---

#### 3. Récupérer les moyens de paiement disponibles pour un pays (Étape 2)

**Endpoint :** `GET /api/internal/v1/payment-links/web/payment-methods/{countryCode}`

**Description :** Récupère la liste de tous les moyens de paiement disponibles pour un pays donné. Les moyens de paiement s'affichent automatiquement après la sélection du pays. Utilisé lors de l'étape 2 du flux de paiement web.

**Path Parameters :**
- `countryCode` (requis) : Code pays ISO (ex: CI, SN, ML, FR)

**Note importante :** 
- La devise est automatiquement déterminée par le pays (pas besoin d'une étape séparée)
- Cet endpoint retourne tous les types de moyens de paiement actifs (mobile money, virement bancaire, carte, etc.)
- Les channels sont filtrés pour ne retourner que ceux avec `status = "active"`

**Réponse succès (200) :**
```json
[
  {
    "id": "37b63794-284b-4a09-863b-9b74a3f621e1",
    "name": "Mobile Money",
    "channelType": "momo",
    "country": "CI",
    "currency": "XOF",
    "rampType": "INSTANT",
    "minAmount": 500.0,
    "maxAmount": 250000.0,
    "status": "active"
  },
  {
    "id": "fa21a1b1-3db4-4412-b733-f71cb3a23b4a",
    "name": "Bank Transfer",
    "channelType": "bank",
    "country": "CI",
    "currency": "XOF",
    "rampType": "MANUAL",
    "minAmount": 10.0,
    "maxAmount": 100000.0,
    "status": "active"
  }
]
```

**Types de moyens de paiement retournés :**
- **Mobile Money (momo)** : Wave, Orange Money, MTN, Moov, etc. (Afrique)
- **Virement bancaire (bank)** : Pour paiements par virement
- **Carte bancaire (card)** : Pour paiements par carte (Europe/Diaspora)

**Réponses d'erreur :**
- `404` : Pays non trouvé
- `400` : Code pays invalide
- `500` : Erreur interne du serveur

---

#### 4. Effectuer un paiement via l'interface web (Étape 5)

**Endpoint :** `POST /api/internal/v1/payment-links/web/payment`

**Description :** Initie un paiement via l'interface web en utilisant mobile money. Cet endpoint intègre avec YellowCard OnRamp et retourne un `redirectUrl` pour rediriger l'utilisateur vers YellowCard afin de compléter le paiement.

**Body :**
```json
{
  "uniqueCode": "string (code unique du lien, REQUIS)",
  "amount": "number (montant à payer, REQUIS)",
  "currency": "string (code devise ISO, REQUIS, ex: XOF, EUR, USD. Déterminé automatiquement par le pays)",
  "phoneNumber": "string (numéro de téléphone du payeur, OBLIGATOIRE pour mobile money, optionnel pour autres moyens, format international, ex: +2250700123456)",
  "countryCode": "string (code pays ISO, REQUIS, ex: CI, SN, ML, FR)",
  "paymentMethodId": "string (ID du canal de paiement depuis /payment-methods, REQUIS)",
  "paymentMethodType": "string (type de moyen de paiement: momo, bank, card, etc., optionnel, défaut: momo)",
  "payerName": "string (nom du payeur, optionnel)",
  "payerEmail": "string (email du payeur, optionnel)",
  "note": "string (message/note optionnel du payeur, optionnel)"
}
```

**Exemple - Paiement Mobile Money :**
```json
{
  "uniqueCode": "gn1mb",
  "amount": 5000.0,
  "currency": "XOF",
  "phoneNumber": "+2250700123456",
  "countryCode": "CI",
  "paymentMethodId": "37b63794-284b-4a09-863b-9b74a3f621e1",
  "paymentMethodType": "momo",
  "payerName": "Jean Dupont",
  "payerEmail": "jean.dupont@example.com",
  "note": "Paiement facture électricité"
}
```

**Exemple - Paiement minimal (mobile money) :**
```json
{
  "uniqueCode": "abc12",
  "amount": 10000.0,
  "currency": "XOF",
  "phoneNumber": "+2250700999999",
  "countryCode": "CI",
  "paymentMethodId": "37b63794-284b-4a09-863b-9b74a3f621e1",
  "paymentMethodType": "momo"
}
```

**Réponse succès (200) :**
```json
{
  "success": true,
  "message": "Payment initiated successfully",
  "redirectUrl": "https://yellowcard.io/payment/...",
  "transactionId": "tx_1234567890",
  "amount": 5000.0,
  "currency": "XOF"
}
```

**Réponses d'erreur :**
- `400` : Requête invalide (montant incorrect, devise incorrecte, lien inactif)
- `404` : Lien de paiement ou wallet du créateur non trouvé
- `410` : Lien de paiement expiré
- `500` : Erreur interne du serveur

**Note importante :** 
- Pour **Mobile Money (momo)** : Après avoir reçu le `redirectUrl`, l'interface web doit rediriger l'utilisateur vers cette URL pour qu'il complète le paiement sur YellowCard. Le statut de la transaction sera mis à jour via un webhook YellowCard (à implémenter).
- Pour **Carte/Virement (bank, card)** : L'intégration avec Guardarian ou autres partenaires est en cours de développement. Actuellement, seul le mobile money est supporté.

---

### Endpoints authentifiés (gestion des liens)

#### 6. Créer un lien de paiement

**Endpoint :** `POST /api/internal/v1/payment-links/create?username={username}`

**Description :** Crée un nouveau lien de paiement unique.

**Query Parameters :**
- `username` (requis) : Username (numéro de téléphone) du créateur du lien

**Body :**
```json
{
  "description": "string (description/libellé du paiement, REQUIS)",
  "amount": "number (optionnel, montant fixe. Si null, montant libre choisi par le payeur)",
  "currency": "string (optionnel, code devise ISO. Si null, devise choisie par le payeur, ex: XOF, EUR, USD)",
  "expiresAt": "string (optionnel, date d'expiration ISO-8601)"
}
```

**Note importante :** Si `amount` et `currency` sont omis, le payeur pourra choisir le montant et la devise lors du paiement via l'interface web (comme sur Djamo Pay).

**Exemple simple (montant et devise libres) :**
```json
{
  "description": "Paiement facture électricité - Janvier 2024"
}
```

**Exemple avec montant fixe :**
```json
{
  "description": "Paiement facture électricité - Janvier 2024",
  "amount": 5000.0,
  "currency": "XOF",
  "expiresAt": "2024-12-31T23:59:59"
}
```

**Exemple avec devise fixe mais montant libre :**
```json
{
  "description": "Cagnotte anniversaire",
  "currency": "XOF"
}
```

**Exemple avec montant et devise libres (choisis par le payeur) :**
```json
{
  "description": "Paiement facture électricité - Janvier 2024"
}
```

**Réponse succès (201) :**
```json
{
  "id": 1,
  "uniqueCode": "gn1mb",
  "paymentUrl": "https://akuunda-pay.io/pay/gn1mb",
  "description": "Paiement facture électricité - Janvier 2024",
  "amount": 5000.0,
  "currency": "XOF",
  "isActive": true,
  "expiresAt": "2024-12-31T23:59:59",
  "totalPayments": 0,
  "totalAmountReceived": 0.0,
  "creatorUsername": "002250759146858",
  "createdAt": "2024-01-15T10:30:00",
  "updatedAt": "2024-01-15T10:30:00"
}
```

**Réponses d'erreur :**
- `400` : Requête invalide (champs manquants ou invalides)
- `404` : Utilisateur non trouvé
- `500` : Erreur interne du serveur

---

#### 7. Récupérer un lien de paiement par code

**Endpoint :** `GET /api/internal/v1/payment-links/{uniqueCode}`

**Description :** Récupère les détails complets d'un lien de paiement à partir de son code unique. Endpoint authentifié pour le créateur du lien.

**Path Parameters :**
- `uniqueCode` (requis) : Code unique du lien (ex: "gn1mb")

**Réponse succès (200) :**
```json
{
  "id": 1,
  "uniqueCode": "gn1mb",
  "paymentUrl": "https://akuunda-pay.io/pay/gn1mb",
  "description": "Paiement facture électricité - Janvier 2024",
  "amount": 5000.0,
  "currency": "XOF",
  "isActive": true,
  "expiresAt": "2024-12-31T23:59:59",
  "totalPayments": 3,
  "totalAmountReceived": 15000.0,
  "creatorUsername": "002250759146858",
  "createdAt": "2024-01-15T10:30:00",
  "updatedAt": "2024-01-15T14:20:00"
}
```

**Réponses d'erreur :**
- `404` : Lien de paiement non trouvé
- `410` : Lien de paiement expiré
- `500` : Erreur interne du serveur

---

#### 8. Récupérer tous les liens d'un utilisateur

**Endpoint :** `GET /api/internal/v1/payment-links/user/{username}`

**Description :** Récupère la liste de tous les liens de paiement créés par un utilisateur. Endpoint authentifié.

**Path Parameters :**
- `username` (requis) : Username (numéro de téléphone) du créateur

**Réponse succès (200) :**
```json
[
  {
    "id": 1,
    "uniqueCode": "gn1mb",
    "paymentUrl": "https://akuunda-pay.io/pay/gn1mb",
    "description": "Paiement facture électricité",
    "amount": 5000.0,
    "currency": "XOF",
    "isActive": true,
    "totalPayments": 3,
    "totalAmountReceived": 15000.0,
    "createdAt": "2024-01-15T10:30:00"
  },
  {
    "id": 2,
    "uniqueCode": "abc12",
    "paymentUrl": "https://akuunda-pay.io/pay/abc12",
    "description": "Cagnotte anniversaire",
    "amount": null,
    "currency": "XOF",
    "isActive": true,
    "totalPayments": 5,
    "totalAmountReceived": 25000.0,
    "createdAt": "2024-01-10T08:15:00"
  }
]
```

**Réponses d'erreur :**
- `404` : Utilisateur non trouvé
- `500` : Erreur interne du serveur

---

#### 9. Désactiver un lien de paiement

**Endpoint :** `PUT /api/internal/v1/payment-links/{uniqueCode}/deactivate?username={username}`

**Description :** Désactive un lien de paiement. Seul le créateur peut désactiver son lien. Endpoint authentifié.

**Path Parameters :**
- `uniqueCode` (requis) : Code unique du lien

**Query Parameters :**
- `username` (requis) : Username du créateur (pour vérification)

**Réponse succès (200) :**
```json
{
  "success": true,
  "message": "Payment link deactivated successfully"
}
```

**Réponses d'erreur :**
- `403` : Non autorisé (vous n'êtes pas le créateur)
- `404` : Lien de paiement ou utilisateur non trouvé
- `500` : Erreur interne du serveur

---

#### 10. Effectuer un paiement via un lien (utilisateur authentifié)

**Endpoint :** `POST /api/internal/v1/payment-links/process-payment`

**Description :** Traite un paiement effectué via un lien de paiement par un utilisateur authentifié de l'application mobile. Pour les paiements web (non authentifiés), utiliser l'endpoint `/web/payment` ci-dessus.

**Body :**
```json
{
  "uniqueCode": "string (code unique du lien)",
  "amount": "number (montant à payer)",
  "payerUsername": "string (username du payeur)",
  "note": "string (optionnel, note du payeur)"
}
```

**Exemple :**
```json
{
  "uniqueCode": "gn1mb",
  "amount": 5000.0,
  "payerUsername": "002250777832982",
  "note": "Paiement facture électricité - Janvier 2024"
}
```

**Réponse succès (200) :**
```json
{
  "success": true,
  "message": "Payment processed successfully",
  "amount": 5000.0,
  "currency": "XOF"
}
```

**Réponses d'erreur :**
- `400` : Requête invalide (montant incorrect, solde insuffisant, lien inactif)
- `404` : Lien de paiement, payeur ou wallet non trouvé
- `410` : Lien de paiement expiré
- `500` : Erreur interne du serveur

---

## Flux de paiement

### Flux de paiement web (5 étapes) - Interface publique

Le flux de paiement web suit un processus en 5 étapes, inspiré de [Djamo Pay](https://pay.djamo.com/gn1mb) :

```
┌─────────────────────────────────────────────────────────────────┐
│                    FLUX DE PAIEMENT WEB                          │
└─────────────────────────────────────────────────────────────────┘

Étape 1 : Sélection du pays
   │
   ├─► GET /web/countries
   │   └─► Retourne la liste des pays disponibles
   │
   ▼
   ➡️ Les moyens de paiement s'affichent automatiquement
   │
Étape 2 : Choix du moyen de paiement
   │
   ├─► GET /web/payment-methods/{countryCode} (automatique)
   │   └─► Retourne tous les moyens de paiement disponibles
   │       (Mobile Money, Virement bancaire, Carte, etc.)
   │
   ▼
Étape 3 : Saisie des informations utilisateur (optionnel)
   │
   ├─► L'utilisateur peut renseigner :
   │   - Nom et prénom
   │   - Adresse e-mail
   │
   ▼
Étape 4 : Saisie du montant
   │
   ├─► L'utilisateur saisit le montant à payer
   │   (si non fixe dans le lien)
   │
   ▼
Étape 5 : Validation du paiement
   │
   ├─► 🟢 Cas 1 - Mobile Money (Afrique)
   │   │   - L'utilisateur saisit son numéro de téléphone
   │   │   - Clique sur "Payer"
   │   │
   │   └─► 🔵 Cas 2 - Carte/Virement (Europe/Diaspora)
   │       - L'utilisateur est redirigé vers le partenaire
   │         (Guardarian, TransFi, etc.)
   │
   ├─► POST /web/payment
   │   └─► Retourne redirectUrl (YellowCard ou Guardarian)
   │
   ▼
Redirection vers le partenaire
   │
   ├─► 🟢 Mobile Money : YellowCard
   │   └─► L'utilisateur complète le paiement mobile money
   │
   └─► 🔵 Carte/Virement : Guardarian
       └─► L'utilisateur saisit ses informations bancaires
   │
   ▼
Confirmation (via webhook)
   │
   └─► Le partenaire redirige vers l'URL de retour
       └─► Le statut de la transaction est mis à jour
```

### Diagramme de séquence complet (paiement web)

```
Payeur Web              Interface Web          Backend              YellowCard          Base de données
   │                          │                    │                    │                      │
   │──1. Accéder au lien──────>│                    │                    │                      │
   │                          │──2. GET /web/{code}─>│                    │                      │
   │                          │                    │──3. Récupérer lien─>│                      │
   │                          │<──4. Infos lien────│                    │                      │
   │<──5. Afficher infos──────│                    │                    │                      │
   │                          │                    │                    │                      │
   │──6. Étape 1: Pays───────>│                    │                    │                      │
   │                          │──7. GET /countries─>│                    │                      │
   │                          │<──8. Liste pays────│                    │                      │
   │<──9. Afficher pays───────│                    │                    │                      │
   │                          │                    │                    │                      │
   │                          │──10. GET /payment-methods/{code}─>│      │                      │
   │                          │                    │──11. Récupérer channels─>│              │
   │                          │<──12. Liste moyens paiement│             │                      │
   │<──13. Afficher moyens───│                    │                    │                      │
   │                          │                    │                    │                      │
   │──14. Étape 2: Moyen─────>│                    │                    │                      │
   │   de paiement            │                    │                    │                      │
   │                          │                    │                    │                      │
   │──15. Étape 3: Infos─────>│                    │                    │                      │
   │   utilisateur (optionnel)│                    │                    │                      │
   │                          │                    │                    │                      │
   │──16. Étape 4: Montant───>│                    │                    │                      │
   │                          │                    │                    │                      │
   │──17. Étape 5: Téléphone─>│                    │                    │                      │
   │   (Mobile Money)         │                    │                    │                      │
   │                          │──18. POST /payment─>│                    │                      │
   │                          │                    │──19. Créer collection─>│                │
   │                          │                    │                    │──20. Traiter paiement│
   │                          │                    │<──21. redirectUrl───│                │
   │                          │<──22. redirectUrl──│                    │                      │
   │<──23. Redirection───────│                    │                    │                      │
   │                          │                    │                    │                      │
   │──24. Compléter paiement───────────────────────────────>│          │                      │
   │                          │                    │                    │                      │
   │                          │                    │<──25. Webhook───────│                      │
   │                          │                    │──26. Mettre à jour──>│                      │
```

### Étapes détaillées du paiement web

1. **Accès au lien**
   - L'utilisateur accède à l'URL du lien (ex: `https://akuunda-pay.io/pay/gn1mb`)
   - L'interface web appelle `GET /web/{uniqueCode}` pour récupérer les informations

2. **Étape 1 - Sélection du pays**
   - L'interface web appelle `GET /web/countries`
   - Affiche la liste des pays disponibles
   - L'utilisateur sélectionne un pays
   - ➡️ **Les moyens de paiement s'affichent automatiquement** (appel automatique à `GET /web/payment-methods/{countryCode}`)

3. **Étape 2 - Choix du moyen de paiement**
   - L'interface web affiche automatiquement les moyens de paiement disponibles pour le pays sélectionné
   - Les moyens de paiement incluent :
     - **Mobile Money (momo)** : Wave, Orange Money, MTN, Moov, etc. (Afrique)
     - **Virement bancaire (bank)** : Pour paiements par virement
     - **Carte bancaire (card)** : Pour paiements par carte (Europe/Diaspora)
   - La devise est automatiquement déterminée par le pays (pas besoin d'une étape séparée)
   - L'utilisateur sélectionne son moyen de paiement

4. **Étape 3 - Saisie des informations utilisateur (optionnel)**
   - L'utilisateur peut renseigner :
     - Nom et prénom
     - Adresse e-mail
   - Ces informations sont facultatives et servent à faciliter le suivi de la transaction ou l'envoi de la confirmation par e-mail

5. **Étape 4 - Saisie du montant**
   - L'utilisateur saisit le montant à payer (si le lien n'a pas de montant fixe)
   - Le montant est confirmé, et le bouton "Continuer" devient actif

6. **Étape 5 - Validation du paiement**
   
   **🟢 Cas 1 — Paiement Mobile Money (Afrique)**
   - L'utilisateur saisit son numéro de téléphone associé au moyen de paiement choisi (Wave, Orange, MTN, Moov…)
   - L'utilisateur clique sur "Payer"
   - L'interface web appelle `POST /web/payment` avec toutes les informations
   - Le backend :
     - Vérifie que le lien est actif et non expiré
     - Vérifie le montant si le lien a un montant fixe
     - Vérifie la devise si le lien a une devise fixe
     - Vérifie que le téléphone est fourni (obligatoire pour mobile money)
     - Appelle YellowCard OnRamp pour créer la collection
     - Crée une transaction avec statut `PENDING`
     - Retourne le `redirectUrl` de YellowCard
   - L'interface web redirige l'utilisateur vers YellowCard
   - L'utilisateur complète le paiement mobile money sur YellowCard

   **🔵 Cas 2 — Paiement par carte ou virement (Europe / Diaspora)**
   - Si le moyen de paiement provient d'un partenaire international (ex: Guardarian, TransFi, etc.)
   - L'utilisateur est redirigé automatiquement vers la page sécurisée du partenaire
   - L'utilisateur saisit ses informations bancaires (carte ou compte)
   - ⚠️ **Note :** L'intégration avec Guardarian pour les paiements carte/virement est en cours de développement. Actuellement, seul le mobile money est supporté.

7. **Confirmation (via webhook)**
   - Le partenaire (YellowCard ou Guardarian) traite le paiement
   - Le partenaire envoie un webhook au backend (à implémenter)
   - Le backend met à jour la transaction avec le statut `COMPLETED`
   - Le wallet du créateur est crédité
   - Les statistiques du lien sont mises à jour

### Flux de paiement pour utilisateurs authentifiés (application mobile)

Pour les utilisateurs authentifiés de l'application mobile, le flux est simplifié :

1. **Vérification du lien**
   - Le lien existe
   - Le lien est actif (`isActive = true`)
   - Le lien n'est pas expiré (`expiresAt` est null ou dans le futur)

2. **Vérification du montant**
   - Si le lien a un montant fixe, vérifier que le montant fourni correspond
   - Si le lien a un montant libre, accepter n'importe quel montant positif

3. **Vérification du payeur**
   - Le payeur existe (utilisateur authentifié)
   - Le payeur a un wallet
   - Le wallet a un solde suffisant

4. **Transfert**
   - Débiter le wallet du payeur
   - Créditer le wallet du créateur
   - Créer une opération DEBIT pour le payeur
   - Créer une opération CREDIT pour le créateur

5. **Enregistrement**
   - Créer une transaction `PaymentLinkTransaction` avec statut COMPLETED
   - Mettre à jour les statistiques du lien (`totalPayments`, `totalAmountReceived`)

---

## Génération de codes uniques

### Algorithme

Le système génère des codes uniques courts (5 caractères) en utilisant :

1. **Caractères autorisés** : `a-z` et `0-9` (alphanumériques minuscules)
2. **Longueur** : 5 caractères (ex: "gn1mb")
3. **Génération** : Utilise `PinHashUtil.generateRandomString()` avec SecureRandom
4. **Unicité** : Vérifie dans la base de données avant d'accepter le code
5. **Retry** : Jusqu'à 10 tentatives si collision

### Exemple de génération

```java
// Tentative 1 : Génère "a3k9m"
// Vérifie dans DB : existe déjà
// Tentative 2 : Génère "gn1mb"
// Vérifie dans DB : n'existe pas
// Retourne "gn1mb"
```

### Format de l'URL

L'URL complète du lien est construite comme suit :
```
{baseUrl}{paymentPath}/{uniqueCode}
```

Exemple :
```
https://akuunda-pay.io/pay/gn1mb
```

Configuration dans `application.properties` :
```properties
payment.link.base-url=https://akuunda-pay.io
payment.link.path=/pay
```

---

## Sécurité et validations

### Validations côté serveur

1. **Création de lien**
   - Description obligatoire et non vide
   - Devise obligatoire et valide
   - Montant positif si fourni
   - Date d'expiration dans le futur si fournie

2. **Paiement**
   - Lien existe et est actif
   - Lien non expiré
   - Montant correspond si lien avec montant fixe
   - Payeur existe et a un wallet
   - Solde suffisant

3. **Désactivation**
   - Seul le créateur peut désactiver son lien
   - Vérification de l'identité via username

### Mesures de sécurité

- **Codes uniques** : Génération cryptographiquement sécurisée (SecureRandom)
- **Vérifications** : Toutes les opérations vérifient l'existence et la validité des entités
- **Transactions** : Utilisation de `@Transactional` pour garantir la cohérence
- **Logging** : Toutes les opérations sont loggées pour audit

---

## Exemples d'utilisation

### Exemple 1 : Facture avec montant fixe (paiement web)

**Création du lien :**
```bash
POST /api/internal/v1/payment-links/create?username=002250759146858
Content-Type: application/json

{
  "description": "Facture électricité - Janvier 2024",
  "amount": 5000.0,
  "currency": "XOF",
  "expiresAt": "2024-02-15T23:59:59"
}
```

**Réponse :**
```json
{
  "uniqueCode": "gn1mb",
  "paymentUrl": "https://akuunda-pay.io/pay/gn1mb",
  ...
}
```

**Flux de paiement web (5 étapes) :**

**Étape 1 - Récupérer les pays :**
```bash
GET /api/internal/v1/payment-links/web/countries
```

**Étape 2 - Récupérer les moyens de paiement pour la Côte d'Ivoire (automatique après sélection du pays) :**
```bash
GET /api/internal/v1/payment-links/web/payment-methods/CI
```

**Réponse :** Liste de tous les moyens de paiement disponibles (Mobile Money, Virement bancaire, etc.)

**Étape 3 - Informations utilisateur (optionnel) :**
L'utilisateur peut renseigner son nom et email (pas d'appel API nécessaire, données incluses dans l'étape 5)

**Étape 4 - Saisie du montant :**
L'utilisateur saisit le montant (pas d'appel API nécessaire, données incluses dans l'étape 5)

**Étape 5 - Initier le paiement (Mobile Money) :**
```bash
POST /api/internal/v1/payment-links/web/payment
Content-Type: application/json

{
  "uniqueCode": "gn1mb",
  "amount": 5000.0,
  "currency": "XOF",
  "phoneNumber": "+2250700123456",
  "countryCode": "CI",
  "paymentMethodId": "37b63794-284b-4a09-863b-9b74a3f621e1",
  "paymentMethodType": "momo",
  "payerName": "Jean Dupont",
  "payerEmail": "jean.dupont@example.com",
  "note": "Paiement facture électricité"
}
```

**Réponse :**
```json
{
  "success": true,
  "message": "Payment initiated successfully",
  "redirectUrl": "https://yellowcard.io/payment/...",
  "transactionId": "tx_1234567890",
  "amount": 5000.0,
  "currency": "XOF"
}
```

L'interface web redirige ensuite l'utilisateur vers le `redirectUrl` pour compléter le paiement sur YellowCard.

### Exemple 1bis : Paiement par utilisateur authentifié (application mobile)

**Paiement :**
```bash
POST /api/internal/v1/payment-links/process-payment
Content-Type: application/json

{
  "uniqueCode": "gn1mb",
  "amount": 5000.0,
  "payerUsername": "002250777832982",
  "note": "Paiement facture électricité"
}
```

### Exemple 2 : Cagnotte avec montant libre (paiement web)

**Création du lien :**
```bash
POST /api/internal/v1/payment-links/create?username=002250759146858
Content-Type: application/json

{
  "description": "Cagnotte anniversaire Jean"
}
```

**Note :** Aucun montant ni devise fixe - le payeur choisit lors du paiement.

**Paiements multiples via l'interface web :**

**Paiement 1 (Mobile Money - Côte d'Ivoire) :**
```bash
POST /api/internal/v1/payment-links/web/payment
{
  "uniqueCode": "abc12",
  "amount": 2000.0,
  "currency": "XOF",
  "phoneNumber": "+2250700123456",
  "countryCode": "CI",
  "paymentMethodId": "37b63794-284b-4a09-863b-9b74a3f621e1",
  "paymentMethodType": "momo"
}
```

**Paiement 2 (Mobile Money - Côte d'Ivoire, autre opérateur) :**
```bash
POST /api/internal/v1/payment-links/web/payment
{
  "uniqueCode": "abc12",
  "amount": 5000.0,
  "currency": "XOF",
  "phoneNumber": "+2250700123457",
  "countryCode": "CI",
  "paymentMethodId": "fa21a1b1-3db4-4412-b733-f71cb3a23b4a",
  "paymentMethodType": "momo"
}
```

**Paiement 3 (Virement bancaire - France) :**
```bash
POST /api/internal/v1/payment-links/web/payment
{
  "uniqueCode": "abc12",
  "amount": 3000.0,
  "currency": "EUR",
  "countryCode": "FR",
  "paymentMethodId": "...",
  "paymentMethodType": "bank",
  "payerName": "Marie Martin",
  "payerEmail": "marie.martin@example.com"
}
```
**Note :** Pour les paiements carte/virement, le téléphone n'est pas requis. L'intégration avec Guardarian est en cours de développement.

**Vérification des statistiques :**
```bash
GET /api/internal/v1/payment-links/abc12
```

**Réponse :**
```json
{
  "uniqueCode": "abc12",
  "totalPayments": 3,
  "totalAmountReceived": 10000.0,
  ...
}
```

---

## Configuration

### Propriétés dans `application.properties`

```properties
# URL de base pour les liens de paiement
payment.link.base-url=https://akuunda-pay.io

# Chemin pour les liens de paiement
payment.link.path=/pay
```

### Personnalisation

Pour changer la longueur du code unique, modifier dans `PaymentLinkServiceImpl` :
```java
private static final int UNIQUE_CODE_LENGTH = 5; // Changer ici
```

---

## FAQ (Foire aux questions)

### Q1 : Combien de personnes peuvent utiliser le même lien ?

**R :** Il n'y a pas de limite. Un même lien peut être utilisé par un nombre illimité de payeurs.

### Q2 : Que se passe-t-il si un lien expire ?

**R :** Les paiements ne peuvent plus être effectués via ce lien. Les paiements déjà effectués ne sont pas affectés.

### Q3 : Peut-on réactiver un lien désactivé ?

**R :** Actuellement, non. Il faudrait créer un nouveau lien. Une fonctionnalité de réactivation pourrait être ajoutée.

### Q4 : Les codes uniques peuvent-ils entrer en collision ?

**R :** Le système vérifie l'unicité avant d'accepter un code. En cas de collision, il génère un nouveau code (jusqu'à 10 tentatives).

### Q5 : Peut-on modifier un lien après sa création ?

**R :** Actuellement, non. Il faudrait désactiver l'ancien lien et créer un nouveau lien. Une fonctionnalité de modification pourrait être ajoutée.

### Q6 : Comment sont gérés les paiements échoués ?

**R :** Les paiements échoués sont enregistrés avec le statut "FAILED" dans `PaymentLinkTransaction`. Les statistiques du lien ne sont pas mises à jour pour les paiements échoués.

---

## Conclusion

Le système de liens de paiement en masse offre une solution flexible et sécurisée pour :
- ✅ Recevoir des paiements de plusieurs personnes
- ✅ Gérer des factures avec montants fixes
- ✅ Organiser des cagnottes et collectes de fonds
- ✅ Suivre les paiements en temps réel
- ✅ Contrôler l'activation et l'expiration des liens

Les points forts :
- **Simplicité** : Codes courts et faciles à partager
- **Flexibilité** : Montant fixe ou libre
- **Sécurité** : Validations complètes et transactions atomiques
- **Traçabilité** : Historique complet des paiements

---

**Document généré le :** [Date actuelle]  
**Version :** 1.0  
**Auteur :** Équipe de développement Akuunda

