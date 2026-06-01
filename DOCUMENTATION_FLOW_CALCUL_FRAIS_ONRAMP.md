# Flow - Calcul des Frais OnRamp (Dépôt)

## 📋 Vue d'ensemble

Ce document décrit le flux complet du calcul des frais pour une opération OnRamp (dépôt) avec YellowCard (Afrique) et Guardarian (Europe/International).

---

## 🔄 Flow YellowCard (Afrique)

### Étape 1 : Requête utilisateur
```
POST /api/internal/v1/fees/calculate
{
  "amount": 2005.0,
  "currency": "XOF",
  "countryCode": "CI",
  "operator": "yellowcard"  // Optionnel, détecté automatiquement
}
```

### Étape 2 : Récupération du taux YellowCard buy (USD → XOF)
**API:** YellowCard `/business/rates` ou `/api/internal/v1/yellow-card/rates/{channelId}?currencyCode=XAF`  
**Output:** Taux RYC (ex: 587.26)

```
1 USD = 587.26 XOF
```

**Explication:** Ce taux permet de convertir directement le montant XOF en USD et de le reconvertir en XOF selon les taux YellowCard du jour.

### Étape 3 : Conversion XOF → USD directement via le taux YellowCard buy
**Formule:** `USD_brut = M_XOF / R_YC`

**Input:** 5000 XOF  
**Taux YellowCard buy:** 587.26  
**Output:** 
- Montant en USD (ex: 8.5106 USD)

```
5000 XOF / 587.26 = 8.5106 USD (valeur brute, toutes décimales préservées)
```

**⚠️ IMPORTANT - Précision des calculs :**

**Principe fondamental :** Utiliser la **valeur brute** (sans arrondi) pour tous les calculs intermédiaires, et arrondir uniquement à la fin pour l'affichage.

- La conversion XOF → USD peut donner beaucoup de décimales (ex: 8.5106...)
- **On ne doit PAS arrondir** cette valeur avant de faire les calculs suivants
- Si on arrondit trop tôt (ex: à 2 décimales), on perd de la précision et on ne retrouve plus le montant initial après reconversion
- **Solution :** Garder la valeur brute pour tous les calculs, arrondir uniquement à la fin (2 décimales) pour l'affichage

### Étape 4 : Reconversion USD → XOF via le taux YellowCard buy
**Formule:** `XOF_brut = USD_brut × R_YC`

```
8.5106 USD × 587.26 = 5000.00 XOF (on retrouve exactement le montant initial)
```

**🔍 Pourquoi on retrouve le montant initial ?**

C'est le résultat de la **conversion directe via YellowCard** :
1. **5000 XOF** (montant initial) → converti en **8.5106 USD** via le taux YellowCard buy
2. **8.5106 USD** → reconverti en **5000 XOF** via le même taux YellowCard buy
3. On retrouve **exactement** le montant initial car on utilise le même taux dans les deux sens

**Note importante:** 
- L'utilisateur **paie** le montant initial (5000 XOF)
- Les frais sont **calculés** sur le montant initial (5000 XOF)
- Le montant **reçu** est calculé à partir du montant initial moins les frais (5000 - 100 = 4900 XOF)
- L'arrondi à 2 décimales est fait **uniquement à la fin** pour l'affichage

### Étape 5 : Calcul des frais Akuunda (2%)
**Formule:** `Frais = M_XOF × 0.02`

```
5000 XOF × 0.02 = 100 XOF
```

**⚠️ IMPORTANT :** Le calcul utilise le **montant initial** (non arrondi). L'arrondi à 2 décimales est fait uniquement à la fin pour l'affichage.

### Étape 6 : Calcul du montant reçu
**Formule:** `Montant net = M_XOF - Frais estimés`

```
5000 XOF - 100 XOF = 4900 XOF
```

**Note:** Le montant reçu est calculé à partir du montant initial moins les frais.

