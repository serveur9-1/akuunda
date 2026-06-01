package org.akuunda.akuundawallet.wallet.service;

import org.akuunda.akuundawallet.wallet.api.entities.Wallet;

import java.util.List;

/**
 * Service pour gérer le séquestre via smart contract (Polygon USDC).
 * Flow : Client approve() + deposit() directement vers le smart contract.
 */
public interface SmartContractEscrowService {

    /**
     * Dépose les fonds dans le smart contract directement depuis le wallet client.
     * Étape 1 : Client approve() USDC vers le smart contract (signature client).
     * Étape 2 : Client deposit() vers le smart contract (signature client).
     *
     * @param escrowContractAddress Adresse du smart contract
     * @param amount Montant à déposer (en USDC)
     * @param clientWallet Wallet du client (source)
     * @param intermediateWallet Wallet admin (non utilisé pour les fonds, conservé pour compatibilité)
     * @param smartContractWalletAddress Adresse du smart contract (destination)
     * @param clientPin PIN du client (null = enregistrement en base uniquement)
     * @param paymentCode Code unique du paiement (pour le smart contract)
     * @param vendorAddress Adresse du vendeur (pour le smart contract)
     * @param serviceStartDate Timestamp Unix du début du service (pour le smart contract)
     * @return Hash de la transaction Venly ou identifiant DB
     */
    String depositToEscrow(String escrowContractAddress, Double amount, Wallet clientWallet,
                          Wallet intermediateWallet, String smartContractWalletAddress, String clientPin,
                          String paymentCode, String vendorAddress, Long serviceStartDate);

    /**
     * Effectue le dépôt (approve + deposit()) depuis le wallet client directement.
     * À utiliser lors des retries.
     *
     * @param escrowContractAddress Adresse du smart contract
     * @param amount Montant à déposer (en USDC)
     * @param intermediateWallet Wallet admin (pour compatibilité, non utilisé pour signer ici)
     * @param paymentCode Code unique du paiement (pour le smart contract)
     * @param vendorAddress Adresse du vendeur (pour le smart contract)
     * @param serviceStartDate Timestamp Unix du début du service (pour le smart contract)
     * @param clientWallet Wallet du client (source des fonds)
     * @param clientPin PIN du client
     * @return Hash de la transaction Venly ou identifiant d'erreur
     */
    String depositToEscrowStep2Only(String escrowContractAddress, Double amount, Wallet intermediateWallet,
                                    String paymentCode, String vendorAddress, Long serviceStartDate,
                                    Wallet clientWallet, String clientPin);

    /**
     * Libère les fonds du smart contract directement vers le vendeur via appel de fonction release().
     * Le smart contract transfère les USDC au vendorAddress enregistré lors du deposit().
     * Le wallet admin signe la transaction sans recevoir de fonds.
     *
     * @param escrowContractAddress Adresse du smart contract
     * @param amount Montant à libérer (non utilisé, le contrat gère le montant)
     * @param vendorWalletAddress Adresse du wallet du vendeur (non utilisé, le contrat le récupère)
     * @param paymentCode Code unique du paiement
     * @param intermediateWallet Wallet admin (signe la transaction, ne reçoit pas de fonds)
     * @return Hash de la transaction Venly
     */
    String releaseFunds(String escrowContractAddress, Double amount, String vendorWalletAddress,
                        String paymentCode, Wallet intermediateWallet);

    /**
     * Libère les fonds du smart contract directement vers le vendeur via appel de fonction release() (avec wallet).
     * Le smart contract transfère les USDC au vendorAddress enregistré lors du deposit().
     * Le wallet admin signe la transaction sans recevoir de fonds.
     *
     * @param escrowContractAddress Adresse du smart contract
     * @param amount Montant à libérer (non utilisé, le contrat gère le montant)
     * @param vendorWalletAddress Adresse du wallet du vendeur (non utilisé, le contrat le récupère)
     * @param paymentCode Code unique du paiement
     * @param intermediateWallet Wallet admin (signe la transaction, ne reçoit pas de fonds)
     * @param escrowWallet Wallet de séquestre (non utilisé avec smart contract)
     * @return Hash de la transaction Venly
     */
    String releaseFunds(String escrowContractAddress, Double amount, String vendorWalletAddress,
                        String paymentCode, Wallet intermediateWallet, Wallet escrowWallet);

