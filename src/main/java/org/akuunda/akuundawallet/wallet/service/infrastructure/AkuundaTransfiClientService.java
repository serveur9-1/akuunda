package org.akuunda.akuundawallet.wallet.service.infrastructure;

import org.akuunda.akuundawallet.wallet.api.dto.external.transfi.*;
import org.springframework.http.ResponseEntity;

public interface AkuundaTransfiClientService {

    // ── Configuration ──────────────────────────────────────────────
    ResponseEntity<Object> listMids(int page, int limit);
    ResponseEntity<Object> getSupportedCurrencies(String direction, String userType, int page, int limit);
    ResponseEntity<Object> getPaymentMethods(String direction, String currency, boolean headlessMode, String userType, int page, int limit);
    ResponseEntity<Object> getIbanPaymentMethods(String currency, String beneficiaryType, int limit, int page);
    ResponseEntity<Object> listTokens(String direction, int limit, int page);
    ResponseEntity<Object> getTransactionLimits(String userId, Double fiatAmountInUSD, String orderType, String paymentType);
    ResponseEntity<Object> getQrCodeDetails(String qrCode, String currency);

    // ── Exchange Rates ──────────────────────────────────────────────
    ResponseEntity<Object> getExchangeRates(String amount,
                                            String sourceCurrency,
                                            String destinationCurrency,
                                            String orderType,
                                            String direction,
                                            String paymentCode,
                                            String paymentType,
                                            String toPaymentCode,
                                            String toPaymentType);

    // ── Users ───────────────────────────────────────────────────────
    ResponseEntity<Object> listIndividualUsers(int page, int limit, String email, String phone);
    ResponseEntity<Object> createIndividualUser(TransfiCreateIndividualUserRequest request);
    ResponseEntity<Object> listBusinessUsers(int page, int limit, String email, String phone);
    ResponseEntity<Object> createBusinessUser(TransfiCreateBusinessUserRequest request);

    // ── Recipients ──────────────────────────────────────────────────
    ResponseEntity<Object> getRecipientMandatoryFields(TransfiRecipientMandatoryFieldsRequest request);
    ResponseEntity<Object> createIndividualRecipient(TransfiCreateIndividualRecipientRequest request);
    ResponseEntity<Object> createBusinessRecipient(TransfiCreateBusinessRecipientRequest request);

    // ── KYC ─────────────────────────────────────────────────────────
    ResponseEntity<TransfiKycResponse> initiateStandardKyc(TransfiKycRequest request);
    ResponseEntity<TransfiKycResponse> initiateAdvancedKyc(TransfiKycRequest request);
    ResponseEntity<Object> shareKycSameVendor(TransfiKycShareRequest request);
    ResponseEntity<Object> shareKycThirdVendor(TransfiKycShareThirdVendorRequest request);
    ResponseEntity<Object> shareKycAdditionalDocs(TransfiKycShareAddDocsRequest request);
    ResponseEntity<Object> getKycStatus(String userId);

    // ── KYB ─────────────────────────────────────────────────────────
    ResponseEntity<Object> initiateStandardKyb(TransfiKybRequest request);
    ResponseEntity<Object> initiateAdvancedKyb(TransfiKybRequest request);
    ResponseEntity<Object> getKybStatus(String userId);

    // ── Orders ──────────────────────────────────────────────────────
    ResponseEntity<Object> listOrders(int page, int limit, String status, String orderType, String userId, String currency);
    ResponseEntity<Object> createOrder(TransfiCreateOrderRequest request);
    ResponseEntity<Object> getOrderById(String orderId);

    // ── Invoices ────────────────────────────────────────────────────
    ResponseEntity<Object> uploadInvoice(TransfiUploadInvoiceRequest request);

    // ── Balance ─────────────────────────────────────────────────────
    ResponseEntity<Object> getBalance(String currency, int page, int limit);
    ResponseEntity<Object> getIbanBalance(String accountId, String currency);

    // ── IBAN ────────────────────────────────────────────────────────
    ResponseEntity<Object> createVirtualIban(TransfiCreateIbanRequest request);
    ResponseEntity<Object> listVirtualIbans(String userId, String currency, int limit, int page);
    ResponseEntity<Object> linkIbanOrder(TransfiLinkOrderRequest request);

    // ── Simulation (sandbox only) ───────────────────────────────────
    ResponseEntity<Object> simulateIncomingIbanDeposit(TransfiSimulateIbanDepositRequest request);
    ResponseEntity<Object> reconcileIbanDeposit(String userId, String orderId);
}
