package org.akuunda.akuundawallet.wallet.service.infrastructure;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.generated.Uint256;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collections;

/**
 * Service pour encoder les appels de fonctions smart contract en format ABI.
 */
public class AbiEncodingService {

    /**
     * Encode l'appel de la fonction deposit() du smart contract escrow.
     * 
     * @param paymentCode Code unique du paiement
     * @param vendorAddress Adresse du vendeur
     * @param amount Montant en USDC (en wei, donc multiplier par 10^6 pour USDC)
     * @param serviceStartDate Timestamp Unix du début du service
     * @return Données encodées en hexadécimal (0x...)
     */
    public static String encodeDeposit(String paymentCode, String vendorAddress, Double amount, Long serviceStartDate) {
        // USDC a 6 décimales, donc multiplier par 10^6
        BigInteger amountInWei = BigDecimal.valueOf(amount)
                .multiply(BigDecimal.valueOf(1_000_000))
                .setScale(0, RoundingMode.HALF_UP)
                .toBigInteger();
        BigInteger startDate = BigInteger.valueOf(serviceStartDate);

        Function function = new Function(
                "deposit",
                Arrays.asList(
                        new Utf8String(paymentCode),
                        new Address(vendorAddress),
                        new Uint256(amountInWei),
                        new Uint256(startDate)
                ),
                Collections.emptyList()
        );

        return FunctionEncoder.encode(function);
    }

    /**
     * Encode l'appel de la fonction release() du smart contract escrow.
     * 
     * @param paymentCode Code unique du paiement
     * @return Données encodées en hexadécimal (0x...)
     */
    public static String encodeRelease(String paymentCode) {
        Function function = new Function(
                "release",
                Arrays.asList(new Utf8String(paymentCode)),
                Collections.emptyList()
        );

        return FunctionEncoder.encode(function);
    }

    /**
     * Encode l'appel de la fonction refund() du smart contract escrow.
     * 
     * @param paymentCode Code unique du paiement
     * @return Données encodées en hexadécimal (0x...)
     */
    public static String encodeRefund(String paymentCode) {
        Function function = new Function(
                "refund",
                Arrays.asList(new Utf8String(paymentCode)),
                Collections.emptyList()
        );

        return FunctionEncoder.encode(function);
    }

    /**
     * Encode l'appel de la fonction partialRefund() du smart contract escrow.
     * 
     * @param paymentCode Code unique du paiement
     * @param vendorAmount Montant pour le vendeur en USDC (en wei)
     * @param clientAmount Montant pour le client en USDC (en wei)
     * @return Données encodées en hexadécimal (0x...)
     */
    public static String encodePartialRefund(String paymentCode, Double vendorAmount, Double clientAmount) {
        // USDC a 6 décimales
        BigInteger vendorAmountInWei = BigDecimal.valueOf(vendorAmount)
                .multiply(BigDecimal.valueOf(1_000_000))
                .setScale(0, RoundingMode.HALF_UP)
                .toBigInteger();
        BigInteger clientAmountInWei = BigDecimal.valueOf(clientAmount)
                .multiply(BigDecimal.valueOf(1_000_000))
                .setScale(0, RoundingMode.HALF_UP)
                .toBigInteger();

        Function function = new Function(
                "partialRefund",
                Arrays.asList(
                        new Utf8String(paymentCode),
                        new Uint256(vendorAmountInWei),
                        new Uint256(clientAmountInWei)
                ),
                Collections.emptyList()
        );

        return FunctionEncoder.encode(function);
    }

    /**
     * Encode l'appel de la fonction approve() du contrat ERC-20 (USDC).
     * Nécessaire avant tout transferFrom() par le smart contract escrow.
     *
     * @param spenderAddress Adresse du smart contract autorisé à dépenser les tokens
     * @param amount Montant à approuver en USDC
     * @return Données encodées en hexadécimal (0x...)
     */
    public static String encodeApprove(String spenderAddress, Double amount) {
        BigInteger amountInWei = BigDecimal.valueOf(amount)
                .multiply(BigDecimal.valueOf(1_000_000))
                .setScale(0, RoundingMode.HALF_UP)
                .toBigInteger();

        Function function = new Function(
                "approve",
                Arrays.asList(
                        new Address(spenderAddress),
                        new Uint256(amountInWei)
                ),
                Collections.emptyList()
        );

        return FunctionEncoder.encode(function);
    }
}
