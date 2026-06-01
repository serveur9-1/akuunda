# Akuunda Pay — Guide d'intégration Checkout

## Vue d'ensemble

L'intégration Akuunda Pay repose sur **3 étapes** :

1. Créer une session de paiement (serveur → API)
2. Rediriger l'acheteur sur la page hébergée
3. Recevoir la confirmation (webhook ou redirect)

```
Serveur marchand          Akuunda Pay              Acheteur
       │                       │                       │
       │  POST /api/v1/payments│                       │
       │──────────────────────►│                       │
       │◄──────────────────────│                       │
       │  { url, id, status }  │                       │
       │                       │                       │
       │  redirect(url) ───────────────────────────────►│
       │                       │  (paiement MoMo/MELD) │
       │◄──────────────────────────────── webhook POST  │
       │  { event, data }      │                       │
```

---

## Environnements

| Environnement | Base URL API                                   | Page paiement                        |
|---------------|------------------------------------------------|--------------------------------------|
| Production    | `https://wallet.akuunda-pay.io`                | `https://qr.akuunda-pay.io`          |
| Sandbox       | `https://walletdev.akuunda-pay.io`             | `https://qr.akuunda-pay.io`          |

> Les clés `sk_test_…` n'acceptent les appels que sur le serveur sandbox.  
> Les clés `sk_live_…` n'acceptent les appels que sur le serveur production.

---

## Authentification

Deux formats acceptés — préférer `Authorization: Bearer` :

```http
Authorization: Bearer sk_live_xxxxxxxxxxxxxxxx
```

```http
X-API-Key: sk_live_xxxxxxxxxxxxxxxx
```

Les clés API sont générées depuis le tableau de bord Akuunda Pay.

---

## 1. Créer un paiement

### Requête

```http
POST /api/v1/payments
Content-Type: application/json
Authorization: Bearer sk_live_xxx
Idempotency-Key: cmd-123-attempt-1   (optionnel)
```

```json
{
  "amount": 15000,
  "currency": "XOF",
  "reference": "CMD-123",
  "description": "Commande #123 — 2 articles",
  "paymentCountry": "CI",
  "returnUrl": "https://boutique.ci/commandes/123/merci",
  "cancelUrl": "https://boutique.ci/panier",
  "webhookUrl": "https://boutique.ci/api/webhooks/akuunda",
  "metadata": {
    "orderId": "123",
    "customerId": "456"
  }
}
```

| Champ            | Type     | Requis | Description                                                                 |
|------------------|----------|--------|-----------------------------------------------------------------------------|
| `amount`         | number   | Oui    | Montant en unité de la devise (pas de centimes)                             |
| `currency`       | string   | Oui    | Code ISO 4217 : `XOF`, `XAF`, `GHS`, `NGN`, `EUR`, `USD`…                  |
| `reference`      | string   | Oui    | Référence unique côté marchand (numéro de commande)                         |
| `description`    | string   | Non    | Affiché sur la page de paiement                                             |
| `paymentCountry` | string   | Non    | Code ISO 3166-1 alpha-2 (`CI`, `SN`, `CM`…). Si fourni, pas de sélecteur de pays affiché. Idéal pour les boutiques et billetteries à marché local. |
| `returnUrl`      | string   | Non    | URL de redirection après paiement réussi (override la valeur de la clé API) |
| `cancelUrl`      | string   | Non    | URL si l'acheteur annule                                                    |
| `webhookUrl`     | string   | Non    | URL de notification serveur à serveur (override la valeur de la clé API)    |
| `metadata`       | object   | Non    | Paires clé/valeur libres, restituées telles quelles dans le webhook          |

### Réponse `201 Created`

```json
{
  "id": "pay_s2MEDVmLFNcgget9",
  "url": "https://qr.akuunda-pay.io/checkout/pay_s2MEDVmLFNcgget9",
  "mode": "live",
  "status": "pending",
  "amount": 15000,
  "currency": "XOF",
  "reference": "CMD-123",
  "expiresAt": "2026-05-13T15:30:00",
  "createdAt": "2026-05-13T14:30:00"
}
```

> **Idempotency-Key** : si vous renvoyez la même requête avec la même clé, le serveur retourne l'objet existant sans créer de doublon. Utile en cas de timeout réseau.

---

## 2. Rediriger l'acheteur

```js
// Côté serveur — après création
res.redirect(payment.url)
```

La page hébergée gère :
- La sélection du pays (si `paymentCountry` non fourni et devise multi-pays)
- Le choix de l'opérateur Mobile Money (YellowCard) ou la saisie email (MELD/Mercuryo)
- La présentation des frais avant confirmation
- Les langues : français / anglais (détection navigateur)
- Le mode test (bandeau "MODE TEST" automatique avec `sk_test_…`)

---

## 3. Recevoir la confirmation

### Option A — Webhook (recommandé)