    /**
     * Rembourse les fonds du smart contract directement vers le client (depositor) via appel de fonction refund().
     * Le smart contract transfère les USDC au depositor (= le client dans le nouveau flow).
     * Le wallet admin signe la transaction sans recevoir de fonds.
     *
     * @param escrowContractAddress Adresse du smart contract
     * @param amount Montant à rembourser (non utilisé, le contrat gère le montant)
     * @param clientWalletAddress Adresse du wallet du client (non utilisé, le contrat récupère le depositor)
     * @param paymentCode Code unique du paiement
     * @param intermediateWallet Wallet admin (signe la transaction, ne reçoit pas de fonds)
     * @return Hash de la transaction Venly
     */
    String refundToClient(String escrowContractAddress, Double amount, String clientWalletAddress,
                          String paymentCode, Wallet intermediateWallet);

    /**
     * Rembourse les fonds du smart contract directement vers le client via appel de fonction refund() (avec wallet).
     * Le wallet admin signe la transaction sans recevoir de fonds.
     *
     * @param escrowContractAddress Adresse du smart contract
     * @param amount Montant à rembourser (non utilisé, le contrat gère le montant)
     * @param clientWalletAddress Adresse du wallet du client (non utilisé, le contrat récupère le depositor)
     * @param paymentCode Code unique du paiement
     * @param intermediateWallet Wallet admin (signe la transaction, ne reçoit pas de fonds)
     * @param escrowWallet Wallet de séquestre (non utilisé avec smart contract)
     * @return Hash de la transaction Venly
     */
    String refundToClient(String escrowContractAddress, Double amount, String clientWalletAddress,
                         String paymentCode, Wallet intermediateWallet, Wallet escrowWallet);

    /**
     * Distribue les fonds directement aux deux parties via appel de fonction partialRefund() du smart contract.
     * Le smart contract envoie vendorAmount au vendeur et clientAmount au client (depositor).
     * Le wallet admin signe la transaction sans recevoir de fonds.
     *
     * @param escrowContractAddress Adresse du smart contract
     * @param vendorAmount Montant à envoyer directement au vendeur
     * @param clientAmount Montant à rembourser directement au client
     * @param vendorWalletAddress Adresse du wallet du vendeur (non utilisé, le contrat le récupère)
     * @param clientWalletAddress Adresse du wallet du client (non utilisé, le contrat le récupère)
     * @param paymentCode Code unique du paiement
     * @param intermediateWallet Wallet admin (signe la transaction, ne reçoit pas de fonds)
     * @return Hash de la transaction Venly
     */
    String partialRefund(String escrowContractAddress, Double vendorAmount, Double clientAmount,
                         String vendorWalletAddress, String clientWalletAddress,
                         String paymentCode, Wallet intermediateWallet);

    /**
     * Distribue les fonds directement aux deux parties via appel de fonction partialRefund() du smart contract (avec wallet).
     * Le wallet admin signe la transaction sans recevoir de fonds.
     *
     * @param escrowContractAddress Adresse du smart contract
     * @param vendorAmount Montant à envoyer directement au vendeur
     * @param clientAmount Montant à rembourser directement au client
     * @param vendorWalletAddress Adresse du wallet du vendeur (non utilisé, le contrat le récupère)
     * @param clientWalletAddress Adresse du wallet du client (non utilisé, le contrat le récupère)
     * @param paymentCode Code unique du paiement
     * @param intermediateWallet Wallet admin (signe la transaction, ne reçoit pas de fonds)
     * @param escrowWallet Wallet de séquestre (non utilisé avec smart contract)
     * @return Hash de la transaction Venly
     */
    String partialRefund(String escrowContractAddress, Double vendorAmount, Double clientAmount,
                         String vendorWalletAddress, String clientWalletAddress,
                         String paymentCode, Wallet intermediateWallet, Wallet escrowWallet);

    /**
     * Transfert USDC direct d'un wallet vers une adresse (sans smart contract escrow).
     * Utilisé pour la distribution DYNAMIC_ASSIGNMENT depuis le wallet intermédiaire.
     */
    String transferUsdc(String toAddress, Double amount, Wallet fromWallet);

    // ── Nouvelles méthodes AkuundaConditionalPayment (partenaires API) ────────

    String registerConfig(String contractAddress, String configId,
                          List<String> beneficiaryAddrs, List<Integer> sharesBps,
                          Wallet adminWallet);

    String grantRole(String contractAddress, String roleBytes32, String account, Wallet adminWallet);

    String deactivateConfig(String contractAddress, String configId, Wallet adminWallet);

    String approveAndDeposit(String contractAddress, String paymentId, String configId,
                             Double amount, Wallet clientWallet, String clientPin);

    String distribute(String contractAddress, String paymentId, Wallet relayerWallet);

    String refund(String contractAddress, String paymentId, Wallet relayerWallet);

    String distributeAndRefund(String contractAddress, String paymentId,
                               int penaltyBps, Wallet relayerWallet);
}
