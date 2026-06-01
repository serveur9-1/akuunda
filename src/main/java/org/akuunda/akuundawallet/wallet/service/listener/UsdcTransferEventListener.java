package org.akuunda.akuundawallet.wallet.service.listener;

import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.wallet.api.dao.OneTimePaymentLinkRepository;
import org.akuunda.akuundawallet.wallet.api.dao.PartnerContractPaymentRepository;
import org.akuunda.akuundawallet.wallet.api.dao.PermanentLinkSessionRepository;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.api.entities.OneTimePaymentLink;
import org.akuunda.akuundawallet.wallet.api.entities.PartnerContractPayment;
import org.akuunda.akuundawallet.wallet.api.entities.PermanentLinkSession;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.wallet.service.PartnerPaymentProcessingService;
import org.akuunda.akuundawallet.wallet.service.task.PaymentDepositPollingTask;
import org.akuunda.akuundawallet.wallet.service.task.PermanentLinkDepositPollingTask;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.websocket.WebSocketService;
import org.web3j.protocol.core.methods.response.Log;

import io.reactivex.disposables.Disposable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Écoute en temps réel les événements ERC20 Transfer sur le contrat USDC (Polygon).
 *
 * <p>Dès qu'un Transfer arrive sur une adresse CREATE2 surveillée, le paiement correspondant
 * est traité immédiatement dans un thread séparé — sans attendre le scheduler de sécurité.
 *
 * <p>Le scheduler {@code PartnerPaymentMonitorScheduler} reste actif (fixedDelay = 30 min)
 * comme filet de sécurité en cas de déconnexion WebSocket non détectée.
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "akuunda.usdc.listener.enabled", havingValue = "true", matchIfMissing = false)
public class UsdcTransferEventListener implements InitializingBean, DisposableBean {

    // ERC20 Transfer(address indexed from, address indexed to, uint256 value)
    private static final String TRANSFER_TOPIC =
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    private final PartnerContractPaymentRepository paymentRepository;
    private final PartnerPaymentProcessingService processingService;
    private final WalletRepository walletRepository;
    private final OneTimePaymentLinkRepository oneTimePaymentLinkRepository;
    private final PermanentLinkSessionRepository permanentLinkSessionRepository;
    private final PaymentDepositPollingTask paymentDepositPollingTask;
    private final PermanentLinkDepositPollingTask permanentLinkDepositPollingTask;

    @Value("${polygon.rpc.ws.url:wss://polygon-bor-rpc.publicnode.com}")
    private String wsRpcUrl;

    @Value("${akuunda.escrow.token.address:0x3c499c542cEF5E3811e1192ce70d8cC03d5c3359}")
    private String usdcContractAddress;

    @Value("${akuunda.intermediate.wallet.id:}")
    private String intermediateWalletId;

    @Value("${akuunda.intermediate.wallet.address:}")
    private String intermediateWalletAddress;

    /** Adresses CREATE2 surveillées pour les PartnerContractPayment. */
    private final Set<String> watchedAddresses = ConcurrentHashMap.newKeySet();

    /** Adresses CREATE2 surveillées pour les OneTimePaymentLink (SIMPLE et CONDITIONAL). */
    private final Set<String> watchedOneTimeLinks = ConcurrentHashMap.newKeySet();

    /** Adresses CREATE2 surveillées pour les PermanentLinkSession. */
    private final Set<String> watchedPermanentSessions = ConcurrentHashMap.newKeySet();

    private volatile Web3j web3j;
    private volatile Disposable subscription;

    public UsdcTransferEventListener(
            PartnerContractPaymentRepository paymentRepository,
            PartnerPaymentProcessingService processingService,
            WalletRepository walletRepository,
            OneTimePaymentLinkRepository oneTimePaymentLinkRepository,
            PermanentLinkSessionRepository permanentLinkSessionRepository,
            PaymentDepositPollingTask paymentDepositPollingTask,
            PermanentLinkDepositPollingTask permanentLinkDepositPollingTask) {
        this.paymentRepository = paymentRepository;
        this.processingService = processingService;
        this.walletRepository = walletRepository;
        this.oneTimePaymentLinkRepository = oneTimePaymentLinkRepository;
        this.permanentLinkSessionRepository = permanentLinkSessionRepository;
        this.paymentDepositPollingTask = paymentDepositPollingTask;
        this.permanentLinkDepositPollingTask = permanentLinkDepositPollingTask;
    }