Le serveur Akuunda envoie un `POST` vers votre `webhookUrl` dès que le paiement est confirmé.

#### Payload `payment.completed`

```json
{
  "event": "payment.completed",
  "timestamp": "2026-05-13T14:35:12Z",
  "data": {
    "checkoutCode": "pay_s2MEDVmLFNcgget9",
    "reference": "CMD-123",
    "amount": 15000,
    "currency": "XOF",
    "status": "paid",
    "mode": "live",
    "paidAt": "2026-05-13T14:35:10",
    "payerName": "David Aman",
    "payerPhone": "+2250709997042",
    "paymentProvider": "YELLOWCARD",
    "metadata": { "orderId": "123", "customerId": "456" }
  }
}
```

#### Payload `payment.refunded`

```json
{
  "event": "payment.refunded",
  "data": {
    "checkoutCode": "pay_s2MEDVmLFNcgget9",
    "reference": "CMD-123",
    "amount": 15000,
    "refundedAmount": 15000,
    "refundedAt": "2026-05-13T16:00:00",
    "refundReason": "Commande annulée"
  }
}
```

#### Vérifier la signature

Chaque webhook est signé avec votre `apiSecret` (HMAC-SHA256). Vérifiez systématiquement :

```js
// Node.js
const crypto = require('crypto')

function verifyWebhook(rawBody, signature, apiSecret) {
  const expected = crypto
    .createHmac('sha256', apiSecret)
    .update(rawBody, 'utf8')
    .digest('hex')
  return crypto.timingSafeEqual(
    Buffer.from(signature),
    Buffer.from(expected)
  )
}

app.post('/api/webhooks/akuunda', express.raw({ type: 'application/json' }), (req, res) => {
  const sig = req.headers['x-akuunda-signature']
  if (!verifyWebhook(req.body, sig, process.env.AKUUNDA_API_SECRET)) {
    return res.status(401).send('Invalid signature')
  }
  const { event, data } = JSON.parse(req.body)
  if (event === 'payment.completed' && data.status === 'paid') {
    markOrderPaid(data.reference)
  }
  res.sendStatus(200)
})
```

```php
// PHP
function verifyWebhook(string $rawBody, string $signature, string $apiSecret): bool {
    $expected = hash_hmac('sha256', $rawBody, $apiSecret);
    return hash_equals($expected, $signature);
}

$rawBody  = file_get_contents('php://input');
$signature = $_SERVER['HTTP_X_AKUUNDA_SIGNATURE'] ?? '';

if (!verifyWebhook($rawBody, $signature, getenv('AKUUNDA_API_SECRET'))) {
    http_response_code(401);
    exit;
}

$payload = json_decode($rawBody, true);
if ($payload['event'] === 'payment.completed' && $payload['data']['status'] === 'paid') {
    markOrderPaid($payload['data']['reference']);
}
http_response_code(200);
```

### Option B — Redirect params

Si vous ne gérez pas de webhook, l'acheteur est redirigé sur votre `returnUrl` avec les paramètres :

```
https://boutique.ci/commandes/123/merci
  ?status=success
  &reference=CMD-123
  &checkoutCode=pay_s2MEDVmLFNcgget9
```

> ⚠️ Ne validez jamais un paiement **uniquement** sur les paramètres de redirect — ils peuvent être falsifiés. Vérifiez toujours via le webhook ou via `GET /api/v1/payments/{id}`.

---

## 4. Consulter un paiement

```http
GET /api/v1/payments/pay_s2MEDVmLFNcgget9
Authorization: Bearer sk_live_xxx
```

Retourne le même objet `PaymentResponse` avec le `status` courant :

| Status      | Description                              |
|-------------|------------------------------------------|
| `pending`   | Session créée, en attente de paiement    |
| `paid`      | Paiement confirmé                        |
| `expired`   | Session expirée (1h après création)      |
| `cancelled` | Annulé par l'acheteur                    |
| `failed`    | Échec technique                          |
| `refunded`  | Remboursé (total ou partiel)             |

---

## 5. Rembourser un paiement

```http
POST /api/v1/payments/pay_s2MEDVmLFNcgget9/refunds
Authorization: Bearer sk_live_xxx
Content-Type: application/json
```

```json
{ "reason": "Commande annulée par le client" }
```

Pour un remboursement partiel :

```json
{ "amount": 5000, "reason": "Remboursement partiel" }
```

---

## 6. Mode test (Sandbox)

Avec une clé `sk_test_…` :

- La page affiche un bandeau **"MODE TEST"**
- Aucun débit réel n'est effectué
- Simuler un paiement réussi :

```http
POST /api/v1/payments/pay_xxx/_test/complete
Authorization: Bearer sk_test_xxx
```

Cela marque le paiement `paid` et déclenche le webhook comme en production.

---

## 7. Exemples par plateforme

### Node.js / Express

