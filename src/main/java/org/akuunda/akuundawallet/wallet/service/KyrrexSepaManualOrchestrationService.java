package org.akuunda.akuundawallet.wallet.service;

import org.akuunda.akuundawallet.wallet.api.dto.external.KyrrexSepaOrchestrationRequest;

import java.util.Map;

public interface KyrrexSepaManualOrchestrationService {
    Map<String, Object> openOrRefreshIban(String username, KyrrexSepaOrchestrationRequest request);
    Map<String, Object> orchestrate(String username, KyrrexSepaOrchestrationRequest request);
}
