package org.akuunda.akuundawallet.wallet.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.wallet.api.dao.PermanentLinkRepository;
import org.akuunda.akuundawallet.wallet.api.dao.PermanentLinkSessionRepository;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.wallet.api.dto.PermanentLinkSessionResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.MeldSessionResponse;
import org.akuunda.akuundawallet.wallet.api.dto.external.OnRampRequest;
import org.akuunda.akuundawallet.wallet.api.dto.external.RecipientDto;
import org.akuunda.akuundawallet.wallet.api.dto.external.WidgetSessionData;
import org.akuunda.akuundawallet.wallet.api.entities.PermanentLink;
import org.akuunda.akuundawallet.wallet.api.entities.PermanentLinkSession;
import org.akuunda.akuundawallet.wallet.service.PermanentLinkPaymentService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaMeldServiceClient;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaYellowCardClientService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PermanentLinkPaymentServiceImpl implements PermanentLinkPaymentService {

    private final PermanentLinkRepository permanentLinkRepository;
    private final PermanentLinkSessionRepository permanentLinkSessionRepository;
    private final WalletRepository walletRepository;
    private final AkuundaYellowCardClientService akuundaYellowCardClientService;
    private final AkuundaMeldServiceClient akuundaMeldServiceClient;

    @Value("${server.url}")
    private String serverBaseUrl;

    /**
     * Provider Meld sous-jacent par défaut pour les flux checkout public. L'intégration
     * Akuunda a été configurée et certifiée avec Mercuryo ; si le frontend oublie ce
     * champ, on évite un 400 côté Meld en imposant ce provider par défaut.
     */
    private static final String DEFAULT_MELD_PROVIDER = "MERCURYO";

    @Override
    public ResponseEntity<Object> initiateYellowCardPayment(String sessionCode, OnRampRequest request) {
        try {
            log.info("Initiation paiement YellowCard pour session: {}", sessionCode);

            PermanentLinkSession session = permanentLinkSessionRepository.findBySessionCode(sessionCode).orElse(null);
            if (session == null) {
                log.warn("Session non trouvée: {}", sessionCode);
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Session non trouvée"));
            }

            if (!"CREATED".equals(session.getStatus())) {
                log.warn("Session {} a le statut {}, paiement impossible", sessionCode, session.getStatus());
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "La session n'est pas dans l'état CREATED"));
            }

            if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.warn("Session {} expirée", sessionCode);
                session.setStatus("EXPIRED");
                permanentLinkSessionRepository.save(session);
                return ResponseEntity.status(HttpStatus.GONE)
                        .body(Map.of("error", "La session a expiré"));
            }

            PermanentLink permanentLink = session.getPermanentLink();
            Users creator = permanentLink.getCreator();

            if (creator.getUserId() == null || creator.getUserId().isBlank()) {
                log.error("Le créateur {} n'a pas de userId configuré", creator.getUsername());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("error", "Identifiant du créateur manquant"));
            }

            // ─── Validation des informations de l'acheteur (source) ──────────────────
            // Le marchand n'a aucune visibilité sur ces champs : c'est la page checkout
            // publique qui doit les collecter (pays, canal, opérateur, numéro de compte,
            // nom du titulaire). On valide ici pour ne plus jamais appeler YellowCard avec
            // un payload vide et tomber sur un cryptique 400 « missing accountNumber in source ».
            ResponseEntity<Object> buyerCheck = validateBuyerInfo(request);
            if (buyerCheck != null) {
                return buyerCheck;
            }

            // Dédupe du téléphone : pour les paiements Mobile Money, le `accountNumber`
            // EST le numéro de téléphone. On l'aligne côté serveur pour que le frontend
            // n'ait à demander qu'une seule fois cette information à l'utilisateur.
            normalizeMomoSourcePhone(request);

            // Validation upfront du profil marchand : YellowCard exige des champs KYC complets sur
            // le `recipient`. Si des champs manquent on retourne un 422 explicite avec la liste,
            // plutôt que d'envoyer une requête condamnée à un 400 cryptique côté YellowCard.
            List<String> missingKycFields = validateMerchantKycForYellowCard(creator);
            if (!missingKycFields.isEmpty()) {
                log.warn("YellowCard: profil marchand '{}' incomplet, champs manquants: {}",
                        creator.getUsername(), missingKycFields);
                Map<String, Object> body = new java.util.LinkedHashMap<>();
                body.put("code", "MERCHANT_KYC_INCOMPLETE");
                body.put("message", "Profil marchand incomplet pour YellowCard. Champs manquants : "
                        + String.join(", ", missingKycFields)
                        + ". Compléter via PUT /api/internal/v1/users/{realmName}/profile/" + creator.getUsername()
                        + " (countryCode, idType, idNumber...) ou via le dashboard Pro Akuunda Pay > Paramètres > Profil.");
                body.put("missingFields", missingKycFields);
                body.put("merchantUsername", creator.getUsername());
                body.put("merchantUserId", creator.getUserId());
                body.put("updateProfileEndpoint",
                        "PUT /api/internal/v1/users/{realmName}/profile/" + creator.getUsername());
                return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
            }

            // Le `recipient` côté YellowCard = celui qui reçoit le paiement = le marchand.
            // On le construit côté serveur depuis le profil du créateur du lien, JAMAIS depuis le
            // body du client (la page checkout publique n'a pas accès aux infos KYC du marchand).
            // Si le client a envoyé `recipient.country` (pays de réception), on le préserve en cas
            // d'absence de pays sur le profil marchand.
            RecipientDto clientHint = request.getRecipient();
            RecipientDto rebuiltRecipient = buildRecipientFromMerchant(creator, clientHint);
            request.setRecipient(rebuiltRecipient);

            log.info("YellowCard PermanentLink [session={}] envoi avec recipient: name='{}' phone='{}' email='{}' country='{}' dob='{}' idType='{}' idNumber='{}' businessName='{}'",
                    sessionCode,
                    rebuiltRecipient.getName(),
                    rebuiltRecipient.getPhone(),
                    rebuiltRecipient.getEmail(),
                    rebuiltRecipient.getCountry(),
                    rebuiltRecipient.getDob(),
                    rebuiltRecipient.getIdType(),
                    rebuiltRecipient.getIdNumber(),
                    rebuiltRecipient.getBusinessName());

            ResponseEntity<Object> yellowCardResponse = akuundaYellowCardClientService
                    .createCollectionForPermanentLink(request, session.getCreate2WalletAddress(), creator.getUserId());

            if (!yellowCardResponse.getStatusCode().is2xxSuccessful()) {
                log.error("Erreur YellowCard pour session {}: {}", sessionCode, yellowCardResponse.getBody());
                return yellowCardResponse;
            }

            // Extraire le transactionId de la réponse
            String transactionId = null;
            if (yellowCardResponse.getBody() instanceof Map<?, ?> responseMap) {
                Object tid = responseMap.get("transactionId");
                if (tid != null) {
                    transactionId = tid.toString();
                }
            }

            // Mettre à jour la session
            session.setStatus("PENDING");
            session.setPaymentProvider("YELLOWCARD");
            session.setExternalTransactionId(transactionId);
            if (request.getCountry() != null) {
                session.setCountryCode(request.getCountry());
            }
            if (request.getRecipient() != null) {
                if ("institution".equalsIgnoreCase(request.getCustomerType())) {
                    if (request.getRecipient().getBusinessName() != null) {
                        session.setPayerName(request.getRecipient().getBusinessName());
                    }
                } else {
                    if (request.getRecipient().getName() != null) {
                        session.setPayerName(request.getRecipient().getName());
                    }
                }
                if (request.getRecipient().getPhone() != null) {
                    session.setPayerPhone(request.getRecipient().getPhone());
                }
                if (request.getRecipient().getEmail() != null) {
                    session.setPayerEmail(request.getRecipient().getEmail());
                }
            }
            if (session.getPayerPhone() == null && request.getSource() != null) {
                if (request.getSource().getPhoneNumber() != null) {
                    session.setPayerPhone(request.getSource().getPhoneNumber());
                }
            }
            permanentLinkSessionRepository.save(session);

            log.info("Session {} mise à jour: statut PENDING, provider YELLOWCARD, transactionId: {}", sessionCode, transactionId);
            return yellowCardResponse;

        } catch (Exception e) {
            log.error("Erreur lors de l'initiation du paiement YellowCard pour session: {}", sessionCode, e);
            Map<String, Object> body = new java.util.LinkedHashMap<>();
            body.put("code", "YELLOWCARD_INIT_FAILED");
            body.put("message", "Erreur interne lors de l'initiation du paiement YellowCard.");
            body.put("exception", e.getClass().getSimpleName());
            body.put("detail", e.getMessage());
            body.put("sessionCode", sessionCode);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }

    @Override
    public ResponseEntity<MeldSessionResponse> initiateMeldPayment(String sessionCode, WidgetSessionData request) {
        try {
            log.info("Initiation paiement Meld pour session: {}", sessionCode);

            PermanentLinkSession session = permanentLinkSessionRepository.findBySessionCode(sessionCode).orElse(null);
            if (session == null) {
                log.warn("Session non trouvée: {}", sessionCode);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            if (!"CREATED".equals(session.getStatus())) {
                log.warn("Session {} a le statut {}, paiement impossible", sessionCode, session.getStatus());
                return ResponseEntity.status(HttpStatus.CONFLICT).build();
            }

            if (session.getExpiresAt() != null && session.getExpiresAt().isBefore(LocalDateTime.now())) {
                log.warn("Session {} expirée", sessionCode);
                session.setStatus("EXPIRED");
                permanentLinkSessionRepository.save(session);
                return ResponseEntity.status(HttpStatus.GONE).build();
            }

            PermanentLink permanentLink = session.getPermanentLink();
            Users creator = permanentLink.getCreator();

            // Provider Meld sous-jacent : Mercuryo par défaut côté checkout public (l'intégration
            // a été configurée pour lui). Si le frontend laisse `serviceProvider` vide, on
            // utilise ce défaut côté serveur pour éviter un 400 côté Meld.
            String serviceProvider = (request.getServiceProvider() != null && !request.getServiceProvider().isBlank())
                    ? request.getServiceProvider().trim()
                    : DEFAULT_MELD_PROVIDER;

            // externalCustomerId Meld = identifiant *payeur* (client final), pas marchand.
            // Priorité : email transmis par le frontend (collecté côté checkout) > email marchand >
            // username marchand (dernier recours pour ne jamais envoyer une valeur vide).
            String externalCustomerId = pickFirstNonBlank(
                    request.getCustomerEmail(),
                    creator.getEmail(),
                    creator.getUsername());

            log.info("Meld payment: provider={}, externalCustomerId={} (source={})",
                    serviceProvider,
                    externalCustomerId,
                    request.getCustomerEmail() != null && !request.getCustomerEmail().isBlank()
                            ? "customerEmail"
                            : creator.getEmail() != null && !creator.getEmail().isBlank()
                                    ? "merchantEmail"
                                    : "merchantUsername");

            ResponseEntity<MeldSessionResponse> meldResponse = akuundaMeldServiceClient.createMeldSession(
                    creator.getUsername(),
                    externalCustomerId,
                    serviceProvider,
                    request.getSourceCurrencyCode(),
                    request.getDestinationCurrencyCode(),
                    request.getSourceAmount(),
                    request.getSessionType(),
                    request.getCountryCode(),
                    session.getCreate2WalletAddress(),
                    request.getClientIpAddress()
            );

            if (!meldResponse.getStatusCode().is2xxSuccessful() || meldResponse.getBody() == null) {
                log.error("Erreur Meld pour session {}: status={}", sessionCode, meldResponse.getStatusCode());
                return meldResponse;
            }

            MeldSessionResponse meldBody = meldResponse.getBody();

            // Mettre à jour la session
            session.setStatus("PENDING");
            session.setPaymentProvider("MELD");
            session.setExternalTransactionId(meldBody.getId());
            if (meldBody.getServiceProviderWidgetUrl() != null) {
                session.setProviderWidgetUrl(meldBody.getServiceProviderWidgetUrl());
            }
            if (request.getCountryCode() != null) {
                session.setCountryCode(request.getCountryCode());
            }
            permanentLinkSessionRepository.save(session);

            // Build secure redirect URL (never expose provider URL to frontend)
            String payRedirectUrl = serverBaseUrl + "/api/internal/v1/pay-redirect/" + session.getSessionCode();
            meldBody.setPayRedirectUrl(payRedirectUrl);

            log.info("Session {} mise à jour: statut PENDING, provider MELD, transactionId: {}", sessionCode, meldBody.getId());
            return meldResponse;

        } catch (Exception e) {
            log.error("Erreur lors de l'initiation du paiement Meld pour session: {}", sessionCode, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @Override
    public ResponseEntity<List<PermanentLinkSessionResponse>> getSessionsBySlug(String username, String slug, String status, int page, int size) {
        try {
            log.info("Récupération des sessions pour le lien {} par l'utilisateur {}", slug, username);

            PermanentLink permanentLink = permanentLinkRepository.findByMerchantSlug(slug).orElse(null);
            if (permanentLink == null) {
                log.warn("Lien permanent non trouvé: {}", slug);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            }

            if (!permanentLink.getCreator().getUsername().equals(username)) {
                log.warn("L'utilisateur {} n'est pas le créateur du lien {}", username, slug);
                return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            }

            PageRequest pageable = PageRequest.of(page, size);
            Page<PermanentLinkSession> sessionPage;
            if (status != null && !status.isBlank()) {
                sessionPage = permanentLinkSessionRepository
                        .findAllByPermanentLinkAndStatusOrderByCreatedAtDesc(permanentLink, status, pageable);
            } else {
                sessionPage = permanentLinkSessionRepository
                        .findAllByPermanentLinkOrderByCreatedAtDesc(permanentLink, pageable);
            }

            List<PermanentLinkSessionResponse> responses = sessionPage.getContent().stream()
                    .map(this::buildSessionResponse)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(responses);

        } catch (Exception e) {
            log.error("Erreur lors de la récupération des sessions pour le lien {} par {}", slug, username, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Construit le {@link RecipientDto} à envoyer à YellowCard à partir du profil marchand
     * (créateur du lien permanent). C'est <strong>toujours</strong> côté serveur que cette
     * information est résolue : la page checkout publique ne dispose pas du KYC marchand.
     *
     * <p>Règles :</p>
     * <ul>
     *     <li>Compte PRO avec raison sociale ({@code raisonSociale}) → {@code customerType =
     *         institution} (à positionner par l'appelant) et {@code businessName} renseigné.</li>
     *     <li>Sinon → identité physique du créateur : {@code firstname + lastname}.</li>
     *     <li>{@code recipient.country} = {@code creator.countryCurrency.countryCode}, avec
     *         fallback sur le hint client si le marchand n'a pas de pays renseigné.</li>
     *     <li>{@code dob} : on convertit {@code creator.dateNaissance} (ISO {@code YYYY-MM-DD})
     *         au format {@code MM/DD/YYYY} attendu par YellowCard.</li>
     * </ul>
     */
    private RecipientDto buildRecipientFromMerchant(Users creator, RecipientDto clientHint) {
        RecipientDto recipient = new RecipientDto();

        String firstname = trim(creator.getFirstname());
        String lastname = trim(creator.getLastname());
        if (firstname != null || lastname != null) {
            String fullName = ((firstname != null ? firstname : "") + " " + (lastname != null ? lastname : "")).trim();
            if (!fullName.isEmpty()) {
                recipient.setName(fullName);
            }
        }
        if (trim(creator.getRaisonSociale()) != null) {
            recipient.setBusinessName(creator.getRaisonSociale().trim());
        }
        if (trim(creator.getSiret()) != null) {
            recipient.setBusinessId(creator.getSiret().trim());
        }

        recipient.setPhone(trim(creator.getMobilePhone()));
        recipient.setEmail(trim(creator.getEmail()));
        recipient.setAddress(trim(creator.getAdresse()));
        recipient.setDob(formatDobForYellowCard(creator.getDateNaissance()));
        recipient.setIdNumber(trim(creator.getIdNumber()));
        recipient.setIdType(trim(creator.getIdType()));
        recipient.setAdditionalIdNumber(trim(creator.getAdditionalIdNumber()));
        recipient.setAdditionalIdType(trim(creator.getAdditionalIdType()));

        // Pays marchand : toujours depuis le wallet primaire (`wallet.currency.country_code`).
        // Si le marchand n'a pas de wallet (cas pathologique), on accepte en dernier recours
        // le hint envoyé par le client checkout.
        String merchantCountry = resolveMerchantCountry(creator);
        if (merchantCountry == null && clientHint != null) {
            merchantCountry = trim(clientHint.getCountry());
        }
        recipient.setCountry(merchantCountry);

        log.info("YellowCard recipient construit depuis le marchand '{}' (country={}, hasName={}, hasPhone={}, hasEmail={}, hasIdNumber={})",
                creator.getUsername(),
                merchantCountry,
                recipient.getName() != null,
                recipient.getPhone() != null,
                recipient.getEmail() != null,
                recipient.getIdNumber() != null);

        return recipient;
    }

    /**
     * Vérifie que les informations de l'acheteur (payer) saisies côté checkout public
     * sont présentes et cohérentes avec le canal de paiement choisi. Retourne {@code null}
     * si tout est OK, ou une {@link ResponseEntity} {@code 422 BUYER_INFO_INCOMPLETE}
     * avec la liste précise des champs manquants à présenter à l'utilisateur.
     *
     * <p>Règles, dans l'ordre :</p>
     * <ol>
     *     <li>{@code country} (code pays ISO-2 — ex. CI/SN/BJ/TG pour XOF) : obligatoire.</li>
     *     <li>{@code channelId} (= moyen de paiement YellowCard) : obligatoire.</li>
     *     <li>{@code source.accountNumber} : obligatoire (téléphone si momo, IBAN si bank).</li>
     *     <li>{@code source.accountName} : obligatoire (nom du titulaire).</li>
     *     <li>Si {@code source.accountType} ∈ {momo, mobile_money} : {@code source.networkId}
     *         (opérateur — Orange, MTN, Wave, Moov…) est obligatoire.</li>
     * </ol>
     */
    private ResponseEntity<Object> validateBuyerInfo(OnRampRequest request) {
        List<String> missing = new java.util.ArrayList<>();

        if (trim(request.getCountry()) == null) missing.add("country");
        if (trim(request.getChannelId()) == null) missing.add("channelId");

        var source = request.getSource();
        String accountType = source != null ? trim(source.getAccountType()) : null;
        if (source == null || trim(source.getAccountNumber()) == null) missing.add("source.accountNumber");
        if (source == null || trim(source.getAccountName()) == null) missing.add("source.accountName");
        if (isMomoChannel(accountType)) {
            if (source == null || trim(source.getNetworkId()) == null) missing.add("source.networkId");
        }

        if (missing.isEmpty()) return null;

        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("code", "BUYER_INFO_INCOMPLETE");
        body.put("message", "Informations payeur manquantes pour ce canal de paiement. "
                + "Le sélecteur pays, le canal et la fiche source (numéro + nom titulaire) "
                + "doivent être saisis côté page checkout.");
        body.put("missingFields", missing);
        body.put("accountType", accountType);
        if (isMomoChannel(accountType)) {
            body.put("expectedSourceFields", List.of("accountNumber", "accountName", "networkId"));
        } else if ("bank".equalsIgnoreCase(accountType)) {
            body.put("expectedSourceFields", List.of("accountNumber", "accountName", "accountBank"));
        } else {
            body.put("expectedSourceFields", List.of("accountNumber", "accountName"));
        }
        log.warn("BUYER_INFO_INCOMPLETE: missing={} accountType={}", missing, accountType);
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    /**
     * Pour les paiements Mobile Money, YellowCard attend à la fois {@code accountNumber}
     * et {@code phoneNumber} dans la {@code source}. C'est en pratique la même valeur,
     * donc on demande à l'utilisateur final un seul champ côté checkout (« numéro de
     * téléphone Mobile Money ») et on aligne ici les deux propriétés côté serveur.
     */
    private void normalizeMomoSourcePhone(OnRampRequest request) {
        if (request == null || request.getSource() == null) return;
        var source = request.getSource();
        if (!isMomoChannel(source.getAccountType())) return;
        String accountNumber = trim(source.getAccountNumber());
        String phoneNumber = trim(source.getPhoneNumber());
        if (accountNumber != null && phoneNumber == null) {
            source.setPhoneNumber(accountNumber);
        } else if (phoneNumber != null && accountNumber == null) {
            source.setAccountNumber(phoneNumber);
        }
    }

    private static boolean isMomoChannel(String accountType) {
        if (accountType == null) return false;
        String t = accountType.trim().toLowerCase();
        return t.equals("momo") || t.equals("mobile_money") || t.equals("mobilemoney");
    }

    /**
     * Résout le code pays ISO-2 du marchand depuis son wallet primaire
     * ({@code wallet.currency.country_code}). Le wallet est la source unique de
     * vérité pour le pays — chaque utilisateur a son wallet provisionné avec une
     * {@code CountryCurrency} dès la création du compte, et le payload {@code /getUser}
     * l'expose tel quel ({@code wallets[i].countryCode}).
     *
     * <p>Retourne {@code null} si l'utilisateur n'a aucun wallet provisionné (cas
     * pathologique) ou si son wallet n'a pas de devise associée.</p>
     */
    private String resolveMerchantCountry(Users creator) {
        if (creator == null) return null;
        try {
            Wallet wallet = walletRepository.findByUsers(creator);
            if (wallet != null && wallet.getCurrency() != null) {
                return trim(wallet.getCurrency().getCountryCode());
            }
        } catch (Exception e) {
            log.warn("Impossible de résoudre le wallet du marchand '{}' pour le country: {}",
                    creator.getUsername(), e.getMessage());
        }
        return null;
    }

    private static String trim(String value) {
        if (value == null) return null;
        String t = value.trim();
        return t.isEmpty() ? null : t;
    }

    private static String pickFirstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            String t = trim(v);
            if (t != null) return t;
        }
        return null;
    }

    /**
     * Vérifie que le profil marchand contient le minimum requis par YellowCard pour
     * construire un {@code recipient} valide (KYC OnRamp). Retourne la liste des champs
     * manquants ; liste vide = profil OK.
     *
     * <p>Pour un compte business ({@code raisonSociale} renseignée) on accepte le nom
     * d'entreprise en lieu et place de firstname/lastname.</p>
     */
    private List<String> validateMerchantKycForYellowCard(Users creator) {
        List<String> missing = new java.util.ArrayList<>();

        boolean hasBusiness = trim(creator.getRaisonSociale()) != null;
        boolean hasIndividual = trim(creator.getFirstname()) != null && trim(creator.getLastname()) != null;
        if (!hasBusiness && !hasIndividual) {
            // On signale séparément ce qui manque pour aider le marchand à comprendre.
            if (trim(creator.getFirstname()) == null) missing.add("firstname");
            if (trim(creator.getLastname()) == null) missing.add("lastname");
        }
        if (trim(creator.getMobilePhone()) == null) missing.add("mobilePhone");
        if (trim(creator.getEmail()) == null) missing.add("email");
        // Le pays du marchand est porté par son wallet (`wallet.currency.country_code`).
        // On ne l'exige PAS sur le profil — c'est uniquement si le marchand n'a aucun
        // wallet provisionné (cas pathologique) qu'on signalera ce manquement.
        if (resolveMerchantCountry(creator) == null) {
            missing.add("wallet.countryCode (aucun wallet provisionné)");
        }
        // Pour un compte particulier on exige la date de naissance + pièce d'identité.
        // Pour une entreprise (raisonSociale), YellowCard accepte un dossier business sans dob personnelle.
        if (!hasBusiness) {
            if (trim(creator.getDateNaissance()) == null) missing.add("dateNaissance");
            if (trim(creator.getIdType()) == null) missing.add("idType");
            if (trim(creator.getIdNumber()) == null) missing.add("idNumber");
        }
        return missing;
    }

    /**
     * Convertit une date de naissance stockée ({@code dateNaissance}) au format ISO
     * {@code YYYY-MM-DD} (ou variantes courantes) vers {@code MM/DD/YYYY} attendu par
     * YellowCard.
     */
    private String formatDobForYellowCard(String dateNaissance) {
        String raw = trim(dateNaissance);
        if (raw == null) return null;
        java.util.regex.Matcher iso = java.util.regex.Pattern
                .compile("^(\\d{4})-(\\d{2})-(\\d{2})$")
                .matcher(raw);
        if (iso.matches()) {
            return iso.group(2) + "/" + iso.group(3) + "/" + iso.group(1);
        }
        java.util.regex.Matcher slashed = java.util.regex.Pattern
                .compile("^(\\d{2})/(\\d{2})/(\\d{4})$")
                .matcher(raw);
        if (slashed.matches()) {
            return raw; // Already MM/DD/YYYY
        }
        log.warn("YellowCard recipient: format dateNaissance non reconnu '{}' — envoyé tel quel", raw);
        return raw;
    }

    private PermanentLinkSessionResponse buildSessionResponse(PermanentLinkSession session) {
        return PermanentLinkSessionResponse.builder()
                .id(session.getId())
                .sessionCode(session.getSessionCode())
                .permanentLinkSlug(session.getPermanentLink().getMerchantSlug())
                .amount(session.getAmount())
                .currency(session.getCurrency())
                .reference(session.getReference())
                .status(session.getStatus())
                .create2WalletAddress(session.getCreate2WalletAddress())
                .paymentIdBytes32(session.getPaymentIdBytes32())
                .payerName(session.getPayerName())
                .payerPhone(session.getPayerPhone())
                .payerEmail(session.getPayerEmail())
                .externalTransactionId(session.getExternalTransactionId())
                .paymentProvider(session.getPaymentProvider())
                .countryCode(session.getCountryCode())
                .expiresAt(session.getExpiresAt())
                .paidAt(session.getPaidAt())
                .createdAt(session.getCreatedAt())
                .merchantUsername(session.getPermanentLink().getCreator().getUsername())
                .merchantUserId(session.getPermanentLink().getCreator().getUserId())
                .build();
    }
}
