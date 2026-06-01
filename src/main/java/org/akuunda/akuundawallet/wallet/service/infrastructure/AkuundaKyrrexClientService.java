package org.akuunda.akuundawallet.wallet.service.infrastructure;

import org.akuunda.akuundawallet.wallet.api.dto.external.*;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface AkuundaKyrrexClientService {

    // Session
    ResponseEntity<KyrrexSessionResponse> createSession(String username);
    ResponseEntity<KyrrexSessionLoginResponse> loginSession(String username);
    ResponseEntity<Boolean> logoutSession(String username);
    ResponseEntity<KyrrexSessionListResponse> listSessions(String username, Integer page, Integer perPage);

    // Members
    ResponseEntity<KyrrexMemberInfoResponse> getMemberInfo(String username);
    ResponseEntity<KyrrexMemberTotalBalanceResponse> getMemberTotalBalance(String username, String outputAsset);
    ResponseEntity<List<KyrrexMemberAccountResponse>> getMemberAccounts(String username);

    // Providers & Instruments
    ResponseEntity<List<KyrrexProviderResponse>> getFiatProviders(String username);
    ResponseEntity<List<KyrrexProviderResponse>> getFiatDepositProviders(String username);
    ResponseEntity<List<KyrrexProviderResponse>> getFiatWithdrawalProviders(String username);
    ResponseEntity<KyrrexCustomerResponse> registerCustomerAtProvider(String username, String providerId);
    ResponseEntity<KyrrexCustomerResponse> getCustomerStatus(String username, String providerId);
    ResponseEntity<KyrrexCardInstrumentResponse> createCardInstrument(String username, String providerId, KyrrexCardInstrumentRequest request);
    ResponseEntity<List<KyrrexCardInstrumentResponse>> getCardInstruments(String username, String providerId, String instrument);
    ResponseEntity<String> createBankTransferInstrument(String username, String providerId, Object body);
    ResponseEntity<String> getBankTransferInstrumentDetails(String username, String providerId, String instrumentId);

    // Markets & Settings
    ResponseEntity<List<KyrrexMarketResponse>> getFiatMarkets(String username, int page, int perPage);
    ResponseEntity<KyrrexMarketResponse> getMarketInfo(String username, String marketId);
    ResponseEntity<String> getMarketSettings(String username);
    ResponseEntity<String> getCurrencySettings(String username);

    // Fiat Deposit
    ResponseEntity<KyrrexFiatDepositResponse> createFiatDeposit(String username, KyrrexFiatDepositRequest request);
    ResponseEntity<KyrrexFiatDepositLinkResponse> generateFiatDepositLink(String username, KyrrexFiatDepositLinkRequest request);
    ResponseEntity<String> getFiatDepositFees(String username, String instrument, String providerId);
    ResponseEntity<String> getFiatDepositFeeEstimate(String username, String providerId, String instrument, String amount);
    ResponseEntity<String> getFiatDepositHistory(String username);
    ResponseEntity<KyrrexFiatDepositDetailResponse> getFiatDepositById(String username, String depositId);

    // Withdrawal Fee Estimate
    ResponseEntity<KyrrexWithdrawalFeeEstimateResponse> getWithdrawalFeeEstimate(String username, String instrument, String amount, String providerId);

    // On-Ramp: Advanced Exchange
    ResponseEntity<KyrrexAdvancedExchangeEstimateResponse> getAdvancedExchangeEstimate(String username, String fiatCurrency, String cryptoAsset, BigDecimal fiatAmount, String providerId);
    ResponseEntity<KyrrexAdvancedExchangeResponse> executeAdvancedExchange(String username, KyrrexAdvancedExchangeRequest request);
    ResponseEntity<String> getAdvancedExchangeHistory(String username);

    // Off-Ramp: Exchange/Swap
    ResponseEntity<KyrrexExchangeEstimateResponse> getExchangeEstimate(String username, String inputAsset, String outputAsset, BigDecimal amount, String providerId);
    ResponseEntity<String> executeExchange(String username, KyrrexExchangeRequest request);
    ResponseEntity<KyrrexSwapResponse> createSwap(String username, KyrrexSwapRequest request);

    // Off-Ramp: Fiat Withdrawal
    ResponseEntity<String> getFiatWithdrawalFees(String username, String instrument, String providerId);
    ResponseEntity<KyrrexFiatWithdrawalResponse> executeFiatWithdrawal(String username, KyrrexFiatWithdrawalRequest request);
    ResponseEntity<KyrrexFiatWithdrawalBankDetailsResponse> createFiatWithdrawalBankDetails(String username, KyrrexFiatWithdrawalBankDetailsRequest request);
    ResponseEntity<KyrrexFiatWithdrawalCardResponse> createFiatWithdrawalCard(String username, KyrrexFiatWithdrawalCardRequest request);
    ResponseEntity<String> getFiatWithdrawalHistory(String username);

    // Crypto Assets, Deposits & Withdrawals
    ResponseEntity<KyrrexPaginatedAssetsResponse> getAssets(String username, Boolean activeDeposit, Boolean activeWithdrawal, Integer page, Integer perPage);
    ResponseEntity<String> createDepositAddress(String username, String dchain, String name);
    ResponseEntity<KyrrexCryptoDepositLinkResponse> generateCryptoDepositLink(String username, KyrrexCryptoDepositLinkRequest request);
    ResponseEntity<String> getDepositAddresses(String username);
    ResponseEntity<String> getCryptoDeposits(String username);
    ResponseEntity<KyrrexCryptoDepositDetailResponse> getCryptoDepositById(String username, String depositId);
    ResponseEntity<String> createRequisite(String username, String currency, String address, String network, String label);
    ResponseEntity<String> getRequisites(String username);
    ResponseEntity<KyrrexDepositRequisitesResponse> getDepositRequisites(String username, String currency, String network);
    ResponseEntity<KyrrexWithdrawalRequisitesResponse> getWithdrawalRequisites(String username, String currency, String network);
    ResponseEntity<KyrrexCryptoAddressValidationResponse> validateCryptoWithdrawalAddress(String username, KyrrexCryptoAddressValidationRequest request);
    ResponseEntity<String> executeCryptoWithdrawal(String username, String currency, BigDecimal amount, String requisiteId);
    ResponseEntity<String> getCryptoWithdrawalHistory(String username);
    ResponseEntity<KyrrexCryptoWithdrawalDetailResponse> getCryptoWithdrawalById(String username, String withdrawalId);

    // Simple Transactions
    ResponseEntity<List<SimpleTransactionResponse>> getSimpleTransactions(String username);

    // Member Registration
    ResponseEntity<KyrrexMemberSignUpResponse> registerMember(String username, KyrrexMemberSignUpRequest request);

    /** True si une ligne active existe dans kyrrex_user_credentials. */
    boolean hasActiveKyrrexCredentials(String username);

    /** Inscrit chez Kyrrex si besoin (utilise les credentials business, pas ceux du membre). */
    ResponseEntity<KyrrexMemberSignUpResponse> ensureKyrrexMemberRegistered(
            String username, KyrrexMemberSignUpRequest request);

    /**
     * Résout un country_id valide chez Kyrrex (catalogue business) à partir du profil users
     * ou du téléphone (+33 → FR, +225 → CI, etc.).
     */
    int resolveSignupCountryId(String username, Integer requestedCountryId);

    /** Catalogue pays Kyrrex via session business (sans credentials membre). */
    List<KyrrexCountryResponse> listCountriesForRegistration();

    // KYC
    ResponseEntity<KyrrexKycTokenResponse> generateKycToken(String username);
    ResponseEntity<KyrrexKycWebLinkResponse> generateKycWebLink(String username);
    ResponseEntity<KyrrexKycTokenResponse> generateKycSharedToken(String username);
    ResponseEntity<List<KyrrexKycLevelsResponse>> getKycLevels(String username);
    ResponseEntity<KyrrexKycStatusResponse> getKycStatus(String username, String customerId);

    // KYB
    ResponseEntity<KyrrexKybTokenResponse> generateKybToken(String username);
    ResponseEntity<KyrrexKybWebLinkResponse> generateKybWebLink(String username);
    ResponseEntity<KyrrexLegalEntityInfoResponse> getLegalEntityInfo(String username);

    // Tools
    ResponseEntity<List<KyrrexCountryResponse>> getCountries(String username);
    ResponseEntity<List<KyrrexCountryCodeResponse>> getCountriesCodes(String username);
    ResponseEntity<List<KyrrexIdentificationDocumentResponse>> getIdentificationDocuments(String username);
    ResponseEntity<List<KyrrexVaspResponse>> getVasps(String username);
    ResponseEntity<KyrrexTimestampResponse> getTimestamp(String username);

    // Customer
    ResponseEntity<KyrrexCustomerResponse> getCustomerDetails(String username);

    // Balances
    ResponseEntity<List<KyrrexBalanceResponse>> getBalances(String username);

    // Credential Management
    ResponseEntity<Map<String, String>> storeUserCredentials(String username, String accessKey, String secretKey);

    /**
     * Réimporte les clés d'un membre Kyrrex déjà existant (après perte en base, ex. migration V2062).
     */
    ResponseEntity<Map<String, String>> importMemberCredentials(
            String username, String uid, String accessKey, String secretKey);
    ResponseEntity<Map<String, String>> getUserCredentialStatus(String username);
    ResponseEntity<Void> revokeUserCredentials(String username);
}
