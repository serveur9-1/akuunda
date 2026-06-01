package org.akuunda.akuundawallet.wallet.service;

import org.akuunda.akuundawallet.wallet.api.entities.Wallet;

/**
 * Service d'interface avec le smart contract AkuundaPaymentFactory déployé sur Polygon.
 * Adresse Factory : 0x4fa9e998dba19a0c4b831259c556a4d99052f0a4
 */
public interface PaymentFactoryContractService {

    /**
     * Appelle Factory.createPaymentLink() via Venly pour créer un paiement on-chain
     * et obtenir l'adresse CREATE2 du wallet temporaire.
     *
     * @param paymentId bytes32 unique du paiement
     * @param merchantAddress adresse Polygon du wallet du marchand
     * @param amountInUSDC montant en USDC (sera converti en wei avec 6 decimales)
     * @param description description du paiement
     * @param durationInSeconds durée d'expiration en secondes
     * @param signerWallet le wallet qui signe la transaction (relayer/admin)
     * @return l'adresse CREATE2 du wallet temporaire, ou null en cas d'erreur
     */
    String createPaymentLink(String paymentId, String merchantAddress, Double amountInUSDC,
                             String description, Long durationInSeconds, Wallet signerWallet);

    /**
     * Appelle Factory.executePayment() via Venly pour transférer les USDC
     * du wallet temporaire vers le wallet du marchand.
     */
    String executePayment(String paymentId, Wallet signerWallet);

    /**
     * Appelle Factory.isPaymentReceived() en lecture seule (call, pas transaction)
     * pour vérifier si les USDC sont arrivés sur le wallet CREATE2.
     */
    boolean isPaymentReceived(String paymentId);

    /**
     * Appelle Factory.getWalletAddress() en lecture seule
     * pour récupérer l'adresse CREATE2 du wallet temporaire.
     */
    String getWalletAddress(String paymentId);

    /**
     * Appelle Factory.getPayment() en lecture seule
     * pour récupérer les infos du paiement on-chain.
     */
    PaymentInfo getPayment(String paymentId);

    /**
     * Reads the USDC balance of a given address by calling USDC.balanceOf() directly.
     * Returns the balance in USDC (human-readable, divided by 10^6).
     */
    Double getUsdcBalance(String walletAddress);

    /**
     * Appelle Factory.refundPayment() pour rembourser les USDC.
     */
    String refundPayment(String paymentId, String refundToAddress, Wallet signerWallet);

    /**
     * Génère un paymentId bytes32 déterministe à partir du code unique du lien.
     */
    String generatePaymentId(String uniqueCode);

    /**
     * Informations d'un paiement on-chain.
     *
     * @param merchantAddress adresse du marchand
     * @param amount montant en USDC (en wei, 6 decimales)
     * @param expiration timestamp d'expiration
     * @param walletAddress adresse CREATE2 du wallet temporaire
     * @param status 0=Pending, 1=Completed, 2=Refunded, 3=Expired
     * @param description description du paiement
     */
    record PaymentInfo(
            String merchantAddress,
            Double amount,
            Long expiration,
            String walletAddress,
            int status,
            String description
    ) {}
}
