package org.akuunda.akuundawallet.wallet.service.impl;

import org.akuunda.akuundawallet.common.Exceptions.ErrorResponse;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.dao.PermanentLinkRepository;
import org.akuunda.akuundawallet.wallet.api.dao.PermanentLinkSessionRepository;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.api.dto.CreatePermanentLinkRequest;
import org.akuunda.akuundawallet.wallet.api.dto.CreatePermanentLinkSessionRequest;
import org.akuunda.akuundawallet.wallet.api.dto.PermanentLinkResponse;
import org.akuunda.akuundawallet.wallet.api.dto.PermanentLinkSessionResponse;
import org.akuunda.akuundawallet.wallet.api.dto.PermanentLinkStatsResponse;
import org.akuunda.akuundawallet.wallet.api.entities.PermanentLink;
import org.akuunda.akuundawallet.wallet.api.entities.PermanentLinkSession;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.wallet.service.PaymentFactoryContractService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

public class PermanentLinkServiceImplTest {

    @Mock
    private PermanentLinkRepository permanentLinkRepository;

    @Mock
    private PermanentLinkSessionRepository permanentLinkSessionRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private PaymentFactoryContractService paymentFactoryContractService;

    @InjectMocks
    private PermanentLinkServiceImpl service;

    private Users user;
    private Wallet wallet;
    private Wallet adminWallet;
    private PermanentLink permanentLink;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(service, "adminWalletId", "admin-wallet-id");
        ReflectionTestUtils.setField(service, "adminWalletAddress", "");
        ReflectionTestUtils.setField(service, "baseUrl", "https://qr.akuunda-pay.io");

        user = new Users();
        user.setUsername("002250759146858");
        user.setUserId("user-123");
        user.setFirstname("Jean");
        user.setLastname("Dupont");

        wallet = new Wallet();
        wallet.setId("creator-wallet-id");
        wallet.setUsers(user);
        wallet.setAddress("0xABC123");

        adminWallet = new Wallet();
        adminWallet.setId("admin-wallet-id");
        adminWallet.setAddress("0xADMIN");

