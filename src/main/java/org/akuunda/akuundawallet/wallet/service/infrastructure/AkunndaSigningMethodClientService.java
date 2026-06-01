package org.akuunda.akuundawallet.wallet.service.infrastructure;

import org.akuunda.akuundawallet.keycloak.api.dto.external.ExternalSigningMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import java.util.List;

@Validated
public interface AkunndaSigningMethodClientService {


    /**
     * {@inheritDoc}
     */
    ResponseEntity<String> createUserOtherSigningMethod(final String userId, final String body, final String pin);
    /**
     * {@inheritDoc}
     */
    ResponseEntity<String> createUserPinSigningMethod(final String userId, final String body);

    /**
     * Create emergency code signing method for a user
     * @param userId The user ID
     * @param body The request body containing the emergency code
     * @param pinSigningMethodHeader The signing method header with PIN (format: signingMethodId:pinValue)
     * @return Response from Venly
     */
    ResponseEntity<String> createUserEmergencyCodeSigningMethod(final String userId, final String body, final String pinSigningMethodHeader);

    /**
     * Update PIN signing method for a user using emergency code for authentication
     * @param userId The user ID
     * @param signingMethodId The signing method ID to update (PIN signing method ID)
     * @param body The request body containing the new PIN value
     * @param emergencyCodeSigningMethodHeader The signing method header with emergency code (format: emergencyCodeSigningMethodId:emergencyCodeValue)
     * @return Response from Venly
     */
    ResponseEntity<String> updatePinSigningMethod(final String userId, final String signingMethodId, final String body, final String emergencyCodeSigningMethodHeader);

    /**
     * Update emergency code signing method using the current emergency code for authentication
     * @param userId The user ID
     * @param signingMethodId The emergency code signing method ID
     * @param body The request body containing the new emergency code value
     * @param currentEmergencyCodeHeader Signing-Method header: emergencyCodeSigningMethodId:currentFullEmergencyCode
     * @return Response from Venly
     */
    ResponseEntity<String> updateEmergencyCodeSigningMethod(final String userId, final String signingMethodId, final String body, final String currentEmergencyCodeHeader);

    /**
     * {@inheritDoc}
     */
    ResponseEntity<List<ExternalSigningMethod>> getAllSigningMethods(final String userId);

}
