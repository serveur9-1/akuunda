```markdown
# Akuunda Wallet

Description
------------
Akuunda Wallet est un service backend Java (Maven) qui fournit la logique serveur pour un porte‑monnaie (wallet) : gestion des comptes utilisateurs, soldes, transactions, historiques et intégration avec des services de paiement externes (ex. génération de liens de paiement).

Ce dépôt contient l'application serveur (probablement Spring Boot) empaquetée avec Maven. Le projet expose des endpoints REST pour :
- gérer les comptes et soldes,
- initier/reconcilier des paiements,
- recevoir des webhooks de prestataires de paiement.

Prérequis
----------
- Git
- JDK 17+ (ou version ciblée par pom.xml)
- Maven 3.6+ (optionnel si on utilise le wrapper `./mvnw`)
- Docker (optionnel pour DB local)
- Un SGBD (ex. PostgreSQL) pour la base de données

Structure du dépôt
-------------------
- pom.xml — configuration Maven
- mvnw, mvnw.cmd — Maven wrapper
- src/main/java — code source Java (controllers, services, repositories)
- src/main/resources — configuration (application.yml/properties)
- README.md — ce fichier

Variables d'environnement (exemples)
------------------------------------
Les noms réels peuvent varier selon le code : vérifie `src/main/resources` et le code pour les propriétés exactes.

**Base de données :**
- SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/akuunda
- SPRING_DATASOURCE_USERNAME=akuunda
- SPRING_DATASOURCE_PASSWORD=changeme
- SPRING_JPA_HIBERNATE_DDL_AUTO=update

**Serveur :**
- SERVER_PORT=8080

**Sécurité (Keycloak) :**
- KEYCLOAK_ISSUER_URI=https://gestionsecu.akuunda-pay.io/realms/akuunda-realm
- JWT_SECRET=changeme (si authentication JWT utilisée)

**YellowCard :**
- YELLOWCARD_API_URL=https://api.yellowcard.io
- YELLOWCARD_API_KEY=...

**Guardarian :**
- GUARDIAN_API_URL=https://api.guardarian.com/v1
- GUARDIAN_API_KEY=...

**Currency Freaks :**
- CURRENCY_FREAKS_API_KEY=...

Installation et démarrage local (rapide)
----------------------------------------
1. Cloner le repo
   git clone https://github.com/David-akuundapay/akuunda-wallet.git
   cd akuunda-wallet

2. Préparer la base de données (ex. PostgreSQL via Docker)
   docker run --name akuunda-postgres -e POSTGRES_USER=akuunda -e POSTGRES_PASSWORD=changeme -e POSTGRES_DB=akuunda -p 5432:5432 -d postgres:15

3. Exporter les variables d'environnement (ou créer un fichier .env basé sur .env.example)
   export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/akuunda
   export SPRING_DATASOURCE_USERNAME=akuunda
   export SPRING_DATASOURCE_PASSWORD=changeme
   export SERVER_PORT=8080

4. Construire et lancer
   chmod +x mvnw
   ./mvnw clean package
   # ou lancer directement
   ./mvnw spring-boot:run
   # ou exécuter le jar
   java -jar target/*.jar

5. Vérifier
   - Health : http://localhost:8080/actuator/health (si actuator activé)
   - Swagger / OpenAPI : http://localhost:8080/swagger-ui.html (si présent)

Tests
-----
- Lancer la suite de tests : ./mvnw test

Points d'intégration (paiement externe)
---------------------------------------
Ce service intègre plusieurs prestataires de paiement :

**YellowCard (Afrique)**
- OnRamp : Dépôt via Mobile Money (Wave, Orange, MTN, Moov)
- OffRamp : Retrait vers Mobile Money
- Calcul des frais : 2% pour OnRamp, 3.5% pour OffRamp

**Guardarian (Europe/International)**
- OnRamp : Dépôt via carte bancaire ou virement
- Calcul des frais : Utilise l'API `/estimate` de Guardarian (généralement 0.5%)
- Fallback : Si l'API n'est pas disponible, utilise un taux fixe de 0.5%

**Flux recommandé :**
1. Frontend ou wallet backend demande la création d'un lien de paiement (via endpoint `/api/internal/v1/payment-links`)
2. L'utilisateur paie via le lien (YellowCard ou Guardarian selon le pays)
3. Le prestataire appelle le webhook configuré
4. À la confirmation, le wallet crédite le compte de l'utilisateur

**Calcul des frais :**
- Endpoint : `POST /api/internal/v1/fees/calculate`
- Détection automatique de l'opérateur selon le pays
- Documentation complète : `DOCUMENTATION_FLOW_CALCUL_FRAIS_ONRAMP.md`

Sécurité et bonnes pratiques
-----------------------------
- Ne stocke jamais de clés sensibles dans le repo. Utilise des variables d'environnement ou un secret manager.
- Active HTTPS en production.
- Vérifie et valide les webhooks (HMAC signature ou token).
- Ajoute des tests d'intégration pour les flows de paiement.

Dépannage
---------
- Erreur de connexion DB : vérifier SPRING_DATASOURCE_URL / utilisateur / mot de passe et que PostgreSQL écoute sur le port.
- Problème de build : vérifier la version de Java et le pom.xml.
- Endpoints manquants : regarder les controllers dans src/main/java pour connaître les routes exposées.

Contribution
------------
- Branche par défaut de développement : `dev`
- Créer une branche feature/bugfix, ouvrir PR vers `dev`.

Licence
-------
A renseigner.
```
"# akuunda" 