        permanentLink = PermanentLink.builder()
                .id(1L)
                .merchantSlug("boutique-mama-coco")
                .creator(user)
                .description("Paiement boutique")
                .amount(null)
                .currency(null)
                .isActive(true)
                .totalSessions(0)
                .totalCompletedPayments(0)
                .totalAmountReceived(0.0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    // =====================================================================
    // createPermanentLink
    // =====================================================================

    @Test
    void createPermanentLink_Success() {
        CreatePermanentLinkRequest request = new CreatePermanentLinkRequest();
        request.setMerchantSlug("boutique-mama-coco");
        request.setDescription("Paiement boutique");

        when(userRepository.getUsersByUsername("002250759146858")).thenReturn(user);
        when(walletRepository.findByUsers(user)).thenReturn(wallet);
        when(permanentLinkRepository.existsByMerchantSlug("boutique-mama-coco")).thenReturn(false);
        when(permanentLinkRepository.save(any(PermanentLink.class))).thenReturn(permanentLink);

        ResponseEntity<?> response =
                service.createPermanentLink("002250759146858", request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertInstanceOf(PermanentLinkResponse.class, response.getBody());
        PermanentLinkResponse body = (PermanentLinkResponse) response.getBody();
        assertEquals("boutique-mama-coco", body.getMerchantSlug());
        assertTrue(body.getPaymentUrl().contains("boutique-mama-coco"));
    }

    @Test
    void createPermanentLink_MerchantWalletMissing() {
        CreatePermanentLinkRequest request = new CreatePermanentLinkRequest();
        request.setMerchantSlug("boutique-mama-coco");
        request.setDescription("Paiement boutique");

        when(userRepository.getUsersByUsername("002250759146858")).thenReturn(user);
        when(walletRepository.findByUsers(user)).thenReturn(null);
        when(walletRepository.findWalletByUsers(user)).thenReturn(List.of());

        ResponseEntity<?> response =
                service.createPermanentLink("002250759146858", request);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertInstanceOf(ErrorResponse.class, response.getBody());
        ErrorResponse err = (ErrorResponse) response.getBody();
        assertFalse(err.isSuccess());
        assertEquals("MERCHANT_WALLET_REQUIRED", err.getErrors().get(0).getCode());
    }

    @Test
    void createPermanentLink_UserNotFound() {
        CreatePermanentLinkRequest request = new CreatePermanentLinkRequest();
        request.setMerchantSlug("boutique-mama-coco");
        request.setDescription("Paiement boutique");

        when(userRepository.getUsersByUsername("unknown")).thenReturn(null);

        ResponseEntity<?> response =
                service.createPermanentLink("unknown", request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void createPermanentLink_SlugAlreadyExists() {
        CreatePermanentLinkRequest request = new CreatePermanentLinkRequest();
        request.setMerchantSlug("boutique-mama-coco");
        request.setDescription("Paiement boutique");

        when(userRepository.getUsersByUsername("002250759146858")).thenReturn(user);
        when(walletRepository.findByUsers(user)).thenReturn(wallet);
        when(permanentLinkRepository.existsByMerchantSlug("boutique-mama-coco")).thenReturn(true);

        ResponseEntity<?> response =
                service.createPermanentLink("002250759146858", request);

        assertEquals(HttpStatus.CONFLICT, response.getStatusCode());
        assertNull(response.getBody());
    }

    // =====================================================================
    // createSession
    // =====================================================================

    @Test
    void createSession_Success() {
        CreatePermanentLinkSessionRequest request = new CreatePermanentLinkSessionRequest(
                25.0, "USDC", "CMD-01", "Jean Dupont", "+2250700123456", null);

        when(permanentLinkRepository.findByMerchantSlug("boutique-mama-coco"))
                .thenReturn(Optional.of(permanentLink));
        when(permanentLinkSessionRepository.existsBySessionCode(anyString())).thenReturn(false);
        when(paymentFactoryContractService.generatePaymentId(anyString())).thenReturn("0xpaymentid123");
        when(walletRepository.findByUsers(user)).thenReturn(wallet);
        when(walletRepository.findById("admin-wallet-id")).thenReturn(Optional.of(adminWallet));
        when(paymentFactoryContractService.createPaymentLink(anyString(), anyString(), anyDouble(),
                anyString(), anyLong(), any(Wallet.class))).thenReturn("0xCREATE2ADDRESS");

        PermanentLinkSession savedSession = PermanentLinkSession.builder()
                .id(1L)
                .sessionCode("a1b2c3d4e5f6")
                .permanentLink(permanentLink)
                .amount(25.0)
                .currency("USDC")
                .reference("CMD-01")
                .status("CREATED")
                .paymentIdBytes32("0xpaymentid123")
                .create2WalletAddress("0xCREATE2ADDRESS")
                .expiresAt(LocalDateTime.now().plusHours(24))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(permanentLinkSessionRepository.save(any(PermanentLinkSession.class))).thenReturn(savedSession);
        when(permanentLinkRepository.save(any(PermanentLink.class))).thenReturn(permanentLink);

        ResponseEntity<?> response =
                service.createSession("boutique-mama-coco", request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertInstanceOf(PermanentLinkSessionResponse.class, response.getBody());
        PermanentLinkSessionResponse sessionBody = (PermanentLinkSessionResponse) response.getBody();
        assertEquals("a1b2c3d4e5f6", sessionBody.getSessionCode());
        assertEquals("CREATED", sessionBody.getStatus());
    }

    @Test
    void createSession_MerchantWalletMissing() {
        CreatePermanentLinkSessionRequest request = new CreatePermanentLinkSessionRequest(
                25.0, "USDC", "CMD-01", null, null, null);

        when(permanentLinkRepository.findByMerchantSlug("boutique-mama-coco"))
                .thenReturn(Optional.of(permanentLink));
        when(permanentLinkSessionRepository.existsBySessionCode(anyString())).thenReturn(false);
        when(paymentFactoryContractService.generatePaymentId(anyString())).thenReturn("0xpaymentid123");
        when(walletRepository.findByUsers(user)).thenReturn(null);
        when(walletRepository.findWalletByUsers(user)).thenReturn(List.of());

        ResponseEntity<?> response = service.createSession("boutique-mama-coco", request);

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, response.getStatusCode());
        assertInstanceOf(ErrorResponse.class, response.getBody());
        assertEquals("MERCHANT_WALLET_REQUIRED", ((ErrorResponse) response.getBody()).getErrors().get(0).getCode());
    }

    @Test
    void createSession_LinkNotFound() {
        CreatePermanentLinkSessionRequest request = new CreatePermanentLinkSessionRequest(
                25.0, "USDC", null, null, null, null);

        when(permanentLinkRepository.findByMerchantSlug("nonexistent")).thenReturn(Optional.empty());

        ResponseEntity<?> response =
                service.createSession("nonexistent", request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void createSession_LinkInactive() {
        CreatePermanentLinkSessionRequest request = new CreatePermanentLinkSessionRequest(
                25.0, "USDC", null, null, null, null);

        PermanentLink inactiveLink = PermanentLink.builder()
                .id(1L)
                .merchantSlug("boutique-mama-coco")
                .creator(user)
                .description("Paiement boutique")
                .isActive(false)
                .totalSessions(0)
                .totalCompletedPayments(0)
                .totalAmountReceived(0.0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(permanentLinkRepository.findByMerchantSlug("boutique-mama-coco"))
                .thenReturn(Optional.of(inactiveLink));

        ResponseEntity<?> response =
                service.createSession("boutique-mama-coco", request);

        assertEquals(HttpStatus.GONE, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void createSession_FixedAmount_OverridesRequestAmount() {
        PermanentLink fixedAmountLink = PermanentLink.builder()
                .id(1L)
                .merchantSlug("boutique-mama-coco")
                .creator(user)
                .description("Paiement boutique")
                .amount(100.0) // fixed amount
                .isActive(true)
                .totalSessions(0)
                .totalCompletedPayments(0)
                .totalAmountReceived(0.0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        CreatePermanentLinkSessionRequest request = new CreatePermanentLinkSessionRequest(
                25.0, "USDC", "CMD-01", null, null, null); // request amount = 25, but link = 100

        when(permanentLinkRepository.findByMerchantSlug("boutique-mama-coco"))
                .thenReturn(Optional.of(fixedAmountLink));
        when(permanentLinkSessionRepository.existsBySessionCode(anyString())).thenReturn(false);
        when(paymentFactoryContractService.generatePaymentId(anyString())).thenReturn("0xpaymentid123");
        when(walletRepository.findByUsers(user)).thenReturn(wallet);
        when(walletRepository.findById("admin-wallet-id")).thenReturn(Optional.of(adminWallet));
        when(paymentFactoryContractService.createPaymentLink(anyString(), anyString(), anyDouble(),
                anyString(), anyLong(), any(Wallet.class))).thenReturn("0xCREATE2ADDRESS");

        ArgumentCaptor<PermanentLinkSession> captor = ArgumentCaptor.forClass(PermanentLinkSession.class);
        PermanentLinkSession savedSession = PermanentLinkSession.builder()
                .id(1L)
                .sessionCode("a1b2c3d4e5f6")
                .permanentLink(fixedAmountLink)
                .amount(100.0) // should be 100 (fixed)
                .currency("USDC")
                .status("CREATED")
                .paymentIdBytes32("0xpaymentid123")
                .create2WalletAddress("0xCREATE2ADDRESS")
                .expiresAt(LocalDateTime.now().plusHours(24))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(permanentLinkSessionRepository.save(captor.capture())).thenReturn(savedSession);
        when(permanentLinkRepository.save(any(PermanentLink.class))).thenReturn(fixedAmountLink);

        service.createSession("boutique-mama-coco", request);

        PermanentLinkSession captured = captor.getValue();
        assertEquals(100.0, captured.getAmount()); // fixed amount should be used
    }

    // =====================================================================
    // getSessionByCode
    // =====================================================================

    @Test
    void getSessionByCode_Success() {
        PermanentLinkSession session = buildSession("CREATED", LocalDateTime.now().plusHours(24));
        when(permanentLinkSessionRepository.findBySessionCode("a1b2c3d4e5f6")).thenReturn(Optional.of(session));

        ResponseEntity<PermanentLinkSessionResponse> response =
                service.getSessionByCode("a1b2c3d4e5f6");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("CREATED", response.getBody().getStatus());
    }

    @Test
    void getSessionByCode_NotFound() {
        when(permanentLinkSessionRepository.findBySessionCode("notexist")).thenReturn(Optional.empty());

        ResponseEntity<PermanentLinkSessionResponse> response =
                service.getSessionByCode("notexist");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    // =====================================================================
    // deactivatePermanentLink
    // =====================================================================

    @Test
    void deactivatePermanentLink_Success() {
        when(permanentLinkRepository.findByMerchantSlug("boutique-mama-coco"))
                .thenReturn(Optional.of(permanentLink));
        when(permanentLinkRepository.save(any())).thenReturn(permanentLink);

        ResponseEntity<String> response =
                service.deactivatePermanentLink("002250759146858", "boutique-mama-coco");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("deactivated successfully"));
        assertFalse(permanentLink.getIsActive());
    }

    @Test
    void deactivatePermanentLink_NotOwner() {
        Users otherUser = new Users();
        otherUser.setUsername("other-user");

        when(permanentLinkRepository.findByMerchantSlug("boutique-mama-coco"))
                .thenReturn(Optional.of(permanentLink));

        ResponseEntity<String> response =
                service.deactivatePermanentLink("other-user", "boutique-mama-coco");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // =====================================================================
    // getPermanentLinkStats
    // =====================================================================

    @Test
    void getPermanentLinkStats_Success() {
        when(permanentLinkRepository.findByMerchantSlug("boutique-mama-coco"))
                .thenReturn(Optional.of(permanentLink));
        when(permanentLinkSessionRepository.countByPermanentLinkAndStatus(permanentLink, "CREATED")).thenReturn(2L);
        when(permanentLinkSessionRepository.countByPermanentLinkAndStatus(permanentLink, "PENDING")).thenReturn(1L);

        ResponseEntity<PermanentLinkStatsResponse> response =
                service.getPermanentLinkStats("002250759146858", "boutique-mama-coco");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("boutique-mama-coco", response.getBody().getMerchantSlug());
        assertEquals(3L, response.getBody().getActiveSessions());
    }

    // =====================================================================
    // Helper
    // =====================================================================

    private PermanentLinkSession buildSession(String status, LocalDateTime expiresAt) {
        return PermanentLinkSession.builder()
                .id(1L)
                .sessionCode("a1b2c3d4e5f6")
                .permanentLink(permanentLink)
                .amount(25.0)
                .currency("USDC")
                .status(status)
                .expiresAt(expiresAt)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
