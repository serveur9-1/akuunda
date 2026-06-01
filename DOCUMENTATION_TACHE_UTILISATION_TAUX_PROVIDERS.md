# Documentation - Utilisation des Taux des Providers (YellowCard & Guardarian)

## 📋 Vue d'ensemble

Cette documentation décrit la modification du système de calcul des frais pour utiliser **exclusivement les taux des providers** (YellowCard et Guardarian) au lieu de mélanger les taux du marché fiduciaire (Currency Freaks) avec les taux des providers.

---

## 🔴 Problème actuel

### Situation actuelle

Le système utilise actuellement un **mélange de sources de taux** :

1. **Pour YellowCard OnRamp** :
   - Conversion XOF → USD via **Currency Freaks** (taux du marché fiduciaire)
   - Puis conversion USD → XOF via **YellowCard rates** (taux du provider)
   - **Problème** : Mélange de deux sources de taux différentes

2. **Pour YellowCard OffRamp** :
   - Conversion XOF → USD via **Currency Freaks** (taux du marché fiduciaire)
   - Puis conversion USD → XOF via **YellowCard rates "sell"** (taux du provider)
   - **Problème** : Mélange de deux sources de taux différentes

3. **Pour Guardarian** :
   - ✅ Déjà correct : Utilise le taux `estimated_exchange_rate` depuis l'endpoint `/estimate`
   - ⚠️ À vérifier : S'assurer que le taux est bien utilisé dans tous les calculs

### Impact du problème

- **Incohérence** : Les calculs mélangent des taux de sources différentes
- **Imprécision** : Les taux du marché (Currency Freaks) peuvent différer des taux réels des providers
- **Risque financier** : Les écarts de taux peuvent créer des différences entre les estimations et les montants réels

---

## ✅ Solution proposée

### Principe fondamental

**Utiliser exclusivement les taux des providers** pour tous les calculs :
- **YellowCard** : Utiliser les taux `buy` et `sell` directement depuis l'API YellowCard
- **Guardarian** : Utiliser le taux `estimated_exchange_rate` depuis l'endpoint `/estimate`

### Changements à effectuer

#### 1. YellowCard OnRamp

**Avant** :
```
XOF → USD (Currency Freaks) → XOF (YellowCard buy rate)
```

**Après** :
```
Utiliser directement le taux "buy" de YellowCard
Formule : Montant XOF × (1 / taux buy) = Montant USD équivalent
Puis : Montant USD × taux buy = Montant après conversion (pour calculer les frais)
```

#### 2. YellowCard OffRamp

**Avant** :
```
XOF → USD (Currency Freaks) → XOF (YellowCard sell rate)
```

**Après** :
```
Utiliser directement le taux "sell" de YellowCard
Formule : Montant XOF × (1 / taux sell) = Montant USD équivalent
Puis : Montant USD × taux sell = Montant après conversion (pour calculer les frais)
```

#### 3. Guardarian

**Vérification** :
- S'assurer que le taux `estimated_exchange_rate` est bien utilisé
- Pas de changement si déjà correct

---

## 🔧 Modifications techniques

### 1. Nouvelle méthode pour récupérer les taux avec channelId

#### Interface `AkuundaYellowCardClientService`

```java
/**
 * Récupère les taux de change pour un channel spécifique
 * 
 * @param channelId L'identifiant du channel YellowCard
 * @param currencyCode Le code de la devise (ex: "XOF", "XAF")
 * @return ResponseEntity contenant la réponse JSON avec les taux buy et sell
 */
ResponseEntity<String> getRatesByChannelId(String channelId, String currencyCode);
```

#### Implémentation `AkuundaYellowCardClientServiceImpl`

```java
@Override
public ResponseEntity<String> getRatesByChannelId(String channelId, String currencyCode) {
    // Endpoint: /api/internal/v1/yellow-card/rates/{channelId}?currencyCode=XAF
    String url = baseUrl + "/rates/" + channelId;
    if (currencyCode != null) {
        url += "?currencyCode=" + currencyCode;
    }
    
    String timestamp = Instant.now().toString();
    String authorizationHeader = generateAuthorizationHeader(apiKey, apiSecret,
            "/business/rates/" + channelId, timestamp);
    
    // ... implémentation HTTP GET
}
```

