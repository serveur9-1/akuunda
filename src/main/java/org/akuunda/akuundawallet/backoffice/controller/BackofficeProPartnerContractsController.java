package org.akuunda.akuundawallet.backoffice.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.akuunda.akuundawallet.backoffice.dto.ApiSuccess;
import org.akuunda.akuundawallet.backoffice.repository.BackofficeUserRepository;
import org.akuunda.akuundawallet.wallet.service.PartnerContractService;
import org.akuunda.akuundawallet.wallet.api.dto.partner.PartnerContractResponse;
import org.akuunda.akuundawallet.wallet.api.dto.partner.PartnerPaymentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping(path = "/api/v1/pro/partner-contracts", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Backoffice - Pro Partner Contracts")
@RequiredArgsConstructor
public class BackofficeProPartnerContractsController {

    private final PartnerContractService partnerContractService;
    private final BackofficeUserRepository backofficeUserRepository;

    @GetMapping
    @Operation(summary = "Lister les contrats partenaire du marchand connecté")
    public ResponseEntity<ApiSuccess<List<PartnerContractResponse>>> listContracts() {
        String partnerUsername = resolveWalletUsername();
        return ResponseEntity.ok(ApiSuccess.of(
                partnerContractService.getPartnerContracts(partnerUsername)));
    }

    @GetMapping("/{contractCode}")
    @Operation(summary = "Détail d'un contrat partenaire")
    public ResponseEntity<ApiSuccess<PartnerContractResponse>> getContract(
            @PathVariable String contractCode) {
        String partnerUsername = resolveWalletUsername();
        PartnerContractResponse contract = partnerContractService.getContract(contractCode);
        if (contract == null || contract.getPartnerUsername() == null
                || !contract.getPartnerUsername().equals(partnerUsername)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contrat introuvable");
        }
        return ResponseEntity.ok(ApiSuccess.of(contract));
    }

    @GetMapping("/{contractCode}/payments")
    @Operation(summary = "Lister les paiements d'un contrat partenaire")
    public ResponseEntity<ApiSuccess<List<PartnerPaymentResponse>>> getContractPayments(
            @PathVariable String contractCode) {
        String partnerUsername = resolveWalletUsername();
        return ResponseEntity.ok(ApiSuccess.of(
                partnerContractService.getContractPayments(contractCode, partnerUsername)));
    }

    private String resolveWalletUsername() {
        JwtAuthenticationToken auth = (JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication();
        Jwt jwt = auth.getToken();
        String email = jwt.getClaimAsString("email");
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email absent du token JWT");
        }
        return backofficeUserRepository.findByEmailIgnoreCase(email)
                .map(u -> u.getWalletUsername())
                .filter(w -> w != null && !w.isBlank())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Aucun compte marchand associé à cet email"));
    }
}
