package org.akuunda.akuundawallet.wallet.service.impl;

import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.dao.OneTimePaymentLinkRepository;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.api.dto.CreateOneTimePaymentLinkRequest;
import org.akuunda.akuundawallet.wallet.api.dto.OneTimePaymentLinkResponse;
import org.akuunda.akuundawallet.wallet.api.entities.OneTimePaymentLink;
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

public class OneTimePaymentLinkServiceImplTest {

    @Mock
    private OneTimePaymentLinkRepository oneTimePaymentLinkRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private PaymentFactoryContractService paymentFactoryContractService;

    @InjectMocks
    private OneTimePaymentLinkServiceImpl service;

    private Users user;
    private Wallet wallet;
    private Wallet adminWallet;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        ReflectionTestUtils.setField(service, "adminWalletId", "admin-wallet-id");
        ReflectionTestUtils.setField(service, "adminWalletAddress", "");

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
    }

    // =====================================================================
    // createOneTimePaymentLink
    // =====================================================================

    @Test
    void createOneTimePaymentLink_Success() {
        CreateOneTimePaymentLinkRequest request = new CreateOneTimePaymentLinkRequest(
                "Facture #001", 5000.0, "XOF", null);

        when(userRepository.getUsersByUsername("002250759146858")).thenReturn(user);
        when(oneTimePaymentLinkRepository.existsByUniqueCode(anyString())).thenReturn(false);
        when(paymentFactoryContractService.generatePaymentId(anyString())).thenReturn("0xpaymentid123");
        when(walletRepository.findByUsers(user)).thenReturn(wallet);
        when(walletRepository.findById("admin-wallet-id")).thenReturn(Optional.of(adminWallet));
        when(paymentFactoryContractService.createPaymentLink(anyString(), anyString(), anyDouble(),
                anyString(), anyLong(), any(Wallet.class))).thenReturn("0xCREATE2ADDRESS");

        OneTimePaymentLink saved = OneTimePaymentLink.builder()
                .id(1L)
                .uniqueCode("a1b2c3d4")
                .creator(user)
                .description("Facture #001")
                .amount(5000.0)
                .currency("XOF")
                .status("CREATED")
                .expiresAt(LocalDateTime.now().plusHours(24))
                .paymentIdBytes32("0xpaymentid123")
                .create2WalletAddress("0xCREATE2ADDRESS")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        when(oneTimePaymentLinkRepository.save(any(OneTimePaymentLink.class))).thenReturn(saved);

        ResponseEntity<OneTimePaymentLinkResponse> response =
                service.createOneTimePaymentLink("002250759146858", request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("a1b2c3d4", response.getBody().getUniqueCode());
        assertEquals("CREATED", response.getBody().getStatus());
    }

    @Test
    void createOneTimePaymentLink_UserNotFound() {
        CreateOneTimePaymentLinkRequest request = new CreateOneTimePaymentLinkRequest(
                "Facture #001", 5000.0, "XOF", null);

        when(userRepository.getUsersByUsername("unknown")).thenReturn(null);

        ResponseEntity<OneTimePaymentLinkResponse> response =
                service.createOneTimePaymentLink("unknown", request);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNull(response.getBody());
    }

    @Test
    void createOneTimePaymentLink_NullAmount() {
        CreateOneTimePaymentLinkRequest request = new CreateOneTimePaymentLinkRequest(
                "Facture #001", null, "XOF", null);

        when(userRepository.getUsersByUsername("002250759146858")).thenReturn(user);
        when(oneTimePaymentLinkRepository.existsByUniqueCode(anyString())).thenReturn(false);
        when(paymentFactoryContractService.generatePaymentId(anyString())).thenReturn("0xpaymentid123");
        when(walletRepository.findByUsers(user)).thenReturn(wallet);
        when(walletRepository.findById("admin-wallet-id")).thenReturn(Optional.of(adminWallet));
        when(paymentFactoryContractService.createPaymentLink(anyString(), anyString(), anyDouble(),
                anyString(), anyLong(), any(Wallet.class))).thenReturn("0xCREATE2ADDRESS");

        OneTimePaymentLink saved = OneTimePaymentLink.builder()
                .id(1L).uniqueCode("a1b2c3d4").creator(user)
                .description("Facture #001").amount(null).currency("XOF").status("CREATED")
                .expiresAt(LocalDateTime.now().plusHours(24))
                .paymentIdBytes32("0xpaymentid123").create2WalletAddress("0xCREATE2ADDRESS")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(oneTimePaymentLinkRepository.save(any(OneTimePaymentLink.class))).thenReturn(saved);

        ResponseEntity<OneTimePaymentLinkResponse> response =
                service.createOneTimePaymentLink("002250759146858", request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void createOneTimePaymentLink_NegativeAmount() {
        CreateOneTimePaymentLinkRequest request = new CreateOneTimePaymentLinkRequest(
                "Facture #001", -100.0, "XOF", null);

        when(userRepository.getUsersByUsername("002250759146858")).thenReturn(user);
        when(oneTimePaymentLinkRepository.existsByUniqueCode(anyString())).thenReturn(false);
        when(paymentFactoryContractService.generatePaymentId(anyString())).thenReturn("0xpaymentid123");
        when(walletRepository.findByUsers(user)).thenReturn(wallet);
        when(walletRepository.findById("admin-wallet-id")).thenReturn(Optional.of(adminWallet));
        when(paymentFactoryContractService.createPaymentLink(anyString(), anyString(), anyDouble(),
                anyString(), anyLong(), any(Wallet.class))).thenReturn("0xCREATE2ADDRESS");

        OneTimePaymentLink saved = OneTimePaymentLink.builder()
                .id(1L).uniqueCode("a1b2c3d4").creator(user)
                .description("Facture #001").amount(-100.0).currency("XOF").status("CREATED")
                .expiresAt(LocalDateTime.now().plusHours(24))
                .paymentIdBytes32("0xpaymentid123").create2WalletAddress("0xCREATE2ADDRESS")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(oneTimePaymentLinkRepository.save(any(OneTimePaymentLink.class))).thenReturn(saved);

        ResponseEntity<OneTimePaymentLinkResponse> response =
                service.createOneTimePaymentLink("002250759146858", request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void createOneTimePaymentLink_NullCurrency() {
        CreateOneTimePaymentLinkRequest request = new CreateOneTimePaymentLinkRequest(
                "Facture #001", 5000.0, null, null);

        when(userRepository.getUsersByUsername("002250759146858")).thenReturn(user);
        when(oneTimePaymentLinkRepository.existsByUniqueCode(anyString())).thenReturn(false);
        when(paymentFactoryContractService.generatePaymentId(anyString())).thenReturn("0xpaymentid123");
        when(walletRepository.findByUsers(user)).thenReturn(wallet);
        when(walletRepository.findById("admin-wallet-id")).thenReturn(Optional.of(adminWallet));
        when(paymentFactoryContractService.createPaymentLink(anyString(), anyString(), anyDouble(),
                anyString(), anyLong(), any(Wallet.class))).thenReturn("0xCREATE2ADDRESS");

        OneTimePaymentLink saved = OneTimePaymentLink.builder()
                .id(1L).uniqueCode("a1b2c3d4").creator(user)
                .description("Facture #001").amount(5000.0).currency(null).status("CREATED")
                .expiresAt(LocalDateTime.now().plusHours(24))
                .paymentIdBytes32("0xpaymentid123").create2WalletAddress("0xCREATE2ADDRESS")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(oneTimePaymentLinkRepository.save(any(OneTimePaymentLink.class))).thenReturn(saved);

        ResponseEntity<OneTimePaymentLinkResponse> response =
                service.createOneTimePaymentLink("002250759146858", request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
    }

    @Test
    void createOneTimePaymentLink_DescriptionOnly() {
        CreateOneTimePaymentLinkRequest request = new CreateOneTimePaymentLinkRequest(
                "Facture n°2024-001", null, null, null);

        when(userRepository.getUsersByUsername("002250759146858")).thenReturn(user);
        when(oneTimePaymentLinkRepository.existsByUniqueCode(anyString())).thenReturn(false);
        when(paymentFactoryContractService.generatePaymentId(anyString())).thenReturn("0xpaymentid123");
        when(walletRepository.findByUsers(user)).thenReturn(wallet);
        when(walletRepository.findById("admin-wallet-id")).thenReturn(Optional.of(adminWallet));
        when(paymentFactoryContractService.createPaymentLink(anyString(), anyString(), anyDouble(),
                anyString(), anyLong(), any(Wallet.class))).thenReturn("0xCREATE2ADDRESS");

        OneTimePaymentLink saved = OneTimePaymentLink.builder()
                .id(1L).uniqueCode("a1b2c3d4").creator(user)
                .description("Facture n°2024-001").amount(null).currency(null).status("CREATED")
                .expiresAt(LocalDateTime.now().plusHours(24))
                .paymentIdBytes32("0xpaymentid123").create2WalletAddress("0xCREATE2ADDRESS")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(oneTimePaymentLinkRepository.save(any(OneTimePaymentLink.class))).thenReturn(saved);

        ResponseEntity<OneTimePaymentLinkResponse> response =
                service.createOneTimePaymentLink("002250759146858", request);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("a1b2c3d4", response.getBody().getUniqueCode());
    }

    @Test
    void createOneTimePaymentLink_DefaultExpiry24h() {
        CreateOneTimePaymentLinkRequest request = new CreateOneTimePaymentLinkRequest(
                "Facture #001", 5000.0, "XOF", null);

        when(userRepository.getUsersByUsername("002250759146858")).thenReturn(user);
        when(oneTimePaymentLinkRepository.existsByUniqueCode(anyString())).thenReturn(false);
        when(paymentFactoryContractService.generatePaymentId(anyString())).thenReturn("0xpaymentid123");
        when(walletRepository.findByUsers(user)).thenReturn(wallet);
        when(walletRepository.findById("admin-wallet-id")).thenReturn(Optional.of(adminWallet));
        when(paymentFactoryContractService.createPaymentLink(anyString(), anyString(), anyDouble(),
                anyString(), anyLong(), any(Wallet.class))).thenReturn("0xCREATE2ADDRESS");

        ArgumentCaptor<OneTimePaymentLink> captor = ArgumentCaptor.forClass(OneTimePaymentLink.class);
        OneTimePaymentLink saved = OneTimePaymentLink.builder()
                .id(1L).uniqueCode("a1b2c3d4").creator(user)
                .description("Facture #001").amount(5000.0).currency("XOF").status("CREATED")
                .expiresAt(LocalDateTime.now().plusHours(24))
                .paymentIdBytes32("0xpaymentid123").create2WalletAddress("0xCREATE2ADDRESS")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(oneTimePaymentLinkRepository.save(captor.capture())).thenReturn(saved);

        service.createOneTimePaymentLink("002250759146858", request);

        OneTimePaymentLink captured = captor.getValue();
        assertNotNull(captured.getExpiresAt());
        assertTrue(captured.getExpiresAt().isAfter(LocalDateTime.now().plusHours(23)));
    }

    // =====================================================================
    // getOneTimePaymentLinkByCode
    // =====================================================================

    @Test
    void getOneTimePaymentLinkByCode_Success() {
        OneTimePaymentLink link = buildLink("CREATED", LocalDateTime.now().plusHours(24));
        when(oneTimePaymentLinkRepository.findByUniqueCode("a1b2c3d4")).thenReturn(Optional.of(link));

        ResponseEntity<OneTimePaymentLinkResponse> response =
                service.getOneTimePaymentLinkByCode("a1b2c3d4");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("CREATED", response.getBody().getStatus());
    }

    @Test
    void getOneTimePaymentLinkByCode_NotFound() {
        when(oneTimePaymentLinkRepository.findByUniqueCode("notexist")).thenReturn(Optional.empty());

        ResponseEntity<OneTimePaymentLinkResponse> response =
                service.getOneTimePaymentLinkByCode("notexist");

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }

    @Test
    void getOneTimePaymentLinkByCode_Expired() {
        OneTimePaymentLink link = buildLink("CREATED", LocalDateTime.now().minusHours(1));
        when(oneTimePaymentLinkRepository.findByUniqueCode("a1b2c3d4")).thenReturn(Optional.of(link));

        ResponseEntity<OneTimePaymentLinkResponse> response =
                service.getOneTimePaymentLinkByCode("a1b2c3d4");

        assertEquals(HttpStatus.GONE, response.getStatusCode());
    }

    // =====================================================================
    // cancelOneTimePaymentLink
    // =====================================================================

    @Test
    void cancelOneTimePaymentLink_Success() {
        OneTimePaymentLink link = buildLink("CREATED", LocalDateTime.now().plusHours(24));
        when(userRepository.getUsersByUsername("002250759146858")).thenReturn(user);
        when(oneTimePaymentLinkRepository.findByUniqueCode("a1b2c3d4")).thenReturn(Optional.of(link));
        when(oneTimePaymentLinkRepository.save(any())).thenReturn(link);

        ResponseEntity<String> response =
                service.cancelOneTimePaymentLink("002250759146858", "a1b2c3d4");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(response.getBody().contains("cancelled successfully"));
        assertEquals("CANCELLED", link.getStatus());
    }

    @Test
    void cancelOneTimePaymentLink_NotCreator() {
        Users otherUser = new Users();
        otherUser.setUsername("other-user");

        OneTimePaymentLink link = buildLink("CREATED", LocalDateTime.now().plusHours(24));
        when(userRepository.getUsersByUsername("other-user")).thenReturn(otherUser);
        when(oneTimePaymentLinkRepository.findByUniqueCode("a1b2c3d4")).thenReturn(Optional.of(link));

        ResponseEntity<String> response =
                service.cancelOneTimePaymentLink("other-user", "a1b2c3d4");

        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    void cancelOneTimePaymentLink_AlreadyPaid() {
        OneTimePaymentLink link = buildLink("PAID", LocalDateTime.now().plusHours(24));
        when(userRepository.getUsersByUsername("002250759146858")).thenReturn(user);
        when(oneTimePaymentLinkRepository.findByUniqueCode("a1b2c3d4")).thenReturn(Optional.of(link));

        ResponseEntity<String> response =
                service.cancelOneTimePaymentLink("002250759146858", "a1b2c3d4");

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    // =====================================================================
    // Unique code generation
    // =====================================================================

    @Test
    void generatedCode_Has8Characters() {
        CreateOneTimePaymentLinkRequest request = new CreateOneTimePaymentLinkRequest(
                "Test", 1000.0, "XOF", null);

        when(userRepository.getUsersByUsername("002250759146858")).thenReturn(user);
        when(oneTimePaymentLinkRepository.existsByUniqueCode(anyString())).thenReturn(false);
        when(paymentFactoryContractService.generatePaymentId(anyString())).thenReturn("0xpaymentid123");
        when(walletRepository.findByUsers(user)).thenReturn(wallet);
        when(walletRepository.findById("admin-wallet-id")).thenReturn(Optional.of(adminWallet));
        when(paymentFactoryContractService.createPaymentLink(anyString(), anyString(), anyDouble(),
                anyString(), anyLong(), any(Wallet.class))).thenReturn("0xCREATE2ADDRESS");

        ArgumentCaptor<OneTimePaymentLink> captor = ArgumentCaptor.forClass(OneTimePaymentLink.class);
        OneTimePaymentLink saved = OneTimePaymentLink.builder()
                .id(1L).uniqueCode("a1b2c3d4").creator(user)
                .description("Test").amount(1000.0).currency("XOF").status("CREATED")
                .expiresAt(LocalDateTime.now().plusHours(24))
                .paymentIdBytes32("0xpaymentid123").create2WalletAddress("0xCREATE2ADDRESS")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
        when(oneTimePaymentLinkRepository.save(captor.capture())).thenReturn(saved);

        service.createOneTimePaymentLink("002250759146858", request);

        OneTimePaymentLink captured = captor.getValue();
        assertNotNull(captured.getUniqueCode());
        assertEquals(8, captured.getUniqueCode().length());
        assertTrue(captured.getUniqueCode().matches("[a-z0-9]{8}"));
    }

    // =====================================================================
    // getUserOneTimePaymentLinks
    // =====================================================================

    @Test
    void getUserOneTimePaymentLinks_Success() {
        OneTimePaymentLink link1 = buildLink("CREATED", LocalDateTime.now().plusHours(24));
        OneTimePaymentLink link2 = buildLink("PAID", null);

        when(userRepository.getUsersByUsername("002250759146858")).thenReturn(user);
        when(oneTimePaymentLinkRepository.findByCreatorOrderByCreatedAtDesc(user))
                .thenReturn(List.of(link1, link2));

        ResponseEntity<List<OneTimePaymentLinkResponse>> response =
                service.getUserOneTimePaymentLinks("002250759146858");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
    }

    // =====================================================================
    // Helper
    // =====================================================================

    private OneTimePaymentLink buildLink(String status, LocalDateTime expiresAt) {
        return OneTimePaymentLink.builder()
                .id(1L)
                .uniqueCode("a1b2c3d4")
                .creator(user)
                .description("Facture #001")
                .amount(5000.0)
                .currency("XOF")
                .status(status)
                .expiresAt(expiresAt)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