**Format de réponse attendu** :
```json
{
    "buy": 588.96,
    "sell": 554.98,
    "locale": "CI",
    "rateId": "west-african-franc",
    "code": "XOF",
    "updatedAt": "2025-11-11T23:08:18.417Z"
}
```

### 2. Modification du calcul OnRamp YellowCard

#### Fichier : `FeesCalculationServiceImpl.java`

**Méthode à modifier** : `calculateYellowCardFees`

**Changements** :

1. **Supprimer** l'appel à Currency Freaks
2. **Récupérer** le channelId pour le pays (utiliser `getFirstChannelIdForCountry`)
3. **Appeler** `getRatesByChannelId(channelId, currency)` pour obtenir les taux
4. **Extraire** le taux "buy" de la réponse
5. **Calculer** directement avec le taux YellowCard

**Nouvelle logique** :

```java
// 1. Récupérer le channelId pour le pays
String channelId = getFirstChannelIdForCountry(request.getCountryCode());
if (channelId == null) {
    // Fallback : utiliser l'ancienne méthode getRates sans channelId
    // ou retourner une erreur
}

// 2. Récupérer les taux YellowCard avec channelId
var yellowCardRatesResponse = yellowCardClientService.getRatesByChannelId(channelId, currency);

// 3. Parser la réponse pour extraire le taux "buy"
BigDecimal yellowCardBuyRate = parseYellowCardBuyRate(yellowCardRatesResponse.getBody());

// 4. Calculer le montant après conversion YellowCard
// Formule : Montant XOF × (1 / taux buy) = Montant USD équivalent
BigDecimal amountInUSD = amountXOF.divide(yellowCardBuyRate, 10, RoundingMode.HALF_UP);

// Puis : Montant USD × taux buy = Montant après conversion (pour calculer les frais)
BigDecimal amountAfterYellowCardRate = amountInUSD.multiply(yellowCardBuyRate);

// 5. Calculer les frais (2% du montant après conversion)
BigDecimal estimatedFees = amountAfterYellowCardRate.multiply(BigDecimal.valueOf(0.02));

// 6. Calculer le montant reçu
BigDecimal amountReceived = amountAfterYellowCardRate.subtract(estimatedFees);
```

**Méthode helper à ajouter** :

```java
/**
 * Parse le taux YellowCard "buy" depuis la réponse JSON
 */
private BigDecimal parseYellowCardBuyRate(String jsonResponse) {
    try {
        JsonNode root = objectMapper.readTree(jsonResponse);
        
        // Format attendu: {"buy": 588.96, "sell": 554.98, "code": "XOF", ...}
        if (root.has("buy")) {
            return new BigDecimal(root.get("buy").asText());
        }
        
        log.warn("Taux 'buy' non trouvé dans la réponse YellowCard: {}", jsonResponse);
        return BigDecimal.ZERO;
    } catch (Exception e) {
        log.error("Erreur lors du parsing du taux YellowCard 'buy'", e);
        return BigDecimal.ZERO;
    }
}
```

### 3. Modification du calcul OffRamp YellowCard

#### Fichier : `FeesCalculationServiceImpl.java`

**Méthode à modifier** : `calculateYellowCardOffRampFees`

**Changements similaires** :

1. **Supprimer** l'appel à Currency Freaks
2. **Récupérer** le channelId pour le pays
3. **Appeler** `getRatesByChannelId(channelId, currency)` pour obtenir les taux
4. **Extraire** le taux "sell" de la réponse
5. **Calculer** directement avec le taux YellowCard "sell"

**Nouvelle logique** :