### Étape 7 : Réponse API
```json
{
  "amountSent": 5000.0,        // Ce que l'utilisateur paie (montant initial)
  "currency": "XOF",
  "estimatedFees": 100.0,      // Frais calculés (2% du montant initial = 5000 × 0.02)
  "amountReceived": 4900.0,    // Montant reçu = M_XOF - Frais = 5000 - 100
  "exchangeRate": 587.26,
  "feePercentage": 2.0,
  "operator": "yellowcard",
  "breakdown": {
    "yellowCardRate": 587.26,
    "akuundaFeeRate": 0.02,
    "amountInUsd": 8.5106,
    "amountAfterYellowCardRate": 5000.0
  }
}
```

---

## 🔄 Flow Guardarian (Europe/International)

### Étape 1 : Requête utilisateur
```
POST /api/internal/v1/fees/calculate
{
  "amount": 30.0,
  "currency": "EUR",
  "countryCode": "FR",
  "operator": "guardarian"  // Optionnel, détecté automatiquement
}
```

### Étape 2 : Appel Guardarian /estimate
**API:** Guardarian `/estimate` (GET)  
**Endpoint:** `GET /v1/estimate?from_currency=EUR&to_currency=USDC&from_amount=30.0`

**Input:**
- `from_currency`: EUR
- `to_currency`: USDC
- `from_amount`: 30.0

**Note:** L'API Guardarian utilise `from_amount` (pas `amount`) dans les paramètres GET.

**Output:**
```json
{
  "to_currency": "USDC",
  "from_currency": "EUR",
  "to_network": "ARBITRUM",
  "value": "33.5936302",
  "service_fees": [
    {
      "amount": "0.15",
      "currency": "EUR",
      "name": "Service fee",
      "percentage": "0.5%"
    }
  ],
  "estimated_exchange_rate": "1.11978768",
  "converted_amount": {
    "amount": "29.85",
    "currency": "EUR"
  },
  "network_fee": {
    "currency": "USDC",
    "amount": "0.12247536"
  }
}
```

**Parsing de la réponse:**
- `converted_amount.amount` → `estimatedAmount` (29.85 EUR)
- `estimated_exchange_rate` → `rate` (1.11978768)
- `service_fees[0].amount` → utilisé pour validation (0.15 EUR)

### Étape 3 : Extraction des données Guardarian
**Données extraites de la réponse:**
- `estimatedAmount` = `converted_amount.amount` = **29.85 EUR**
- `rate` = `estimated_exchange_rate` = **1.11978768**

### Étape 4 : Calcul des frais
**Formule:** `Montant_saisi - Montant_estimé_Guardarian`

```
30.0 EUR - 29.85 EUR = 0.15 EUR
```

**Note:** Les frais sont calculés automatiquement par Guardarian et inclus dans `converted_amount`. Le calcul local vérifie la cohérence.

### Étape 5 : Calcul du montant reçu
**Formule:** `Montant_estimé_Guardarian` (déjà calculé par Guardarian)

```
29.85 EUR
```

### Étape 6 : Calcul du pourcentage de frais
**Formule:** `(Frais / Montant_saisi) × 100`

```
(0.15 / 30.0) × 100 = 0.5%
```

