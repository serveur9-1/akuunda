package org.akuunda.akuundawallet.wallet.service.transfi;

import lombok.RequiredArgsConstructor;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.api.dto.external.transfi.TransfiCreateOrderRequest;
import org.akuunda.akuundawallet.wallet.api.dto.external.transfi.TransfiOrderDestinationDto;
import org.akuunda.akuundawallet.wallet.api.dto.external.transfi.TransfiOrderSourceDto;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Aligns TransFi order creation with other providers (Guardarian, widgets): optional
 * {@link TransfiCreateOrderRequest#getUsername wallet username} fills missing wallet address
 * and crypto symbol from our DB. Explicit JSON fields always win.
 */
@Service
@RequiredArgsConstructor
public class TransfiCreateOrderContextService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransfiPersistenceService transfiPersistenceService;

    /**
     * @return empty if nothing to do or enrichment succeeded; otherwise a short French error message (404 body)
     */
    public Optional<String> enrichFromUsername(TransfiCreateOrderRequest request) {
        String username = request.getUsername();
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        username = username.trim();

        Users user = userRepository.getUsersByUsername(username);
        if (user == null) {
            return Optional.of("Utilisateur introuvable pour username=" + username);
        }

        Wallet wallet = walletRepository.findByUsersUsername(username);
        if (wallet == null || wallet.getAddress() == null || wallet.getAddress().isBlank()) {
            return Optional.of("Aucun wallet avec adresse valide pour username=" + username);
        }

        String crypto = resolveWalletCryptoCode(wallet);
        String orderType = request.getOrderType() == null ? "" : request.getOrderType().toLowerCase(Locale.ROOT);

        // If caller supplied a username but no TransFi userId, fall back to the local mapping.
        if (isBlank(request.getUserId())) {
            transfiPersistenceService.resolveTransfiUserId(username).ifPresent(request::setUserId);
        }

        String profileFiat = resolveProfileFiat(user);

        switch (orderType) {
            case "onramp", "payin", "gaming" -> {
                applyDestinationCrypto(request.getDestination(), wallet.getAddress(), crypto);
                fillFiatCurrencyIfMissing(request.getSource(), profileFiat);
            }
            case "offramp", "payout" -> {
                applySourceCrypto(request.getSource(), wallet.getAddress(), crypto);
                fillFiatCurrencyIfMissing(request.getDestination(), profileFiat);
            }
            case "swap" -> applySwapCrypto(request.getSource(), request.getDestination(), wallet.getAddress(), crypto);
            default -> applySwapCrypto(request.getSource(), request.getDestination(), wallet.getAddress(), crypto);
        }

        fillCustomerMetaDataFromProfile(request, user, wallet, crypto);
        return Optional.empty();
    }

    /** Same resolution rules as {@link #enrichFromUsername} — for GET /context/wallet. */
    public Optional<TransfiWalletContext> resolveWalletContext(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        String u = username.trim();
        Users user = userRepository.getUsersByUsername(u);
        if (user == null) {
            return Optional.empty();
        }
        Wallet wallet = walletRepository.findByUsersUsername(u);
        if (wallet == null || wallet.getAddress() == null || wallet.getAddress().isBlank()) {
            return Optional.empty();
        }
        String crypto = resolveWalletCryptoCode(wallet);
        String fiat = null;
        if (user.getCountryCurrency() != null && user.getCountryCurrency().getCurrencyCode() != null) {
            fiat = user.getCountryCurrency().getCurrencyCode().toUpperCase(Locale.ROOT);
        }
        return Optional.of(new TransfiWalletContext(u, wallet.getAddress(), crypto, fiat));
    }

    private static String resolveWalletCryptoCode(Wallet wallet) {
        if (wallet.getSymbol() != null && !wallet.getSymbol().isBlank()) {
            return wallet.getSymbol().trim().toUpperCase(Locale.ROOT);
        }
        return "USDC";
    }

    private static void applyDestinationCrypto(TransfiOrderDestinationDto dest, String address, String crypto) {
        if (dest == null) {
            return;
        }
        if (isBlank(dest.getCurrency())) {
            dest.setCurrency(crypto);
        }
        if (matchesWalletAsset(dest.getCurrency(), crypto) && isBlank(dest.getWalletAddress())) {
            dest.setWalletAddress(address);
        }
    }

    private static void applySourceCrypto(TransfiOrderSourceDto source, String address, String crypto) {
        if (source == null) {
            return;
        }
        if (isBlank(source.getCurrency())) {
            source.setCurrency(crypto);
        }
        if (!matchesWalletAsset(source.getCurrency(), crypto)) {
            return;
        }
        if (isBlank(source.getWalletAddress()) && isBlank(source.getSendersWalletAddress())) {
            source.setWalletAddress(address);
        }
    }

    /**
     * Swap / types atypiques : on ne remplit que si la devise du côté concerné est déjà celle du wallet
     * (évite de forcer USDC quand le client veut EUR → USDC mais a laissé la source vide par erreur).
     */
    private static void applySwapCrypto(TransfiOrderSourceDto src, TransfiOrderDestinationDto dest,
            String address, String crypto) {
        if (src != null
                && !isBlank(src.getCurrency())
                && matchesWalletAsset(src.getCurrency(), crypto)
                && isBlank(src.getWalletAddress())
                && isBlank(src.getSendersWalletAddress())) {
            src.setWalletAddress(address);
        }
        if (dest != null
                && !isBlank(dest.getCurrency())
                && matchesWalletAsset(dest.getCurrency(), crypto)
                && isBlank(dest.getWalletAddress())) {
            dest.setWalletAddress(address);
        }
    }

    private static String resolveProfileFiat(Users user) {
        if (user.getCountryCurrency() == null || user.getCountryCurrency().getCurrencyCode() == null) {
            return null;
        }
        String fiat = user.getCountryCurrency().getCurrencyCode().trim();
        return fiat.isEmpty() ? null : fiat.toUpperCase(Locale.ROOT);
    }

    private static void fillFiatCurrencyIfMissing(TransfiOrderSourceDto src, String fiat) {
        if (src == null || fiat == null || !isBlank(src.getCurrency())) return;
        src.setCurrency(fiat);
    }

    private static void fillFiatCurrencyIfMissing(TransfiOrderDestinationDto dest, String fiat) {
        if (dest == null || fiat == null || !isBlank(dest.getCurrency())) return;
        dest.setCurrency(fiat);
    }

    /**
     * Adds non-PII Akuunda context to {@code customerMetaData} (without overwriting client-supplied keys)
     * so support/back-office can correlate a TransFi order with our wallet, and TransFi widgets can pre-fill
     * identity fields. customerMetaData is forwarded as-is by TransFi in webhooks and admin views.
     */
    private static void fillCustomerMetaDataFromProfile(TransfiCreateOrderRequest req, Users user,
            Wallet wallet, String crypto) {
        Map<String, Object> meta = req.getCustomerMetaData();
        if (meta == null) {
            meta = new LinkedHashMap<>();
            req.setCustomerMetaData(meta);
        }
        putIfAbsentNonBlank(meta, "akuundaUsername", user.getUsername());
        putIfAbsentNonBlank(meta, "akuundaUserId", user.getUserId());
        putIfAbsentNonBlank(meta, "akuundaEmail", user.getEmail());
        putIfAbsentNonBlank(meta, "akuundaFirstName", user.getFirstname());
        putIfAbsentNonBlank(meta, "akuundaLastName", user.getLastname());
        putIfAbsentNonBlank(meta, "akuundaMobilePhone", user.getMobilePhone());
        putIfAbsentNonBlank(meta, "akuundaWalletAddress", wallet.getAddress());
        putIfAbsentNonBlank(meta, "akuundaWalletAsset", crypto);
        if (user.getCountryCurrency() != null) {
            putIfAbsentNonBlank(meta, "akuundaCountryCode", user.getCountryCurrency().getCountryCode());
        }
    }

    private static void putIfAbsentNonBlank(Map<String, Object> map, String key, String value) {
        if (value == null || value.isBlank() || map.containsKey(key)) return;
        map.put(key, value);
    }

    private static boolean matchesWalletAsset(String requestedCurrency, String walletCrypto) {
        return isBlank(requestedCurrency) || requestedCurrency.equalsIgnoreCase(walletCrypto);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    public record TransfiWalletContext(String username, String walletAddress, String cryptoCurrency, String fiatCurrency) {}

    /**
     * Resolves a (sourceCurrency, destinationCurrency) pair for /exchange-rates when one or both
     * are missing and a username is provided. Mirrors the rules used in /orders enrichment:
     *   - onramp/payin  : source = profile fiat, destination = wallet crypto
     *   - offramp/payout: source = wallet crypto, destination = profile fiat
     * Explicit values always win. Returns the resolved pair (possibly with nulls if nothing could
     * be derived) so callers can still hand back a clear 400 for the missing field.
     */
    public ExchangeRateCurrencies resolveExchangeRateCurrencies(String username, String orderType,
            String sourceCurrency, String destinationCurrency) {
        String src = sourceCurrency;
        String dest = destinationCurrency;
        if (username == null || username.isBlank()) {
            return new ExchangeRateCurrencies(src, dest);
        }
        Optional<TransfiWalletContext> ctx = resolveWalletContext(username);
        if (ctx.isEmpty()) {
            return new ExchangeRateCurrencies(src, dest);
        }
        TransfiWalletContext c = ctx.get();
        String type = orderType == null ? "" : orderType.toLowerCase(Locale.ROOT);
        switch (type) {
            case "onramp", "payin", "fiat_prefund", "gaming" -> {
                if (isBlank(src) && c.fiatCurrency() != null) src = c.fiatCurrency();
                if (isBlank(dest)) dest = c.cryptoCurrency();
            }
            case "offramp", "payout", "crypto_prefund" -> {
                if (isBlank(src)) src = c.cryptoCurrency();
                if (isBlank(dest) && c.fiatCurrency() != null) dest = c.fiatCurrency();
            }
            default -> { /* swap & co: don't guess, the user must be explicit */ }
        }
        return new ExchangeRateCurrencies(src, dest);
    }

    public record ExchangeRateCurrencies(String sourceCurrency, String destinationCurrency) {}
}