    // =========================================================================
    // CYCLE DE VIE
    // =========================================================================

    @Override
    public void afterPropertiesSet() {
        loadPendingPayments();
        connect();
    }

    @Override
    public void destroy() {
        disposeSubscription();
        if (web3j != null) {
            try {
                web3j.shutdown();
            } catch (Exception e) {
                log.warn("Erreur fermeture Web3j: {}", e.getMessage());
            }
            web3j = null;
        }
        log.info("[UsdcListener] Arrêt propre du listener WebSocket.");
    }

    // =========================================================================
    // GESTION DES ADRESSES SURVEILLÉES
    // =========================================================================

    /**
     * Simule un événement Transfer USDC vers une adresse CREATE2.
     * Uniquement pour les tests — ne jamais appeler en production.
     */
    public void simulateTransfer(String toAddress, String fakeTxHash) {
        log.warn("[UsdcListener][TEST] Simulation Transfer vers {} (txHash={})", toAddress, fakeTxHash);
        org.web3j.protocol.core.methods.response.Log fakeLog =
                new org.web3j.protocol.core.methods.response.Log();
        // topic[0] = Transfer signature, topic[1] = from (dummy), topic[2] = to (paddé)
        String paddedTo = "0x000000000000000000000000" + toAddress.replace("0x", "").toLowerCase();
        fakeLog.setTopics(java.util.List.of(
                "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
                "0x0000000000000000000000000000000000000000000000000000000000000000",
                paddedTo
        ));
        fakeLog.setTransactionHash(fakeTxHash != null ? fakeTxHash : "0xTEST");
        handleTransferEvent(fakeLog);
    }

    /**
     * Enregistre une adresse CREATE2 de PartnerContractPayment à surveiller.
     * Appelée juste après la création d'un paiement (dans PartnerContractServiceImpl).
     */
    public void registerPayment(String create2Address) {
        if (create2Address == null || create2Address.isBlank()) return;
        watchedAddresses.add(create2Address.toLowerCase());
        log.info("[UsdcListener] Adresse PartnerContract enregistrée: {}", create2Address);
    }

    /**
     * Retire une adresse CREATE2 de PartnerContractPayment de la surveillance.
     */
    public void unregisterPayment(String create2Address) {
        if (create2Address == null || create2Address.isBlank()) return;
        watchedAddresses.remove(create2Address.toLowerCase());
        log.info("[UsdcListener] Adresse PartnerContract désactivée: {}", create2Address);
    }

    /**
     * Enregistre une adresse CREATE2 de OneTimePaymentLink à surveiller.
     * Appelée juste après la création d'un lien de paiement unique.
     */
    public void registerOneTimePayment(String create2Address) {
        if (create2Address == null || create2Address.isBlank()) return;
        watchedOneTimeLinks.add(create2Address.toLowerCase());
        log.info("[UsdcListener] Adresse OneTimeLink enregistrée: {}", create2Address);
    }

    /**
     * Enregistre une adresse CREATE2 de PermanentLinkSession à surveiller.
     * Appelée juste après la création d'une session de lien permanent.
     */
    public void registerPermanentLinkSession(String create2Address) {
        if (create2Address == null || create2Address.isBlank()) return;
        watchedPermanentSessions.add(create2Address.toLowerCase());
        log.info("[UsdcListener] Adresse PermanentLinkSession enregistrée: {}", create2Address);
    }

    // =========================================================================
    // CONNEXION / RECONNEXION
    // =========================================================================

    /**
     * Vérifie toutes les 30 secondes si la subscription est encore active et reconnecte si nécessaire.
     */
    @Scheduled(fixedDelayString = "${akuunda.usdc.listener.reconnect.interval-ms:30000}")
    public void checkAndReconnect() {
        if (subscription == null || subscription.isDisposed()) {
            log.warn("[UsdcListener] Subscription inactive — tentative de reconnexion...");
            disposeSubscription();
            if (web3j != null) {
                try { web3j.shutdown(); } catch (Exception ignored) {}
                web3j = null;
            }
            connect();
        }
    }

