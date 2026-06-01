package org.akuunda.akuundawallet.wallet.service;

import org.akuunda.akuundawallet.wallet.api.dto.CreatePermanentLinkRequest;
import org.akuunda.akuundawallet.wallet.api.dto.CreatePermanentLinkSessionRequest;
import org.akuunda.akuundawallet.wallet.api.dto.PermanentLinkResponse;
import org.akuunda.akuundawallet.wallet.api.dto.PermanentLinkSessionResponse;
import org.akuunda.akuundawallet.wallet.api.dto.PermanentLinkStatsResponse;
import org.springframework.http.ResponseEntity;

import java.util.List;

public interface PermanentLinkService {

    /** Réponse normale {@link PermanentLinkResponse}, ou {@link org.akuunda.akuundawallet.common.Exceptions.ErrorResponse} en 422 si aucun wallet marchand. */
    ResponseEntity<?> createPermanentLink(String username, CreatePermanentLinkRequest request);

    ResponseEntity<PermanentLinkResponse> getPermanentLinkBySlug(String merchantSlug);

    ResponseEntity<List<PermanentLinkResponse>> getUserPermanentLinks(String username);

    /** Réponse normale {@link PermanentLinkSessionResponse}, ou {@link org.akuunda.akuundawallet.common.Exceptions.ErrorResponse} en 422 si aucun wallet marchand. */
    ResponseEntity<?> createSession(String merchantSlug, CreatePermanentLinkSessionRequest request);

    ResponseEntity<PermanentLinkSessionResponse> getSessionByCode(String sessionCode);

    ResponseEntity<PermanentLinkStatsResponse> getPermanentLinkStats(String username, String merchantSlug);

    ResponseEntity<String> deactivatePermanentLink(String username, String merchantSlug);

    ResponseEntity<String> activatePermanentLink(String username, String merchantSlug);
}