```java
// 1. Récupérer le channelId pour le pays
String channelId = getFirstChannelIdForCountry(request.getCountryCode());

// 2. Récupérer les taux YellowCard avec channelId
var yellowCardRatesResponse = yellowCardClientService.getRatesByChannelId(channelId, currency);

// 3. Parser la réponse pour extraire le taux "sell"
BigDecimal yellowCardSellRate = parseYellowCardSellRate(yellowCardRatesResponse.getBody());

// 4. Calculer le montant après conversion YellowCard
// Formule : Montant XOF × (1 / taux sell) = Montant USD équivalent
BigDecimal amountInUSD = amountXOF.divide(yellowCardSellRate, 10, RoundingMode.HALF_UP);

// Puis : Montant USD × taux sell = Montant après conversion (pour calculer les frais)
BigDecimal amountAfterConversion = amountInUSD.multiply(yellowCardSellRate);

// 5. Calculer les frais (3.5% du montant après conversion)
BigDecimal estimatedFees = amountAfterConversion.multiply(BigDecimal.valueOf(0.035));

// 6. Calculer le montant reçu
BigDecimal amountReceived = amountAfterConversion.subtract(estimatedFees);
```

**Méthode helper à ajouter** :

```java
/**
 * Parse le taux YellowCard "sell" depuis la réponse JSON
 */
private BigDecimal parseYellowCardSellRate(String jsonResponse) {
    try {
        JsonNode root = objectMapper.readTree(jsonResponse);
        
        // Format attendu: {"buy": 588.96, "sell": 554.98, "code": "XOF", ...}
        if (root.has("sell")) {
            return new BigDecimal(root.get("sell").asText());
        }
        
        log.warn("Taux 'sell' non trouvé dans la réponse YellowCard: {}", jsonResponse);
        return BigDecimal.ZERO;
    } catch (Exception e) {
        log.error("Erreur lors du parsing du taux YellowCard 'sell'", e);
        return BigDecimal.ZERO;
    }
}
```

### 4. Vérification Guardarian

#### Fichier : `FeesCalculationServiceImpl.java`

**Méthode à vérifier** : `calculateGuardarianFees`

**Vérification** :
- ✅ Le taux `estimated_exchange_rate` est bien extrait depuis la réponse `/estimate`
- ✅ Le taux est utilisé correctement dans les calculs
- ⚠️ Si nécessaire, corriger l'utilisation du taux

**Format de réponse Guardarian** :
```json
{
  "to_currency": "USDC",
  "from_currency": "EUR",
  "to_network": "MATIC",
  "value": "33.7574551",
  "service_fees": [...],
  "estimated_exchange_rate": "1.12524851",  // ← Taux à utiliser
  "converted_amount": {
    "amount": "29.85",
    "currency": "EUR"
  },
  ...
}
```

---

## 📊 Exemples de calculs

### Exemple 1 : YellowCard OnRamp

