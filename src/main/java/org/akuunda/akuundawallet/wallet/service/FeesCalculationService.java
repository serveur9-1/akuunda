package org.akuunda.akuundawallet.wallet.service;

import org.akuunda.akuundawallet.wallet.api.dto.FeesCalculationRequest;
import org.akuunda.akuundawallet.wallet.api.dto.FeesCalculationResponse;
import org.springframework.http.ResponseEntity;

public interface FeesCalculationService {
    /**
     * Calcule les frais estimés pour une opération de dépôt (OnRamp).
     * 
     * @param request La requête contenant le montant, la devise, le pays et l'opérateur
     * @return ResponseEntity contenant les frais estimés, le montant net et les détails du calcul
     */
    ResponseEntity<FeesCalculationResponse> calculateFees(FeesCalculationRequest request);
    
    /**
     * Calcule les frais estimés pour une opération de retrait (OffRamp).
     * 
     * @param request La requête contenant le montant, la devise, le pays et l'opérateur
     * @return ResponseEntity contenant les frais estimés, le montant net et les détails du calcul
     */
    ResponseEntity<FeesCalculationResponse> calculateOffRampFees(FeesCalculationRequest request);
}

