# Documentation Complète : Système d'Emergency Code et Réinitialisation de PIN

## Table des matières

1. [Vue d'ensemble](#vue-densemble)
2. [Flow 1 : Définition du PIN](#flow-1--définition-du-pin)
3. [Flow 2 : Définition de l'Emergency Code](#flow-2--définition-de-lemergency-code)
4. [Flow 3 : Réinitialisation du PIN avec Emergency Code](#flow-3--réinitialisation-du-pin-avec-emergency-code)
5. [Architecture technique](#architecture-technique)
6. [Modèle de données](#modèle-de-données)
7. [Endpoints API](#endpoints-api)
8. [Aspects sécurité](#aspects-sécurité)
9. [Diagrammes de flux](#diagrammes-de-flux)

---

## Vue d'ensemble

Ce système permet aux utilisateurs de :
- **Créer un PIN** automatiquement lors de l'enregistrement (register) pour sécuriser leur wallet
- **Définir un Emergency Code** comme méthode de récupération de compte (optionnel lors du register, ou via endpoint dédié)
- **Réinitialiser un PIN oublié** en utilisant leur Emergency Code

Le système est basé sur **Venly** (service de wallet blockchain) qui gère les méthodes d'authentification (signing methods) pour chaque utilisateur.

### Principe fondamental

Les **Emergency Code** et **PIN** sont deux **signing methods** différents associés au même utilisateur Venly (`userId`). L'Emergency Code sert de **preuve d'identité** pour autoriser la modification du PIN lorsque celui-ci est oublié.

---

## Flow 1 : Création du PIN (Lors de l'enregistrement)

### Description

Le PIN est **automatiquement créé** lors de l'enregistrement (register) d'un utilisateur via l'endpoint `/users/{realmName}/users/particulier` ou `/users/enterprise`. Le PIN est stocké de manière sécurisée en base de données locale et créé comme signing method chez Venly lors de la création de l'utilisateur.

**Note :** L'endpoint `/pin/define` a été désactivé car le PIN est maintenant créé automatiquement lors de l'enregistrement.

### Processus détaillé

```
┌─────────────┐
│   Utilisateur│
└──────┬──────┘
       │ Fournit: données utilisateur + pinCode (+ partialEmergencyCode optionnel)
       ▼
┌─────────────────────────────────────────────────────────────┐
│                        Backend                                │
├─────────────────────────────────────────────────────────────┤
│ 1. Génère une string aléatoire de 24 caractères              │
│ 2. Hash cette string avec le PIN:                            │
│    encryptedString = hash(generated + ":" + pincode)        │
│ 3. Envoie à Venly:                                           │
│    POST /api/users/{userId}/signing-methods                  │
│    Body: {"type":"PIN", "value":"{pincode}"}                 │
└──────┬──────────────────────────────────────────────────────┘
       │
       ├───► Venly (création signing method PIN)
       │
       └───► Base de données locale
            Table: user_pins
            - userId
            - generatedString (24 caractères)
            - encryptedString (hash)
            - createdAt
```

### Étapes techniques

1. **Génération**
   - Génère 24 caractères aléatoires (`PinHashUtil.generateRandomString(24)`)
   - Crée un hash sécurisé avec le PIN (`PinHashUtil.hashGeneratedAndPin()`)

2. **Création chez Venly**
   - Crée un signing method de type `"PIN"` avec la valeur du PIN
   - Venly stocke le PIN de manière sécurisée

3. **Stockage local**
   - Si Venly accepte, sauvegarde en base :
     - `userId` : ID de l'utilisateur Venly
     - `generatedString` : Les 24 caractères générés
     - `encryptedString` : Hash sécurisé (PBKDF2 avec 120,000 itérations)
     - `createdAt` : Date de création

### Données stockées

| Champ | Type | Description |
|-------|------|-------------|
| `id` | UUID | Identifiant unique de l'enregistrement |
| `userId` | String | ID utilisateur Venly |
| `generatedString` | String | 24 caractères aléatoires générés |
| `encryptedString` | String | Hash PBKDF2 (format: `pbkdf2$120000$salt$hash`) |
| `createdAt` | OffsetDateTime | Date et heure de création |

---

## Flow 2 : Définition de l'Emergency Code

### Description

Permet à un utilisateur de définir un Emergency Code de récupération. Le système génère 20 caractères aléatoires, l'utilisateur fournit 5 caractères personnels, et le code complet (25 caractères) est envoyé à Venly.

**L'emergency code peut être créé de deux manières :**
1. **Lors de l'enregistrement (register)** : en fournissant le paramètre `partialEmergencyCode` (facultatif) lors de la création de l'utilisateur
2. **Via l'endpoint dédié** : `/emergency-code/define` (après la création de l'utilisateur)

Les deux méthodes utilisent exactement la même logique via le service `EmergencyCodeService`.

### Mesure de sécurité importante

⚠️ **Les 20 caractères générés sont stockés chiffrés en base, mais les 5 caractères du client ne sont JAMAIS stockés.** C'est une mesure de sécurité pour protéger le code d'urgence complet.

### Processus détaillé

```
┌─────────────┐
│   Utilisateur│
└──────┬──────┘
       │ Fournit: username + partialEmergencyCode (5 caractères) + pincode
       ▼
┌─────────────────────────────────────────────────────────────┐
│                        Backend                                │
├─────────────────────────────────────────────────────────────┤
│ 1. Génère 20 caractères aléatoires                           │
│ 2. Chiffre les 20 caractères (AES réversible)              │
│ 3. Construit le code complet:                               │
│    fullEmergencyCode = generated (20) + partial (5) = 25    │
│ 4. Envoie à Venly:                                           │
│    POST /api/users/{userId}/signing-methods                  │
│    Body: {"type":"EMERGENCY_CODE", "value":"{25 chars}"}   │
└──────┬──────────────────────────────────────────────────────┘
       │
       ├───► Venly (création signing method EMERGENCY_CODE)
       │
       └───► Base de données locale
            Table: user_emergency_codes
            - userId
            - encryptedString (20 caractères chiffrés AES)
            - createdAt
            ⚠️ Les 5 caractères du client NE SONT PAS stockés
```

### Étapes techniques

1. **Génération**
   - Génère 20 caractères aléatoires (`PinHashUtil.generateRandomString(20)`)
   - Chiffre ces 20 caractères avec AES (`EmergencyCodeEncryptionUtil.encrypt()`)
   - Utilise AES-256 en mode ECB avec PKCS5Padding

2. **Construction du code complet**
   - `fullEmergencyCode = generated (20) + partialEmergencyCode (5) = 25 caractères`

3. **Création chez Venly**
   - Crée un signing method de type `"EMERGENCY_CODE"` avec les 25 caractères
   - Venly stocke le code complet de manière sécurisée

4. **Stockage local**
   - Si Venly accepte, sauvegarde UNIQUEMENT :
     - `userId` : ID de l'utilisateur Venly
     - `encryptedString` : Les 20 caractères générés chiffrés (AES)
     - `createdAt` : Date de création
   - ⚠️ **Les 5 caractères du client ne sont JAMAIS stockés**

### Données stockées

| Champ | Type | Description |
|-------|------|-------------|
| `id` | UUID | Identifiant unique de l'enregistrement |
| `userId` | String | ID utilisateur Venly |
| `encryptedString` | String | 20 caractères générés chiffrés en Base64 (AES) |
| `createdAt` | OffsetDateTime | Date et heure de création |

### Exemple concret

```
Client fournit: "ABC12"
Système génère: "xK9mP2qR7sT4vW8yZ1n" (20 caractères)

Code complet envoyé à Venly: "xK9mP2qR7sT4vW8yZ1nABC12" (25 caractères)

Stocké en base (chiffré): "xK9mP2qR7sT4vW8yZ1n" (chiffré en AES)
⚠️ "ABC12" n'est JAMAIS stocké
```

---

## Flow 3 : Réinitialisation du PIN avec Emergency Code

### Description

Permet à un utilisateur ayant oublié son PIN de le réinitialiser en utilisant son Emergency Code comme preuve d'identité.

### Principe de sécurité

L'Emergency Code et le PIN sont deux **signing methods différents** mais associés au **même utilisateur** (`userId`) dans Venly. L'Emergency Code sert de **preuve d'identité** : si l'utilisateur peut fournir les 5 caractères corrects de son Emergency Code, cela prouve qu'il est le propriétaire du compte et peut donc modifier le PIN.

### Processus détaillé

```
┌─────────────┐
│   Utilisateur│ (PIN oublié, hors session)
└──────┬──────┘
       │ Fournit: username + emergencyCode (5 caractères) + newPincode
       ▼
┌─────────────────────────────────────────────────────────────┐
│                        Backend                                │
├─────────────────────────────────────────────────────────────┤
│ ÉTAPE 0: Récupération de l'utilisateur                      │
│ ─────────────────────────────────────────────────────────── │
│ 1. Récupère l'utilisateur depuis le username en base locale │
│ 2. Extrait le userId depuis l'entité Users                 │
│                                                              │
│ ÉTAPE 1: Vérification de l'emergency code                   │
│ ─────────────────────────────────────────────────────────── │
│ 1. Récupère les 20 caractères stockés (chiffrés)            │
│ 2. Déchiffre les 20 caractères                              │
│ 3. Reconstruit: generated (20) + provided (5) = 25         │
│ 4. Récupère le signing method EMERGENCY_CODE de Venly      │
│ 5. Vérifie chez Venly avec les 25 caractères complets       │
│    (utilise comme header Signing-Method)                    │
│                                                              │
│ ÉTAPE 2: Si vérification OK → Modification du PIN           │
│ ─────────────────────────────────────────────────────────── │
│ 1. Récupère tous les signing methods de l'utilisateur       │
│ 2. Trouve le signing method de type "PIN"                  │
│ 3. Génère nouvelles données pour le nouveau PIN            │
│ 4. Met à jour le PIN chez Venly:                            │
│    PUT /api/users/{userId}/signing-methods/{pinMethodId}    │
│ 5. Sauvegarde le nouveau PIN en base locale                │
└─────────────────────────────────────────────────────────────┘
```

### Étapes techniques détaillées

#### Étape 0 : Récupération de l'utilisateur

1. **Récupération depuis le username**
   ```java
   // Récupère l'utilisateur depuis le username (numéro de téléphone)
   Users user = userRepository.getUsersByUsername(username);
   if (user == null || user.getUserId() == null) {
       // Retourne une erreur 404 avec message JSON formaté
       return ResponseEntity.status(HttpStatus.NOT_FOUND)
           .body("{\"success\": false, \"error\": \"User not found\", ...}");
   }
   String userId = user.getUserId();
   ```

#### Étape 1 : Vérification de l'Emergency Code

1. **Récupération des données**
   ```java
   // Récupère l'enregistrement le plus récent
   UserEmergencyCode userEmergencyCode = 
       userEmergencyCodeRepository.findTopByUserIdOrderByCreatedAtDesc(userId);
   ```

2. **Déchiffrement**
   ```java
   // Déchiffre les 20 caractères générés
   String generated = EmergencyCodeEncryptionUtil.decrypt(
       userEmergencyCode.getEncryptedString()
   );
   ```

3. **Reconstruction du code complet**
   ```java
   // Reconstruit: 20 générés + 5 fournis = 25 caractères
   String fullEmergencyCode = generated + emergencyCode;
   ```

4. **Récupération du signing method Emergency Code**
   ```java
   // Récupère tous les signing methods de l'utilisateur
   List<ExternalSigningMethod> signingMethods = 
       getAllSigningMethods(userId);
   
   // Trouve celui de type EMERGENCY_CODE
   ExternalSigningMethod emergencyCodeMethod = 
       signingMethods.stream()
           .filter(m -> "EMERGENCY_CODE".equals(m.getType()))
           .findFirst();
   ```

5. **Vérification chez Venly**
   ```java
   // Utilise l'emergency code complet pour authentifier une requête
   String signingMethodHeader = signingMethodId + ":" + fullEmergencyCode;
   
   // Teste avec une requête GET vers l'utilisateur
   // Si Venly accepte (status 200) → Emergency code valide
   ```

#### Étape 2 : Modification du PIN

1. **Récupération du signing method PIN**
   ```java
   // Trouve le signing method de type PIN
   ExternalSigningMethod pinMethod = 
       signingMethods.stream()
           .filter(m -> "PIN".equals(m.getType()))
           .findFirst();
   ```

2. **Génération des nouvelles données**
   ```java
   // Génère nouvelles données pour le nouveau PIN
   String generated = PinHashUtil.generateRandomString(24);
   String encrypted = PinHashUtil.hashGeneratedAndPin(generated, newPincode);
   ```

3. **Mise à jour chez Venly**
   ```java
   // Met à jour le PIN
   PUT /api/users/{userId}/signing-methods/{pinMethodId}
   Body: {"type":"PIN", "value":"{newPincode}"}
   ```

4. **Sauvegarde locale**
   ```java
   // Sauvegarde le nouveau PIN en base
   UserPin entity = UserPin.builder()
       .userId(userId)
       .generatedString(generated)
       .encryptedString(encrypted)
       .createdAt(OffsetDateTime.now())
       .build();
   userPinRepository.save(entity);
   ```

### Diagramme de flux complet

```
┌──────────────────────────────────────────────────────────────┐
│                    Réinitialisation PIN                       │
└──────────────────────────────────────────────────────────────┘

User Request: {username: "002250759146858", emergencyCode: "ABC12", newPincode: "5678"}
       │
       ▼
┌─────────────────────────────────────────────────────────────┐
│ 0. GET USER FROM USERNAME                                    │
│ ───────────────────────────────────────────────────────────  │
│ Base de données locale                                       │
│   └─► Récupère Users depuis username                        │
│       └─► Extrait userId: "d92dde6d-..."                    │
└─────────────────────────────────────────────────────────────┘
       │
       ▼
┌─────────────────────────────────────────────────────────────┐
│ 1. VERIFY EMERGENCY CODE                                     │
│ ───────────────────────────────────────────────────────────  │
│ Base de données                                              │
│   └─► Récupère encryptedString (20 chars chiffrés)          │
│       └─► Déchiffre → "xK9mP2qR7sT4vW8yZ1n"                 │
│                                                            │
│ Reconstruction: "xK9mP2qR7sT4vW8yZ1n" + "ABC12"           │
│              = "xK9mP2qR7sT4vW8yZ1nABC12" (25 chars)      │
│                                                            │
│ Venly                                                       │
│   └─► GET /api/users/{userId}                              │
│       Header: Signing-Method: {id}:{25chars}               │
│       └─► Status 200 ✅ → Emergency code valide             │
└─────────────────────────────────────────────────────────────┘
       │
       ▼ (Si OK)
┌─────────────────────────────────────────────────────────────┐
│ 2. UPDATE PIN                                               │
│ ───────────────────────────────────────────────────────────  │
│ Venly                                                       │
│   └─► GET /api/users/{userId} (récupère signing methods)   │
│       └─► Trouve PIN method (type="PIN")                  │
│                                                            │
│ Génération nouvelles données                                │
│   └─► generated: "aB3cD5eF7gH9iJ1kL2mN" (24 chars)        │
│   └─► encrypted: hash(generated + ":" + "5678")           │
│                                                            │
│ Venly                                                       │
│   └─► PUT /api/users/{userId}/signing-methods/{pinId}      │
│       Body: {"type":"PIN", "value":"5678"}                │
│       └─► Status 200 ✅                                    │
│                                                            │
│ Base de données                                             │
│   └─► Sauvegarde nouveau PIN                               │
│       - userId                                             │
│       - generatedString: "aB3cD5eF7gH9iJ1kL2mN"           │
│       - encryptedString: hash...                           │
└─────────────────────────────────────────────────────────────┘
```

---

## Architecture technique

### Composants principaux

```
┌─────────────────────────────────────────────────────────────┐
│                     Couche Contrôleurs                        │
├─────────────────────────────────────────────────────────────┤
│ • PinController                                             │
│   - POST /api/internal/v1/pin/reset                         │
│     (utilise username au lieu de userId - hors session)     │
│                                                              │
│ • EmergencyCodeController                                    │
│   - POST /api/internal/v1/emergency-code/define             │
│     (utilise username au lieu de userId - hors session)     │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    Couche Services                            │
├─────────────────────────────────────────────────────────────┤
│ • PinService / PinServiceImpl                                │
│   - definePin()                                              │
│   - resetPinWithEmergencyCode(username, ...)                │
│     (utilise username, récupère userId en interne)         │
│                                                              │
│ • EmergencyCodeService / EmergencyCodeServiceImpl            │
│   - defineEmergencyCode(username, ...)                       │
│     (utilise username, récupère userId en interne)          │
│   - verifyEmergencyCode()                                    │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│              Couche Infrastructure (Venly)                    │
├─────────────────────────────────────────────────────────────┤
│ • AkunndaSigningMethodClientService                          │
│   - createUserPinSigningMethod()                             │
│   - createUserEmergencyCodeSigningMethod()                   │
│   - updatePinSigningMethod()                                 │
│   - getAllSigningMethods()                                   │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                    Couche Accès Données                       │
├─────────────────────────────────────────────────────────────┤
│ • UserPinRepository                                          │
│   - findTopByUserIdOrderByCreatedAtDesc()                    │
│                                                              │
│ • UserEmergencyCodeRepository                                │
│   - findTopByUserIdOrderByCreatedAtDesc()                    │
└─────────────────────────────────────────────────────────────┘
```

### Utilitaires de sécurité

1. **PinHashUtil**
   - `generateRandomString(int length)` : Génère des strings aléatoires
   - `hashGeneratedAndPin(String generated, String pin)` : Hash PBKDF2 avec 120,000 itérations

2. **EmergencyCodeEncryptionUtil**
   - `encrypt(String plainText)` : Chiffrement AES-256 réversible
   - `decrypt(String encryptedText)` : Déchiffrement AES-256
   - Utilise AES/ECB/PKCS5Padding

### Relations entre composants

```
PinController
    │
    ├─► PinService
    │       │
    │       ├─► EmergencyCodeService (pour vérifier l'identité)
    │       │       │
    │       │       └─► UserEmergencyCodeRepository
    │       │       └─► AkunndaSigningMethodClientService
    │       │
    │       ├─► UserPinRepository
    │       └─► AkunndaSigningMethodClientService
    │
    └─► EmergencyCodeController
            │
            └─► EmergencyCodeService
                    │
                    ├─► UserEmergencyCodeRepository
                    └─► AkunndaSigningMethodClientService
```

---

## Modèle de données

### Table : `user_pins`

Stoque les informations du PIN pour chaque utilisateur.

**Important :** Lors d'une modification du PIN, l'enregistrement existant est **mis à jour** (pas de création d'un nouvel enregistrement). Cela garantit qu'il n'y a qu'un seul PIN actif par utilisateur dans la base de données.

| Colonne | Type | Description | Contraintes |
|---------|------|-------------|-------------|
| `id` | UUID | Identifiant unique | PRIMARY KEY, AUTO GENERATED |
| `user_id` | VARCHAR | ID utilisateur Venly | NOT NULL, INDEX |
| `generated_string` | VARCHAR | 24 caractères aléatoires générés | NOT NULL |
| `encrypted_string` | VARCHAR | Hash PBKDF2 du PIN | NOT NULL |
| `created_at` | TIMESTAMP | Date de création | NOT NULL |

**Format `encrypted_string` :**
```
pbkdf2$120000${base64_salt}${base64_hash}
```

**Exemple :**
```
pbkdf2$120000$dGVzdFNhbHQ=$YWJjZGVmZ2hpams...
```

### Table : `user_emergency_codes`

Stoque uniquement les 20 caractères générés (chiffrés) pour l'emergency code.

| Colonne | Type | Description | Contraintes |
|---------|------|-------------|-------------|
| `id` | UUID | Identifiant unique | PRIMARY KEY, AUTO GENERATED |
| `user_id` | VARCHAR | ID utilisateur Venly | NOT NULL, INDEX |
| `encrypted_string` | VARCHAR | 20 caractères générés chiffrés (AES) | NOT NULL |
| `created_at` | TIMESTAMP | Date de création | NOT NULL |

**Format `encrypted_string` :**
```
Base64(AES-256-encrypt(generated_20_chars))
```

**Exemple :**
```
xK9mP2qR7sT4vW8yZ1n (chiffré en Base64)
```

⚠️ **Important :** Les 5 caractères du client ne sont JAMAIS stockés dans cette table.

### Schéma relationnel

```
┌─────────────────────┐
│   Venly Users       │
│  (External)          │
│                     │
│  userId: String     │
└──────────┬──────────┘
           │
           │ 1:N
           │
    ┌──────┴──────────────────────┐
    │                               │
    ▼                               ▼
┌──────────────┐          ┌─────────────────────┐
│  user_pins   │          │user_emergency_codes  │
├──────────────┤          ├─────────────────────┤
│ id           │          │ id                   │
│ user_id ─────┼──────────┤ user_id              │
│ generated_*  │          │ encrypted_string     │
│ encrypted_*  │          │ created_at           │
│ created_at   │          └─────────────────────┘
└──────────────┘
```

---

## Endpoints API

### Base URL

```
http://localhost:8089/api/internal/v1
```

### Format des réponses

Toutes les réponses sont maintenant au format JSON structuré avec les champs suivants :
- `success` : boolean indiquant si l'opération a réussi
- `message` : message descriptif
- `username` : nom d'utilisateur (numéro de téléphone)
- `userId` : ID utilisateur Venly (si disponible)
- `error` : type d'erreur (en cas d'échec)

### 1. Créer un utilisateur avec PIN (Register)

**Endpoint :** `POST /users/{realmName}/users/particulier` (ou `/users/enterprise`)

**Description :** Crée un nouvel utilisateur. Le PIN est automatiquement créé chez Venly et stocké en base de données locale lors de la création. L'emergency code est optionnel et peut être créé en même temps si fourni.

**Headers :**
```
Content-Type: application/json
```

**Body :**
```json
{
  "username": "string",
  "firstName": "string",
  "lastName": "string",
  "countryCode": "string",
  "mobilePhone": "string",
  "pinCode": "string (code PIN, 6 caractères)",
  "email": "string (optionnel)",
  "partialEmergencyCode": "string (optionnel, 5 caractères. Si null/vide, aucun emergency code n'est créé)"
}
```

**Exemple avec emergency code :**
```json
{
  "username": "002250759146810",
  "firstName": "Franck",
  "lastName": "Franck12",
  "countryCode": "CI",
  "mobilePhone": "002250777832982",
  "pinCode": "123456",
  "partialEmergencyCode": "ABC12"
}
```

**Exemple sans emergency code :**
```json
{
  "username": "002250759146810",
  "firstName": "Franck",
  "lastName": "Franck12",
  "countryCode": "CI",
  "mobilePhone": "002250777832982",
  "pinCode": "123456",
  "partialEmergencyCode": ""
}
```

**Réponse succès (200) :**
```json
{
  "success": true,
  "message": "User created successfully",
  "user": {
    "userId": "d92dde6d-fe6c-44ef-85d9-9d86ac73505d",
    "username": "002250759146861",
    "firstName": "kouame",
    "lastName": "suzanne"
  },
  "pin": {
    "created": true,
    "status": "PIN created "
  },
  "emergencyCode": {
    "created": true,
    "message": "Emergency code created successfully"
  }
}
```

**Note :** Si l'emergency code n'a pas été créé (par exemple si `partialEmergencyCode` est vide), la réponse sera :
```json
{
  "success": true,
  "message": "User created successfully",
  "user": {
    "userId": "d92dde6d-fe6c-44ef-85d9-9d86ac73505d",
    "username": "002250759146861",
    "firstName": "kouame",
    "lastName": "suzanne"
  },
  "pin": {
    "created": true,
    "status": "PIN created "
  },
  "emergencyCode": {
    "created": false,
    "message": "No emergency code provided"
  }
}
```

**Note importante :**
- Le PIN est **obligatoire** et est créé automatiquement chez Venly et stocké en base lors de la création de l'utilisateur
- L'emergency code est **facultatif** :
  - Si `partialEmergencyCode` est fourni et non vide, un emergency code sera créé avec la même logique que l'endpoint `/emergency-code/define`
  - Si `partialEmergencyCode` est `null`, vide (`""`), ou omis, aucun emergency code ne sera créé (l'utilisateur pourra le créer plus tard via l'endpoint dédié)

---

### ⚠️ Définition du PIN (Endpoint désactivé)

**Endpoint :** `POST /pin/define` ❌ **DÉSACTIVÉ**

**Status :** Cet endpoint a été désactivé car le PIN est maintenant créé automatiquement lors de l'enregistrement (register) de l'utilisateur.

**Alternative :** Utiliser l'endpoint de création d'utilisateur (`POST /users/{realmName}/users/particulier` ou `/users/enterprise`) qui crée automatiquement le PIN.

---

### 2. Définir un Emergency Code

**Endpoint :** `POST /api/internal/v1/emergency-code/define`

**Description :** Crée un emergency code pour un utilisateur. Le système génère 20 caractères et les concatène avec les 5 caractères fournis par le client (total 25). Un PIN doit avoir été créé au préalable car il est nécessaire pour authentifier la création de l'emergency code chez Venly.

**Note :** L'emergency code peut également être créé lors de l'enregistrement de l'utilisateur en fournissant le paramètre `partialEmergencyCode` (voir section 1).

**Important :** Cet endpoint utilise le **username** (numéro de téléphone) au lieu du `userId` car il peut être utilisé hors session. Le système récupère automatiquement le `userId` depuis le username en base de données locale.

**Headers :**
```
Content-Type: application/json
```

**Body :**
```json
{
  "username": "string (numéro de téléphone de l'utilisateur)",
  "partialEmergencyCode": "string (5 caractères choisis par le client)",
  "pincode": "string (code PIN de l'utilisateur, nécessaire pour authentifier la création chez Venly)"
}
```

**Exemple :**
```json
{
  "username": "002250759146858",
  "partialEmergencyCode": "BCD25",
  "pincode": "123456"
}
```

**Réponse succès (200) :**
```json
{
  "success": true,
  "message": "Emergency code created successfully",
  "username": "002250759146858",
  "userId": "d92dde6d-fe6c-44ef-85d9-9d86ac73505d"
}
```

**Réponse erreur (400) - Requête invalide :**
```json
{
  "success": false,
  "error": "Invalid PIN",
  "message": "PIN code cannot be null or empty",
  "username": "002250759146858"
}
```

**Réponse erreur (404) - Utilisateur non trouvé :**
```json
{
  "success": false,
  "error": "User not found",
  "message": "User with username '002250759146858' not found in database",
  "username": "002250759146858"
}
```

**Réponse erreur (500) - Erreur interne :**
```json
{
  "success": false,
  "error": "Internal error",
  "message": "Error defining emergency code: ...",
  "username": "002250759146858"
}
```

---

### 3. Réinitialiser le PIN avec Emergency Code

**Endpoint :** `POST /api/internal/v1/pin/reset`

**Description :** Réinitialise le PIN d'un utilisateur en utilisant son Emergency Code comme preuve d'identité.

**Important :** Cet endpoint utilise le **username** (numéro de téléphone) au lieu du `userId` car il est utilisé **hors session** (l'utilisateur n'est pas connecté et n'a donc pas accès à son `userId`). Le système récupère automatiquement le `userId` depuis le username en base de données locale.

**Headers :**
```
Content-Type: application/json
```

**Body :**
```json
{
  "username": "string (numéro de téléphone de l'utilisateur)",
  "emergencyCode": "string (5 caractères que l'utilisateur a mémorisés)",
  "newPincode": "string (nouveau code PIN, 6 caractères requis par Venly)"
}
```

**Exemple :**
```json
{
  "username": "002250759146858",
  "emergencyCode": "BCD25",
  "newPincode": "456789"
}
```

**Réponse succès (200) :**
```json
{
  "success": true,
  "message": "PIN reset successfully",
  "username": "002250759146858",
  "userId": "d92dde6d-fe6c-44ef-85d9-9d86ac73505d"
}
```

**Réponse erreur (401) - Emergency Code invalide :**
```json
{
  "success": false,
  "error": "Venly error",
  "message": "Emergency code verification failed",
  "username": "002250759146858",
  "userId": "d92dde6d-fe6c-44ef-85d9-9d86ac73505d"
}
```

**Réponse erreur (404) - Emergency Code non trouvé :**
```json
{
  "success": false,
  "error": "No emergency code found",
  "message": "No emergency code found for user. Please create an emergency code first via /api/internal/v1/emergency-code/define or during registration.",
  "username": "002250759146858",
  "userId": "d92dde6d-fe6c-44ef-85d9-9d86ac73505d"
}
```

**Réponse erreur (404) - Utilisateur non trouvé :**
```json
{
  "success": false,
  "error": "User not found",
  "message": "User with username '002250759146858' not found in database",
  "username": "002250759146858"
}
```

**Réponse erreur (500) - Erreur interne :**
```json
{
  "success": false,
  "error": "Internal error",
  "message": "Error resetting PIN: ...",
  "username": "002250759146858"
}
```

---

## Aspects sécurité

### 1. Stockage des données

#### PIN
- ✅ **generatedString** : Stocké en clair (nécessaire pour vérification)
- ✅ **encryptedString** : Hash PBKDF2 avec 120,000 itérations
- ✅ **PIN lui-même** : Stocké uniquement chez Venly, jamais en base locale

#### Emergency Code
- ✅ **20 caractères générés** : Stockés chiffrés (AES-256)
- ✅ **5 caractères du client** : **JAMAIS stockés** (mesure de sécurité)
- ✅ **Code complet (25 chars)** : Stocké uniquement chez Venly

### 2. Chiffrement

#### PIN - PBKDF2
```
Algorithme: PBKDF2WithHmacSHA256
Itérations: 120,000
Longueur clé: 256 bits
Salt: 16 bytes aléatoires
Format: pbkdf2$120000${salt}${hash}
```

#### Emergency Code - AES
```
Algorithme: AES
Mode: ECB
Padding: PKCS5Padding
Taille clé: 256 bits
Format sortie: Base64
```

### 3. Mesures de sécurité

| Aspect | Mesure |
|--------|--------|
| **Stockage PIN** | Hash PBKDF2 avec 120,000 itérations |
| **Stockage Emergency Code** | Chiffrement AES-256 réversible |
| **Données sensibles client** | Les 5 caractères Emergency Code ne sont jamais stockés |
| **Vérification** | Toujours effectuée auprès de Venly |
| **Transport** | HTTPS requis (via Venly API) |
| **Clés de chiffrement** | Devraient être dans `application.properties` en production |

### 4. Recommandations production

1. **Clé AES pour Emergency Code**
   ```properties
   # À ajouter dans application.properties
   emergency.code.encryption.key=your-secure-256-bit-key-here
   ```

2. **Rotation des clés**
   - Implémenter une rotation périodique des clés de chiffrement

3. **Audit**
   - Logger toutes les tentatives de réinitialisation de PIN
   - Logger les échecs de vérification d'emergency code

4. **Rate Limiting**
   - Limiter le nombre de tentatives de réinitialisation par utilisateur
   - Bloquer après X échecs consécutifs

---

## Diagrammes de flux

### Flow complet : De la création à la réinitialisation

```
┌──────────────────────────────────────────────────────────────┐
│                    FLOW COMPLET                              │
└──────────────────────────────────────────────────────────────┘

┌──────────┐
│  USER    │
└────┬─────┘
     │
     ▼
┌──────────────────────────────────────────┐
│ 1. REGISTER USER                         │
│    POST /users/{realm}/users/particulier │
│                                          │
│    Fournit:                              │
│    - pinCode (obligatoire)              │
│    - partialEmergencyCode (optionnel)   │
└──────┬───────────────────────────────────┘
       │
       ├───► Crée utilisateur chez Venly
       ├───► Crée PIN automatiquement
       ├───► Stocke PIN en base locale
       └───► Crée Emergency Code si fourni
       │
       ├──────────────────────────────────────┐
       │ (Alternative si pas fait lors register)│
       │                                      │
       ▼                                      ▼
┌──────────────────┐              ┌──────────────────────┐
│ 2. CREATE PIN    │              │ 2. CREATE EMERGENCY   │
│ (si nécessaire)  │              │     CODE             │
│                  │              │   (si nécessaire)     │
│ Input:           │              │                      │
│ - userId         │              │ Input:               │
│ - pincode        │              │ - userId             │
│                  │              │ - partialCode (5)    │
│ Output:          │              │                      │
│ - PIN créé       │              │ Output:              │
│ - Saved to DB    │              │ - Emergency créé    │
│ - Saved to Venly │              │ - 20 chars saved    │
└──────────────────┘              │ - 25 chars to Venly │
     │                            └──────────────────────┘
     │                                      │
     │                                      │
     └──────────────┬───────────────────────┘
                    │
                    │ (User oublie son PIN)
                    │
                    ▼
         ┌──────────────────────┐
         │ 3. RESET PIN         │
         │    WITH EMERGENCY    │
         │                      │
         │ Input:               │
         │ - userId             │
         │ - emergencyCode (5)  │
         │ - newPincode         │
         │                      │
         │ Process:             │
         │ 1. Verify emergency  │
         │ 2. Update PIN        │
         │                      │
         │ Output:              │
         │ - PIN mis à jour     │
         └──────────────────────┘
```

### Séquence temporelle

```
Utilisateur          Backend              Base Données         Venly
    │                   │                     │                 │
    │──define PIN──────>│                     │                 │
    │                   │─────────────────────┼────────────────>│
    │                   │<────────────────────┼─────────────────│
    │                   │────────────────────>│                 │
    │<──PIN créé────────│                     │                 │
    │                   │                     │                 │
    │──define EC────────>│                     │                 │
    │                   │─────────────────────┼────────────────>│
    │                   │<────────────────────┼─────────────────│
    │                   │────────────────────>│                 │
    │<──EC créé─────────│                     │                 │
    │                   │                     │                 │
    │ (Oublie PIN)      │                     │                 │
    │                   │                     │                 │
    │──reset PIN───────>│                     │                 │
    │                   │────────────────────>│                 │
    │                   │<────────────────────│                 │
    │                   │─────────────────────┼────────────────>│
    │                   │<────────────────────┼─────────────────│
    │                   │─────────────────────┼────────────────>│
    │                   │<────────────────────┼─────────────────│
    │                   │────────────────────>│                 │
    │<──PIN mis à jour──│                     │                 │
```

---

## FAQ (Foire aux questions)

### Q1 : Pourquoi stocker les 20 caractères mais pas les 5 du client ?

**R :** C'est une mesure de sécurité. Si la base de données est compromise, l'attaquant n'a pas accès au code complet. Il lui manque toujours les 5 caractères que seul l'utilisateur connaît.

### Q2 : Comment fonctionne la vérification chez Venly ?

**R :** On utilise l'emergency code complet (25 caractères) comme header `Signing-Method` dans une requête vers Venly. Si Venly accepte la requête (status 200), cela signifie que l'emergency code est correct pour cet utilisateur.

### Q3 : Pourquoi utiliser `username` au lieu de `userId` pour les endpoints de reset et define emergency code ?

**R :** Ces endpoints sont utilisés **hors session** (l'utilisateur n'est pas connecté). L'utilisateur n'a donc pas accès à son `userId` Venly, mais il connaît son numéro de téléphone (username). Le système récupère automatiquement le `userId` depuis le username en base de données locale.

### Q4 : Que se passe-t-il si l'utilisateur oublie aussi son Emergency Code ?

**R :** Dans ce cas, il faudra implémenter un autre mécanisme de récupération (par exemple, vérification d'identité via support client, email de récupération, etc.). Ce système n'est pas implémenté dans la version actuelle.

### Q5 : Peut-on avoir plusieurs Emergency Codes ?

**R :** Actuellement, le système stocke le dernier Emergency Code créé (via `findTopByUserIdOrderByCreatedAtDesc`). Il faudrait modifier le code pour supporter plusieurs emergency codes actifs.

### Q6 : Quelle est la différence entre PIN et Emergency Code dans Venly ?

**R :** Ce sont deux **signing methods** différents avec des types différents :
- PIN : Type `"PIN"` - utilisé pour signer des transactions normales
- Emergency Code : Type `"EMERGENCY_CODE"` - utilisé uniquement pour la récupération de compte

### Q7 : Pourquoi utiliser AES réversible pour l'Emergency Code mais PBKDF2 pour le PIN ?

**R :** 
- **PIN** : On n'a jamais besoin de récupérer le PIN original, donc un hash (PBKDF2) suffit
- **Emergency Code** : On doit pouvoir récupérer les 20 caractères générés pour reconstruire le code complet, donc on utilise un chiffrement réversible (AES)

---

## Conclusion

Ce système offre une solution sécurisée pour :
- ✅ Gérer les PINs utilisateur
- ✅ Fournir un mécanisme de récupération via Emergency Code
- ✅ Permettre la réinitialisation du PIN en cas d'oubli

Les points forts :
- **Sécurité** : Les données sensibles sont correctement protégées
- **Flexibilité** : Utilise Venly comme source de vérité
- **Architecture** : Code modulaire et maintenable

---

---

## Modifications récentes (Version 2.0)

### Changements majeurs

1. **Utilisation de `username` au lieu de `userId` pour les endpoints hors session**
   - `/api/internal/v1/pin/reset` : utilise maintenant `username` (numéro de téléphone)
   - `/api/internal/v1/emergency-code/define` : utilise maintenant `username` (numéro de téléphone)
   - Le système récupère automatiquement le `userId` depuis le username en base de données locale

2. **Réponses JSON formatées**
   - Toutes les réponses sont maintenant au format JSON structuré
   - Format standard : `{"success": boolean, "message": string, "username": string, "userId": string, ...}`
   - Messages d'erreur plus clairs et détaillés

3. **Mécanisme de retry pour les signing methods**
   - Ajout d'un mécanisme de retry avec délai progressif (1s, 1.5s, 2s) pour récupérer les signing methods depuis Venly
   - Gestion des délais de propagation de l'API Venly

4. **Amélioration des logs**
   - Logs plus détaillés avec username et userId pour la traçabilité
   - Logs de debug pour le diagnostic

5. **Réponse structurée lors de la création d'utilisateur**
   - La réponse inclut maintenant le statut de création du PIN et de l'emergency code
   - Format JSON avec détails de l'utilisateur créé

6. **Validation renforcée**
   - Vérifications explicites des champs obligatoires
   - Messages d'erreur JSON formatés pour tous les cas d'erreur

### Migration depuis la version 1.0

Si vous utilisez les anciens endpoints avec `userId`, vous devez migrer vers l'utilisation de `username` :

**Avant (v1.0) :**
```json
{
  "userId": "12345678-1234-1234-1234-123456789012",
  "emergencyCode": "ABC12",
  "newPincode": "567890"
}
```

**Après (v2.0) :**
```json
{
  "username": "002250759146858",
  "emergencyCode": "ABC12",
  "newPincode": "567890"
}
```

---

**Document généré le :** [Date actuelle]  
**Version :** 2.0  
**Auteur :** Équipe de développement Akuunda

