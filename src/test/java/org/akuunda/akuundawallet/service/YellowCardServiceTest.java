package org.akuunda.akuundawallet.service;

import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.transfert.impl.service.TransfertService;
import org.akuunda.akuundawallet.wallet.api.dao.OperationRepository;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.api.dto.external.*;
import org.akuunda.akuundawallet.wallet.api.entities.Operation;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.wallet.api.service.WalletService;
import org.akuunda.akuundawallet.wallet.config.YellowCardChannelConfig;
import org.akuunda.akuundawallet.wallet.service.infrastructure.CurrencyFreaksClientService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.impl.AkuundaYellowCardClientServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.http.*;

import java.net.http.HttpRequest;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class YellowCardServiceTest {

    @Mock
    private TransfertService transfertService;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private OperationRepository operationRepository;
    @Mock
    private CurrencyFreaksClientService freaksClientService;
    @Mock
    private WalletService walletService;
    @Mock
    private org.akuunda.akuundawallet.wallet.service.infrastructure.AkunndaWalletClientService walletClientService;
    @Mock
    private YellowCardChannelConfig channelConfig;
    @Mock
    private HttpClient httpClient; // Pour les méthodes GET externes

    @InjectMocks
    private AkuundaYellowCardClientServiceImpl yellowCardService;

    private Users user;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        
        // Set @Value injected fields using reflection
        ReflectionTestUtils.setField(yellowCardService, "baseUrl", "https://api.yellowcard.io/business");
        ReflectionTestUtils.setField(yellowCardService, "apiKey", "test-api-key");
        ReflectionTestUtils.setField(yellowCardService, "apiSecret", "test-api-secret");

        user = new Users();
        user.setUsername("22997000000");
        user.setUserId("user123");

        wallet = new Wallet();
        wallet.setUsers(user);
        wallet.setAddress("0xABC123");
        wallet.setBalance(100.0);
        wallet.setDeviseBalance(100.0);
    }

    // ===========================
    // 🟩 TESTS createCollection()
    // ===========================
    @Test
    void testCreateCollection_Success() {
        OnRampRequest request = new OnRampRequest();
        RecipientDto recipient = new RecipientDto();
        recipient.setPhone("22997000000");
        recipient.setName("John Doe");
        recipient.setCountry("BJ");
        request.setRecipient(recipient);
        request.setCurrency("XOF");
        request.setAmount(10.0);
        request.setCountry("BJ");
        request.setChannelId("MOMO");

        when(userRepository.getUsersByUsername("22997000000")).thenReturn(user);
        when(walletRepository.findByUsers(user)).thenReturn(wallet);

        CurrencyConversionResponse mockConversion = new CurrencyConversionResponse();
        mockConversion.setConvertedAmount("10");
        when(freaksClientService.convertCurrency(anyString(), anyString(), anyDouble()))
                .thenReturn(ResponseEntity.ok(mockConversion));

        AkuundaYellowCardClientServiceImpl spyService = Mockito.spy(yellowCardService);
        ResponseEntity<Object> apiResponse = ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("status", "success"));
        doReturn(apiResponse).when(spyService)
                .getObjectResponseEntity(anyString(), anyString(), anyString(), anyString());

        ResponseEntity<Object> response = spyService.createCollection(request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().toString().contains("success"));
        verify(walletRepository, times(1)).saveAndFlush(any(Wallet.class));
        verify(operationRepository, times(1)).save(any(Operation.class));
    }

    @Test
    void testCreateCollection_UserNotFound() {
        OnRampRequest request = new OnRampRequest();
        RecipientDto recipient = new RecipientDto();
        recipient.setPhone("22999999999");
        request.setRecipient(recipient);
        request.setAmount(10);

        when(userRepository.getUsersByUsername("22999999999")).thenReturn(null);

        ResponseEntity<Object> response = yellowCardService.createCollection(request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().toString().contains("Utilisateur non trouvé"));
    }

    // ================================
    // 🟩 TESTS GET endpoints (HttpClient)
    // ================================

    @Test
    void testGetChannels_Success() throws Exception {
        String jsonResponse = "{\"channels\": [\"momo\", \"bank\"]}";
        HttpResponse mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);

        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        AkuundaYellowCardClientServiceImpl spyService = Mockito.spy(yellowCardService);
        doReturn(mockClient).when(spyService).createHttpClient(); // méthode utilitaire à ajouter

        ResponseEntity<String> response = spyService.getChannels("BJ");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(jsonResponse, response.getBody());
    }

    @Test
    void testGetChannels_HttpError() throws Exception {
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(400);
        when(mockResponse.body()).thenReturn("Bad request");

        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        AkuundaYellowCardClientServiceImpl spyService = Mockito.spy(yellowCardService);
        doReturn(mockClient).when(spyService).createHttpClient();

        ResponseEntity<String> response = spyService.getChannels("BJ");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void testGetChannels_InternalError() throws Exception {
        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenThrow(new IOException("Network error"));

        AkuundaYellowCardClientServiceImpl spyService = Mockito.spy(yellowCardService);
        doReturn(mockClient).when(spyService).createHttpClient();

        ResponseEntity<String> response = spyService.getChannels("BJ");

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
    }

    @Test
    void testGetRates_Success() throws Exception {
        String jsonResponse = "{\"USD\": 650.0}";
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);

        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        AkuundaYellowCardClientServiceImpl spyService = Mockito.spy(yellowCardService);
        doReturn(mockClient).when(spyService).createHttpClient();

        ResponseEntity<String> response = spyService.getRates("XOF");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(jsonResponse, response.getBody());
    }

    @Test
    void testGetNetworks_Success() throws Exception {
        String jsonResponse = "{\"networks\": [\"POLYGON\", \"BSC\"]}";
        HttpResponse<String> mockResponse = mock(HttpResponse.class);
        when(mockResponse.statusCode()).thenReturn(200);
        when(mockResponse.body()).thenReturn(jsonResponse);

        HttpClient mockClient = mock(HttpClient.class);
        when(mockClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResponse);

        AkuundaYellowCardClientServiceImpl spyService = Mockito.spy(yellowCardService);
        doReturn(mockClient).when(spyService).createHttpClient();

        ResponseEntity<String> response = spyService.getNetworks("BJ");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(jsonResponse, response.getBody());
    }

    // ===============================================
    // 🟩 TESTS Channel Resolution (New Feature)
    // ===============================================

    @Test
    void testCreateCollection_WithChannelResolution() {
        // Arrange
        OnRampRequest request = new OnRampRequest();
        RecipientDto recipient = new RecipientDto();
        recipient.setPhone("22597000000");
        recipient.setName("Test User");
        recipient.setCountry("CI");
        request.setRecipient(recipient);
        request.setCurrency("XOF");
        request.setAmount(10000.0);
        request.setCountry("CI");
        request.setChannelId("wrong-channel-id");
        
        // Add source with networkId
        SourceDto source = new SourceDto();
        source.setNetworkId("orange-money-ci");
        source.setAccountNumber("22597000000");
        source.setAccountType("momo");
        request.setSource(source);

        when(userRepository.getUsersByUsername("22597000000")).thenReturn(user);
        when(walletRepository.findByUsers(user)).thenReturn(wallet);
        
        // Mock CurrencyFreaksClientService for fallback conversion
        CurrencyConversionResponse mockConversion = new CurrencyConversionResponse();
        mockConversion.setConvertedAmount("15.38");
        when(freaksClientService.convertCurrency(anyString(), anyString(), anyDouble()))
                .thenReturn(ResponseEntity.ok(mockConversion));

        // Mock the static config to resolve orange-money-ci -> channel2
        when(channelConfig.resolve("CI", "deposit", "orange-money-ci")).thenReturn("channel2");

        AkuundaYellowCardClientServiceImpl spyService = Mockito.spy(yellowCardService);
        
        // Mock getRates to return valid rates (needed for conversion)
        String ratesJson = "[{\"buy\":650.0,\"sell\":640.0,\"currency\":\"XOF\"}]";
        doReturn(ResponseEntity.ok(ratesJson)).when(spyService).getRates(anyString());
        doReturn(ResponseEntity.ok(ratesJson)).when(spyService).getRatesByChannelId(anyString(), anyString());
        
        // Mock API call response
        ResponseEntity<Object> apiResponse = ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("status", "success"));
        doReturn(apiResponse).when(spyService)
                .getObjectResponseEntity(anyString(), anyString(), anyString(), anyString());

        // Act
        ResponseEntity<Object> response = spyService.createCollection(request);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        // Verify that the channelId was resolved to channel2 (supports orange-money-ci)
        assertEquals("channel2", request.getChannelId());
        // Verify that static config was called (ZERO API calls to YellowCard)
        verify(channelConfig, times(1)).resolve("CI", "deposit", "orange-money-ci");
    }

    @Test
    void testCreateCollection_ChannelResolution_FallbackOnNoMapping() {
        // Arrange - Test that when no static mapping exists, original channel is kept
        OnRampRequest request = new OnRampRequest();
        RecipientDto recipient = new RecipientDto();
        recipient.setPhone("22597000000");
        recipient.setName("Test User");
        recipient.setCountry("CI");
        request.setRecipient(recipient);
        request.setCurrency("XOF");
        request.setAmount(10000.0);
        request.setCountry("CI");
        request.setChannelId("original-channel-id");
        
        SourceDto source = new SourceDto();
        source.setNetworkId("unknown-network"); // Network not in config
        source.setAccountNumber("22597000000");
        source.setAccountType("momo");
        request.setSource(source);

        when(userRepository.getUsersByUsername("22597000000")).thenReturn(user);
        when(walletRepository.findByUsers(user)).thenReturn(wallet);
        
        // Mock CurrencyFreaksClientService for fallback conversion
        CurrencyConversionResponse mockConversion = new CurrencyConversionResponse();
        mockConversion.setConvertedAmount("15.38");
        when(freaksClientService.convertCurrency(anyString(), anyString(), anyDouble()))
                .thenReturn(ResponseEntity.ok(mockConversion));

        // Mock config to return null (no mapping for this network)
        when(channelConfig.resolve("CI", "deposit", "unknown-network")).thenReturn(null);

        AkuundaYellowCardClientServiceImpl spyService = Mockito.spy(yellowCardService);
        
        // Mock getRates to return valid rates (needed for conversion)
        String ratesJson = "[{\"buy\":650.0,\"sell\":640.0,\"currency\":\"XOF\"}]";
        doReturn(ResponseEntity.ok(ratesJson)).when(spyService).getRates(anyString());
        doReturn(ResponseEntity.ok(ratesJson)).when(spyService).getRatesByChannelId(anyString(), anyString());
        
        ResponseEntity<Object> apiResponse = ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("status", "success"));
        doReturn(apiResponse).when(spyService)
                .getObjectResponseEntity(anyString(), anyString(), anyString(), anyString());

        // Act
        ResponseEntity<Object> response = spyService.createCollection(request);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        // Verify that the original channelId is preserved when no mapping exists
        assertEquals("original-channel-id", request.getChannelId());
    }

    @Test
    void testCreateCollection_ChannelResolution_KeepsCorrectChannel() {
        // Arrange - Test that when channelId is already correct, it's kept as-is
        OnRampRequest request = new OnRampRequest();
        RecipientDto recipient = new RecipientDto();
        recipient.setPhone("22597000000");
        recipient.setName("Test User");
        recipient.setCountry("BJ");
        request.setRecipient(recipient);
        request.setCurrency("XOF");
        request.setAmount(10000.0);
        request.setCountry("BJ");
        request.setChannelId("correct-channel"); // Already correct
        
        SourceDto source = new SourceDto();
        source.setNetworkId("mtn-benin");
        source.setAccountNumber("22597000000");
        source.setAccountType("momo");
        request.setSource(source);

        when(userRepository.getUsersByUsername("22597000000")).thenReturn(user);
        when(walletRepository.findByUsers(user)).thenReturn(wallet);
        
        // Mock CurrencyFreaksClientService for fallback conversion
        CurrencyConversionResponse mockConversion = new CurrencyConversionResponse();
        mockConversion.setConvertedAmount("15.38");
        when(freaksClientService.convertCurrency(anyString(), anyString(), anyDouble()))
                .thenReturn(ResponseEntity.ok(mockConversion));

        // Mock config to return the correct channel (which is same as what was provided)
        when(channelConfig.resolve("BJ", "deposit", "mtn-benin")).thenReturn("correct-channel");

        AkuundaYellowCardClientServiceImpl spyService = Mockito.spy(yellowCardService);
        
        // Mock getRates to return valid rates (needed for conversion)
        String ratesJson = "[{\"buy\":650.0,\"sell\":640.0,\"currency\":\"XOF\"}]";
        doReturn(ResponseEntity.ok(ratesJson)).when(spyService).getRates(anyString());
        doReturn(ResponseEntity.ok(ratesJson)).when(spyService).getRatesByChannelId(anyString(), anyString());
        
        ResponseEntity<Object> apiResponse = ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("status", "success"));
        doReturn(apiResponse).when(spyService)
                .getObjectResponseEntity(anyString(), anyString(), anyString(), anyString());

        // Act
        ResponseEntity<Object> response = spyService.createCollection(request);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        // Verify that the channelId was kept as correct-channel
        assertEquals("correct-channel", request.getChannelId());
    }

    @Test
    void testCreateCollection_NoChannelResolution_WhenNetworkIdMissing() {
        // Arrange
        OnRampRequest request = new OnRampRequest();
        RecipientDto recipient = new RecipientDto();
        recipient.setPhone("22597000000");
        recipient.setName("Test User");
        recipient.setCountry("CI");
        request.setRecipient(recipient);
        request.setCurrency("XOF");
        request.setAmount(10000.0);
        request.setCountry("CI");
        request.setChannelId("original-channel");
        
        // Source without networkId
        SourceDto source = new SourceDto();
        source.setAccountNumber("22597000000");
        source.setAccountType("momo");
        request.setSource(source);

        when(userRepository.getUsersByUsername("22597000000")).thenReturn(user);
        when(walletRepository.findByUsers(user)).thenReturn(wallet);
        
        // Mock CurrencyFreaksClientService for fallback conversion
        CurrencyConversionResponse mockConversion = new CurrencyConversionResponse();
        mockConversion.setConvertedAmount("15.38");
        when(freaksClientService.convertCurrency(anyString(), anyString(), anyDouble()))
                .thenReturn(ResponseEntity.ok(mockConversion));

        AkuundaYellowCardClientServiceImpl spyService = Mockito.spy(yellowCardService);
        
        // Mock getRates to return valid rates (needed for conversion)
        String ratesJson = "[{\"buy\":650.0,\"sell\":640.0,\"currency\":\"XOF\"}]";
        doReturn(ResponseEntity.ok(ratesJson)).when(spyService).getRates(anyString());
        doReturn(ResponseEntity.ok(ratesJson)).when(spyService).getRatesByChannelId(anyString(), anyString());
        
        ResponseEntity<Object> apiResponse = ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("status", "success"));
        doReturn(apiResponse).when(spyService)
                .getObjectResponseEntity(anyString(), anyString(), anyString(), anyString());

        // Act
        ResponseEntity<Object> response = spyService.createCollection(request);

        // Assert
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        // Verify that the channelId remains unchanged when networkId is not provided
        assertEquals("original-channel", request.getChannelId());
        // Verify channelConfig.resolve was never called (no networkId means no resolution attempt)
        verify(channelConfig, never()).resolve(anyString(), anyString(), anyString());
    }

    // ===============================================
    // 🟩 TESTS OffRamp NetworkId Resolution (CI)
    // ===============================================

    @Test
    void testCreateOffRamp_OldNetworkId_IsReplacedByNewNetworkId() throws Exception {
        // Arrange: Frontend sends old Wave CI networkId
        OffRampRequest request = buildCiOffRampRequest(
                "8d18204e-b51f-4554-815d-71586d0dac13", // old Wave networkId
                "33a82864-6460-43d7-9fc0-911f9bd8d50a"  // old channelId
        );

        when(userRepository.getUsersByUsername("22597000000")).thenReturn(user);
        wallet.setGasBalance(1.0);
        when(walletRepository.findByUsers(user)).thenReturn(wallet);
        when(walletService.getWalletBalance("22597000000")).thenReturn(ResponseEntity.ok("100.0"));

        AkuundaYellowCardClientServiceImpl spyService = Mockito.spy(yellowCardService);

        // Mock getChannels for channelId resolution and rate conversion
        String channelsJson = "{\"channels\":[{\"id\":\"2a210914-ea86-4a0c-8a67-f4a5f7e22655\"," +
                "\"rampType\":\"withdraw\",\"status\":\"active\",\"apiStatus\":\"active\"," +
                "\"apiMax\":500000,\"apiMin\":100}]}";
        doReturn(ResponseEntity.ok(channelsJson)).when(spyService).getChannels(anyString());

        // Mock getNetworks for networkId resolution
        String networksJson = "{\"networks\":[" +
                "{\"id\":\"90d4d5ea-71a3-4490-8bb7-0f1e0dfa735b\",\"name\":\"Wave\"}," +
                "{\"id\":\"81837c65-f0d8-4e21-a354-5cbfc4bfe533\",\"name\":\"MTN\"}," +
                "{\"id\":\"401a79b8-50bd-41fc-9102-b5d4650a02aa\",\"name\":\"Orange\"}" +
                "]}";
        doReturn(ResponseEntity.ok(networksJson)).when(spyService).getNetworks("CI");

        // Mock rates in proper YellowCard format
        String ratesJson = "{\"rates\":[{\"code\":\"XOF\",\"sell\":640.0,\"buy\":650.0}]}";
        doReturn(ResponseEntity.ok(ratesJson)).when(spyService).getRates(anyString());
        doReturn(ResponseEntity.ok(ratesJson)).when(spyService).getRatesByChannelId(anyString(), anyString());

        // Mock API payment response
        String paymentResponseJson = "{\"id\":\"pay123\",\"sequenceId\":\"seq123\",\"status\":\"pending\"}";
        doReturn(ResponseEntity.status(HttpStatus.CREATED).body((Object) paymentResponseJson))
                .when(spyService).getObjectResponseEntity(anyString(), anyString(), anyString(), anyString());

        // Act
        ResponseEntity<Object> response = spyService.createOffRampPaiements(request, "1234", "22597000000");

        // Assert: new Wave networkId should be used
        assertEquals("90d4d5ea-71a3-4490-8bb7-0f1e0dfa735b", request.getDestination().getNetworkId());
        // Assert: new channelId should be used
        assertEquals("2a210914-ea86-4a0c-8a67-f4a5f7e22655", request.getChannelId());
        verify(spyService, times(1)).getNetworks("CI");
    }

    @Test
    void testCreateOffRamp_UnknownNetworkId_IsNotReplaced() throws Exception {
        // Arrange: Frontend sends unknown networkId (not in static mapping)
        String unknownNetworkId = "ffffffff-ffff-ffff-ffff-ffffffffffff";
        OffRampRequest request = buildCiOffRampRequest(
                unknownNetworkId,
                "33a82864-6460-43d7-9fc0-911f9bd8d50a"
        );

        when(userRepository.getUsersByUsername("22597000000")).thenReturn(user);
        wallet.setGasBalance(1.0);
        when(walletRepository.findByUsers(user)).thenReturn(wallet);
        when(walletService.getWalletBalance("22597000000")).thenReturn(ResponseEntity.ok("100.0"));

        AkuundaYellowCardClientServiceImpl spyService = Mockito.spy(yellowCardService);

        // Mock getChannels
        String channelsJson = "{\"channels\":[{\"id\":\"2a210914-ea86-4a0c-8a67-f4a5f7e22655\"," +
                "\"rampType\":\"withdraw\",\"status\":\"active\",\"apiStatus\":\"active\"," +
                "\"apiMax\":500000,\"apiMin\":100}]}";
        doReturn(ResponseEntity.ok(channelsJson)).when(spyService).getChannels(anyString());

        // Mock rates
        String ratesJson = "{\"rates\":[{\"code\":\"XOF\",\"sell\":640.0,\"buy\":650.0}]}";
        doReturn(ResponseEntity.ok(ratesJson)).when(spyService).getRates(anyString());
        doReturn(ResponseEntity.ok(ratesJson)).when(spyService).getRatesByChannelId(anyString(), anyString());

        String paymentResponseJson = "{\"id\":\"pay123\",\"sequenceId\":\"seq123\",\"status\":\"pending\"}";
        doReturn(ResponseEntity.status(HttpStatus.CREATED).body((Object) paymentResponseJson))
                .when(spyService).getObjectResponseEntity(anyString(), anyString(), anyString(), anyString());

        // Act
        spyService.createOffRampPaiements(request, "1234", "22597000000");

        // Assert: unknown networkId preserved as-is, getNetworks never called
        assertEquals(unknownNetworkId, request.getDestination().getNetworkId());
        verify(spyService, never()).getNetworks(anyString());
    }

    @Test
    void testCreateOffRamp_NonCiCountry_NetworkIdNotResolved() throws Exception {
        // Arrange: OffRamp for another country (BJ) - no networkId resolution
        OffRampRequest request = buildOffRampRequest(
                "BJ",
                "a25d6c19-752e-4807-82ab-a60909c0c68e", // old MTN CI networkId (should be ignored for BJ)
                "some-channel-id"
        );

        when(userRepository.getUsersByUsername("22997000000")).thenReturn(user);
        wallet.setGasBalance(1.0);
        when(walletRepository.findByUsers(user)).thenReturn(wallet);
        when(walletService.getWalletBalance("22997000000")).thenReturn(ResponseEntity.ok("100.0"));

        AkuundaYellowCardClientServiceImpl spyService = Mockito.spy(yellowCardService);

        // Mock rates
        String ratesJson = "{\"rates\":[{\"code\":\"XOF\",\"sell\":640.0,\"buy\":650.0}]}";
        doReturn(ResponseEntity.ok(ratesJson)).when(spyService).getRates(anyString());
        doReturn(ResponseEntity.ok(ratesJson)).when(spyService).getRatesByChannelId(anyString(), anyString());
        // Needed for getFirstChannelIdForCountry during rate conversion (returns empty channels)
        doReturn(ResponseEntity.ok("{\"channels\":[]}")).when(spyService).getChannels("BJ");

        String paymentResponseJson = "{\"id\":\"pay123\",\"sequenceId\":\"seq123\",\"status\":\"pending\"}";
        doReturn(ResponseEntity.status(HttpStatus.CREATED).body((Object) paymentResponseJson))
                .when(spyService).getObjectResponseEntity(anyString(), anyString(), anyString(), anyString());

        // Act
        spyService.createOffRampPaiements(request, "1234", "22997000000");

        // Assert: no networkId resolution for BJ (getNetworks never called)
        verify(spyService, never()).getNetworks(anyString());
        assertEquals("a25d6c19-752e-4807-82ab-a60909c0c68e", request.getDestination().getNetworkId());
    }

    // Helper to build a CI OffRampRequest
    private OffRampRequest buildCiOffRampRequest(String networkId, String channelId) {
        return buildOffRampRequest("CI", networkId, channelId);
    }

    private OffRampRequest buildOffRampRequest(String country, String networkId, String channelId) {
        OffRampRequest request = new OffRampRequest();
        request.setCountry(country);
        request.setChannelId(channelId);
        request.setCurrency("XOF");
        request.setAmount(5000.0);
        request.setForceAccept(true);
        request.setDirectSettlement(false);

        RecipientDto sender = new RecipientDto();
        sender.setName("Kouassi David");
        sender.setCountry(country);
        sender.setPhone(country.equals("CI") ? "+2250709997042" : "+22997000000");
        sender.setEmail("test@example.com");
        sender.setIdNumber("CI123456");
        sender.setIdType("NIN");
        request.setSender(sender);

        SourceDto destination = new SourceDto();
        destination.setAccountName("Aman David");
        destination.setAccountNumber(country.equals("CI") ? "+2250709997042" : "+22997000000");
        destination.setAccountType("momo");
        destination.setNetworkId(networkId);
        request.setDestination(destination);

        SettlementInfoDto settlementInfo = new SettlementInfoDto();
        settlementInfo.setCryptoCurrency("USDC");
        settlementInfo.setCryptoNetwork("POLYGON");
        settlementInfo.setCryptoAmount(1000.0); // 1000 XOF / 640 ≈ 1.56 USD (doit être >= 1 USD)
        request.setSettlementInfo(settlementInfo);

        return request;
    }
}