    private void loadPendingPayments() {
        // ── PartnerContractPayment ──
        try {
            List<PartnerContractPayment> pending =
                    paymentRepository.findByCreate2StatusAndStatusOrderByCreatedAtAsc(
                            "waiting_payment", "pending_condition");
            for (PartnerContractPayment p : pending) {
                if (p.getCreate2WalletAddress() != null && !p.getCreate2WalletAddress().isBlank()) {
                    watchedAddresses.add(p.getCreate2WalletAddress().toLowerCase());
                }
            }
            log.info("[UsdcListener] {} adresse(s) PartnerContract chargées au démarrage.", pending.size());
        } catch (Exception e) {
            log.error("[UsdcListener] Erreur chargement PartnerContractPayment: {}", e.getMessage(), e);
        }

        // ── OneTimePaymentLink (SIMPLE et CONDITIONAL, hors PARTNER_CONTRACT) ──
        try {
            List<OneTimePaymentLink> pendingLinks =
                    oneTimePaymentLinkRepository.findByStatusIn(List.of("CREATED", "PENDING"));
            int count = 0;
            for (OneTimePaymentLink link : pendingLinks) {
                if ("PARTNER_CONTRACT".equals(link.getLinkType())) continue;
                if (link.getCreate2WalletAddress() != null && !link.getCreate2WalletAddress().isBlank()) {
                    watchedOneTimeLinks.add(link.getCreate2WalletAddress().toLowerCase());
                    count++;
                }
            }
            log.info("[UsdcListener] {} adresse(s) OneTimeLink chargées au démarrage.", count);
        } catch (Exception e) {
            log.error("[UsdcListener] Erreur chargement OneTimePaymentLink: {}", e.getMessage(), e);
        }

        // ── PermanentLinkSession (PENDING, sans partnerContractPaymentCode) ──
        try {
            List<PermanentLinkSession> pendingSessions =
                    permanentLinkSessionRepository.findByStatusIn(List.of("PENDING"));
            int count = 0;
            for (PermanentLinkSession session : pendingSessions) {
                if (session.getPartnerContractPaymentCode() != null) continue;
                if (session.getCreate2WalletAddress() != null && !session.getCreate2WalletAddress().isBlank()) {
                    watchedPermanentSessions.add(session.getCreate2WalletAddress().toLowerCase());
                    count++;
                }
            }
            log.info("[UsdcListener] {} adresse(s) PermanentLinkSession chargées au démarrage.", count);
        } catch (Exception e) {
            log.error("[UsdcListener] Erreur chargement PermanentLinkSession: {}", e.getMessage(), e);
        }
    }

    private void connect() {
        try {
            WebSocketService wsService = new WebSocketService(wsRpcUrl, false);
            wsService.connect();
            web3j = Web3j.build(wsService);

            EthFilter filter = new EthFilter(
                    DefaultBlockParameterName.LATEST,
                    DefaultBlockParameterName.LATEST,
                    usdcContractAddress);
            filter.addSingleTopic(TRANSFER_TOPIC);

            subscription = web3j.ethLogFlowable(filter).subscribe(
                    this::handleTransferEvent,
                    error -> {
                        log.error("[UsdcListener] Erreur stream WebSocket: {} — reconnexion forcée", error.getMessage());
                        disposeSubscription();
                    }
            );

            log.info("[UsdcListener] Connecté à {} — surveillance USDC Transfer sur {}",
                    wsRpcUrl, usdcContractAddress);
        } catch (Exception e) {
            log.error("[UsdcListener] Échec de connexion WebSocket ({}): {}", wsRpcUrl, e.getMessage(), e);
        }
    }

    private void disposeSubscription() {
        if (subscription != null && !subscription.isDisposed()) {
            try {
                subscription.dispose();
            } catch (Exception e) {
                log.warn("[UsdcListener] Erreur dispose subscription: {}", e.getMessage());
            }
        }
        subscription = null;
    }

    // =========================================================================
    // TRAITEMENT DES ÉVÉNEMENTS
    // =========================================================================

