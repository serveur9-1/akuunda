package org.akuunda.akuundawallet.wallet.api.dto.external;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour MeldSessionResponse — vérifie la sérialisation des champs.
 */
public class MeldSessionResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serviceProviderWidgetUrlIsSerializedToJson() throws Exception {
        MeldSessionResponse response = new MeldSessionResponse();
        response.setId("abc123");
        response.setServiceProviderWidgetUrl("https://provider.example.com/widget");

        String json = objectMapper.writeValueAsString(response);

        assertTrue(json.contains("serviceProviderWidgetUrl"),
                "serviceProviderWidgetUrl should be present in JSON output");
        assertTrue(json.contains("https://provider.example.com/widget"),
                "serviceProviderWidgetUrl value should be present in JSON output");
    }

    @Test
    void serviceProviderWidgetUrlIsDeserializedFromJson() throws Exception {
        String json = "{\"id\":\"abc123\",\"serviceProviderWidgetUrl\":\"https://provider.example.com/widget\"}";

        MeldSessionResponse response = objectMapper.readValue(json, MeldSessionResponse.class);

        assertEquals("https://provider.example.com/widget", response.getServiceProviderWidgetUrl());
    }

    @Test
    void payRedirectUrlIsSerializedToJson() throws Exception {
        MeldSessionResponse response = new MeldSessionResponse();
        response.setPayRedirectUrl("https://walletdev.akuunda-pay.io/api/internal/v1/pay-redirect/otpl/xyz");

        String json = objectMapper.writeValueAsString(response);

        assertTrue(json.contains("payRedirectUrl"),
                "payRedirectUrl should be present in JSON output");
    }
}
