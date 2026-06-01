# Documentation - Affichage du Solde avec Taux d'Achat du Partenaire

## 📋 Vue d'ensemble

Cette documentation décrit l'implémentation de l'affichage du solde utilisateur en devise locale en utilisant le **taux d'achat du partenaire** (YellowCard ou Guardarian) sauvegardé lors du dernier dépôt.

---

## 🎯 Principe de fonctionnement

### Logique d'affichage

1. **Récupération du solde USDC** : Le système récupère le solde en USDC du wallet de l'utilisateur
2. **Conversion en devise locale** : Le solde USDC est multiplié par le **taux d'achat sauvegardé** du partenaire selon le pays du compte client
3. **Taux sauvegardé** : Le taux d'achat est sauvegardé lors de chaque dépôt (OnRamp) et utilisé jusqu'au prochain dépôt
4. **Mise à jour du taux** : Lors d'un nouveau dépôt, si le taux a changé, l'ancien taux est écrasé par le nouveau

### Formule de conversion

```
Affichage solde = Montant USDC × Taux d'achat YellowCard (sauvegardé)
```

### Exemple concret

**Scénario :**
- **Dépôt effectué** : 1900 XOF
- **Taux YellowCard au moment du dépôt** : 588.08 XOF/USD
- **Montant USDC déposé** : 3.11898703 USDC

**Calcul de l'affichage :**
```
Affichage solde = 3.11898703 × 588.08
Affichage solde = 1,833.96 XOF
```

