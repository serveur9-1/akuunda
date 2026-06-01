# Akuunda Pay — Intégration API

Trois endpoints suffisent pour accepter un paiement en ligne.

Base URL : `https://wallet.akuunda-pay.io`

---

## 1. Récupérer une clé API (dashboard, une seule fois)

Connecte-toi au dashboard, puis :

```bash
curl -X POST https://wallet.akuunda-pay.io/api/v1/merchant/keys \
  -H "Authorization: Bearer <JWT du dashboard>" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Boutique principale",
    "mode": "live",
    "webhookUrl": "https://boutique.com/api/webhooks/akuunda",
    "returnUrl":  "https://boutique.com/success",
    "cancelUrl":  "https://boutique.com/cancel"
  }'
```

Réponse 201 :

```json
{
  "id": 12,
  "name": "Boutique principale",
  "mode": "live",
  "apiKey": "sk_live_a1b2c3...",
  "webhookSecret": "whsec_a1b2c3...",
  "webhookUrl": "...",
  "returnUrl": "...",
  "cancelUrl": "...",
  "createdAt": "2026-05-13T08:30:00"
}
```

> **Sandbox** : passe `"mode": "test"` pour obtenir une clé `sk_test_…`. Les paiements créés avec une clé test peuvent être complétés manuellement via `POST /api/v1/payments/{id}/_test/complete` (voir §5).

> **Important** : `webhookSecret` n'est affiché qu'à la création. Garde-le bien — il sert à vérifier la signature `X-Akuunda-Signature` des webhooks.

Autres routes utiles :

```bash
GET    /api/v1/merchant/keys           # lister
DELETE /api/v1/merchant/keys/{id}      # révoquer
```

---

## 2. Créer un paiement

```bash
curl -X POST https://wallet.akuunda-pay.io/api/v1/payments \
  -H "Authorization: Bearer sk_live_a1b2c3..." \
  -H "Content-Type: application/json" \
  -d '{
    "amount": 15000,
    "currency": "XOF",
    "reference": "CMD-123",
    "description": "Commande #123 - 3 articles",
    "metadata": { "orderId": "123" }
  }'
```

Réponse 201 :

```json
{
  "id": "pay_K8x2Lm9aBq7vNcRz",
  "url": "https://qr.akuunda-pay.io/checkout/pay_K8x2Lm9aBq7vNcRz",
  "status": "pending",
  "amount": 15000,
  "currency": "XOF",
  "reference": "CMD-123",
  "description": "Commande #123 - 3 articles",
  "metadata": { "orderId": "123" },
  "expiresAt": "2026-05-13T09:30:00",
  "createdAt": "2026-05-13T08:30:00"
}
```

Redirige l'utilisateur vers `url`. À la fin (succès ou annulation), le navigateur revient vers `returnUrl` / `cancelUrl`.

> Le header `X-API-Key: sk_live_…` est aussi accepté pour compatibilité, mais `Authorization: Bearer …` est recommandé.

### Idempotence

Ajoute `Idempotency-Key: <uuid>` pour rendre `POST /payments` rejouable sans risque (réseau coupé, retry client, etc.).
Le **même couple `(clé API, Idempotency-Key)`** retourne **toujours le même paiement** au lieu d'en créer un nouveau.

```bash
curl -X POST https://wallet.akuunda-pay.io/api/v1/payments \
  -H "Authorization: Bearer sk_live_…" \
  -H "Idempotency-Key: 7b3d5f4e-…-9e0a" \
  -H "Content-Type: application/json" \
  -d '{ "amount": 15000, "currency": "XOF", "reference": "CMD-123" }'
```

### Champs du corps

| Champ | Obligatoire | Description |
|---|---|---|
| `amount` | ✓ | Montant numérique |
| `currency` | ✓ | ISO 4217 (XOF, EUR, USD…) |
| `reference` | ✓ | Identifiant unique côté marchand |
| `description` | | Texte court affiché au client |
| `returnUrl` | | Surcharge l'URL de succès enregistrée sur la clé |
| `cancelUrl` | | Surcharge l'URL d'annulation |
| `webhookUrl` | | Surcharge l'URL webhook |
| `metadata` | | Paires clé/valeur, restituées intactes dans le webhook |

