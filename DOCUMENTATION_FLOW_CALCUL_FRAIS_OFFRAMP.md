# Flow - Calcul des Frais OffRamp (Retrait)

## 📋 Vue d'ensemble

Ce document décrit le flux complet du calcul des frais pour une opération **OffRamp (retrait)** avec :
- **YellowCard** (Afrique) : Retrait vers Mobile Money (Wave, Orange, MTN, Moov)
- **Guardarian** (Europe/International) : Retrait vers compte bancaire ou carte

**Objectif :** Permettre à l'utilisateur de connaître les frais qui lui seront prélevés avant d'effectuer un retrait.

---

## 🔄 Flow YellowCard (Afrique)

### Étape 1 : Requête utilisateur
```
POST /api/internal/v1/fees/calculate-offramp
{
  "amount": 2005.0,
  "currency": "XOF",
  "countryCode": "CI",
  "operator": "yellowcard"  // Optionnel, détecté automatiquement
}
```

### Étape 2 : Récupération du taux YellowCard "sell" (USD → XOF)
**API:** YellowCard `/business/rates`  
**Taux utilisé:** `sell` (taux de vente)  
**Output:** Taux RYC (ex: 566.94)

```
1 USD = 566.94 XOF (taux sell)
```

**Explication:** Pour l'OffRamp, on utilise le taux "sell" car l'utilisateur vend sa crypto pour recevoir de la monnaie fiat.

### Étape 3 : Conversion XOF → USD via le taux "sell"
**Formule:** `Montant XOF / Taux YC (sell)`

```
2005 XOF / 566.94 = 3.54 USD (arrondi pour affichage)
```

**Explication:** On convertit le montant XOF en USD pour effectuer les calculs intermédiaires.

**⚠️ IMPORTANT - Précision des calculs :**

**Principe fondamental :** Utiliser la **valeur brute** (sans arrondi) pour tous les calculs intermédiaires, et arrondir uniquement à la fin pour l'affichage.

- La conversion XOF → USD peut donner beaucoup de décimales (ex: 3.5366666...)
- **On ne doit PAS arrondir** cette valeur avant de faire les calculs suivants
- Si on arrondit trop tôt (ex: à 2 ou 4 décimales), on perd de la précision et on ne retrouve plus le montant initial après reconversion
- **Solution :** Garder la valeur brute (10 décimales pour la division) pour tous les calculs, arrondir uniquement à la fin (2 décimales) pour l'affichage

### Étape 4 : Conversion USD → XOF via le taux "sell"
**Formule:** `Montant USD × Taux YC (sell) = (Montant XOF / Taux YC) × Taux YC`

```
3.54 USD × 566.94 = 2005.00 XOF (arrondi pour affichage)
```

**🔍 Pourquoi cette double conversion ?**

Selon la procédure, on effectue :
1. **2005 XOF** → converti en **3.54 USD** via le taux "sell" (valeur brute conservée)
2. **3.54 USD** → reconverti en **2005 XOF** via le taux "sell" (valeur brute conservée)

Mathématiquement, `(Montant XOF / Taux YC) × Taux YC = Montant XOF`, donc on revient au montant initial.

**Pourquoi cette étape ?**
- Pour suivre exactement la procédure définie
- Pour avoir une traçabilité complète des conversions
- Pour permettre des ajustements futurs si nécessaire

**⚠️ IMPORTANT :** Les calculs utilisent les **valeurs brutes** (non arrondies) pour garantir la précision maximale. L'arrondi à 2 décimales est fait uniquement à la fin pour l'affichage.

### Étape 5 : Calcul des frais Akuunda (3.5%)
**Formule:** `(Montant XOF/Taux YC × Taux YC) × 0.035`

```
2005.00 XOF × 0.035 = 70.175 XOF → arrondi à 70.18 XOF
```

**Explication:** Les frais sont calculés sur le montant après conversion (qui est égal au montant initial dans ce cas).