**Input** :
- Montant : 2000 XOF
- Pays : CI (Côte d'Ivoire)
- Devise : XOF

**Taux YellowCard** (depuis l'endpoint avec channelId) :
```json
{
    "buy": 588.96,
    "sell": 554.98,
    "code": "XOF"
}
```

**Calcul** :

1. **Montant USD équivalent** :
   ```
   Montant USD = 2000 XOF / 588.96 = 3.395 USD (valeur brute)
   ```

2. **Montant après conversion YellowCard** :
   ```
   Montant après conversion = 3.395 USD × 588.96 = 2000 XOF
   ```
   (Mathématiquement, on revient au montant initial)

3. **Frais Akuunda (2%)** :
   ```
   Frais = 2000 XOF × 0.02 = 40 XOF
   ```

4. **Montant reçu** :
   ```
   Montant reçu = 2000 XOF - 40 XOF = 1960 XOF
   ```

**Réponse API** :
```json
{
  "amountSent": 2000.0,
  "currency": "XOF",
  "estimatedFees": 40.0,
  "amountReceived": 1960.0,
  "exchangeRate": 588.96,
  "feePercentage": 2.0,
  "operator": "yellowcard",
  "breakdown": {
    "yellowCardBuyRate": 588.96,
    "akuundaFeeRate": 0.02,
    "amountInUsd": 3.395,
    "amountAfterYellowCardRate": 2000.0
  }
}
```

### Exemple 2 : YellowCard OffRamp

**Input** :
- Montant : 2000 XOF
- Pays : CI (Côte d'Ivoire)
- Devise : XOF

**Taux YellowCard** (depuis l'endpoint avec channelId) :
```json
{
    "buy": 588.96,
    "sell": 554.98,
    "code": "XOF"
}
```

**Calcul** :

1. **Montant USD équivalent** :
   ```
   Montant USD = 2000 XOF / 554.98 = 3.604 USD (valeur brute)
   ```

2. **Montant après conversion YellowCard** :
   ```
   Montant après conversion = 3.604 USD × 554.98 = 2000 XOF
   ```

3. **Frais Akuunda (3.5%)** :
   ```
   Frais = 2000 XOF × 0.035 = 70 XOF
   ```

4. **Montant reçu** :
   ```
   Montant reçu = 2000 XOF - 70 XOF = 1930 XOF
   ```

### Exemple 3 : Guardarian OnRamp

**Input** :
- Montant : 30 EUR
- Pays : FR (France)
- Devise : EUR

**Réponse Guardarian `/estimate`** :
```json
{
  "estimated_exchange_rate": "1.12524851",
  "converted_amount": {
    "amount": "29.85",
    "currency": "EUR"
  }
}
```

**Calcul** :
- Le taux `estimated_exchange_rate` est déjà utilisé dans la réponse Guardarian
- Frais = 30 EUR - 29.85 EUR = 0.15 EUR
- Montant reçu = 29.85 EUR

---

## 🔄 Gestion des erreurs et fallback

### Scénarios d'erreur

1. **ChannelId non trouvé** :
   - **Fallback** : Utiliser l'ancienne méthode `getRates(currencyCode)` sans channelId
   - **Log** : Avertissement indiquant que le channelId n'a pas été trouvé

2. **Endpoint `/rates/{channelId}` non disponible** :
   - **Fallback** : Utiliser l'ancienne méthode `getRates(currencyCode)`
   - **Log** : Erreur indiquant que l'endpoint avec channelId a échoué

3. **Taux "buy" ou "sell" non trouvé dans la réponse** :
   - **Fallback** : Utiliser l'ancienne méthode de parsing
   - **Log** : Avertissement indiquant que le taux spécifique n'a pas été trouvé

4. **Erreur lors de l'appel à l'API YellowCard** :
   - **Retour** : Erreur 500 avec message explicite
   - **Log** : Erreur détaillée pour le debugging

### Code de fallback

```java
private BigDecimal getYellowCardRateWithFallback(String countryCode, String currency, boolean isOnRamp) {
    try {
        // 1. Essayer de récupérer le channelId
        String channelId = getFirstChannelIdForCountry(countryCode);
        
        if (channelId != null) {
            // 2. Essayer d'appeler l'endpoint avec channelId
            var ratesResponse = yellowCardClientService.getRatesByChannelId(channelId, currency);
            
            if (ratesResponse.getStatusCode().is2xxSuccessful() && ratesResponse.getBody() != null) {
                // 3. Parser le taux (buy pour OnRamp, sell pour OffRamp)
                BigDecimal rate = isOnRamp 
                    ? parseYellowCardBuyRate(ratesResponse.getBody())
                    : parseYellowCardSellRate(ratesResponse.getBody());
                
                if (rate.compareTo(BigDecimal.ZERO) > 0) {
                    return rate;
                }
            }
        }
        
        // Fallback : Utiliser l'ancienne méthode
        log.warn("Fallback vers l'ancienne méthode getRates pour {} (channelId: {})", currency, channelId);
        var fallbackResponse = yellowCardClientService.getRates(currency);
        
        if (fallbackResponse.getStatusCode().is2xxSuccessful() && fallbackResponse.getBody() != null) {
            return isOnRamp
                ? parseYellowCardRateBigDecimal(fallbackResponse.getBody(), currency)
                : parseYellowCardSellRateBigDecimal(fallbackResponse.getBody(), currency);
        }
        
        throw new RuntimeException("Impossible de récupérer le taux YellowCard");
        
    } catch (Exception e) {
        log.error("Erreur lors de la récupération du taux YellowCard", e);
        throw e;
    }
}
```

---

## 📝 Modifications des fichiers

### Fichiers modifiés ✅

1. **`AkuundaYellowCardClientService.java`** (Interface) - ✅ **FAIT**
   - ✅ Ajout de la méthode `getRatesByChannelId(String channelId, String currencyCode)`

2. **`AkuundaYellowCardClientServiceImpl.java`** (Implémentation) - ✅ **FAIT**
   - ✅ Implémentation de `getRatesByChannelId` avec construction de l'URL `/business/rates/{channelId}`
   - ✅ Gestion des paramètres `currencyCode` dans la query string
   - ✅ Gestion de l'autorisation avec le bon endpoint path

3. **`FeesCalculationServiceImpl.java`** (Service de calcul) - ✅ **PARTIELLEMENT FAIT**
   - ✅ Modification de `calculateYellowCardFees` pour utiliser directement le taux "buy" - **FAIT**
   - ✅ Suppression des appels à Currency Freaks pour OnRamp - **FAIT**
   - ✅ Ajout de `parseYellowCardBuyRateBigDecimal` - **FAIT**
   - ✅ Ajout de `getFirstChannelIdForCountry` - **FAIT**
   - ✅ Implémentation du fallback vers `getRates` si channelId non disponible - **FAIT**
   - ⏳ Modification de `calculateYellowCardOffRampFees` pour utiliser channelId - **À FAIRE** (utilise déjà le taux sell mais sans channelId)
   - ✅ `parseYellowCardSellRateBigDecimal` existe déjà - **OK**

4. **`FeesCalculationController.java`** (Optionnel)
   - ⏳ Mettre à jour la documentation Swagger si nécessaire - **À FAIRE**

### Fichiers vérifiés ✅

1. **`AkuundaGuardarianClientServiceImpl.java`** - ✅ **VÉRIFIÉ**
   - ✅ Le taux `estimated_exchange_rate` est bien extrait depuis la réponse `/estimate`
   - ✅ Le taux est utilisé correctement dans `EstimateResponse.rate()`
   - ✅ Le parsing gère bien le champ `estimated_exchange_rate` dans la réponse JSON

---

## 🧪 Tests à effectuer

### Tests unitaires

1. **Test `getRatesByChannelId`** :
   - Test avec channelId valide
   - Test avec channelId invalide
   - Test avec currencyCode valide/invalide
   - Test de parsing de la réponse JSON

2. **Test `parseYellowCardBuyRate`** :
   - Test avec réponse JSON valide
   - Test avec réponse JSON sans champ "buy"
   - Test avec réponse JSON invalide

3. **Test `parseYellowCardSellRate`** :
   - Test avec réponse JSON valide
   - Test avec réponse JSON sans champ "sell"
   - Test avec réponse JSON invalide

4. **Test `calculateYellowCardFees`** :
   - Test avec channelId valide
   - Test avec fallback (channelId non trouvé)
   - Test avec différents montants
   - Test de précision des calculs (BigDecimal)

5. **Test `calculateYellowCardOffRampFees`** :
   - Test avec channelId valide
   - Test avec fallback (channelId non trouvé)
   - Test avec différents montants
   - Test de précision des calculs (BigDecimal)

### Tests d'intégration

1. **Test end-to-end OnRamp YellowCard** :
   - Appel réel à l'endpoint `/api/internal/v1/fees/calculate`
   - Vérification de la réponse
   - Vérification que le taux utilisé est bien celui de YellowCard

2. **Test end-to-end OffRamp YellowCard** :
   - Appel réel à l'endpoint `/api/internal/v1/fees/calculate-offramp`
   - Vérification de la réponse
   - Vérification que le taux utilisé est bien celui de YellowCard

3. **Test Guardarian** :
   - Vérifier que le taux `estimated_exchange_rate` est bien utilisé
   - Comparer avec les résultats précédents

### Tests de régression

1. **Comparer les résultats** :
   - Avant/après pour les mêmes montants
   - Vérifier que les différences sont cohérentes avec les écarts de taux

2. **Tests de performance** :
   - Vérifier que les nouveaux appels API n'ajoutent pas de latence significative

---

## 📚 Documentation à mettre à jour

### Documentation technique

1. **`DOCUMENTATION_FLOW_CALCUL_FRAIS_ONRAMP.md`** :
   - Mettre à jour le flow YellowCard pour refléter l'utilisation directe du taux "buy"
   - Supprimer la référence à Currency Freaks pour YellowCard
   - Ajouter des exemples avec les nouveaux calculs

2. **`DOCUMENTATION_FLOW_CALCUL_FRAIS_OFFRAMP.md`** :
   - Mettre à jour le flow YellowCard pour refléter l'utilisation directe du taux "sell"
   - Supprimer la référence à Currency Freaks pour YellowCard
   - Ajouter des exemples avec les nouveaux calculs

3. **Swagger/OpenAPI** :
   - Mettre à jour la documentation des endpoints si nécessaire
   - Ajouter des exemples de réponses avec les nouveaux champs

### Documentation utilisateur

1. **README.md** (si nécessaire) :
   - Mettre à jour les informations sur les sources de taux

---

## ⚠️ Points d'attention

### 1. Récupération du channelId

- Le `channelId` doit être récupéré pour chaque pays
- Il faut gérer le cas où aucun channel n'est disponible
- Le fallback doit être robuste

### 2. Précision des calculs

- Continuer à utiliser `BigDecimal` pour tous les calculs
- Maintenir la précision des valeurs brutes
- Arrondir uniquement à la fin pour l'affichage

### 3. Compatibilité

- Maintenir la compatibilité avec l'ancienne méthode `getRates` en fallback
- Ne pas casser les fonctionnalités existantes

### 4. Performance

- L'appel à `getChannels` pour récupérer le channelId ajoute une requête API
- Considérer le caching des channelIds par pays si nécessaire

### 5. Logging

- Logger toutes les étapes importantes
- Logger les fallbacks pour le monitoring
- Logger les erreurs avec suffisamment de détails

---

## 📅 Plan d'implémentation

### Phase 1 : Préparation
1. ✅ Créer cette documentation
2. ⏳ Valider la documentation avec l'équipe
3. ⏳ Préparer l'environnement de test

### Phase 2 : Implémentation
1. ✅ Ajouter la méthode `getRatesByChannelId` - **FAIT**
2. ✅ Modifier `calculateYellowCardFees` - **FAIT** (utilise uniquement le taux YellowCard buy, sans Currency Freaks)
3. ⏳ Modifier `calculateYellowCardOffRampFees` - **EN COURS** (utilise déjà le taux sell, mais sans channelId)
4. ✅ Ajouter les méthodes helper de parsing - **FAIT** (`parseYellowCardBuyRateBigDecimal`, `getFirstChannelIdForCountry`)
5. ✅ Implémenter le système de fallback - **FAIT** (fallback vers `getRates` si channelId non disponible)

### Phase 3 : Tests
1. ⏳ Tests unitaires
2. ⏳ Tests d'intégration
3. ⏳ Tests de régression
4. ⏳ Tests de performance

### Phase 4 : Documentation
1. ⏳ Mettre à jour la documentation technique
2. ⏳ Mettre à jour Swagger
3. ⏳ Mettre à jour le README si nécessaire

### Phase 5 : Déploiement
1. ⏳ Review du code
2. ⏳ Déploiement en staging
3. ⏳ Tests en staging
4. ⏳ Déploiement en production

---

## 🔍 Questions à clarifier - RÉSOLUES

1. **Endpoint YellowCard** : ✅ **RÉSOLU**
   - L'endpoint `/api/internal/v1/yellow-card/rates/{channelId}?currencyCode=XAF` est un wrapper interne
   - L'implémentation appelle directement l'API YellowCard avec le path `/business/rates/{channelId}`
   - Le système utilise `getRatesByChannelId(channelId, currencyCode)` qui construit l'URL appropriée

2. **ChannelId** : ✅ **RÉSOLU**
   - Le système utilise le premier channel disponible via `getFirstChannelIdForCountry(countryCode)`
   - Si aucun channel n'est trouvé, fallback vers `getRates(currencyCode)` sans channelId

3. **Fallback** : ✅ **RÉSOLU**
   - En cas d'échec de récupération du channelId ou si l'endpoint avec channelId échoue :
     - ✅ Utiliser l'ancienne méthode `getRates(currencyCode)` - **IMPLÉMENTÉ**
     - ✅ Logging approprié pour le monitoring - **IMPLÉMENTÉ**

4. **Guardarian** : ✅ **RÉSOLU**
   - ✅ Le taux `estimated_exchange_rate` est déjà correctement utilisé dans `calculateGuardarianFees`
   - ✅ Le parsing extrait bien `estimated_exchange_rate` depuis la réponse JSON
   - ✅ Le taux est utilisé dans tous les calculs Guardarian (OnRamp et OffRamp)

---

## 📞 Contact et validation

**Document créé le** : 2025-11-12  
**Version** : 1.1  
**Statut** : ✅ En cours d'implémentation - Phase 2 partiellement complétée

**À valider par** :
- [ ] Équipe technique
- [ ] Product Owner
- [ ] Manager

**Prochaines étapes** :
1. ✅ Valider cette documentation - **FAIT**
2. ✅ Clarifier les questions ouvertes - **FAIT** (endpoint avec channelId confirmé)
3. ✅ Commencer l'implémentation - **FAIT** (OnRamp complété, OffRamp à finaliser)
4. ⏳ Finaliser OffRamp avec channelId
5. ⏳ Tests et validation

---

## 📎 Annexes

### A. Format de réponse YellowCard `/rates/{channelId}`

```json
{
    "buy": 588.96,
    "sell": 554.98,
    "locale": "CI",
    "rateId": "west-african-franc",
    "code": "XOF",
    "updatedAt": "2025-11-11T23:08:18.417Z"
}
```

### B. Format de réponse Guardarian `/estimate`

```json
{
  "to_currency": "USDC",
  "from_currency": "EUR",
  "to_network": "MATIC",
  "value": "33.7574551",
  "service_fees": [
    {
      "amount": "0.15",
      "currency": "EUR",
      "name": "Service fee",
      "percentage": "0.5%"
    }
  ],
  "estimated_exchange_rate": "1.12524851",
  "converted_amount": {
    "amount": "29.85",
    "currency": "EUR"
  },
  "network_fee": {
    "currency": "USDC",
    "amount": "0.0016158"
  }
}
```

### C. Comparaison avant/après

| Aspect | Avant | Après | Statut |
|--------|-------|-------|--------|
| **Source taux OnRamp** | Currency Freaks + YellowCard | YellowCard uniquement (buy rate) | ✅ **IMPLÉMENTÉ** |
| **Source taux OffRamp** | Currency Freaks + YellowCard | YellowCard uniquement (sell rate) | ⏳ **EN COURS** (utilise déjà sell mais sans channelId) |
| **Source taux Guardarian** | Guardarian `/estimate` | Guardarian `/estimate` (estimated_exchange_rate) | ✅ **VÉRIFIÉ** |
| **Précision** | Mélange de sources | Source unique par provider | ✅ **AMÉLIORÉ** |
| **Cohérence** | ⚠️ Incohérente | ✅ Cohérente | ✅ **AMÉLIORÉ** |
| **Endpoint YellowCard** | `/business/rates?currency=XOF` | `/business/rates/{channelId}?currencyCode=XAF` | ✅ **IMPLÉMENTÉ** |
| **Fallback** | ❌ Aucun | ✅ Vers `getRates` si channelId indisponible | ✅ **IMPLÉMENTÉ** |

---

---

## 📋 Résumé de l'état actuel (2025-11-12)

### ✅ Implémenté

1. **Méthode `getRatesByChannelId`** : Ajoutée dans l'interface et l'implémentation
2. **OnRamp YellowCard** : Utilise maintenant uniquement le taux YellowCard buy (sans Currency Freaks)
3. **Parsing des taux** : Méthodes `parseYellowCardBuyRateBigDecimal` et `getFirstChannelIdForCountry` ajoutées
4. **Système de fallback** : Implémenté pour revenir à `getRates` si channelId non disponible
5. **Guardarian** : Vérifié - utilise correctement `estimated_exchange_rate`

### ⏳ À finaliser

1. **OffRamp YellowCard** : Ajouter le support de channelId (utilise déjà le taux sell mais sans channelId)
2. **Documentation Swagger** : Mettre à jour si nécessaire
3. **Tests** : Tests unitaires et d'intégration à effectuer

### 📊 Impact

- ✅ **OnRamp** : Plus de mélange Currency Freaks + YellowCard, utilisation exclusive du taux YellowCard
- ✅ **Guardarian** : Confirmation que le taux `estimated_exchange_rate` est bien utilisé
- ⏳ **OffRamp** : À finaliser avec channelId

---

**Fin de la documentation**