```js
const axios = require('axios')

// Créer un paiement
app.post('/checkout', async (req, res) => {
  const { data } = await axios.post(
    'https://wallet.akuunda-pay.io/api/v1/payments',
    {
      amount: req.body.total,
      currency: 'XOF',
      reference: req.body.orderId,
      paymentCountry: 'CI',
      returnUrl: `https://boutique.ci/orders/${req.body.orderId}/confirm`,
      cancelUrl: 'https://boutique.ci/panier',
      webhookUrl: 'https://boutique.ci/api/webhooks/akuunda',
    },
    { headers: { Authorization: `Bearer ${process.env.AKUUNDA_API_KEY}` } }
  )
  res.redirect(data.url)
})
```

### PHP / WooCommerce

```php
// Création du paiement
function create_akuunda_payment($order) {
    $response = wp_remote_post('https://wallet.akuunda-pay.io/api/v1/payments', [
        'headers' => [
            'Authorization' => 'Bearer ' . get_option('akuunda_api_key'),
            'Content-Type'  => 'application/json',
        ],
        'body' => json_encode([
            'amount'         => (float) $order->get_total(),
            'currency'       => get_woocommerce_currency(),
            'reference'      => (string) $order->get_id(),
            'paymentCountry' => get_option('akuunda_payment_country', 'CI'),
            'returnUrl'      => $order->get_checkout_order_received_url(),
            'cancelUrl'      => wc_get_cart_url(),
            'webhookUrl'     => rest_url('akuunda/v1/webhook'),
            'metadata'       => ['orderId' => (string) $order->get_id()],
        ]),
    ]);
    return json_decode(wp_remote_retrieve_body($response));
}
```

### Python / Django

```python
import requests, os

def create_payment(request, order):
    resp = requests.post(
        'https://wallet.akuunda-pay.io/api/v1/payments',
        json={
            'amount': float(order.total),
            'currency': 'XOF',
            'reference': str(order.id),
            'paymentCountry': 'CI',
            'returnUrl': request.build_absolute_uri(f'/orders/{order.id}/confirm/'),
            'cancelUrl': request.build_absolute_uri('/cart/'),
            'webhookUrl': request.build_absolute_uri('/webhooks/akuunda/'),
        },
        headers={'Authorization': f'Bearer {os.environ["AKUUNDA_API_KEY"]}'},
        timeout=10,
    )
    resp.raise_for_status()
    return redirect(resp.json()['url'])
```

---

## 8. Pays et devises supportés

### YellowCard (Mobile Money)

| Pays          | Code | Devise | Opérateurs                          |
|---------------|------|--------|-------------------------------------|
| Côte d'Ivoire | CI   | XOF    | Orange, MTN, Wave, Moov             |
| Sénégal       | SN   | XOF    | Orange, Wave, Free                  |
| Burkina Faso  | BF   | XOF    | Orange, Moov                        |
| Mali          | ML   | XOF    | Orange, Moov                        |
| Bénin         | BJ   | XOF    | MTN, Moov                           |
| Niger         | NE   | XOF    | Airtel, Moov                        |
| Ghana         | GH   | GHS    | MTN, Vodafone, AirtelTigo           |
| Nigeria       | NG   | NGN    | MTN, Airtel, Glo, 9mobile           |
| Kenya         | KE   | KES    | M-Pesa, Airtel                      |
| Cameroun      | CM   | XAF    | MTN, Orange                         |

### MELD / Mercuryo (carte, virement SEPA…)

Devises fiat internationales : `EUR`, `USD`, `GBP`, `CAD`…  
L'acheteur est redirigé sur le widget Mercuryo pour compléter le paiement par carte ou virement.

---

## 9. Erreurs courantes

| Code HTTP | Cause                                    | Solution                                      |
|-----------|------------------------------------------|-----------------------------------------------|
| `401`     | Clé API manquante ou invalide            | Vérifier le header `Authorization`            |
| `400`     | Champ manquant ou invalide               | Vérifier le corps de la requête               |
| `409`     | Idempotency-Key déjà utilisée            | Retourner l'objet existant (replay sûr)       |
| `404`     | Paiement introuvable                     | Vérifier l'`id` ou la clé API utilisée        |
| `422`     | Action impossible sur ce statut          | Ex : rembourser un paiement non `paid`        |
| `500`     | Erreur serveur                           | Réessayer avec backoff exponentiel            |

---

## 10. Bonnes pratiques

- **Ne faites jamais confiance aux paramètres de redirect** pour valider un paiement — utilisez le webhook.
- **Vérifiez toujours la signature** du webhook avec votre `apiSecret`.
- **Utilisez `Idempotency-Key`** lors de la création pour éviter les doublons en cas de timeout.
- **Testez en sandbox** avant de passer en production — remplacez `sk_test_` par `sk_live_` et l'URL serveur.
- **Stockez le `checkoutCode`** dans votre base de données pour associer le webhook à la commande.
- **Expirez votre panier** si le paiement reste `pending` après l'`expiresAt` (1h).
