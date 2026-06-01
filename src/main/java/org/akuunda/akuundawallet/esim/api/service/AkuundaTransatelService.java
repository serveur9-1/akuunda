package org.akuunda.akuundawallet.esim.api.service;

import org.akuunda.akuundawallet.esim.api.dto.CatalogPageDto;
import org.akuunda.akuundawallet.esim.api.dto.SubscribeProductRequestDto;
import org.akuunda.akuundawallet.esim.api.dto.ActivateEsimRequestDto;
import org.akuunda.akuundawallet.esim.api.dto.WebhookRequestDto;
import org.akuunda.akuundawallet.esim.api.dto.PurchaseFlowRequestDto;
import org.akuunda.akuundawallet.esim.api.dto.MsisdnResponseDto;
import org.akuunda.akuundawallet.esim.api.dto.RenewSubscriptionRequestDto;
import org.akuunda.akuundawallet.esim.api.dto.RenewProductRequestDto;
import org.akuunda.akuundawallet.esim.api.dto.CancelRenewalRequestDto;
import org.akuunda.akuundawallet.esim.api.dto.ModifySubscriptionRequestDto;
import org.akuunda.akuundawallet.esim.api.dto.ManageCreditRequestDto;
import org.akuunda.akuundawallet.esim.api.dto.ManageDataSafeguardRequestDto;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;

@Validated
public interface AkuundaTransatelService {

    ResponseEntity<?> getCatalog(int offset, int limit, String q, String country, String category,
            String continent, Boolean world, String region);

    ResponseEntity<JsonNode> getCatalogProduct(String productId);

    ResponseEntity<JsonNode> subscribeProduct(SubscribeProductRequestDto request);

    ResponseEntity<JsonNode> getInventory(String msisdn, String statuses, boolean withBalances);

    ResponseEntity<JsonNode> activateEsim(ActivateEsimRequestDto request);

    ResponseEntity<JsonNode> getEsimDetails(String simSerial, boolean history);

    ResponseEntity<JsonNode> getSubscriberDetails(String simSerial);

    ResponseEntity<JsonNode> getSubscriptionStatus(String subscriptionId, String msisdn);

    ResponseEntity<JsonNode> getSubscriptionDetails(String subscriptionId, boolean withBalances);

    ResponseEntity<JsonNode> getTransactionStatus(String transactionId);

    ResponseEntity<JsonNode> getInternetBalance(String msisdn);

    ResponseEntity<MsisdnResponseDto> getMsisdnForUser(String userId);

    ResponseEntity<org.akuunda.akuundawallet.esim.api.dto.LineStatusResponseDto> getLineStatusForUser(String userId);

    ResponseEntity<JsonNode> createWebhook(WebhookRequestDto request);

    ResponseEntity<Void> handleWebhookEvent(String payload, String signatureHeader);

    ResponseEntity<Object> purchaseFlow(PurchaseFlowRequestDto request);

    ResponseEntity<Object> renewLastSubscription(RenewSubscriptionRequestDto request);

    ResponseEntity<Object> renewProduct(RenewProductRequestDto request);

    ResponseEntity<JsonNode> terminateSubscriber(String simSerial, String ratePlan);

    ResponseEntity<JsonNode> suspendSubscriber(String simSerial, String ratePlan);

    ResponseEntity<JsonNode> reactivateSubscriber(String simSerial, String ratePlan);

    // OCS Options — Catalog
    ResponseEntity<JsonNode> getCatalogOptions();

    ResponseEntity<JsonNode> getCatalogDataSafeguardOption();

    // OCS Options — Inventory
    ResponseEntity<JsonNode> getSubscriberOptions(String msisdn);

    // OCS Products — Subscriptions (orderType passthrough)
    ResponseEntity<JsonNode> cancelRenewal(CancelRenewalRequestDto request);

    ResponseEntity<JsonNode> modifySubscription(ModifySubscriptionRequestDto request);

    // OCS Options — Subscriptions
    ResponseEntity<JsonNode> manageCredit(ManageCreditRequestDto request);

    ResponseEntity<JsonNode> manageDataSafeguard(ManageDataSafeguardRequestDto request);

    // Utilitaires
    ResponseEntity<java.util.Map<String, String>> getExchangeRate(String from, String to);
}
