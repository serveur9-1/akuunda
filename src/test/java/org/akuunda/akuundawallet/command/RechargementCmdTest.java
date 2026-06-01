package org.akuunda.akuundawallet.command;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.akuunda.akuundawallet.transfert.api.dto.command.RechargementCmd;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RechargementCmdTest {

    @Test
    void testDeserialization() throws Exception {
        // JSON d'entrée
        String json = """
            {
                "username": "2250758286600",
                "amount": "100",
                "devise": "EUR",
                "type": "CREDIT"
            }
        """;

        // ObjectMapper pour la désérialisation
        ObjectMapper objectMapper = new ObjectMapper();

        // Désérialisation
        RechargementCmd rechargementCmd = objectMapper.readValue(json, RechargementCmd.class);

        // Assertions
        assertEquals("2250758286600", rechargementCmd.getUsername());
        assertEquals("100", rechargementCmd.getAmount());
        assertEquals("EUR", rechargementCmd.getDevise());
        assertEquals("CREDIT", rechargementCmd.getType());
    }
}
