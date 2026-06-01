package org.akuunda.akuundawallet.wallet.service.infrastructure;

import org.springframework.http.ResponseEntity;

public interface AkuundaSignaturesClientService {

    /**
     * Generates a signature for Mt Pelerin.
     *
     * @param data The data to be signed.
     * @return The generated signature.
     */
    ResponseEntity<String> generateSignature(String data);
}
