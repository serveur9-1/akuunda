package org.akuunda.akuundawallet.service;

import org.akuunda.akuundawallet.common.security.HttpClientCall;
import org.akuunda.akuundawallet.keycloak.api.dto.external.ExternalSigningMethod;
import org.akuunda.akuundawallet.keycloak.api.dto.external.ExternalUserCreateResponse;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkunndaUserClientService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.impl.AkunndaSigningMethodClientServiceImpl;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.http.*;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AkunndaSigningMethodClientServiceTest {

    @Mock
    private AkunndaUserClientService akunndaUserClientService;

    @InjectMocks
    private AkunndaSigningMethodClientServiceImpl service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // injecter les valeurs des @Value manuellement
        setField("venlyServerUrlBase", "https://api.venly.io");
        setField("venlyAuthentificationURL", "https://auth.venly.io");
        setField("venlyClientId", "fake-client-id");
        setField("venlyClientSecret", "fake-client-secret");
    }

    private void setField(String fieldName, String value) {
        try {
            var field = AkunndaSigningMethodClientServiceImpl.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(service, value);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    // ==============================
    //  TEST createUserPinSigningMethod
    // ==============================
    @Test
    void testCreateUserPinSigningMethod_Success() throws Exception {
        // Mock token
        HttpResponse<String> mockAuthResponse = mock(HttpResponse.class);
        when(mockAuthResponse.body()).thenReturn("FAKE_TOKEN");

        // Mock appel HTTP vers Venly
        HttpResponse<String> mockVenlyResponse = mock(HttpResponse.class);
        when(mockVenlyResponse.statusCode()).thenReturn(200);
        when(mockVenlyResponse.body()).thenReturn("{\"status\":\"ok\"}");

        // Mock des méthodes statiques de HttpClientCall
        try (MockedStatic<HttpClientCall> mockedHttp = mockStatic(HttpClientCall.class)) {
            mockedHttp.when(() -> HttpClientCall.getAuthenticationToken(anyString(), anyString(), anyString()))
                    .thenReturn(mockAuthResponse);
            mockedHttp.when(() -> HttpClientCall.httpGetWithBody(anyString(), anyString(), anyString()))
                    .thenReturn(mockVenlyResponse);

            ResponseEntity<String> response = service.createUserPinSigningMethod("user123", "{\"body\":\"test\"}");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().contains("ok"));
        }
    }

    @Test
    void testCreateUserPinSigningMethod_NullResponse() throws Exception {
        HttpResponse<String> mockAuthResponse = mock(HttpResponse.class);
        when(mockAuthResponse.body()).thenReturn("TOKEN");

        HttpResponse<String> mockVenlyResponse = mock(HttpResponse.class);
        when(mockVenlyResponse.statusCode()).thenReturn(400);
        when(mockVenlyResponse.body()).thenReturn("error");

        try (MockedStatic<HttpClientCall> mockedHttp = mockStatic(HttpClientCall.class)) {
            mockedHttp.when(() -> HttpClientCall.getAuthenticationToken(anyString(), anyString(), anyString()))
                    .thenReturn(mockAuthResponse);
            mockedHttp.when(() -> HttpClientCall.httpGetWithBody(anyString(), anyString(), anyString()))
                    .thenReturn(mockVenlyResponse);

            ResponseEntity<String> response = service.createUserPinSigningMethod("user123", "body");

            assertEquals(HttpStatus.EXPECTATION_FAILED, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().contains("Error"));
        }
    }

    @Test
    void testCreateUserPinSigningMethod_Exception() throws Exception {
        HttpResponse<String> mockAuthResponse = mock(HttpResponse.class);
        when(mockAuthResponse.body()).thenReturn("TOKEN");

        try (MockedStatic<HttpClientCall> mockedHttp = mockStatic(HttpClientCall.class)) {
            mockedHttp.when(() -> HttpClientCall.getAuthenticationToken(anyString(), anyString(), anyString()))
                    .thenReturn(mockAuthResponse);
            mockedHttp.when(() -> HttpClientCall.httpGetWithBody(anyString(), anyString(), anyString()))
                    .thenThrow(new IOException("Network error"));

            ResponseEntity<String> response = service.createUserPinSigningMethod("user123", "body");

            assertEquals(HttpStatus.EXPECTATION_FAILED, response.getStatusCode());
        }
    }

    // ==============================
    // 🧩 TEST getAllSigningMethods
    // ==============================
    @Test
    void testGetAllSigningMethods_Success() {
        // Mock user response
        var method1 = new ExternalSigningMethod();
        var method2 = new ExternalSigningMethod();
        var result = new Object() {
            List<ExternalSigningMethod> getSigningMethods() {
                return List.of(method1, method2);
            }
        };

        var bodyMock = new ExternalUserCreateResponse();

        ResponseEntity<ExternalUserCreateResponse> mockUserResponse =
                new ResponseEntity<>(bodyMock, HttpStatus.OK);

        when(akunndaUserClientService.getUserById("user123"))
                .thenReturn(mockUserResponse);

        ResponseEntity<List<ExternalSigningMethod>> response = service.getAllSigningMethods("user123");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
    }

    @Test
    void testGetAllSigningMethods_Failure() {
        var bodyMock = new ExternalUserCreateResponse();

        ResponseEntity<ExternalUserCreateResponse> mockUserResponse =
                new ResponseEntity<>(bodyMock, HttpStatus.OK);

        when(akunndaUserClientService.getUserById("user123"))
                .thenReturn(mockUserResponse);

        ResponseEntity<List<ExternalSigningMethod>> response = service.getAllSigningMethods("user123");

        assertEquals(HttpStatus.EXPECTATION_FAILED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());
    }

    // ==============================
    // 🧩 TEST createUserOtherSigningMethod
    // ==============================
    @Test
    void testCreateUserOtherSigningMethod_Success() throws Exception {
        HttpResponse<String> mockAuthResponse = mock(HttpResponse.class);
        when(mockAuthResponse.body()).thenReturn("TOKEN");

        HttpResponse<String> mockVenlyResponse = mock(HttpResponse.class);
        when(mockVenlyResponse.statusCode()).thenReturn(200);
        when(mockVenlyResponse.body()).thenReturn("{\"status\":\"ok\"}");

        try (MockedStatic<HttpClientCall> mockedHttp = mockStatic(HttpClientCall.class)) {
            mockedHttp.when(() -> HttpClientCall.getAuthenticationToken(anyString(), anyString(), anyString()))
                    .thenReturn(mockAuthResponse);
            mockedHttp.when(() -> HttpClientCall.httpGetWithBodyAndHeader(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockVenlyResponse);

            ResponseEntity<String> response = service.createUserOtherSigningMethod("user123", "body", "1234");

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertTrue(response.getBody().contains("ok"));
        }
    }

    @Test
    void testCreateUserOtherSigningMethod_Exception() throws Exception {
        HttpResponse<String> mockAuthResponse = mock(HttpResponse.class);
        when(mockAuthResponse.body()).thenReturn("TOKEN");

        try (MockedStatic<HttpClientCall> mockedHttp = mockStatic(HttpClientCall.class)) {
            mockedHttp.when(() -> HttpClientCall.getAuthenticationToken(anyString(), anyString(), anyString()))
                    .thenReturn(mockAuthResponse);
            mockedHttp.when(() -> HttpClientCall.httpGetWithBodyAndHeader(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new InterruptedException("Timeout"));

            ResponseEntity<String> response = service.createUserOtherSigningMethod("user123", "body", "1234");

            assertEquals(HttpStatus.EXPECTATION_FAILED, response.getStatusCode());
        }
    }
}