---

## 3. Lire le statut d'un paiement

```bash
curl https://wallet.akuunda-pay.io/api/v1/payments/pay_K8x2Lm9aBq7vNcRz \
  -H "Authorization: Bearer sk_live_a1b2c3..."
```

`status` possibles : `pending`, `paid`, `expired`, `cancelled`, `failed`, `refunded`.

---

## 4. Rembourser un paiement

```bash
# Remboursement total
curl -X POST https://wallet.akuunda-pay.io/api/v1/payments/pay_K8x2Lm9aBq7vNcRz/refunds \
  -H "Authorization: Bearer sk_live_..."

# Remboursement partiel
curl -X POST https://wallet.akuunda-pay.io/api/v1/payments/pay_K8x2Lm9aBq7vNcRz/refunds \
  -H "Authorization: Bearer sk_live_..." \
  -H "Content-Type: application/json" \
  -d '{ "amount": 5000, "reason": "Article retourné" }'
```

Réponse 200 : le paiement avec `status` = `refunded` (si total) ou toujours `paid` (si partiel), enrichi de `refundedAmount` et `refundedAt`. Un webhook `payment.refunded` est aussi envoyé.

---

## 5. Sandbox / mode test

Crée une clé en `"mode": "test"` (préfixe `sk_test_…`). Les paiements créés avec cette clé sont **isolés** de la production. Pour simuler un paiement réussi :

```bash
curl -X POST https://wallet.akuunda-pay.io/api/v1/payments/pay_T3st123…/_test/complete \
  -H "Authorization: Bearer sk_test_..."
```

Le paiement passe à `paid` et déclenche le webhook `payment.completed` exactement comme en production. Idéal pour tester ton intégration sans toucher de vrais flux.

> ⚠️ Cet endpoint est rejeté avec `403` si la clé n'est pas une clé de test.

---

## 6. Webhook (recommandé)

Quand un paiement passe à `paid`, Akuunda envoie un POST à ton `webhookUrl` :

```http
POST /api/webhooks/akuunda HTTP/1.1
Content-Type: application/json
X-Akuunda-Signature: 9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08

{
  "event": "payment.completed",
  "data": {
    "checkoutCode": "pay_K8x2Lm9aBq7vNcRz",
    "reference": "CMD-123",
    "amount": 15000,
    "currency": "XOF",
    "status": "paid",
    "mode": "live",
    "paidAt": "2026-05-13T08:35:00",
    "metadata": { "orderId": "123" }
  },
  "timestamp": "2026-05-13T08:35:01Z"
}
```

Événements possibles : `payment.completed`, `payment.refunded`.

### Vérifier la signature (Node.js)

```js
const crypto = require('crypto');
const payload = req.rawBody; // string brut, AVANT json parse
const expected = crypto
  .createHmac('sha256', process.env.AKUUNDA_WEBHOOK_SECRET) // whsec_…
  .update(payload)
  .digest('hex');
if (expected !== req.header('x-akuunda-signature')) {
  return res.status(401).end();
}
```

### Vérifier la signature (PHP)

```php
$payload  = file_get_contents('php://input');
$expected = hash_hmac('sha256', $payload, getenv('AKUUNDA_WEBHOOK_SECRET'));
if (!hash_equals($expected, $_SERVER['HTTP_X_AKUUNDA_SIGNATURE'] ?? '')) {
  http_response_code(401); exit;
}
```

---

## Récap intégration en 30 secondes

1. Crée une clé sur le dashboard → garde `apiKey` + `webhookSecret`.
2. À chaque commande : `POST /api/v1/payments` → redirige le client vers `url`.
3. À réception du webhook `payment.completed` → vérifie la signature et marque la commande payée.

C'est tout.
