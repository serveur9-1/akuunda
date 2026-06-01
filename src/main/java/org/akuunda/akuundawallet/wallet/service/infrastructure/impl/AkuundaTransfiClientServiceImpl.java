package org.akuunda.akuundawallet.wallet.service.infrastructure.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dto.external.transfi.*;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaTransfiClientService;
import org.akuunda.akuundawallet.wallet.service.transfi.TransfiPersistenceService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AkuundaTransfiClientServiceImpl implements AkuundaTransfiClientService {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final TransfiPersistenceService transfiPersistenceService;

    @Value("${transfi.api.base-url:https://sandbox-api.transfi.com}")
    private String baseUrl;

    @Value("${transfi.api.username:}")
    private String apiUsername;

    @Value("${transfi.api.password:}")
    private String apiPassword;

    @Value("${transfi.api.mid:}")
    private String mid;

    // ── Configuration ──────────────────────────────────────────────

    @Override
    public ResponseEntity<Object> listMids(int page, int limit) {
        String url = baseUrl + "/v3/config/list-mids?page=" + page + "&limit=" + limit;
        return sendGet(url, Object.class);
    }

    @Override
    public ResponseEntity<Object> getSupportedCurrencies(String direction, String userType, int page, int limit) {
        String url = baseUrl + "/v3/config/supported-currencies?direction=" + direction
                + "&userType=" + userType + "&page=" + page + "&limit=" + limit;
        return sendGet(url, Object.class);
    }

    @Override
    public ResponseEntity<Object> getPaymentMethods(String direction, String currency,
            boolean headlessMode, String userType, int page, int limit) {
        String url = baseUrl + "/v3/config/payment-methods?direction=" + direction
                + "&currency=" + currency + "&headlessMode=" + headlessMode
                + "&userType=" + userType + "&page=" + page + "&limit=" + limit;
        return sendGet(url, Object.class);
    }

    @Override
    public ResponseEntity<Object> getIbanPaymentMethods(String currency, String beneficiaryType, int limit, int page) {
        StringBuilder url = new StringBuilder(baseUrl + "/v3/config/payment-methods/iban?");
        if (currency != null && !currency.isBlank()) url.append("currency=").append(enc(upper(currency))).append("&");
        if (beneficiaryType != null && !beneficiaryType.isBlank()) url.append("beneficiaryType=").append(enc(beneficiaryType)).append("&");
        url.append("limit=").append(limit).append("&page=").append(page);
        return sendGet(url.toString(), Object.class);
    }

    @Override
    public ResponseEntity<Object> listTokens(String direction, int limit, int page) {
        String url = baseUrl + "/v3/config/list-tokens?direction=" + enc(direction)
                + "&limit=" + limit
                + "&page=" + page;
        return sendGet(url, Object.class);
    }

    @Override
    public ResponseEntity<Object> getTransactionLimits(String userId, Double fiatAmountInUSD,
                                                       String orderType, String paymentType) {
        StringBuilder url = new StringBuilder(baseUrl + "/v3/config/transaction-limits?");
        if (userId != null && !userId.isBlank()) url.append("userId=").append(enc(userId)).append("&");
        if (fiatAmountInUSD != null) url.append("fiatAmountInUSD=").append(fiatAmountInUSD).append("&");
        if (orderType != null && !orderType.isBlank()) url.append("orderType=").append(enc(orderType)).append("&");
        if (paymentType != null && !paymentType.isBlank()) url.append("paymentType=").append(enc(paymentType));
        return sendGet(url.toString(), Object.class);
    }

    @Override
    public ResponseEntity<Object> getQrCodeDetails(String qrCode, String currency) {
        String url = baseUrl + "/v3/config/qr-code-details?qrCode=" + enc(qrCode)
                + "&currency=" + enc(upper(currency));
        return sendGet(url, Object.class);
    }

    // ── Exchange Rates ──────────────────────────────────────────────

    @Override
    public ResponseEntity<Object> getExchangeRates(String amount,
                                                   String sourceCurrency,
                                                   String destinationCurrency,
                                                   String orderType,
                                                   String direction,
                                                   String paymentCode,
                                                   String paymentType,
                                                   String toPaymentCode,
                                                   String toPaymentType) {
        StringBuilder url = new StringBuilder(baseUrl + "/v3/exchange-rates?");
        url.append("amount=").append(enc(amount)).append("&");
        url.append("sourceCurrency=").append(enc(upper(sourceCurrency))).append("&");
        url.append("destinationCurrency=").append(enc(upper(destinationCurrency))).append("&");
        url.append("orderType=").append(enc(orderType));
        if (direction != null && !direction.isBlank()) url.append("&direction=").append(enc(direction));
        if (paymentCode != null && !paymentCode.isBlank()) url.append("&paymentCode=").append(enc(upper(paymentCode)));
        if (paymentType != null && !paymentType.isBlank()) url.append("&paymentType=").append(enc(paymentType));
        if (toPaymentCode != null && !toPaymentCode.isBlank()) url.append("&toPaymentCode=").append(enc(upper(toPaymentCode)));
        if (toPaymentType != null && !toPaymentType.isBlank()) url.append("&toPaymentType=").append(enc(toPaymentType));
        return sendGet(url.toString(), Object.class);
    }

    // ── Users ───────────────────────────────────────────────────────

    @Override
    public ResponseEntity<Object> listIndividualUsers(int page, int limit, String email, String phone) {
        StringBuilder url = new StringBuilder(baseUrl + "/v3/users/individual?page=" + page + "&limit=" + limit);
        if (email != null && !email.isBlank()) url.append("&email=").append(enc(email));
        if (phone != null && !phone.isBlank()) url.append("&phone=").append(enc(phone));
        return sendGet(url.toString(), Object.class);
    }

    @Override
    public ResponseEntity<Object> createIndividualUser(TransfiCreateIndividualUserRequest request) {
        ResponseEntity<Object> response = sendPost(baseUrl + "/v3/users/individual", request, Object.class);
        if (response.getStatusCode().is2xxSuccessful()) {
            transfiPersistenceService.saveUserMapping(request.getUsername(), "individual", response.getBody());
        }
        return response;
    }

    @Override
    public ResponseEntity<Object> listBusinessUsers(int page, int limit, String email, String phone) {
        StringBuilder url = new StringBuilder(baseUrl + "/v3/users/business?page=" + page + "&limit=" + limit);
        if (email != null && !email.isBlank()) url.append("&email=").append(enc(email));
        if (phone != null && !phone.isBlank()) url.append("&phone=").append(enc(phone));
        return sendGet(url.toString(), Object.class);
    }

    @Override
    public ResponseEntity<Object> createBusinessUser(TransfiCreateBusinessUserRequest request) {
        ResponseEntity<Object> response = sendPost(baseUrl + "/v3/users/business", request, Object.class);
        if (response.getStatusCode().is2xxSuccessful()) {
            transfiPersistenceService.saveUserMapping(request.getUsername(), "business", response.getBody());
        }
        return response;
    }

    // ── Recipients ──────────────────────────────────────────────────

    @Override
    public ResponseEntity<Object> getRecipientMandatoryFields(TransfiRecipientMandatoryFieldsRequest request) {
        return sendPost(baseUrl + "/v3/recipients/mandatory-fields", request, Object.class);
    }

    @Override
    public ResponseEntity<Object> createIndividualRecipient(TransfiCreateIndividualRecipientRequest request) {
        return sendPost(baseUrl + "/v3/recipients/individual", request, Object.class);
    }

    @Override
    public ResponseEntity<Object> createBusinessRecipient(TransfiCreateBusinessRecipientRequest request) {
        return sendPost(baseUrl + "/v3/recipients/business", request, Object.class);
    }

    // ── KYC ─────────────────────────────────────────────────────────

    @Override
    public ResponseEntity<TransfiKycResponse> initiateStandardKyc(TransfiKycRequest request) {
        return sendPost(baseUrl + "/v3/kyc/standard", request, TransfiKycResponse.class);
    }

    @Override
    public ResponseEntity<TransfiKycResponse> initiateAdvancedKyc(TransfiKycRequest request) {
        return sendPost(baseUrl + "/v3/kyc/advanced", request, TransfiKycResponse.class);
    }

    @Override
    public ResponseEntity<Object> shareKycSameVendor(TransfiKycShareRequest request) {
        return sendPost(baseUrl + "/v3/kyc/share/same-vendor", request, Object.class);
    }

    @Override
    public ResponseEntity<Object> shareKycThirdVendor(TransfiKycShareThirdVendorRequest request) {
        MultipartBodyBuilder body = new MultipartBodyBuilder()
                .addText("userId", request.getUserId())
                .addText("country", request.getCountry())
                .addText("nationality", request.getNationality())
                .addText("idDocIssuerCountry", request.getIdDocIssuerCountry())
                .addText("idDocType", request.getIdDocType())
                .addText("dob", request.getDob())
                .addText("gender", request.getGender())
                .addText("idDocUserName", request.getIdDocUserName())
                .addText("idDocExpiryDate", request.getIdDocExpiryDate())
                .addText("phoneNo", request.getPhoneNo())
                .addText("street", request.getStreet())
                .addText("city", request.getCity())
                .addText("state", request.getState())
                .addText("postalCode", request.getPostalCode())
                .addFile("idDocFrontSide", request.getIdDocFrontSide())
                .addFile("idDocBackSide", request.getIdDocBackSide())
                .addFile("selfie", request.getSelfie());
        return sendMultipart(baseUrl + "/v3/kyc/share/third-vendor", body, Object.class);
    }

    @Override
    public ResponseEntity<Object> shareKycAdditionalDocs(TransfiKycShareAddDocsRequest request) {
        MultipartBodyBuilder body = new MultipartBodyBuilder()
                .addText("userId", request.getUserId())
                .addText("additionalDocType", request.getAdditionalDocType())
                .addFile("additionalDoc", request.getAdditionalDoc());
        return sendMultipart(baseUrl + "/v3/kyc/share/add-docs", body, Object.class);
    }

    @Override
    public ResponseEntity<Object> getKycStatus(String userId) {
        return sendGet(baseUrl + "/v3/kyc/status/" + enc(userId), Object.class);
    }

    // ── KYB ─────────────────────────────────────────────────────────

    @Override
    public ResponseEntity<Object> initiateStandardKyb(TransfiKybRequest request) {
        return sendPost(baseUrl + "/v3/kyb/standard", request, Object.class);
    }

    @Override
    public ResponseEntity<Object> initiateAdvancedKyb(TransfiKybRequest request) {
        return sendPost(baseUrl + "/v3/kyb/advanced", request, Object.class);
    }

    @Override
    public ResponseEntity<Object> getKybStatus(String userId) {
        return sendGet(baseUrl + "/v3/kyb/status/" + enc(userId), Object.class);
    }

    // ── Orders ──────────────────────────────────────────────────────

    @Override
    public ResponseEntity<Object> listOrders(int page, int limit, String status, String orderType,
            String userId, String currency) {
        StringBuilder url = new StringBuilder(baseUrl + "/v3/orders?page=" + page + "&limit=" + limit);
        if (status != null && !status.isBlank()) url.append("&status=").append(status);
        if (orderType != null && !orderType.isBlank()) url.append("&orderType=").append(orderType);
        if (userId != null && !userId.isBlank()) url.append("&userId=").append(userId);
        if (currency != null && !currency.isBlank()) url.append("&currency=").append(currency);
        return sendGet(url.toString(), Object.class);
    }

    @Override
    public ResponseEntity<Object> createOrder(TransfiCreateOrderRequest request) {
        ResponseEntity<Object> response = sendPost(baseUrl + "/v3/orders", request, Object.class);
        if (response.getStatusCode().is2xxSuccessful()) {
            transfiPersistenceService.saveOrder(request, response.getBody());
        }
        return response;
    }

    @Override
    public ResponseEntity<Object> getOrderById(String orderId) {
        return sendGet(baseUrl + "/v3/orders/" + enc(orderId), Object.class);
    }

    // ── Invoices ────────────────────────────────────────────────────

    @Override
    public ResponseEntity<Object> uploadInvoice(TransfiUploadInvoiceRequest request) {
        MultipartBodyBuilder body = new MultipartBodyBuilder()
                .addText("userId", request.getUserId())
                .addText("direction", request.getDirection())
                .addText("invoiceType", request.getInvoiceType())
                .addFile("invoice", request.getInvoice());
        return sendMultipart(baseUrl + "/v3/invoices/create", body, Object.class);
    }

    // ── Balance ─────────────────────────────────────────────────────

    @Override
    public ResponseEntity<Object> getBalance(String currency, int page, int limit) {
        StringBuilder url = new StringBuilder(baseUrl + "/v3/balance?page=" + page + "&limit=" + limit);
        if (currency != null && !currency.isBlank()) url.append("&currency=").append(currency);
        return sendGet(url.toString(), Object.class);
    }

    // ── IBAN ────────────────────────────────────────────────────────

    @Override
    public ResponseEntity<Object> createVirtualIban(TransfiCreateIbanRequest request) {
        ResponseEntity<Object> response = sendPost(baseUrl + "/v3/iban/create-iban", request, Object.class);
        if (response.getStatusCode().is2xxSuccessful()) {
            transfiPersistenceService.saveIban(request, response.getBody());
        }
        return response;
    }

    @Override
    public ResponseEntity<Object> listVirtualIbans(String userId, String currency, int limit, int page) {
        StringBuilder url = new StringBuilder(baseUrl + "/v3/iban/list-iban?page=" + page + "&limit=" + limit);
        if (userId != null && !userId.isBlank()) url.append("&userId=").append(enc(userId));
        if (currency != null && !currency.isBlank()) url.append("&currency=").append(enc(upper(currency)));
        return sendGet(url.toString(), Object.class);
    }

    @Override
    public ResponseEntity<Object> linkIbanOrder(TransfiLinkOrderRequest request) {
        return sendPost(baseUrl + "/v3/orders/link", request, Object.class);
    }

    @Override
    public ResponseEntity<Object> getIbanBalance(String accountId, String currency) {
        StringBuilder url = new StringBuilder(baseUrl + "/v3/balance/iban?");
        boolean first = true;
        if (accountId != null && !accountId.isBlank()) {
            url.append("accountId=").append(enc(accountId));
            first = false;
        }
        if (currency != null && !currency.isBlank()) {
            if (!first) url.append("&");
            url.append("currency=").append(enc(upper(currency)));
        }
        return sendGet(url.toString(), Object.class);
    }

    // ── Simulation ──────────────────────────────────────────────────

    @Override
    public ResponseEntity<Object> simulateIncomingIbanDeposit(TransfiSimulateIbanDepositRequest request) {
        return sendPost(baseUrl + "/v3/simulation/incoming-iban-deposits", request, Object.class);
    }

    @Override
    public ResponseEntity<Object> reconcileIbanDeposit(String userId, String orderId) {
        return sendGet(baseUrl + "/v3/simulation/reconcile-iban-deposit?userId=" + userId + "&orderId=" + orderId,
                Object.class);
    }

    // ── HTTP helpers ─────────────────────────────────────────────────

    private <T> ResponseEntity<T> sendGet(String url, Class<T> responseType) {
        try {
            HttpRequest request = requestBuilder(url).GET().build();
            return execute(request, responseType);
        } catch (Exception e) {
            log.error("TransFi GET {} error: {}", url, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private <T> ResponseEntity<T> sendPost(String url, Object body, Class<T> responseType) {
        try {
            String json = objectMapper.writeValueAsString(body);
            HttpRequest request = requestBuilder(url)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            return execute(request, responseType);
        } catch (Exception e) {
            log.error("TransFi POST {} error: {}", url, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private <T> ResponseEntity<T> sendMultipart(String url, MultipartBodyBuilder builder, Class<T> responseType) {
        try {
            byte[] body = builder.build();
            HttpRequest request = requestBuilder(url)
                    .header("Content-Type", builder.contentType())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                    .build();
            return execute(request, responseType);
        } catch (Exception e) {
            log.error("TransFi POST (multipart) {} error: {}", url, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @SuppressWarnings("unchecked")
    private <T> ResponseEntity<T> execute(HttpRequest request, Class<T> responseType) throws Exception {
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        log.debug("TransFi {} {} → {}", request.method(), request.uri(), response.statusCode());
        String responseBody = response.body();

        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            T body = responseBody == null || responseBody.isEmpty()
                    ? null
                    : objectMapper.readValue(responseBody, responseType);
            return ResponseEntity.status(response.statusCode()).body(body);
        }

        // Forward TransFi's structured error body to the caller so the client can read
        // error.code, error.message, error.context.details... instead of an opaque 400.
        // Type erasure lets us return the parsed Map as the body regardless of T —
        // Spring re-serializes it via Jackson on the way out.
        log.warn("TransFi API error {}: {}", response.statusCode(), responseBody);
        Object errorBody;
        try {
            errorBody = responseBody == null || responseBody.isEmpty()
                    ? null
                    : objectMapper.readValue(responseBody, Object.class);
        } catch (Exception parseErr) {
            errorBody = responseBody;
        }
        return ResponseEntity.status(response.statusCode()).body((T) errorBody);
    }

    private static String enc(String value) {
        return value == null ? "" : URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * TransFi attend les codes devises (USD, EUR, USDT, BRL, VND, ...) et les
     * paymentCodes (BANK_TRANSFER, DEFAULT, ...) en majuscules. On normalise
     * côté wrapper pour être tolérant aux clients qui envoient en minuscules
     * (sinon TransFi retourne 400 SOURCE_CURRENCY_NOT_SUPPORTED).
     */
    private static String upper(String value) {
        return value == null ? null : value.toUpperCase(Locale.ROOT);
    }

    private HttpRequest.Builder requestBuilder(String url) {
        String credentials = Base64.getEncoder()
                .encodeToString((apiUsername + ":" + apiPassword).getBytes(StandardCharsets.UTF_8));
        return HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", "Basic " + credentials)
                .header("MID", mid)
                .header("Accept", "application/json");
    }

    /**
     * Construit un body multipart/form-data sans dépendance externe.
     * La JVM HttpClient ne supporte pas multipart nativement, on assemble manuellement
     * la séquence d'octets avec les bons en-têtes RFC 2388 et un boundary unique.
     *
     * Comportement :
     *  - addText : ignore les valeurs null (les champs optionnels non renseignés ne sont
     *    pas transmis) — empêche d'envoyer 'null' littéral à TransFi.
     *  - addFile : ignore les MultipartFile null ou vides (idem).
     */
    static final class MultipartBodyBuilder {
        private static final String CRLF = "\r\n";
        private final String boundary = "----TransFiBoundary" + UUID.randomUUID();
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        MultipartBodyBuilder addText(String name, String value) {
            if (value == null) return this;
            try {
                writeAscii("--" + boundary + CRLF);
                writeAscii("Content-Disposition: form-data; name=\"" + name + "\"" + CRLF);
                writeAscii("Content-Type: text/plain; charset=UTF-8" + CRLF + CRLF);
                out.write(value.getBytes(StandardCharsets.UTF_8));
                writeAscii(CRLF);
            } catch (IOException e) {
                throw new IllegalStateException("multipart text part write failed: " + name, e);
            }
            return this;
        }

        MultipartBodyBuilder addFile(String name, MultipartFile file) {
            if (file == null || file.isEmpty()) return this;
            try {
                String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : name;
                String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
                writeAscii("--" + boundary + CRLF);
                writeAscii("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + filename + "\"" + CRLF);
                writeAscii("Content-Type: " + contentType + CRLF + CRLF);
                out.write(file.getBytes());
                writeAscii(CRLF);
            } catch (IOException e) {
                throw new IllegalStateException("multipart file part write failed: " + name, e);
            }
            return this;
        }

        byte[] build() {
            try {
                writeAscii("--" + boundary + "--" + CRLF);
            } catch (IOException e) {
                throw new IllegalStateException("multipart final boundary write failed", e);
            }
            return out.toByteArray();
        }

        String contentType() {
            return "multipart/form-data; boundary=" + boundary;
        }

        private void writeAscii(String s) throws IOException {
            out.write(s.getBytes(StandardCharsets.US_ASCII));
        }
    }
}