**⚠️ IMPORTANT :** Le calcul utilise la **valeur brute** (non arrondie) du montant après conversion. L'arrondi à 2 décimales est fait uniquement à la fin pour l'affichage.

### Étape 6 : Calcul du montant reçu
**Formule:** `(Montant XOF/Taux YC × Taux YC) - Frais estimés`

```
2005 XOF - 70.18 XOF = 1934.82 XOF
```

### Étape 7 : Réponse API
```json
{
  "amountSent": 2005.0,        // Ce que l'utilisateur veut retirer
  "currency": "XOF",
  "estimatedFees": 70.18,      // Frais calculés (3.5% du montant après conversion)
  "amountReceived": 1934.82,    // Montant reçu = 2005 - 70.18
  "exchangeRate": 566.94,
  "feePercentage": 3.5,
  "operator": "yellowcard",
  "breakdown": {
    "yellowCardRate": 566.94,
    "akuundaFeeRate": 0.035,
    "amountInUsd": 3.54,
    "amountAfterYellowCardRate": 2005.0
  }
}
```

---

## 🔄 Flow Guardarian (Europe/International)

### Étape 1 : Requête utilisateur
```
POST /api/internal/v1/fees/calculate-offramp
{
  "amount": 30.0,
  "currency": "USDC",
  "countryCode": "FR",
  "operator": "guardarian"  // Optionnel, détecté automatiquement
}
```

### Étape 2 : Appel Guardarian /estimate (OffRamp)
**API:** Guardarian `/estimate` (GET)  
**Endpoint:** `GET /v1/estimate?from_currency=USDC&to_currency=EUR&from_amount=30.0`