### Étape 7 : Réponse API
```json
{
  "amountSent": 30.0,          // Ce que l'utilisateur paie
  "currency": "EUR",
  "estimatedFees": 0.15,        // Frais Guardarian (30.0 - 29.85)
  "amountReceived": 29.85,      // Montant reçu (converted_amount.amount)
  "exchangeRate": 1.11978768,   // Taux de change (estimated_exchange_rate)
  "feePercentage": 0.5,         // Pourcentage de frais calculé
  "operator": "guardarian",
  "breakdown": {
    "guardarianExchangeRate": 1.11978768,  // Taux de change Guardarian
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
│ 1. Utilisateur saisit: 5000 XOF                            │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. Récupération taux YellowCard buy (USD → XOF)            │
│    Taux RYC = 587.26                                       │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│ 3. Conversion XOF → USD (directement via YellowCard)        │
│    5000 XOF / 587.26 = 8.5106 USD (valeur brute)           │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│ 4. Reconversion USD → XOF (via YellowCard)                  │
│    8.5106 USD × 587.26 = 5000 XOF (on retrouve l'initial)  │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│ 5. Calcul frais Akuunda (2%)                                │
│    5000 XOF × 0.02 = 100 XOF                                │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│ 6. Calcul montant reçu                                      │
│    5000 XOF - 100 XOF = 4900 XOF                             │
│    (Montant initial - Frais)                                 │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│ 7. Réponse API                                               │
│    amountSent: 5000 XOF (montant initial)                   │
│    estimatedFees: 100 XOF (frais calculés)                  │
│    amountReceived: 4900 XOF (montant initial - frais)      │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔑 Points clés

### YellowCard OnRamp
1. ✅ L'utilisateur **saisit** un montant en monnaie locale (ex: 5000 XOF)
2. ✅ Le backend **convertit** XOF → USD directement via le taux YellowCard buy: USD = XOF / Taux_YellowCard
3. ✅ Le montant USD est **reconverti** XOF via le même taux YellowCard buy: XOF = USD × Taux_YellowCard (on retrouve le montant initial)
4. ✅ Les frais sont **calculés** sur le montant initial (5000 XOF × 2% = 100 XOF)
5. ✅ Le montant **reçu** = Montant initial - Frais estimés (5000 - 100 = 4900 XOF)
6. ✅ **Formule complète:** `Montant net = M_XOF - (M_XOF × 0.02)`

### Guardarian OnRamp
1. ✅ L'utilisateur **saisit** un montant (ex: 30 EUR)
2. ✅ Le backend **appelle** l'API Guardarian `/estimate` pour obtenir les frais
3. ✅ Les frais sont **déjà calculés** par Guardarian (0.15 EUR, soit 0.5%)
4. ✅ Le montant **reçu** = montant estimé Guardarian (29.85 EUR)
5. ✅ **Formule:** `Montant reçu = converted_amount.amount` (déjà calculé par Guardarian)

---

## 🧮 Formules mathématiques

### YellowCard
```
Frais estimés (XOF) = (MXOF × RUSD × RYC) × 0.02
Montant net (XOF) = (MXOF × RUSD × RYC) - Frais estimés
```

**Définitions:**
- `MXOF` = Montant saisi par l'utilisateur en monnaie locale (ex: 2005 XOF)
- `RUSD` = Taux Currency Freaks pour convertir XOF → USD (ex: 0.0017639)
- `RYC` = Taux YellowCard pour convertir USD → XOF (ex: 566.94)
- `(MXOF × RUSD × RYC)` = Montant après double conversion (ex: 2007 XOF)

**Exemple de calcul complet:**
```
Montant saisi: 2005 XOF
↓
Conversion XOF → USD: 2005 × 0.0017639 = 3.54 USD
↓
Conversion USD → XOF: 3.54 × 566.94 = 2007 XOF (montant après conversion)
↓
Frais Akuunda: 2007 × 0.02 = 40.14 ≈ 41 XOF
↓
Montant reçu: 2007 - 41 = 1966 XOF
```

### Guardarian
```
Frais estimés = Montant saisi - converted_amount.amount
Montant reçu = converted_amount.amount
Taux de change = estimated_exchange_rate
Pourcentage de frais = (Frais estimés / Montant saisi) × 100
```

**Définitions:**
- `Montant saisi` = Montant que l'utilisateur veut déposer (ex: 30 EUR)
- `converted_amount.amount` = Montant reçu après conversion (ex: 29.85 EUR)
- `estimated_exchange_rate` = Taux de change utilisé par Guardarian (ex: 1.11978768)
- `service_fees[0].amount` = Frais de service Guardarian (ex: 0.15 EUR)

**Note:** Guardarian calcule automatiquement les frais et le montant converti. Notre API extrait ces valeurs depuis la réponse JSON de l'endpoint `/estimate`.

---

## 📝 Exemple complet (YellowCard)

### Input
```json
{
  "amount": 2005.0,
  "currency": "XOF",
  "countryCode": "CI"
}
```

### Calculs intermédiaires

**Étape 1:** Conversion XOF → USD (Currency Freaks)
```
2005 XOF × 0.0017639 (taux Currency Freaks) = 3.54 USD (arrondi pour affichage)
```
**⚠️ IMPORTANT :** Le calcul utilise la **valeur brute** (non arrondie) pour garantir la précision.

**Étape 2:** Récupération taux YellowCard
```
Taux YellowCard: 566.94 (1 USD = 566.94 XOF)
```

**Étape 3:** Reconversion USD → XOF (YellowCard)
```
3.54 USD × 566.94 = 2007.00 XOF (arrondi pour affichage)
```
**🔍 C'est ici que le 2007 XOF apparaît !**
- C'est le montant initial (2005 XOF) après double conversion (XOF → USD → XOF)
- Ce montant reflète la "valeur équivalente" selon les taux YellowCard du jour
- **⚠️ IMPORTANT :** Le calcul utilise la **valeur brute** (non arrondie) de la conversion USD

**Étape 4:** Calcul des frais (2% du montant après conversion)
```
2007.00 XOF × 0.02 = 40.14 XOF → arrondi à 41 XOF
```
**⚠️ IMPORTANT :** Le calcul utilise la **valeur brute** (non arrondie) du montant après conversion. L'arrondi à 2 décimales est fait uniquement à la fin.

**Étape 5:** Calcul du montant reçu (montant après conversion - frais)
```
2007 XOF - 41 XOF = 1966 XOF
```
**⚠️ IMPORTANT :** Les calculs utilisent les **valeurs brutes** (non arrondies) pour garantir la précision maximale. L'arrondi à 2 décimales est fait uniquement à la fin pour l'affichage.

**Résumé:**
- **2005 XOF** = Montant initial (ce que l'utilisateur paie)
- **2007 XOF** = Montant après conversion YellowCard (MXOF × RUSD × RYC)
- **41 XOF** = Frais calculés (2% de 2007 XOF)
- **1966 XOF** = Montant reçu (2007 - 41) selon la formule: (MXOF × RUSD × RYC) - Frais estimés

### Output
```json
{
  "amountSent": 2005.0,
  "currency": "XOF",
  "estimatedFees": 41.0,
  "amountReceived": 1966.0,
  "exchangeRate": 566.94,
  "feePercentage": 2.0,
  "operator": "yellowcard",
  "breakdown": {
    "currencyFreaksRate": 0.0017639,
    "yellowCardRate": 566.94,
    "akuundaFeeRate": 0.02,
    "amountInUsd": 3.54,
    "amountAfterYellowCardRate": 2007.0
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
- **Précision intermédiaire:** Valeur brute complète (pas d'arrondi prématuré)
- **Précision finale:** 2 décimales (arrondi uniquement à la fin pour l'affichage)
- **Mode d'arrondi:** `HALF_UP` (arrondi à la valeur supérieure si ≥ 0.5)

**Pourquoi cette approche ?**
- Les conversions de devises peuvent donner beaucoup de décimales (ex: 3.666666...)
- Si on arrondit trop tôt, on perd de la précision et on ne retrouve plus le montant initial après reconversion
- En gardant la valeur brute pour les calculs, on garantit la précision maximale
- L'arrondi à 2 décimales est fait uniquement à la fin pour l'affichage à l'utilisateur

---

## 🔄 Envoi à YellowCard API

### ⚠️ IMPORTANT : Valeur brute en USD

Lors de la création d'une collection OnRamp via YellowCard, le système envoie la **valeur brute en USD** (sans arrondi) à l'API YellowCard.

**Exemple concret :**
- Utilisateur saisit : **2000 XOF**
- Conversion XOF → USD (Currency Freaks) : **3.532807416069328 USD** (valeur brute)
- **Cette valeur brute complète est envoyée à YellowCard** dans le champ `amount` du payload JSON

**Pourquoi envoyer la valeur brute ?**
- Si on arrondit à 2 décimales (ex: 3.53 USD) avant d'envoyer à YellowCard, on perd de la précision
- YellowCard effectue ensuite sa propre conversion USD → XOF avec ses taux du jour
- Si la valeur envoyée est arrondie, le montant final en XOF ne correspondra plus exactement au montant initial
- En envoyant la valeur brute (ex: 3.532807416069328), YellowCard peut effectuer ses calculs avec la précision maximale

**Implémentation technique :**
- La méthode `getConvertedAmountBigDecimal()` retourne un `BigDecimal` avec toute la précision
- `ObjectMapper` sérialise automatiquement le `BigDecimal` dans le JSON avec toute sa précision
- Le payload JSON envoyé à YellowCard contient : `"amount": 3.532807416069328` (valeur brute)

**Exemple de payload envoyé à YellowCard :**
```json
{
  "recipient": { ... },
  "source": { ... },
  "amount": 3.532807416069328,  // ← Valeur brute en USD (sans arrondi)
  "localAmount": 2000.0,         // ← Montant en devise locale (XOF) pour référence
  "currency": "XOF",
  "country": "CI",
  ...
}
```

---

## 📍 Endpoint API

```
POST /api/internal/v1/fees/calculate
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

## ✅ Validation

- ✅ Montant doit être positif (> 0)
- ✅ Devise doit être valide (code ISO, ex: XOF, EUR, USD)
- ✅ Code pays doit être valide (code ISO à 2 lettres, ex: CI, FR, SN)
- ✅ Opérateur doit être "yellowcard" ou "guardarian" (si spécifié, sinon détection automatique)

---

## 🔧 Détails techniques Guardarian

### Endpoint utilisé
- **Méthode:** GET
- **URL:** `{guardarian_api_url}/v1/estimate`
- **Paramètres:**
  - `from_currency`: Devise source (ex: EUR)
  - `to_currency`: Devise destination (ex: USDC)
  - `from_amount`: Montant à convertir (ex: 30.0)

### Gestion des erreurs
1. **Tentative POST:** Si l'endpoint POST `/estimate` retourne 404, le système essaie GET
2. **Format des paramètres:** Le système essaie deux formats:
   - `from_currency`, `to_currency`, `from_amount`
   - `from`, `to`, `from_amount`
3. **Fallback:** Si tous les endpoints échouent, utilisation d'un taux fixe de 0.5%

### Parsing de la réponse
Le système parse la réponse JSON avec `JsonNode` pour extraire:
- `converted_amount.amount` → Montant reçu
- `estimated_exchange_rate` → Taux de change
- `service_fees[0].amount` → Frais (pour validation)

---

---

## 📚 Résumé pour explication

### YellowCard OnRamp (Afrique)
**En une phrase:** L'utilisateur saisit un montant, le système le convertit via Currency Freaks et YellowCard, calcule 2% de frais sur le montant après conversion, et le montant reçu = montant après conversion - frais.

**Exemple concret:**
- Utilisateur saisit: **2005 XOF**
- Après conversion: **2007 XOF** (via Currency Freaks + YellowCard)
- Frais: **41 XOF** (2% de 2007)
- Montant reçu: **1966 XOF** (2007 - 41)

### Guardarian OnRamp (Europe/International)
**En une phrase:** L'utilisateur saisit un montant, le système appelle l'API Guardarian qui calcule automatiquement les frais, et le montant reçu = montant estimé par Guardarian.

**Exemple concret:**
- Utilisateur saisit: **30 EUR**
- Guardarian calcule: **29.85 EUR** (montant reçu)
- Frais: **0.15 EUR** (30 - 29.85)
- Pourcentage: **0.5%**

---

**Dernière mise à jour:** 2025-11-12  
**Version:** 1.4  
**Statut Guardarian:** ✅ Opérationnel  
**Statut YellowCard:** ✅ Opérationnel  
**Précision des calculs:** ✅ Valeurs brutes utilisées (pas d'arrondi prématuré)  
**Envoi à YellowCard:** ✅ Valeur brute en USD envoyée (ex: 3.532807416069328)