**Note :** Ce taux (588.08) est sauvegardé et utilisé pour tous les affichages jusqu'au prochain dépôt ou jusqu'à la mise à jour automatique (selon l'option choisie).

---

## 🔧 Implémentation technique

### 1. Ajout du champ `lastBuyRate` dans l'entité `Wallet`

```java
@Entity
public class Wallet implements Serializable {
    // ... champs existants ...
    
    /**
     * Taux d'achat du partenaire sauvegardé lors du dernier dépôt
     * Utilisé pour convertir le solde USDC en devise locale pour l'affichage
     * Format : 1 USD = lastBuyRate XOF (ex: 588.96)
     */
    @Column(name = "last_buy_rate")
    private Double lastBuyRate;
    
    /**
     * Date de dernière mise à jour du taux d'achat
     */
    @Column(name = "last_buy_rate_updated_at")
    private LocalDateTime lastBuyRateUpdatedAt;
    
    /**
     * Partenaire utilisé pour le dernier dépôt (yellowcard ou guardarian)
     */
    @Column(name = "last_provider")
    private String lastProvider;
}
```

### 2. Sauvegarde du taux lors d'un dépôt (OnRamp)

#### Pour YellowCard OnRamp

**Fichier :** `AkuundaYellowCardClientServiceImpl.java`

**Méthode :** `updateWalletAfterOnRamp` ou lors de la création de la collection

```java
private void updateWalletAfterOnRamp(Wallet wallet, OnRampRequest request, String sequenceId) {
    if (wallet == null || request == null) return;

    // ... code existant ...
    
    // 🔹 NOUVEAU : Récupérer et sauvegarder le taux d'achat YellowCard
    String currency = request.getCurrency();
    String countryCode = request.getCountry();
    
    // Récupérer le channelId pour le pays
    String channelId = getFirstChannelIdForCountry(countryCode);
    
    // Récupérer les taux YellowCard
    var ratesResponse = yellowCardClientService.getRatesByChannelId(channelId, currency);
    if (ratesResponse.getStatusCode().is2xxSuccessful() && ratesResponse.getBody() != null) {
        BigDecimal buyRate = parseYellowCardBuyRateBigDecimal(ratesResponse.getBody(), currency);
        if (buyRate.compareTo(BigDecimal.ZERO) > 0) {
            wallet.setLastBuyRate(buyRate.doubleValue());
            wallet.setLastBuyRateUpdatedAt(LocalDateTime.now());
            wallet.setLastProvider("yellowcard");
            log.info("Taux d'achat YellowCard sauvegardé pour wallet {} : {}", wallet.getId(), buyRate);
        }
    }
    
    // ... reste du code existant ...
    walletRepository.saveAndFlush(wallet);
}
```

#### Pour Guardarian OnRamp

**Fichier :** `AkuundaGuardarianClientServiceImpl.java`

**Méthode :** Lors de la création de la transaction Guardarian

```java
// Après l'appel à /estimate, sauvegarder le taux
var estimateResponse = guardarianClientService.getEstimate(estimateRequest);
if (estimateResponse != null && estimateResponse.getStatusCode().is2xxSuccessful() 
        && estimateResponse.getBody() != null) {
    var estimate = estimateResponse.getBody();
    double exchangeRate = estimate.rate(); // estimated_exchange_rate
    
    if (exchangeRate > 0) {
        // Récupérer le wallet de l'utilisateur
        Wallet wallet = walletRepository.findByUsers(user);
        if (wallet != null) {
            wallet.setLastBuyRate(exchangeRate);
            wallet.setLastBuyRateUpdatedAt(LocalDateTime.now());
            wallet.setLastProvider("guardarian");
            walletRepository.saveAndFlush(wallet);
            log.info("Taux d'achat Guardarian sauvegardé pour wallet {} : {}", wallet.getId(), exchangeRate);
        }
    }
}
```

### 3. Modification de l'affichage du solde

**Fichier :** `UserServiceImpl.java`

**Méthode :** `getUserWalletBalance`

```java
@Override
public ResponseEntity<WalletBalanceDto> getUserWalletBalance(String username) {
    log.info("Fetching wallet balance for user: {}", username);

    // 🔹 1️⃣ Vérification de l'utilisateur
    Users user = userRepository.getUsersByUsername(username);
    if (user == null) {
        log.error("User not found with username: {}", username);
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    // 🔹 2️⃣ Récupération du solde du wallet (USDC)
    var walletResponse = walletService.getWalletBalance(user.getUsername());
    if (!walletResponse.getStatusCode().is2xxSuccessful() || walletResponse.getBody() == null) {
        log.error("Failed to fetch wallet balance for user {}", user.getUsername());
        return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).build();
    }

    double userBalance;
    try {
        userBalance = Double.parseDouble(walletResponse.getBody());
    } catch (NumberFormatException e) {
        log.error("Invalid wallet balance format for user {}", user.getUsername());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
    }

    // 🔹 3️⃣ Récupération du wallet associé
    Wallet wallet = walletRepository.findByUsers(user);
    if (wallet == null) {
        log.error("Wallet not found for user {}", user.getUsername());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    // 🔹 4️⃣ Conversion devise (USDC -> devise du wallet)
    String walletCurrency = wallet.getCurrency().getCurrencyCode();
    double convertedAmount = 0.0;
    
    // ⚠️ NOUVEAU : Utiliser le taux d'achat sauvegardé si disponible
    if (wallet.getLastBuyRate() != null && wallet.getLastBuyRate() > 0) {
        // Conversion avec le taux d'achat sauvegardé
        convertedAmount = userBalance * wallet.getLastBuyRate();
        log.info("Conversion utilisant le taux d'achat sauvegardé : {} USDC × {} = {} {}", 
                userBalance, wallet.getLastBuyRate(), convertedAmount, walletCurrency);
    } else {
        // ⚠️ FALLBACK : Si aucun taux sauvegardé, utiliser Currency Freaks (temporaire)
        log.warn("Aucun taux d'achat sauvegardé pour wallet {}, utilisation de Currency Freaks en fallback", wallet.getId());
        var conversionResponse = freaksClientService.convertCurrency("USDC", walletCurrency, userBalance);
        
        if (conversionResponse.getStatusCode().is2xxSuccessful() && conversionResponse.getBody() != null) {
            try {
                convertedAmount = Double.parseDouble(conversionResponse.getBody().getConvertedAmount());
            } catch (NumberFormatException e) {
                log.warn("Invalid conversion amount format for user {}", user.getUsername());
            }
        }
    }

    // 🔹 5️⃣ Arrondi précis avec BigDecimal
    userBalance = roundToTwoDecimals(userBalance);
    convertedAmount = roundToTwoDecimals(convertedAmount);

    log.info("User balance for {}: {} USDC | Converted: {} {} (taux: {})",
            user.getUsername(), userBalance, convertedAmount, walletCurrency, 
            wallet.getLastBuyRate() != null ? wallet.getLastBuyRate() : "Currency Freaks");

    // 🔹 6️⃣ Retour du résultat
    WalletBalanceDto result = new WalletBalanceDto(userBalance, convertedAmount);
    return ResponseEntity.ok(result);
}
```

---

## ⚠️ RISQUE : Changement de taux avant le prochain dépôt

### 🔴 Problème identifié

**Problème :** Si le taux change avant le prochain dépôt, **il y aura des écarts** entre l'affichage et la valeur réelle.

### 📊 Exemple concret

**Scénario initial :**
1. **Dépôt effectué** : 1900 XOF
   - Taux YellowCard au moment du dépôt : **588.08 XOF/USD**
   - Montant USDC déposé : **3.11898703 USDC**
   - Taux sauvegardé : **588.08**

2. **Affichage immédiat** (taux correct) :
   - Solde USDC : 3.11898703 USDC
   - Solde affiché : 3.11898703 × 588.08 = **1,833.96 XOF** ✅ (correct)

**Scénario problématique - Taux change avant le prochain dépôt :**

3. **Changement de taux** : Le taux YellowCard change (ex: baisse à **550.00 XOF/USD**)
   - Le taux sauvegardé reste à **588.08** (pas mis à jour car pas de nouveau dépôt)

4. **Affichage après changement de taux** : L'utilisateur voit toujours
   - Solde USDC : 3.11898703 USDC
   - Solde affiché : 3.11898703 × 588.08 = **1,833.96 XOF** ❌ (incorrect)
   - **Valeur réelle** : 3.11898703 × 550.00 = **1,715.44 XOF**

5. **Écart** : L'utilisateur voit **118.52 XOF de plus** que la valeur réelle
   - **Écart en pourcentage** : +6.9% (affichage surestimé)

### 📊 Impact du risque

#### Cas 1 : Taux en baisse (ex: 588.08 → 550.00)

**Impact :**
- 🔴 **Désavantage pour l'utilisateur** : L'affichage montre plus que la valeur réelle
- ⚠️ **Risque** : L'utilisateur peut penser avoir plus d'argent qu'en réalité
- 📈 **Exemple** :
  - Solde USDC : 3.11898703 USDC
  - Taux sauvegardé : 588.08 (ancien taux)
  - Taux réel actuel : 550.00 (nouveau taux)
  - Affiché (ancien taux) : 3.11898703 × 588.08 = **1,833.96 XOF** ❌
  - Réel (nouveau taux) : 3.11898703 × 550.00 = **1,715.44 XOF** ✅
  - **Écart** : +118.52 XOF (affichage surestimé de 6.9%)

**Conséquence :** L'utilisateur voit 1,833.96 XOF alors qu'il n'a réellement que 1,715.44 XOF en valeur. Il peut prendre des décisions basées sur une information incorrecte.

#### Cas 2 : Taux en hausse (ex: 588.08 → 620.00)

**Impact :**
- ✅ **Avantage pour l'utilisateur** : L'affichage montre moins que la valeur réelle
- ⚠️ **Risque** : L'utilisateur peut penser avoir moins d'argent qu'en réalité
- 📉 **Exemple** :
  - Solde USDC : 3.11898703 USDC
  - Taux sauvegardé : 588.08 (ancien taux)
  - Taux réel actuel : 620.00 (nouveau taux)
  - Affiché (ancien taux) : 3.11898703 × 588.08 = **1,833.96 XOF** ❌
  - Réel (nouveau taux) : 3.11898703 × 620.00 = **1,933.78 XOF** ✅
  - **Écart** : -99.82 XOF (affichage sous-estimé de 5.4%)

**Conséquence :** L'utilisateur voit 1,833.96 XOF alors qu'il a réellement 1,933.78 XOF en valeur. Il peut ne pas utiliser tout son argent disponible.

### 🎯 Recommandations pour atténuer le risque

#### Option 1 : Avertissement à l'utilisateur (Recommandé)

**Afficher un message d'avertissement si le taux est ancien :**

```java
// Calculer l'âge du taux
LocalDateTime lastUpdate = wallet.getLastBuyRateUpdatedAt();
if (lastUpdate != null) {
    long daysSinceUpdate = ChronoUnit.DAYS.between(lastUpdate, LocalDateTime.now());
    
    if (daysSinceUpdate > 1) {
        // Afficher un avertissement
        log.warn("Taux d'achat sauvegardé datant de {} jours pour wallet {}", daysSinceUpdate, wallet.getId());
        // Retourner un flag dans la réponse pour afficher un message à l'utilisateur
    }
}
```

**Réponse API modifiée :**

```java
public class WalletBalanceDto {
    private double userBalance;        // Solde USDC
    private double convertedAmount;    // Solde en devise locale
    private Double lastBuyRate;        // Taux utilisé
    private LocalDateTime rateUpdatedAt; // Date de mise à jour du taux
    private boolean rateStale;         // true si le taux est ancien (> 1 jour)
    private String warningMessage;     // Message d'avertissement si nécessaire
}
```

#### Option 2 : Mise à jour périodique du taux (Alternative)

**Forcer une mise à jour du taux si trop ancien :**

```java
// Si le taux est ancien (> 7 jours), forcer une mise à jour
if (lastUpdate != null) {
    long daysSinceUpdate = ChronoUnit.DAYS.between(lastUpdate, LocalDateTime.now());
    
    if (daysSinceUpdate > 7) {
        // Récupérer le nouveau taux
        updateBuyRateFromProvider(wallet, user);
    }
}
```

#### Option 3 : Affichage avec indication de précision (Alternative)

**Afficher une fourchette ou un indicateur de précision :**

```java
// Calculer une fourchette basée sur la volatilité historique
double currentRate = wallet.getLastBuyRate();
double minRate = currentRate * 0.95;  // -5%
double maxRate = currentRate * 1.05;  // +5%

// Afficher : "Entre 950 XOF et 1,050 XOF (taux approximatif)"
```

---

## 📝 Exemple de réponse API

### Réponse actuelle (sans risque)

```json
{
  "userBalance": 1.70,
  "convertedAmount": 1001.23
}
```

### Réponse recommandée (avec gestion du risque)

```json
{
  "userBalance": 1.70,
  "convertedAmount": 1001.23,
  "lastBuyRate": 588.96,
  "rateUpdatedAt": "2025-11-10T14:30:00",
  "rateStale": false,
  "warningMessage": null,
  "provider": "yellowcard"
}
```

### Réponse avec taux ancien

```json
{
  "userBalance": 1.70,
  "convertedAmount": 1001.23,
  "lastBuyRate": 588.96,
  "rateUpdatedAt": "2025-11-05T14:30:00",
  "rateStale": true,
  "warningMessage": "Le taux de change utilisé pour l'affichage date de 5 jours. La valeur affichée peut différer de la valeur réelle. Effectuez un nouveau dépôt pour mettre à jour le taux.",
  "provider": "yellowcard"
}
```

---

## 🔄 Migration de base de données

### Script SQL pour ajouter les nouveaux champs

```sql
-- Ajouter les colonnes à la table wallet
ALTER TABLE wallet 
ADD COLUMN last_buy_rate DOUBLE,
ADD COLUMN last_buy_rate_updated_at TIMESTAMP,
ADD COLUMN last_provider VARCHAR(50);

-- Index pour améliorer les performances
CREATE INDEX idx_wallet_last_buy_rate_updated_at ON wallet(last_buy_rate_updated_at);
```

---

## ✅ Checklist d'implémentation

- [x] Ajouter les champs `lastBuyRate`, `lastBuyRateUpdatedAt`, `lastProvider` dans l'entité `Wallet` - **FAIT**
- [x] Créer la migration de base de données - **FAIT** (`V999__add_last_buy_rate_to_wallet.sql`)
- [x] Modifier `updateWalletAfterOnRamp` pour YellowCard pour sauvegarder le taux - **FAIT**
- [x] Modifier la création de transaction Guardarian pour sauvegarder le taux - **FAIT**
- [x] Modifier `getUserWalletBalance` pour utiliser le taux sauvegardé - **FAIT**
- [x] Implémenter le fallback vers Currency Freaks si aucun taux sauvegardé - **FAIT**
- [ ] Ajouter la détection de taux ancien (optionnel mais recommandé) - **À FAIRE** (selon besoin)
- [ ] Modifier `WalletBalanceDto` pour inclure les informations sur le taux - **À FAIRE** (selon besoin)
- [x] Ajouter des logs pour tracer les mises à jour de taux - **FAIT**
- [ ] Tests unitaires pour la sauvegarde et la récupération du taux - **À FAIRE**
- [ ] Tests d'intégration pour vérifier le comportement end-to-end - **À FAIRE**

---

## 📊 Monitoring et alertes

### Métriques à surveiller

1. **Pourcentage de wallets avec taux sauvegardé** : Doit être > 90%
2. **Âge moyen des taux sauvegardés** : Doit être < 7 jours
3. **Nombre de conversions avec fallback Currency Freaks** : Doit être < 5%

### Alertes recommandées

- ⚠️ **Alerte** : Si > 10% des wallets n'ont pas de taux sauvegardé
- ⚠️ **Alerte** : Si > 20% des taux sont anciens (> 7 jours)
- 🔴 **Alerte critique** : Si > 50% des conversions utilisent Currency Freaks en fallback

---

## 🎯 Alternatives pour gérer le risque d'écarts

### Option 1 : Mise à jour automatique du taux (RECOMMANDÉ - Simple et sécurisé)

**Principe :** Vérifier périodiquement si le taux a changé et le mettre à jour automatiquement.

**Avantages :**
- ✅ Simple à implémenter
- ✅ Sécurisé : toujours à jour
- ✅ Pas d'écarts pour l'utilisateur
- ✅ Pas besoin d'avertissements

**Implémentation :**

```java
@Override
public ResponseEntity<WalletBalanceDto> getUserWalletBalance(String username) {
    // ... récupération du solde USDC ...
    
    Wallet wallet = walletRepository.findByUsers(user);
    
    // Vérifier si le taux doit être mis à jour (tous les jours ou si > 24h)
    if (shouldUpdateBuyRate(wallet)) {
        updateBuyRateFromProvider(wallet, user);
    }
    
    // Utiliser le taux (maintenant à jour)
    double convertedAmount = userBalance * wallet.getLastBuyRate();
    
    // ... reste du code ...
}

private boolean shouldUpdateBuyRate(Wallet wallet) {
    if (wallet.getLastBuyRate() == null || wallet.getLastBuyRateUpdatedAt() == null) {
        return true; // Pas de taux sauvegardé, il faut le récupérer
    }
    
    // Mettre à jour si le taux a plus de 24h
    long hoursSinceUpdate = ChronoUnit.HOURS.between(
        wallet.getLastBuyRateUpdatedAt(), 
        LocalDateTime.now()
    );
    
    return hoursSinceUpdate >= 24;
}

private void updateBuyRateFromProvider(Wallet wallet, Users user) {
    String currency = wallet.getCurrency().getCurrencyCode();
    String countryCode = user.getCountryCode(); // À ajouter si nécessaire
    
    // Détecter le provider selon le pays
    String provider = detectProvider(countryCode);
    
    if ("yellowcard".equals(provider)) {
        // Récupérer le taux YellowCard
        String channelId = getFirstChannelIdForCountry(countryCode);
        var ratesResponse = yellowCardClientService.getRatesByChannelId(channelId, currency);
        if (ratesResponse.getStatusCode().is2xxSuccessful()) {
            BigDecimal buyRate = parseYellowCardBuyRateBigDecimal(ratesResponse.getBody(), currency);
            if (buyRate.compareTo(BigDecimal.ZERO) > 0) {
                wallet.setLastBuyRate(buyRate.doubleValue());
                wallet.setLastBuyRateUpdatedAt(LocalDateTime.now());
                wallet.setLastProvider("yellowcard");
                walletRepository.save(wallet);
                log.info("Taux d'achat mis à jour automatiquement pour wallet {} : {}", wallet.getId(), buyRate);
            }
        }
    } else if ("guardarian".equals(provider)) {
        // Récupérer le taux Guardarian
        var estimateRequest = new EstimateRequest(currency, "USDC", 1.0);
        var estimateResponse = guardarianClientService.getEstimate(estimateRequest);
        if (estimateResponse.getStatusCode().is2xxSuccessful() && estimateResponse.getBody() != null) {
            double exchangeRate = estimateResponse.getBody().rate();
            if (exchangeRate > 0) {
                wallet.setLastBuyRate(exchangeRate);
                wallet.setLastBuyRateUpdatedAt(LocalDateTime.now());
                wallet.setLastProvider("guardarian");
                walletRepository.save(wallet);
                log.info("Taux d'achat mis à jour automatiquement pour wallet {} : {}", wallet.getId(), exchangeRate);
            }
        }
    }
}
```

**Fréquence de mise à jour :**
- ✅ **Recommandé :** Tous les jours (24h)
- ✅ **Alternative :** À chaque consultation du solde si > 6h
- ⚠️ **Éviter :** Trop fréquent (risque de surcharge API)

### Option 2 : Affichage avec taux réel en temps réel (Plus complexe)

**Principe :** Toujours récupérer le taux actuel pour l'affichage, sans sauvegarder.

**Avantages :**
- ✅ Toujours précis
- ✅ Pas de risque d'écarts

**Inconvénients :**
- ❌ Plus d'appels API (coût et latence)
- ❌ Plus complexe à implémenter
- ❌ Dépendance aux APIs externes

### Option 3 : Avertissement utilisateur (Simple mais moins sécurisé)

**Principe :** Afficher un avertissement si le taux est ancien.

**Avantages :**
- ✅ Simple à implémenter
- ✅ Informe l'utilisateur

**Inconvénients :**
- ❌ L'utilisateur voit toujours une valeur incorrecte
- ❌ Expérience utilisateur dégradée
- ❌ Ne résout pas le problème, juste l'affiche

---

## ✅ Recommandation finale

**Option 1 : Mise à jour automatique du taux (tous les 24h)**

**Pourquoi cette option :**
1. ✅ **Simple** : Logique claire, facile à maintenir
2. ✅ **Sécurisé** : Taux toujours à jour (max 24h de décalage)
3. ✅ **Performant** : Pas d'appel API à chaque consultation
4. ✅ **Fiable** : Écarts minimaux (< 1% généralement sur 24h)
5. ✅ **Expérience utilisateur** : Pas d'avertissements, affichage fiable

**Compromis accepté :**
- Écart maximum possible : ~1-2% si le taux change fortement en 24h
- Acceptable pour un affichage de solde (pas une transaction)

---

## 🎯 Conclusion

L'utilisation du taux d'achat sauvegardé avec **mise à jour automatique tous les 24h** est la solution la plus équilibrée entre simplicité, sécurité et performance.

Cette approche garantit :
- ✅ Des écarts minimaux (< 2% généralement)
- ✅ Une implémentation simple
- ✅ Une bonne expérience utilisateur
- ✅ Un coût API maîtrisé

---

**Document créé le** : 2025-11-12  
**Version** : 1.1  
**Statut** : ✅ Implémenté selon la proposition de David

**Implémentation :**
- ✅ Champs ajoutés dans l'entité `Wallet`
- ✅ Migration SQL créée
- ✅ Sauvegarde du taux YellowCard lors du dépôt OnRamp
- ✅ Sauvegarde du taux Guardarian lors de la création de transaction
- ✅ Affichage du solde utilisant le taux sauvegardé
- ✅ Fallback vers Currency Freaks si aucun taux sauvegardé