    private void handleTransferEvent(Log event) {
        try {
            List<String> topics = event.getTopics();
            // topics[0] = Transfer signature, topics[1] = from, topics[2] = to
            if (topics == null || topics.size() < 3) return;

            // Extraire l'adresse `to` depuis le topic[2] paddé sur 32 bytes
            String toTopic = topics.get(2);
            if (toTopic == null || toTopic.length() < 42) return;

            // Prendre les 40 derniers caractères (adresse sans le padding 0x000...000)
            String toAddress = "0x" + toTopic.substring(toTopic.length() - 40);
            String toAddressLower = toAddress.toLowerCase();

            // ── PartnerContractPayment ──
            if (watchedAddresses.contains(toAddressLower)) {
                log.info("[UsdcListener] Transfer USDC détecté vers CREATE2 PartnerContract: {}, txHash: {}",
                        toAddress, event.getTransactionHash());

                watchedAddresses.remove(toAddressLower);

                PartnerContractPayment payment =
                        paymentRepository.findByCreate2WalletAddressIgnoreCase(toAddress).orElse(null);

                if (payment == null) {
                    log.warn("[UsdcListener] Aucun PartnerContractPayment trouvé pour: {}", toAddress);
                    return;
                }

                Wallet serviceWallet = walletRepository.findByIdWithFetch(intermediateWalletId).orElse(null);
                if (serviceWallet == null) {
                    serviceWallet = walletRepository.findByAddress(intermediateWalletAddress);
                }
                if (serviceWallet == null) {
                    log.error("[UsdcListener] Wallet de service introuvable — traitement impossible pour: {}",
                            payment.getPaymentCode());
                    watchedAddresses.add(toAddressLower);
                    return;
                }

                final PartnerContractPayment finalPayment = payment;
                final Wallet finalWallet = serviceWallet;
                CompletableFuture.runAsync(() -> {
                    try {
                        processingService.processPayment(finalPayment, finalWallet);
                    } catch (Exception ex) {
                        log.error("[UsdcListener] Erreur traitement PartnerContract {}: {}",
                                finalPayment.getPaymentCode(), ex.getMessage(), ex);
                    }
                });
                return;
            }

            // ── OneTimePaymentLink ──
            if (watchedOneTimeLinks.contains(toAddressLower)) {
                log.info("[UsdcListener] Transfer USDC détecté vers CREATE2 OneTimeLink: {}, txHash: {}",
                        toAddress, event.getTransactionHash());

                watchedOneTimeLinks.remove(toAddressLower);
                final double usdcAmount = decodeUsdcAmount(event);
                final String finalAddress = toAddress;

                CompletableFuture.runAsync(() -> {
                    try {
                        paymentDepositPollingTask.processLinkByCreate2Address(finalAddress, usdcAmount);
                    } catch (Exception ex) {
                        log.error("[UsdcListener] Erreur traitement OneTimeLink pour {}: {}",
                                finalAddress, ex.getMessage(), ex);
                    }
                });
                return;
            }

            // ── PermanentLinkSession ──
            if (watchedPermanentSessions.contains(toAddressLower)) {
                log.info("[UsdcListener] Transfer USDC détecté vers CREATE2 PermanentLinkSession: {}, txHash: {}",
                        toAddress, event.getTransactionHash());

                watchedPermanentSessions.remove(toAddressLower);
                final double usdcAmount = decodeUsdcAmount(event);
                final String finalAddress = toAddress;

                CompletableFuture.runAsync(() -> {
                    try {
                        permanentLinkDepositPollingTask.processSessionByCreate2Address(finalAddress, usdcAmount);
                    } catch (Exception ex) {
                        log.error("[UsdcListener] Erreur traitement PermanentLinkSession pour {}: {}",
                                finalAddress, ex.getMessage(), ex);
                    }
                });
            }

        } catch (Exception e) {
            log.error("[UsdcListener] Erreur inattendue dans handleTransferEvent: {}", e.getMessage(), e);
        }
    }

    /**
     * Décode le montant USDC (6 décimales) depuis le champ data d'un événement Transfer ERC20.
     * Retourne 0 en cas d'erreur (le polling de sécurité reprendra le paiement).
     */
    private double decodeUsdcAmount(Log event) {
        try {
            String data = event.getData();
            if (data == null || data.length() < 3) return 0;
            java.math.BigInteger raw = new java.math.BigInteger(
                    data.startsWith("0x") ? data.substring(2) : data, 16);
            return raw.doubleValue() / 1_000_000.0; // USDC a 6 décimales
        } catch (Exception e) {
            log.warn("[UsdcListener] Impossible de décoder l'amount du Transfer event: {}", e.getMessage());
            return 0;
        }
    }
}
