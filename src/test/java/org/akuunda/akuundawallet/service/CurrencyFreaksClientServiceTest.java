package org.akuunda.akuundawallet.service;

import org.akuunda.akuundawallet.wallet.api.dto.external.CurrencyConversionResponse;
import org.akuunda.akuundawallet.wallet.service.infrastructure.impl.CurrencyFreaksClientServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import java.io.IOException;
import java.net.http.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


public class CurrencyFreaksClientServiceTest {

    @InjectMocks
    private CurrencyFreaksClientServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = Mockito.spy(new CurrencyFreaksClientServiceImpl());

        // Configuration par défaut
        setField("currencyFreaksApiUrl", "https://api.currencyfreaks.com");
        setField("currencyFreaksApiKey", "fake-key");
    }

    // =============================
    // 🔧 MÉTHODE UTILITAIRE POUR SETTER LES CHAMPS PRIVÉS
    // =============================
    private void setField(String fieldName, String value) {
        try {
            var field = CurrencyFreaksClientServiceImpl.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(service, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // =============================
    // 🧩 TESTS DE VALIDATION DES PARAMÈTRES
    // =============================

    @Test
    void testConvertCurrency_InvalidFromOrTo() {
        ResponseEntity<CurrencyConversionResponse> response = service.convertCurrency(null, "USD", 10);
        assertEquals(HttpStatus.NOT_ACCEPTABLE, response.getStatusCode());

        response = service.convertCurrency("XOF", null, 10);
        assertEquals(HttpStatus.NOT_ACCEPTABLE, response.getStatusCode());
    }

    @Test
    void testConvertCurrency_MissingApiUrl() {
        setField("currencyFreaksApiUrl", null);
        ResponseEntity<CurrencyConversionResponse> response = service.convertCurrency("XOF", "USD", 10);
        assertEquals(HttpStatus.NOT_ACCEPTABLE, response.getStatusCode());
    }

    @Test
    void testConvertCurrency_MissingApiKey() {
        setField("currencyFreaksApiKey", "");
        ResponseEntity<CurrencyConversionResponse> response = service.convertCurrency("XOF", "USD", 10);
        assertEquals(HttpStatus.NOT_ACCEPTABLE, response.getStatusCode());
    }

    @Test
    void testConvertCurrency_InvalidAmount() {
        ResponseEntity<CurrencyConversionResponse> response = service.convertCurrency("XOF", "USD", 0);
        assertEquals(HttpStatus.NOT_ACCEPTABLE, response.getStatusCode());
    }

    @Test
    void testConvertCurrency_SameCurrency() {
        ResponseEntity<CurrencyConversionResponse> response = service.convertCurrency("XOF", "XOF", 100);
        assertEquals(HttpStatus.NOT_ACCEPTABLE, response.getStatusCode());
    }

    // =============================
    // 🧩 TESTS D'APPEL EXTERNE MOCKÉ (HttpClient)
    // =============================

    @Test
    void testConvertCurrency_Success() throws Exception {
        // --- Mock de la réponse JSON ---
        String jsonResponse = "{\"convertedAmount\":\"6543.21\"}";

        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);

        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        // --- On injecte un HttpClient mockable ---
        CurrencyFreaksClientServiceImpl spyService = Mockito.spy(service);
        doReturn(mockClient).when(spyService).createHttpClient(); // on crée cette méthode utilitaire

        ResponseEntity<CurrencyConversionResponse> response =
                spyService.convertCurrency("XOF", "USD", 100.0);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("6543.21", response.getBody().getConvertedAmount());
    }

    @Test
    void testConvertCurrency_HttpError() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(400);
        when(mockResponse.body()).thenReturn("Bad Request");

        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        CurrencyFreaksClientServiceImpl spyService = Mockito.spy(service);
        doReturn(mockClient).when(spyService).createHttpClient();

        ResponseEntity<CurrencyConversionResponse> response =
                spyService.convertCurrency("XOF", "USD", 100.0);

        assertEquals(HttpStatus.EXPECTATION_FAILED, response.getStatusCode());
    }

    @Test
    void testConvertCurrency_Exception() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Network error"));

        CurrencyFreaksClientServiceImpl spyService = Mockito.spy(service);
        doReturn(mockClient).when(spyService).createHttpClient();

        ResponseEntity<CurrencyConversionResponse> response =
                spyService.convertCurrency("XOF", "USD", 100.0);

        assertEquals(HttpStatus.EXPECTATION_FAILED, response.getStatusCode());
    }
}