**Input:**
- `from_currency`: USDC (crypto que l'utilisateur veut retirer)
- `to_currency`: EUR (devise fiat de destination)
- `from_amount`: 30.0

**Note:** Pour l'OffRamp, on inverse les devises par rapport à l'OnRamp (USDC → EUR au lieu de EUR → USDC).

**Output:**
```json
{
  "to_currency": "EUR",
  "from_currency": "USDC",
  "to_network": "EUR",
  "value": "25.1296726",
  "service_fees": [
    {
      "amount": "0.15",
      "currency": "USDC",
      "name": "Service fee",
      "percentage": "0.5%"
    }
  ],
  "estimated_exchange_rate": "0.83765576",
  "converted_amount": {
    "amount": "29.85",
    "currency": "USDC"
  },
  "network_fee": {
    "currency": "USDC",
    "amount": "0.00423255"
  }
}
```

**Parsing de la réponse:**
- `converted_amount.amount` → `estimatedAmount` (29.85)
- `estimated_exchange_rate` → `rate` (0.83765576)
- `service_fees[0].amount` → utilisé pour validation (0.15)

### Étape 3 : Extraction des données Guardarian
**Données extraites de la réponse:**
- `estimatedAmount` = `converted_amount.amount` = **29.85**
- `rate` = `estimated_exchange_rate` = **0.83765576**

### Étape 4 : Calcul des frais
**Formule:** `Montant_saisi - Montant_estimé_Guardarian`

```
30.0 USDC - 29.85 = 0.15 USDC
```

**Note:** Les frais sont calculés automatiquement par Guardarian et inclus dans `converted_amount`. Le calcul local vérifie la cohérence.

### Étape 5 : Calcul du montant reçu
**Formule:** `Montant_estimé_Guardarian` (déjà calculé par Guardarian)

```
29.85 (en EUR équivalent)
```

### Étape 6 : Calcul du pourcentage de frais
**Formule:** `(Frais / Montant_saisi) × 100`

```
(0.15 / 30.0) × 100 = 0.5%
```

### Étape 7 : Réponse API
```json
{
  "amountSent": 30.0,          // Ce que l'utilisateur veut retirer (USDC)
  "currency": "USDC",
  "estimatedFees": 0.15,        // Frais Guardarian (30.0 - 29.85)
  "amountReceived": 29.85,      // Montant reçu (converted_amount.amount)
  "exchangeRate": 0.83765576,    // Taux de change (estimated_exchange_rate)
  "feePercentage": 0.5,         // Pourcentage de frais calculé
  "operator": "guardarian",
  "breakdown": {
    "guardarianExchangeRate": 0.83765576,  // Taux de change Guardarian
    "guardarianConvertedAmount": 29.85,     // Montant converti (reçu)
    "guardarianServiceFee": 0.15            // Frais de service
  }
}
```

**Note:** Si l'API Guardarian n'est pas disponible, le système utilise un fallback avec un taux fixe de 0.5%.

---

## 📊 Diagramme de flux (YellowCard)

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Utilisateur saisit: 2005 XOF (montant à retirer)       │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. Récupération taux YellowCard "sell" (USD → XOF)        │
│    Taux RYC (sell) = 566.94                                │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. Conversion XOF → USD (via taux "sell")                  │
│    2005 XOF / 566.94 = 3.54 USD                            │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. Conversion USD → XOF (via taux "sell")                  │
│    3.54 USD × 566.94 = 2005 XOF                            │
│    (Montant XOF/Taux YC × Taux YC)                         │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│ 5. Calcul frais Akuunda (3.5%)                              │
│    2005 XOF × 0.035 = 70.175 → arrondi à 70.18 XOF          │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│ 6. Calcul montant reçu                                      │
│    2005 XOF - 70.18 XOF = 1934.82 XOF                       │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│ 7. Réponse API                                               │
│    amountSent: 2005 XOF                                     │
│    estimatedFees: 70.18 XOF                                 │
│    amountReceived: 1934.82 XOF                               │
└─────────────────────────────────────────────────────────────┘
```

---

## 📊 Diagramme de flux (Guardarian)

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Utilisateur saisit: 30 USDC (montant à retirer)          │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. Appel Guardarian /estimate (USDC → EUR)                 │
│    from_currency: USDC, to_currency: EUR                   │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. Extraction données Guardarian                           │
│    converted_amount.amount: 29.85                          │
│    estimated_exchange_rate: 0.83765576                     │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. Calcul frais                                             │
│    30.0 - 29.85 = 0.15 USDC (0.5%)                         │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│ 5. Réponse API                                               │
│    amountSent: 30 USDC                                      │
│    estimatedFees: 0.15 USDC                                 │
│    amountReceived: 29.85 (EUR équivalent)                   │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔑 Points clés

### YellowCard OffRamp
1. ✅ L'utilisateur **saisit** un montant en monnaie locale qu'il veut retirer (ex: 2005 XOF)
2. ✅ Le backend **convertit** XOF → USD → XOF via le taux YellowCard "sell"
3. ✅ Le taux **"sell"** est utilisé (au lieu de "buy" pour l'OnRamp) car l'utilisateur vend sa crypto
4. ✅ Les frais sont **calculés** sur le montant après conversion (2005 XOF × 3.5% = 70.18 XOF)
5. ✅ Le montant **reçu** = (Montant XOF/Taux YC × Taux YC) - Frais estimés (2005 - 70.18 = 1934.82 XOF)
6. ✅ **Formule complète:** `Montant net = (Montant/Taux × Taux) - (Montant/Taux × Taux × 0.035)`
7. ✅ Les frais sont de **3.5%** (au lieu de 2% pour l'OnRamp)

### Guardarian OffRamp
1. ✅ L'utilisateur **saisit** un montant en crypto qu'il veut retirer (ex: 30 USDC)
2. ✅ Le backend **appelle** l'API Guardarian `/estimate` avec USDC → EUR (inverse de OnRamp)
3. ✅ Les frais sont **déjà calculés** par Guardarian (0.15 USDC, soit 0.5%)
4. ✅ Le montant **reçu** = montant estimé Guardarian (29.85 en EUR équivalent)
5. ✅ **Formule:** `Montant reçu = converted_amount.amount` (déjà calculé par Guardarian)

---

## 🧮 Formules mathématiques

### YellowCard OffRamp
```
Frais estimés (XOF) = (Montant XOF/Taux YC × Taux YC) × 0.035
Montant net (XOF) = (Montant XOF/Taux YC × Taux YC) - Frais estimés
```

**Définitions:**
- `Montant XOF` = Montant saisi par l'utilisateur qu'il veut retirer (ex: 2005 XOF)
- `Taux YC` = Taux YellowCard "sell" pour convertir USD → XOF (ex: 566.94)
- `(Montant XOF / Taux YC)` = Montant en USD après conversion (ex: 2005 / 566.94 = 3.54 USD)
- `(Montant XOF / Taux YC × Taux YC)` = Montant après double conversion (ex: 3.54 × 566.94 = 2005 XOF)

**Exemple de calcul complet:**
```
Montant saisi: 2005 XOF
↓
Conversion XOF → USD: 2005 / 566.94 = 3.54 USD
↓
Conversion USD → XOF: 3.54 × 566.94 = 2005 XOF (montant après conversion)
↓
Frais Akuunda: 2005 × 0.035 = 70.175 ≈ 70.18 XOF
↓
Montant reçu: 2005 - 70.18 = 1934.82 XOF
```

**Simplification mathématique:**
```
Frais estimés (XOF) = Montant XOF × 0.035
Montant net (XOF) = Montant XOF - Frais estimés
```

**Note:** Mathématiquement, `(Montant XOF / Taux YC × Taux YC) = Montant XOF`, donc la formule peut être simplifiée. Cependant, l'implémentation suit exactement la procédure définie pour garantir la traçabilité et permettre des ajustements futurs si nécessaire.

---

## 📝 Exemple complet (YellowCard)

### Input
```json
{
  "amount": 2005.0,
  "currency": "XOF",
  "countryCode": "CI",
  "operator": "yellowcard"
}
```

### Calculs intermédiaires

**Étape 1:** Récupération taux YellowCard "sell"
```
Taux YellowCard (sell): 566.94 (1 USD = 566.94 XOF)
```

**Étape 2:** Conversion XOF → USD (via taux "sell")
```
2005 XOF / 566.94 = 3.54 USD (arrondi pour affichage)
```
**⚠️ IMPORTANT :** Le calcul utilise la **valeur brute** (10 décimales) pour garantir la précision.

**Étape 3:** Conversion USD → XOF (via taux "sell")
```
3.54 USD × 566.94 = 2005.00 XOF (arrondi pour affichage)
```
**🔍 C'est ici que le montant après conversion apparaît !**
- C'est le montant initial (2005 XOF) après double conversion (XOF → USD → XOF)
- Mathématiquement, on revient au montant initial
- Ce montant est utilisé pour calculer les frais selon la procédure
- **⚠️ IMPORTANT :** Le calcul utilise la **valeur brute** (non arrondie) de la conversion USD

**Étape 4:** Calcul des frais (3.5% du montant après conversion)
```
2005.00 XOF × 0.035 = 70.175 XOF → arrondi à 70.18 XOF
```
**⚠️ IMPORTANT :** Le calcul utilise la **valeur brute** (non arrondie) du montant après conversion. L'arrondi à 2 décimales est fait uniquement à la fin.

**Étape 5:** Calcul du montant reçu (montant après conversion - frais)
```
2005 XOF - 70.18 XOF = 1934.82 XOF
```
**⚠️ IMPORTANT :** Les calculs utilisent les **valeurs brutes** (non arrondies) pour garantir la précision maximale. L'arrondi à 2 décimales est fait uniquement à la fin pour l'affichage.

**Résumé:**
- **2005 XOF** = Montant initial (ce que l'utilisateur veut retirer)
- **2005 XOF** = Montant après conversion (XOF → USD → XOF)
- **70.18 XOF** = Frais calculés (3.5% de 2005 XOF)
- **1934.82 XOF** = Montant reçu (2005 - 70.18)

### Output
```json
{
  "amountSent": 2005.0,
  "currency": "XOF",
  "estimatedFees": 70.18,
  "amountReceived": 1934.82,
  "exchangeRate": 566.94,
  "feePercentage": 3.5,
  "operator": "yellowcard",
  "breakdown": {
    "yellowCardRate": 566.94,
    "akuundaFeeRate": 0.035,
    "amountInUsd": 3.54,
    "amountAfterYellowCardRate": 2005.0
  }
}
```

---

## 🔍 Détection automatique de l'opérateur

Si `operator` n'est pas spécifié dans la requête:

1. **Pays africains** → YellowCard
   - Liste: CI, SN, ML, BF, TG, BJ, NE, CM, GA, CG, CD, GN, SL, LR, GH, NG, KE, UG, RW, TZ, ZM, ZW, MW, MZ, AO, NA, BW, ZA, LS, SZ, MG, KM, SC, MU, ET, SO, ER, DJ, SD, SS, EG, LY, TN, DZ, MA, MR, TD, CF, GQ, ST, CV, GW

2. **Autres pays** → Guardarian
   - Europe, Amérique, Asie, etc.

---

## ⚠️ Précision financière

- **BigDecimal** utilisé pour tous les calculs financiers
- **Principe fondamental :** Utiliser la **valeur brute** (sans arrondi) pour tous les calculs intermédiaires
- **Précision intermédiaire:** Valeur brute complète (10 décimales pour les divisions, pas d'arrondi prématuré)
- **Précision finale:** 2 décimales (arrondi uniquement à la fin pour l'affichage)
- **Mode d'arrondi:** `HALF_UP` (arrondi à la valeur supérieure si ≥ 0.5)

**Pourquoi cette approche ?**
- Les conversions de devises peuvent donner beaucoup de décimales (ex: 3.5366666...)
- Si on arrondit trop tôt (ex: à 2 ou 4 décimales), on perd de la précision et on ne retrouve plus le montant initial après reconversion
- En gardant la valeur brute pour les calculs, on garantit la précision maximale
- L'arrondi à 2 décimales est fait uniquement à la fin pour l'affichage à l'utilisateur

---

## 🔄 Envoi à YellowCard API

### ⚠️ IMPORTANT : Valeur brute en USD

Lors de la création d'un paiement OffRamp via YellowCard, le système envoie la **valeur brute en USD** (sans arrondi) à l'API YellowCard dans le champ `cryptoAmount` du `settlementInfo`.

**Exemple concret :**
- Utilisateur veut retirer : **2000 XOF**
- Conversion XOF → USD (via taux "sell") : **3.532807416069328 USD** (valeur brute)
- **Cette valeur brute complète est envoyée à YellowCard** dans le champ `settlementInfo.cryptoAmount`

**Pourquoi envoyer la valeur brute ?**
- Si on arrondit à 2 décimales (ex: 3.53 USD) avant d'envoyer à YellowCard, on perd de la précision
- YellowCard effectue ensuite sa propre conversion USD → XOF avec ses taux du jour
- Si la valeur envoyée est arrondie, le montant final en XOF ne correspondra plus exactement au montant initial
- En envoyant la valeur brute (ex: 3.532807416069328), YellowCard peut effectuer ses calculs avec la précision maximale

**Implémentation technique :**
- La méthode `getConvertedAmountBigDecimal()` retourne un `BigDecimal` avec toute la précision
- `ObjectMapper` sérialise automatiquement le `BigDecimal` dans le JSON avec toute sa précision
- Le payload JSON envoyé à YellowCard contient : `"cryptoAmount": 3.532807416069328` (valeur brute)

**Exemple de payload envoyé à YellowCard :**
```json
{
  "sender": { ... },
  "destination": { ... },
  "currency": "XOF",
  "country": "CI",
  "settlementInfo": {
    "cryptoCurrency": "USDC",
    "cryptoNetwork": "POLYGON",
    "cryptoAmount": 3.532807416069328  // ← Valeur brute en USD (sans arrondi)
  },
  ...
}
```

---

## 📍 Endpoint API

```
POST /api/internal/v1/fees/calculate-offramp
Authorization: Bearer {token}
Content-Type: application/json

{
  "amount": 2005.0,
  "currency": "XOF",
  "countryCode": "CI",
  "operator": "yellowcard"  // Optionnel
}
```

---

## 🔄 Différences OnRamp vs OffRamp

| Aspect | OnRamp (Dépôt) | OffRamp (Retrait) |
|--------|----------------|-------------------|
| **Taux YellowCard** | "buy" (taux d'achat) | "sell" (taux de vente) |
| **Frais Akuunda** | 2% | 3.5% |
| **Conversion** | XOF → USD (Currency Freaks) → XOF (YellowCard) | XOF → USD (YellowCard) → XOF (YellowCard) |
| **Calcul frais** | Sur montant après conversion | Sur montant après conversion |
| **Formule frais** | `(MXOF × RUSD × RYC) × 0.02` | `(Montant XOF/Taux YC × Taux YC) × 0.035` |
| **Formule montant reçu** | `(MXOF × RUSD × RYC) - Frais` | `(Montant XOF/Taux YC × Taux YC) - Frais` |
| **Exemple (2005 XOF)** | Frais: 41 XOF, Reçu: 1966 XOF | Frais: 70.18 XOF, Reçu: 1934.82 XOF |

---

## ✅ Validation

- ✅ Montant doit être positif
- ✅ Devise doit être valide (ISO code)
- ✅ Code pays doit être valide (ISO code)
- ✅ Opérateur doit être "yellowcard" (seul opérateur supporté actuellement)
- ⚠️ Si opérateur non supporté → Erreur 400 Bad Request

---

## 🚧 Limitations actuelles

- ✅ **Guardarian supporté** pour l'OffRamp (Europe/International)
- ✅ **YellowCard supporté** pour l'OffRamp (Afrique)
- ⚠️ Support uniquement pour les **pays africains** avec YellowCard
- ⚠️ Support uniquement pour les **pays européens/internationaux** avec Guardarian

---

---

## 📚 Résumé pour explication

### YellowCard OffRamp (Afrique)
**En une phrase:** L'utilisateur saisit un montant qu'il veut retirer, le système le convertit via le taux YellowCard "sell", calcule 3.5% de frais sur le montant après conversion, et le montant reçu = montant après conversion - frais.

**Exemple concret:**
- Utilisateur veut retirer: **2005 XOF**
- Après conversion: **2005 XOF** (XOF → USD → XOF via taux "sell")
- Frais: **70.18 XOF** (3.5% de 2005)
- Montant reçu: **1934.82 XOF** (2005 - 70.18)

**Note:** La double conversion (XOF → USD → XOF) revient mathématiquement au montant initial, mais elle est effectuée pour suivre exactement la procédure définie.

### Guardarian OffRamp (Europe/International)
**En une phrase:** L'utilisateur saisit un montant en crypto qu'il veut retirer, le système appelle l'API Guardarian qui calcule automatiquement les frais, et le montant reçu = montant estimé par Guardarian.

**Exemple concret:**
- Utilisateur veut retirer: **30 USDC**
- Guardarian calcule: **29.85** (montant reçu en EUR équivalent)
- Frais: **0.15 USDC** (30 - 29.85)
- Pourcentage: **0.5%**

---

**Dernière mise à jour:** 2025-11-12  
**Version:** 1.4  
**Statut Guardarian OffRamp:** ✅ Opérationnel  
**Statut YellowCard OffRamp:** ✅ Opérationnel  
**Précision des calculs:** ✅ Valeurs brutes utilisées (pas d'arrondi prématuré)  
**Envoi à YellowCard:** ✅ Valeur brute en USD envoyée (ex: 3.532807416069328)

