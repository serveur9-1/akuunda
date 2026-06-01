# 📋 Documentation - Smart Contract de Séquestre (Escrow) pour Paiements Conditionnels

## 🎯 Vue d'ensemble

Cette documentation explique comment remplacer l'approche actuelle (wallet Venly simple) par un **vrai smart contract de séquestre** déployé sur Polygon. Le smart contract gérera de façon autonome le dépôt, la libération et le remboursement des fonds USDC.

---

## 📊 Comparaison des approches

### Approche actuelle (Wallet simple)
```
Client → Wallet Intermédiaire → Wallet Intermédiaire (séquestre)
```
- ✅ Simple et rapide à mettre en place
- ✅ Flexible (règles modifiables)
- ❌ Centralisé (dépend de l'application)
- ❌ Nécessite confiance en Akuunda/Venly

### Approche Smart Contract
```
Client → Wallet Intermédiaire → Smart Contract (code Solidity)
```
- ✅ Décentralisé et transparent
- ✅ Code vérifiable publiquement
- ✅ Exécution autonome
- ❌ Plus complexe à développer
- ❌ Coûts de déploiement/maintenance

---

## 🏗️ Architecture avec Smart Contract

### Flow complet

```
1. Dépôt
   Client → Wallet Intermédiaire → Smart Contract
   (Les fonds sont verrouillés dans le smart contract)

2. Validation QR
   Application Java → Smart Contract (appel release())
   Smart Contract → Vendeur (transfert automatique)

3. Annulation
   Application Java → Smart Contract (appel refund())
   Smart Contract → Client (remboursement automatique)
```

### Composants nécessaires

1. **Smart Contract Solidity** : Code déployé sur Polygon
2. **Application Java** : Appels au smart contract via Web3j ou Venly
3. **Wallet Intermédiaire** : Pour recevoir les fonds avant transfert au contrat
4. **Infrastructure Venly** : Pour les transferts initiaux

---

## 💻 Code Smart Contract Solidity

### Contrat Escrow complet

```solidity
// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

import "@openzeppelin/contracts/token/ERC20/IERC20.sol";
import "@openzeppelin/contracts/security/ReentrancyGuard.sol";
import "@openzeppelin/contracts/access/Ownable.sol";

/**
 * @title ConditionalPaymentEscrow
 * @dev Smart contract de séquestre pour paiements conditionnels
 * Gère le dépôt, la libération et le remboursement de USDC
 */
contract ConditionalPaymentEscrow is ReentrancyGuard, Ownable {
    // Adresse du token USDC sur Polygon
    IERC20 public immutable usdcToken;
    
    // Structure d'un paiement conditionnel
    struct Payment {
        address client;           // Adresse du client
        address vendor;           // Adresse du vendeur
        uint256 amount;           // Montant en USDC (6 décimales)
        uint256 depositTime;      // Timestamp du dépôt
        uint256 serviceStartDate; // Date prévue de début de service
        PaymentStatus status;     // Statut du paiement
        string paymentCode;       // Code unique du paiement
    }
    
    // Statuts possibles
    enum PaymentStatus {
        Pending,          // En attente de validation
        Validated,        // QR code scanné, service commencé
        Released,         // Fonds libérés au vendeur
        Refunded,         // Remboursement total au client
        RefundedPartial   // Remboursement partiel
    }
    
    // Mapping : paymentCode => Payment
    mapping(string => Payment) public payments;
    
    // Mapping : paymentCode => bool (pour éviter les doublons)
    mapping(string => bool) public paymentExists;
    
    // Événements
    event PaymentDeposited(
        string indexed paymentCode,
        address indexed client,
        address indexed vendor,
        uint256 amount
    );
    
    event PaymentReleased(
        string indexed paymentCode,
        address indexed vendor,
        uint256 amount
    );
    
    event PaymentRefunded(
        string indexed paymentCode,
        address indexed client,
        uint256 amount,
        bool isPartial
    );
    
    /**
     * @dev Constructeur
     * @param _usdcToken Adresse du contrat USDC sur Polygon
     */
    constructor(address _usdcToken) {
        require(_usdcToken != address(0), "Invalid USDC address");
        usdcToken = IERC20(_usdcToken);
    }
    
    /**
     * @dev Dépose les fonds dans le séquestre
     * @param paymentCode Code unique du paiement
     * @param vendor Adresse du vendeur
     * @param amount Montant en USDC (6 décimales)
     * @param serviceStartDate Timestamp de début de service prévu
     */
    function deposit(
        string memory paymentCode,
        address vendor,
        uint256 amount,
        uint256 serviceStartDate
    ) external nonReentrant {
        require(!paymentExists[paymentCode], "Payment already exists");
        require(vendor != address(0), "Invalid vendor address");
        require(amount > 0, "Amount must be greater than 0");
        require(serviceStartDate > block.timestamp, "Invalid start date");
        
        // Transfert des USDC depuis le wallet intermédiaire vers ce contrat
        require(
            usdcToken.transferFrom(msg.sender, address(this), amount),
            "USDC transfer failed"
        );
        
        // Créer le paiement
        payments[paymentCode] = Payment({
            client: msg.sender,
            vendor: vendor,
            amount: amount,
            depositTime: block.timestamp,
            serviceStartDate: serviceStartDate,
            status: PaymentStatus.Pending,
            paymentCode: paymentCode
        });
        
        paymentExists[paymentCode] = true;
        
        emit PaymentDeposited(paymentCode, msg.sender, vendor, amount);
    }
    
    /**
     * @dev Libère les fonds vers le vendeur (après validation QR)
     * @param paymentCode Code unique du paiement
     */
    function release(string memory paymentCode) external nonReentrant {
        Payment storage payment = payments[paymentCode];
        require(paymentExists[paymentCode], "Payment does not exist");
        require(
            payment.status == PaymentStatus.Pending,
            "Payment not in pending status"
        );
        
        // Mettre à jour le statut
        payment.status = PaymentStatus.Validated;
        
        // Transfert vers le vendeur
        require(
            usdcToken.transfer(payment.vendor, payment.amount),
            "USDC transfer to vendor failed"
        );
        
        payment.status = PaymentStatus.Released;
        
        emit PaymentReleased(paymentCode, payment.vendor, payment.amount);
    }
    
    /**
     * @dev Rembourse totalement le client
     * @param paymentCode Code unique du paiement
     */
    function refund(string memory paymentCode) external nonReentrant {
        Payment storage payment = payments[paymentCode];
        require(paymentExists[paymentCode], "Payment does not exist");
        require(
            payment.status == PaymentStatus.Pending,
            "Payment not in pending status"
        );
        
        uint256 refundAmount = payment.amount;
        
        // Mettre à jour le statut
        payment.status = PaymentStatus.Refunded;
        
        // Transfert vers le client
        require(
            usdcToken.transfer(payment.client, refundAmount),
            "USDC transfer to client failed"
        );
        
        emit PaymentRefunded(paymentCode, payment.client, refundAmount, false);
    }
    
    /**
     * @dev Rembourse partiellement (pénalités)
     * @param paymentCode Code unique du paiement
     * @param vendorAmount Montant pour le vendeur (pénalité)
     * @param clientAmount Montant pour le client (remboursement)
     */
    function partialRefund(
        string memory paymentCode,
        uint256 vendorAmount,
        uint256 clientAmount
    ) external nonReentrant {
        Payment storage payment = payments[paymentCode];
        require(paymentExists[paymentCode], "Payment does not exist");
        require(
            payment.status == PaymentStatus.Pending,
            "Payment not in pending status"
        );
        require(
            vendorAmount + clientAmount == payment.amount,
            "Amounts must equal payment amount"
        );
        
        // Mettre à jour le statut
        payment.status = PaymentStatus.RefundedPartial;
        
        // Transfert vers le vendeur (pénalité)
        if (vendorAmount > 0) {
            require(
                usdcToken.transfer(payment.vendor, vendorAmount),
                "USDC transfer to vendor failed"
            );
        }
        
        // Transfert vers le client (remboursement)
        if (clientAmount > 0) {
            require(
                usdcToken.transfer(payment.client, clientAmount),
                "USDC transfer to client failed"
            );
        }
        
        emit PaymentRefunded(paymentCode, payment.client, clientAmount, true);
    }
    
    /**
     * @dev Fonction d'urgence : rembourser tous les paiements en attente (owner only)
     * @param paymentCodes Liste des codes de paiement à rembourser
     */
    function emergencyRefundAll(string[] memory paymentCodes) external onlyOwner {
        for (uint256 i = 0; i < paymentCodes.length; i++) {
            Payment storage payment = payments[paymentCodes[i]];
            if (
                paymentExists[paymentCodes[i]] &&
                payment.status == PaymentStatus.Pending
            ) {
                payment.status = PaymentStatus.Refunded;
                usdcToken.transfer(payment.client, payment.amount);
                emit PaymentRefunded(
                    paymentCodes[i],
                    payment.client,
                    payment.amount,
                    false
                );
            }
        }
    }
    
    /**
     * @dev Récupère les informations d'un paiement
     * @param paymentCode Code unique du paiement
     */
    function getPayment(string memory paymentCode)
        external
        view
        returns (
            address client,
            address vendor,
            uint256 amount,
            uint256 depositTime,
            PaymentStatus status
        )
    {
        require(paymentExists[paymentCode], "Payment does not exist");
        Payment memory payment = payments[paymentCode];
        return (
            payment.client,
            payment.vendor,
            payment.amount,
            payment.depositTime,
            payment.status
        );
    }
    
    /**
     * @dev Vérifie le solde USDC du contrat
     */
    function getContractBalance() external view returns (uint256) {
        return usdcToken.balanceOf(address(this));
    }
}
```

---

## 🚀 Déploiement du Smart Contract

### Prérequis

1. **Node.js** et **npm**
2. **Hardhat** ou **Truffle** (framework de développement)
3. **Wallet avec MATIC** pour payer les frais de gas
4. **Clés privées** pour signer les transactions

### Installation avec Hardhat

```bash
# Créer un nouveau projet
mkdir escrow-contract
cd escrow-contract
npm init -y

# Installer Hardhat
npm install --save-dev hardhat

# Installer les dépendances
npm install @openzeppelin/contracts
npm install @nomiclabs/hardhat-ethers ethers

# Initialiser Hardhat
npx hardhat init
```

### Configuration Hardhat (`hardhat.config.js`)

```javascript
require("@nomiclabs/hardhat-ethers");
require("dotenv").config();

module.exports = {
  solidity: "0.8.20",
  networks: {
    polygon: {
      url: "https://polygon-rpc.com",
      accounts: [process.env.PRIVATE_KEY],
      chainId: 137
    },
    mumbai: {
      url: "https://rpc-mumbai.maticvigil.com",
      accounts: [process.env.PRIVATE_KEY],
      chainId: 80001
    }
  }
};
```

### Script de déploiement (`scripts/deploy.js`)

```javascript
const hre = require("hardhat");

async function main() {
  // Adresse USDC sur Polygon
  const USDC_ADDRESS = "0x3c499c542cEF5E3811e1192ce70d8cC03d5c3359";
  
  // Déployer le contrat
  const ConditionalPaymentEscrow = await hre.ethers.getContractFactory(
    "ConditionalPaymentEscrow"
  );
  const escrow = await ConditionalPaymentEscrow.deploy(USDC_ADDRESS);
  
  await escrow.deployed();
  
  console.log("Escrow contract deployed to:", escrow.address);
  console.log("Deployment transaction:", escrow.deployTransaction.hash);
  
  // Vérifier sur Polygonscan (optionnel)
  // await hre.run("verify:verify", {
  //   address: escrow.address,
  //   constructorArguments: [USDC_ADDRESS],
  // });
}

main()
  .then(() => process.exit(0))
  .catch((error) => {
    console.error(error);
    process.exit(1);
  });
```

### Commandes de déploiement

```bash
# Compiler
npx hardhat compile

# Déployer sur Mumbai (testnet)
npx hardhat run scripts/deploy.js --network mumbai

# Déployer sur Polygon (mainnet)
npx hardhat run scripts/deploy.js --network polygon
```

### Après déploiement

1. **Récupérer l'adresse du contrat** déployé
2. **Vérifier sur Polygonscan** : https://polygonscan.com/address/VOTRE_ADRESSE
3. **Mettre à jour `application.properties`** avec l'adresse du contrat

---

## 🔧 Intégration avec l'application Java

### Option 1 : Utiliser Venly pour appeler le smart contract

Venly supporte l'appel de smart contracts via leur API. Modifier `SmartContractEscrowServiceImpl.java` :

```java
@Override
public String depositToEscrow(String escrowContractAddress, Double amount, 
                              Wallet clientWallet, Wallet intermediateWallet, 
                              String smartContractWalletAddress, String clientPin) {
    
    // Étape 1 : Client → Wallet intermédiaire (inchangé)
    // ... code existant ...
    
    // Étape 2 : Wallet intermédiaire → Smart Contract (appel de fonction)
    String step2Body = String.format("""
        {
          "transactionRequest": {
            "walletId": "%s",
            "to": "%s",
            "value": "0",
            "secretType": "MATIC",
            "type": "CONTRACT_CALL",
            "data": "%s"
          }
        }
        """, 
        intermediateWallet.getId(), 
        escrowContractAddress, // Adresse du smart contract
        encodeDepositFunction(paymentCode, vendorAddress, amount, serviceStartDate)
    );
    
    // Appel via Venly
    var step2Response = transferClientService.executeTransfert(step2Body, escrowServicePin);
    // ... gestion de la réponse ...
}

private String encodeDepositFunction(String paymentCode, String vendorAddress, 
                                     Double amount, Long serviceStartDate) {
    // Encoder les paramètres pour la fonction deposit()
    // Format ABI encoding
    // Utiliser une bibliothèque comme Web3j ou encoder manuellement
}
```

### Option 2 : Utiliser Web3j (recommandé)

Ajouter la dépendance dans `pom.xml` :

```xml
<dependency>
    <groupId>org.web3j</groupId>
    <artifactId>core</artifactId>
    <version>4.9.8</version>
</dependency>
```

Créer un service pour interagir avec le smart contract :

```java
@Service
public class EscrowContractService {
    
    private static final String CONTRACT_ADDRESS = "0x..."; // Adresse du contrat déployé
    private static final String USDC_ADDRESS = "0x3c499c542cEF5E3811e1192ce70d8cC03d5c3359";
    
    private Web3j web3j;
    private Credentials credentials;
    private ConditionalPaymentEscrow contract;
    
    @PostConstruct
    public void init() {
        web3j = Web3j.build(new HttpService("https://polygon-rpc.com"));
        credentials = Credentials.create("PRIVATE_KEY_WALLET_INTERMEDIAIRE");
        contract = ConditionalPaymentEscrow.load(CONTRACT_ADDRESS, web3j, credentials, 
                                                  DefaultGasProvider.GAS_PRICE);
    }
    
    public String deposit(String paymentCode, String vendorAddress, 
                         BigInteger amount, BigInteger serviceStartDate) {
        try {
            TransactionReceipt receipt = contract.deposit(
                paymentCode,
                vendorAddress,
                amount,
                serviceStartDate
            ).send();
            
            return receipt.getTransactionHash();
        } catch (Exception e) {
            log.error("Erreur lors du dépôt dans le smart contract", e);
            throw new RuntimeException("Échec du dépôt", e);
        }
    }
    
    public String release(String paymentCode) {
        try {
            TransactionReceipt receipt = contract.release(paymentCode).send();
            return receipt.getTransactionHash();
        } catch (Exception e) {
            log.error("Erreur lors de la libération", e);
            throw new RuntimeException("Échec de la libération", e);
        }
    }
    
    public String refund(String paymentCode) {
        try {
            TransactionReceipt receipt = contract.refund(paymentCode).send();
            return receipt.getTransactionHash();
        } catch (Exception e) {
            log.error("Erreur lors du remboursement", e);
            throw new RuntimeException("Échec du remboursement", e);
        }
    }
}
```

### Générer les classes Java depuis le contrat Solidity

```bash
# Installer Web3j CLI
npm install -g web3j

# Générer les classes Java
web3j generate solidity \
  --javaTypes \
  --package=org.akuunda.akuundawallet.contracts \
  --outputDir=src/main/java \
  ConditionalPaymentEscrow.sol
```

---

## 📝 Modifications dans `application.properties`

```properties
# ===============================
# Smart Contract Escrow (VRAI SMART CONTRACT)
# ===============================

# Adresse du smart contract déployé sur Polygon
akuunda.escrow.contract.address=0xVOTRE_ADRESSE_CONTRAT_DEPLOYE

# Adresse du token USDC (inchangé)
akuunda.escrow.token.address=0x3c499c542cEF5E3811e1192ce70d8cC03d5c3359

# Clé privée du wallet intermédiaire (pour signer les transactions)
akuunda.escrow.contract.private.key=VOTRE_CLE_PRIVEE

# URL du RPC Polygon
akuunda.escrow.contract.rpc.url=https://polygon-rpc.com

# Wallet intermédiaire (pour recevoir les fonds avant transfert au contrat)
akuunda.intermediate.wallet.id=5192115a-51b7-4f4a-bbb3-ba2515d2e2ce
akuunda.intermediate.wallet.address=0xbc54758d74e5477a118762d5df0a22672169dde3
```

---

## 🔄 Flow modifié avec Smart Contract

### 1. Création du paiement

```java
// Dans ConditionalPaymentServiceImpl.createConditionalPayment()

// Étape 1 : Client → Wallet intermédiaire (via Venly)
String step1TxHash = venlyTransfer(clientWallet, intermediateWallet, amount, clientPin);

// Étape 2 : Wallet intermédiaire → Smart Contract (appel deposit())
String step2TxHash = escrowContractService.deposit(
    paymentCode,
    vendorWallet.getAddress(),
    amountInWei, // Convertir en wei (6 décimales pour USDC)
    serviceStartDateTimestamp
);

// Le smart contract verrouille les fonds automatiquement
```

### 2. Validation QR (libération)

```java
// Dans ConditionalPaymentServiceImpl.validateQRCode()

// Appeler la fonction release() du smart contract
String releaseTxHash = escrowContractService.release(paymentCode);

// Le smart contract transfère automatiquement vers le vendeur
// Pas besoin de transaction Venly supplémentaire
```

### 3. Annulation (remboursement)

```java
// Dans ConditionalPaymentServiceImpl.cancelPayment()

if (refundAmount == paymentAmount) {
    // Remboursement total
    String refundTxHash = escrowContractService.refund(paymentCode);
} else {
    // Remboursement partiel
    String refundTxHash = escrowContractService.partialRefund(
        paymentCode,
        vendorAmountInWei,
        clientAmountInWei
    );
}
```

---

## ✅ Avantages de cette approche

1. **Décentralisation** : La logique est sur la blockchain
2. **Transparence** : Code vérifiable sur Polygonscan
3. **Confiance** : Pas besoin de faire confiance à Akuunda
4. **Sécurité** : Pas de point de défaillance unique
5. **Autonomie** : Exécution automatique sans intervention

---

## ⚠️ Points d'attention

1. **Coûts de gas** : Chaque transaction coûte des MATIC
2. **Immutabilité** : Le code ne peut pas être modifié après déploiement
3. **Tests** : Tests exhaustifs nécessaires avant déploiement mainnet
4. **Audit** : Audit de sécurité recommandé pour les fonds importants
5. **Complexité** : Plus complexe à maintenir qu'un wallet simple

---

## 🧪 Tests du Smart Contract

### Tests avec Hardhat

```javascript
const { expect } = require("chai");
const { ethers } = require("hardhat");

describe("ConditionalPaymentEscrow", function () {
  let escrow;
  let usdc;
  let owner;
  let client;
  let vendor;

  beforeEach(async function () {
    [owner, client, vendor] = await ethers.getSigners();
    
    // Déployer le contrat
    const Escrow = await ethers.getContractFactory("ConditionalPaymentEscrow");
    escrow = await Escrow.deploy(USDC_ADDRESS);
    await escrow.deployed();
  });

  it("Should deposit funds", async function () {
    const amount = ethers.utils.parseUnits("100", 6); // 100 USDC
    
    await usdc.connect(client).approve(escrow.address, amount);
    await escrow.connect(client).deposit(
      "CP-123",
      vendor.address,
      amount,
      Math.floor(Date.now() / 1000) + 86400
    );
    
    expect(await escrow.getContractBalance()).to.equal(amount);
  });

  it("Should release funds to vendor", async function () {
    // ... tests de libération
  });

  it("Should refund to client", async function () {
    // ... tests de remboursement
  });
});
```

---

## 📚 Ressources

- **OpenZeppelin Contracts** : https://docs.openzeppelin.com/contracts
- **Hardhat Documentation** : https://hardhat.org/docs
- **Web3j Documentation** : https://docs.web3j.io
- **Polygon Documentation** : https://docs.polygon.technology
- **USDC Contract Addresses** : https://developers.circle.com/stablecoins/usdc-contract-addresses

---

## 🎯 Conclusion

Cette documentation fournit tous les éléments nécessaires pour déployer et intégrer un vrai smart contract de séquestre. Cependant, pour votre cas d'usage actuel (plateforme centralisée avec règles flexibles), **l'approche wallet simple reste recommandée**.

Le smart contract devient intéressant si vous avez besoin de :
- Décentralisation complète
- Confiance maximale sans tiers
- Transparence totale du code
- Exécution autonome sur la blockchain
