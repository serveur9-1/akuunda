package org.akuunda.akuundawallet.keycloak.impl.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.dto.external.ExternalSigningMethod;
import org.akuunda.akuundawallet.keycloak.api.service.SigningMethodService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkunndaSigningMethodClientService;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class SigningMethodServiceImpl implements SigningMethodService {

    private final AkunndaSigningMethodClientService signingMethodClient;
    private final UserRepository userRepository;

    @Override
    public ResponseEntity<String> createSigning(final String methodValue, final String methodType,
                                                final String userName, final String signingPin,
                                                final String physicalDeviceId) {
        JSONObject body = getBody(methodType, methodValue, physicalDeviceId);

        //verify User exists or not
        final var user = userRepository.getUsersByUsername(userName);
        if (user == null) {
            return new ResponseEntity<>("User not found", HttpStatus.NOT_FOUND);
        }

        final var userId = user.getUserId();

        if (methodType.equals("PIN")) {
            return signingMethodClient.createUserPinSigningMethod(userId, body.toString());
        } else {
            // Venly requires Signing-Method header in format "signingMethodId:pin"
            // Retrieve the user's PIN signing method ID to build the header
            final var signingMethodsResponse = signingMethodClient.getAllSigningMethods(userId);
            if (signingMethodsResponse.getStatusCode() != HttpStatus.OK || signingMethodsResponse.getBody() == null) {
                log.error("Failed to retrieve signing methods for user: {}", userName);
                return new ResponseEntity<>("Failed to retrieve signing methods", HttpStatus.INTERNAL_SERVER_ERROR);
            }

            final var pinMethod = signingMethodsResponse.getBody().stream()
                    .filter(m -> "PIN".equals(m.getType()))
                    .findFirst()
                    .orElse(null);

            if (pinMethod == null) {
                log.error("No PIN signing method found for user: {}", userName);
                return new ResponseEntity<>("No PIN signing method found", HttpStatus.BAD_REQUEST);
            }

            final String signingHeader = pinMethod.getId() + ":" + signingPin;
            return signingMethodClient.createUserOtherSigningMethod(userId, body.toString(), signingHeader);
        }
    }

    @Override
    public ResponseEntity<List<ExternalSigningMethod>> getAllSigningMethods(final String userName) {

        log.info("Get all signing methods for user: {}", userName);
        log.debug("Get all signing methods for user: {}", userName);
        //verify User exists or not
        final var user = userRepository.getUsersByUsername(userName);
        if (user == null) {
            return new ResponseEntity<>(List.of(), HttpStatus.NOT_FOUND);
        }

        //get userId
        final var userId = user.getUserId();

        //get all signing methods
        final var response = signingMethodClient.getAllSigningMethods(userId);
        if (response.getStatusCode() == HttpStatus.OK) {
            return new ResponseEntity<>(response.getBody(), HttpStatus.OK);
        }  else {
            return new ResponseEntity<>(List.of(), response.getStatusCode());
        }
    }

    private JSONObject getBody(String type, String value, String physicalDeviceId) {
        JSONObject signingBody = new JSONObject();
        signingBody.put("type", type);
        signingBody.put("value", value);
        if ("BIOMETRIC".equals(type) && physicalDeviceId != null && !physicalDeviceId.isEmpty()) {
            signingBody.put("physicalDeviceId", physicalDeviceId);
        }
        return signingBody;
    }
}
