package org.akuunda.akuundawallet.wallet.service.yellowcard;

import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YellowCardWidgetQuoteAggregatorTest {

    @Test
    void splitAmount_respectsMaxChunk() {
        assertEquals(List.of(300_000.0, 300_000.0, 150_000.0), YellowCardWidgetQuoteAggregator.splitAmount(750_000));
        assertEquals(List.of(100_000.0), YellowCardWidgetQuoteAggregator.splitAmount(100_000));
        assertEquals(List.of(300_000.0), YellowCardWidgetQuoteAggregator.splitAmount(300_000));
    }

    @Test
    void isWidgetFallbackError_detectsDisabledChannel() {
        ResponseEntity<String> r = ResponseEntity.status(400).body(
                "{\"code\":\"PaymentValidationError\",\"message\":\"channel x disabled for widget use\"}");
        assertTrue(YellowCardWidgetQuoteAggregator.isWidgetFallbackError(r));
    }
}
