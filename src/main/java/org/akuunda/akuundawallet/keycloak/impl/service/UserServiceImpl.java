package org.akuunda.akuundawallet.keycloak.impl.service;

import jakarta.ws.rs.BadRequestException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.utils.SigningMethodEnum;
import org.akuunda.akuundawallet.common.utils.StatusTypeEnum;
import org.akuunda.akuundawallet.keycloak.api.dao.OtpRepository;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.dto.*;
import org.akuunda.akuundawallet.keycloak.api.dto.external.ExternalSigningMethod;
import org.akuunda.akuundawallet.keycloak.api.entities.Otp;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.keycloak.api.service.*;
import org.akuunda.akuundawallet.keycloak.impl.service.mapper.UserMapper;
import org.akuunda.akuundawallet.transfert.impl.service.TransfertService;
import org.akuunda.akuundawallet.wallet.api.dao.WalletRepository;
import org.akuunda.akuundawallet.wallet.api.entities.CountryCurrency;
import org.akuunda.akuundawallet.wallet.api.entities.Wallet;
import org.akuunda.akuundawallet.wallet.api.service.CountryCurrencyService;
import org.akuunda.akuundawallet.wallet.api.service.WalletService;
import org.akuunda.akuundawallet.wallet.service.EmailService;
import org.akuunda.akuundawallet.wallet.service.EmergencyCodeService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkunndaUserClientService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaYellowCardClientService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.AkuundaGuardarianClientService;
import org.akuunda.akuundawallet.wallet.service.infrastructure.CurrencyFreaksClientService;
import org.akuunda.akuundawallet.wallet.api.dto.external.EstimateRequest;
import org.akuunda.akuundawallet.wallet.api.dto.external.EstimateResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.digest.DigestUtils;
import org.json.JSONObject;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
//@Transactional
public class UserServiceImpl implements UserService {

    protected  static  final char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static final String GOOGLE = "google";
    public static final String FACEBOOK = "facebook";

    @Value("${akunnda.service.username}")
    private String walletBaseUsername;

    @Value("${akunnda.service.code}")
    private String walletBasePinCode;

    @Value("${demo.username}")
    private String demoUserName;

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final OtpRepository otpRepository;
    private final OtpService otpService;
    private final KeycloakUserService keycloakUserService;
    private final WalletService walletService;
    private final AkunndaUserClientService userClientService;
    private final CountryCurrencyService countryCurrencyService;
    private final CurrencyFreaksClientService freaksClientService;
    private final AkuundaYellowCardClientService yellowCardClientService;
    private final AkuundaGuardarianClientService guardarianClientService;
    private final SmsService smsService;
    private final EmailService emailService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TransfertService transfertService;
    private final SigningMethodService signingMethodService;
    private final org.akuunda.akuundawallet.wallet.service.PinService pinService;
    private final EmergencyCodeService emergencyCodeService;
    private final org.akuunda.akuundawallet.wallet.api.dao.UserEmergencyCodeRepository userEmergencyCodeRepository;


    @Override
    public void updateUserLocal(final String realmName, final UpdateUserCommand command) {
        final var updateUser = keycloakUserService.updateUser(realmName, command);

        if (updateUser) {
            final var userUpdate = userRepository.getUsersByUsername(command.getUserName());
            if (userUpdate.getUserId() != null) {
                if(!command.getDateCreation().isEmpty()) userUpdate.setDateCreation(command.getDateCreation());
                if(!command.getSiret().isEmpty()) userUpdate.setSiret(command.getSiret());
                if(!command.getAdresse().isEmpty()) userUpdate.setAdresse(command.getAdresse());
                if(!command.getRaisonSociale().isEmpty()) userUpdate.setRaisonSociale(command.getRaisonSociale());
                if(!command.getFirstName().isEmpty()) userUpdate.setFirstname(command.getFirstName());
                if(!command.getLastName().isEmpty()) userUpdate.setLastname(command.getLastName());
                if(!command.getEmail().isEmpty()) userUpdate.setEmail(command.getEmail());
                if(!command.getDateNaissance().isEmpty()) userUpdate.setDateNaissance(command.getDateNaissance());
                userRepository.saveAndFlush(userUpdate);
            }
        }
    }


    @Override
    public ResponseEntity<UserCreateResponse> createUserPart(AddUserPartCommand command, String realmName) {
        log.info("🔍 Received partialEmergencyCode in createUserPart: '{}' (null: {}, empty: {})", 
                command.getPartialEmergencyCode(), 
                command.getPartialEmergencyCode() == null,
                command.getPartialEmergencyCode() != null && command.getPartialEmergencyCode().trim().isEmpty());
        
        String reference = "User for " + command.getFirstName() + " " + command.getLastName();
        AddUserCommand addUserCommand = new AddUserCommand();
        addUserCommand.setUsername(command.getUsername());
        addUserCommand.setFirstName(command.getFirstName());
        addUserCommand.setLastName(command.getLastName());
        addUserCommand.setEmail(command.getEmail());
        addUserCommand.setMobilePhone(command.getMobilePhone());
        addUserCommand.setTypeCompte("PARTICULIER");
        addUserCommand.setCountryCode(command.getCountryCode());
        addUserCommand.setPinCode(command.getPinCode());
        addUserCommand.setGoogleId(command.getGoogleId());
        addUserCommand.setFacebookId(command.getFacebookId());
        addUserCommand.setCguAcceptation(command.isCguAcceptation());
        return createUser(addUserCommand, realmName, command.getPinCode(), command.getPartialEmergencyCode(), reference);
    }

    @Override
    public ResponseEntity<UserCreateResponse> createUserEnterprise(AddUserEntCommand command, String realmName) {
        String reference;
        AddUserCommand addUserCommand = new AddUserCommand();
        addUserCommand.setUsername(command.getUsername());
        addUserCommand.setEmail(command.getEmail());
        addUserCommand.setMobilePhone(command.getMobilePhone());
        addUserCommand.setDateCreation(command.getDateCreation());
        addUserCommand.setSiret(command.getSiret());
        addUserCommand.setRaisonSociale(command.getRaisonSociale());
        addUserCommand.setAdresse(command.getAdresse());
        addUserCommand.setLastName(command.getLastName());
        addUserCommand.setFirstName(command.getFirstName());
        addUserCommand.setCguAcceptation(command.isCguAcceptation());

        addUserCommand.setCountryCode(command.getCountryCode());
        addUserCommand.setPinCode(command.getPinCode());
        if(command.getRaisonSociale().equals("AKUUNDA")) {
            reference = "Akuunda Funding User";
            addUserCommand.setTypeCompte("AKUUNDA");
        } else {
            reference = "User for " + command.getRaisonSociale();
            addUserCommand.setTypeCompte("ENTREPRISE");
        }
        return createUser(addUserCommand, realmName, command.getPinCode(), command.getPartialEmergencyCode(), reference);
    }

    private ResponseEntity<UserCreateResponse> createUser(AddUserCommand command, String realmName,
                                              String pinCode, String partialEmergencyCode, String reference) {

        String signingId = null;
        String signingPin;
        // Récupérer CountryCurrency
        final var countryCurrency = countryCurrencyService
                .findCountryCurrencyByCountryCode(command.getCountryCode());
        if (countryCurrency == null) {
            log.error("CountryCurrency not found for country code : {} ", command.getCountryCode());
            UserCreateResponse userResponse = UserCreateResponse.builder().status("error").message("CountryCurrency not found").data(null).build();
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(userResponse);
        }

        // Créer l'utilisateur sur Venly
        final var body = getCreateUserBody(SigningMethodEnum.PIN.name(), pinCode, reference);

        log.info("Venly Body Response: {}", body);

        final var userCreate = userClientService.createUSer(body.toString()).getBody();
        if (userCreate == null) {
            log.error("User not found in Venly");
            UserCreateResponse userResponse = UserCreateResponse.builder().status("error").message("Error in User create in Venly").data(null).build();
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(userResponse);
        }

        //recuperer la signingMethod de type PIN associée à l'utilisateur
        if (userCreate.getResult() != null && userCreate.getResult().getSigningMethods() != null) {
            signingId = userCreate.getResult().getSigningMethods().stream()
                    .filter(sm -> sm.getType().equals(SigningMethodEnum.PIN.name()))
                    .findFirst()
                    .map(ExternalSigningMethod::getId)
                    .orElse(null);
        }

        log.debug("KeyCloak Info: {}", realmName);

        // Créer l'utilisateur dans Keycloak
        UserRepresentation user = keycloakUserService.addUser(realmName, command).getBody();

        log.debug("KeyCloak User Info: {}", user);

        if (user == null) {
            log.error("User not found in Keycloak");
            UserCreateResponse userResponse = UserCreateResponse.builder().status("error").message("Error in User create in Keycloak").data(null).build();
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(userResponse);
        }

        // Sauvegarder l'utilisateur localement
        Users localUser = createLocalUser(command, user, userCreate.getResult().id,
                userCreate.getResult().getReference(), countryCurrency);
        if (localUser == null) {
            log.error("User exist in local with username : {} ", command.getUsername());
            UserCreateResponse userResponse = UserCreateResponse.builder().status("error").message("Error in User create in local").data(null).build();
            return ResponseEntity.status(HttpStatus.CONFLICT).body(userResponse);
        }

        // Stocker le PIN de manière sécurisée en base locale (même sécurité que /api/internal/v1/pin/define)
        // Le PIN a déjà été créé chez Venly, on le stocke juste en base avec génération de strings et hash
        final var pinStoreResponse = pinService.storePinSecurelyForExistingUser(userCreate.getResult().id, pinCode);
        if (pinStoreResponse.getStatusCode() != HttpStatus.OK) {
            log.warn("Failed to store PIN securely in database for userId: {}, but continuing with wallet creation", userCreate.getResult().id);
            // On continue quand même car le PIN existe chez Venly
        } else {
            log.info("PIN stored securely in database for userId: {}", userCreate.getResult().id);
        }

        // Variables pour construire la réponse détaillée
        boolean emergencyCodeCreated = false;
        String emergencyCodeMessage = "";
        
        // Créer l'emergency code si fourni (facultatif)
        log.info("🔍 Checking emergency code creation - partialEmergencyCode: '{}' (null: {}, empty: {})", 
                partialEmergencyCode, 
                partialEmergencyCode == null, 
                partialEmergencyCode != null && partialEmergencyCode.trim().isEmpty());
        if (partialEmergencyCode != null && !partialEmergencyCode.trim().isEmpty()) {
            log.info("Creating emergency code for userId: {} during user registration with partialEmergencyCode: {}", 
                    userCreate.getResult().id, partialEmergencyCode);
            
            // Vérifier que nous avons le signingId du PIN avant de créer l'emergency code
            if (signingId == null || signingId.isEmpty()) {
                log.error("PIN signing method ID not found for userId: {}, cannot create emergency code. " +
                        "Will need to be created later via endpoint.", userCreate.getResult().id);
                log.error("Response from Venly: result={}, signingMethods={}", 
                        userCreate.getResult() != null, 
                        userCreate.getResult() != null && userCreate.getResult().getSigningMethods() != null 
                            ? userCreate.getResult().getSigningMethods().size() : 0);
                emergencyCodeMessage = "Emergency code not created: PIN signing method ID not found. " +
                        "Please create it later via /api/internal/v1/emergency-code/define";
            } else {
                log.debug("PIN signing method ID found: {} for userId: {}", signingId, userCreate.getResult().id);
                
                // Petite pause pour s'assurer que le PIN signing method est bien disponible via l'API Venly
                // (parfois il y a un délai de propagation après la création)
                log.debug("Waiting 1.5 seconds for PIN signing method to be available via Venly API...");
                try {
                    Thread.sleep(1500); // Attendre 1.5 secondes pour la propagation
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("Thread interrupted while waiting for PIN signing method propagation");
                }
                
                String usernameForEmergencyCode = localUser != null && localUser.getUsername() != null ? localUser.getUsername() : command.getUsername();
                log.info("Attempting to create emergency code for userId: {} (username: {})", userCreate.getResult().id, usernameForEmergencyCode);
                
                try {
                    final var emergencyCodeResponse = emergencyCodeService.defineEmergencyCode(
                            usernameForEmergencyCode, 
                            partialEmergencyCode.trim(), 
                            pinCode);
                    
                    if (emergencyCodeResponse != null && emergencyCodeResponse.getStatusCode().is2xxSuccessful()) {
                        log.info("✅ Emergency code created successfully for userId: {}", userCreate.getResult().id);
                        
                        // Vérifier que l'emergency code a bien été sauvegardé en base
                        // (petit délai pour s'assurer que la transaction est commitée)
                        try {
                            Thread.sleep(500); // Attendre 500ms pour la propagation de la transaction
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        
                        // Vérification dans la base de données
                        var verifyOpt = userEmergencyCodeRepository.findTopByUserIdOrderByCreatedAtDesc(userCreate.getResult().id);
                        if (verifyOpt.isPresent()) {
                            log.info("✅ Verified: Emergency code confirmed in database for userId: {}", userCreate.getResult().id);
                            emergencyCodeCreated = true;
                            emergencyCodeMessage = "Emergency code created successfully";
                        } else {
                            log.error("❌ WARNING: Emergency code created in Venly but NOT found in database for userId: {}", userCreate.getResult().id);
                            emergencyCodeCreated = false;
                            emergencyCodeMessage = "Emergency code created in Venly but verification in database failed. Please try creating it again via /api/internal/v1/emergency-code/define";
                        }
                    } else {
                        String errorBody = "Unknown error";
                        if (emergencyCodeResponse != null && emergencyCodeResponse.getBody() != null) {
                            try {
                                String responseBody = emergencyCodeResponse.getBody();
                                if (responseBody != null && !responseBody.isEmpty()) {
                                    // Si c'est du JSON, on prend juste le début
                                    if (responseBody.length() > 100) {
                                        errorBody = responseBody.substring(0, 100) + "...";
                                    } else {
                                        errorBody = responseBody;
                                    }
                                    // Échapper les guillemets pour éviter les problèmes dans String.format
                                    errorBody = errorBody.replace("\"", "'").replace("\n", " ").replace("\r", " ");
                                }
                            } catch (Exception e) {
                                log.warn("Error processing emergency code error body", e);
                                errorBody = "Error processing response";
                            }
                        }
                        
                        log.error("❌ Failed to create emergency code for userId: {}. Status: {}, Body: {}", 
                                userCreate.getResult().id, 
                                emergencyCodeResponse != null ? emergencyCodeResponse.getStatusCode() : "null",
                                errorBody);
                        emergencyCodeMessage = String.format("Emergency code creation failed (Status: %s). " +
                                "Please create it later via /api/internal/v1/emergency-code/define. Error: %s", 
                                emergencyCodeResponse != null ? emergencyCodeResponse.getStatusCode() : "null",
                                errorBody);
                        // On continue quand même car l'utilisateur est créé, l'emergency code pourra être créé plus tard
                    }
                } catch (Exception e) {
                    log.error("❌ Exception while creating emergency code for userId: {}", userCreate.getResult().id, e);
                    emergencyCodeMessage = "Emergency code creation failed with exception: " + (e.getMessage() != null ? e.getMessage() : "Unknown error");
                    // On continue quand même car l'utilisateur est créé, l'emergency code pourra être créé plus tard
                }
            }
        } else {
            log.info("No emergency code provided for userId: {}, skipping emergency code creation", userCreate.getResult().id);
            emergencyCodeMessage = "No emergency code provided";
        }

        // Créer un portefeuille associé à l'utilisateur
        signingPin = signingId + ":" + pinCode;
        final var walletCrate = walletService.createWallet(localUser.getUsername(), signingPin, countryCurrency, reference);
        if (walletCrate.getStatusCode() != HttpStatus.OK) {
            log.error("Error in wallet creation for user : {} ", localUser.getUsername());
            UserCreateResponse userResponse = UserCreateResponse.builder().status("error").message("Error in wallet creation").data(null).build();
            return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body(userResponse);
        }

        // TODO :  Methode temporaire pour activer et recharger le user
        final var activeUser = activeUser(user.getUsername(), realmName);

        if (activeUser.getStatusCode() != HttpStatus.OK) {
            log.error("Error lors de l'activation de l'user : {} ", localUser.getUsername());
            log.debug("Error lors de l'activation de l'user : {} ", localUser.getUsername());
            UserCreateResponse userResponse = UserCreateResponse.builder().status("succes")
                    .message("User creer mais rechargement du compte en erreur avec statut : "
                    + activeUser.getStatusCode())
                    .data(null).build();
            return ResponseEntity.status(HttpStatus.OK)
                    .body(userResponse);
        }

        // Construire la réponse JSON structurée
        String username = localUser != null && localUser.getUsername() != null ? localUser.getUsername() : command.getUsername();
        String firstName = command.getFirstName() != null ? command.getFirstName() : "";
        String lastName = command.getLastName() != null ? command.getLastName() : "";
        // Échapper les caractères spéciaux dans le message
        String safeEmergencyCodeMessage = emergencyCodeMessage != null 
                ? emergencyCodeMessage.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", " ").replace("\r", " ")
                : "No message";
        
        String responseJson = String.format(
            "{\n" +
            "  \"success\": true,\n" +
            "  \"message\": \"User created successfully\",\n" +
            "  \"user\": {\n" +
            "    \"userId\": \"%s\",\n" +
            "    \"username\": \"%s\",\n" +
            "    \"firstName\": \"%s\",\n" +
            "    \"lastName\": \"%s\"\n" +
            "  },\n" +
            "  \"pin\": {\n" +
            "    \"created\": true,\n" +
            "    \"status\": \"PIN created \"\n" +
            "  },\n" +
            "  \"emergencyCode\": {\n" +
            "    \"created\": %s,\n" +
            "    \"message\": \"%s\"\n" +
            "  }\n" +
            "}",
            userCreate.getResult().id,
            username,
            firstName,
            lastName,
            emergencyCodeCreated,
            safeEmergencyCodeMessage
        );

        UserCreateResponse userResponse = UserCreateResponse.builder()
                .status("success")
                .message("User created successfully")
                .data(responseJson)
                .build();

        return ResponseEntity.status(HttpStatus.OK).body(userResponse);
    }

    @Override
    public ResponseEntity<List<UserDto>> getUsers() {
        List<UserDto> usersDto = new ArrayList<>();
        final var listUsers = userRepository.findAll();
        if (!listUsers.isEmpty()) {
            listUsers.forEach(user -> {
                final var wallets = walletRepository.findWalletByUsers(user);
                usersDto.add(UserMapper.mapUserToUserDto(user, wallets));
            });
            return ResponseEntity.status(HttpStatus.OK).body(usersDto);
        }
        return ResponseEntity.status(HttpStatus.OK).body(usersDto);
    }


    /*
    * activer un utilisateur
    * changer le status de l'utilisateur de false a true
    * Active le compte Keycloak/local sans crédit MATIC automatique.
     */
    @Override
    public ResponseEntity<UserDto> activeUser(String userName, String realmName) {
        log.info("active User : {} ", userName);
        log.debug("active User : {} ", userName);

        if (userName == null) {
            log.error("username is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }

        Users users = userRepository.getUsersByUsername(userName);
        Users systemUser = userRepository.getUsersByUsername(walletBaseUsername);
        if(users != null && systemUser != null) {
            log.info("active user in keycloak");

            final var userKc = keycloakUserService.updateUserStatus(realmName, userName, true);
            if (userKc) {
                log.info("update user in local database");
                log.debug("update user in local database");
                users.setEnabled(true);
                users.setIdentyVerify(true);
                userRepository.saveAndFlush(users);

                // Pas de crédit MATIC à l'activation : le gas est rechargé uniquement lors d'une
                // opération réelle (retrait, forfait, transfert) si le solde utilisateur le permet.

                final var wallets = walletRepository.findWalletByUsers(users);
                UserDto result = UserMapper.mapUserToUserDto(users, wallets);
                return ResponseEntity.status(HttpStatus.OK).body(result);
            } else {
                log.error("invalid account. User not found in keycloak");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
            }
        } else {
            log.error("invalid account. User not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

    }

    @Override
    public ResponseEntity<UserResponse> getUser(final String userName) {
        log.info("get User : {} ", userName);
        return Optional.ofNullable(userRepository.getUsersByUsername(userName))
                .map(user -> {
                    log.info("User found: {}", userName);
                    ResponseEntity<UserDto> userDtoResponse = handleExistingUser(user);
                    if (userDtoResponse.getStatusCode().is2xxSuccessful() && userDtoResponse.getBody() != null) {
                        return ResponseEntity.ok(
                                UserResponse.builder()
                                        .status("success")
                                        .message("User retrieved successfully")
                                        .data(userDtoResponse.getBody())
                                        .build()
                        );
                    } else {
                        log.error("Failed to handle existing user: {}", userName);
                        return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body(
                                UserResponse.builder()
                                        .status("error")
                                        .message("Failed to retrieve user details")
                                        .data(null)
                                        .build()
                        );
                    }
                })
                .orElseGet(() -> {
                    log.error("Invalid account: user {} not found", userName);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                            UserResponse.builder()
                                    .status("error")
                                    .message("User not found")
                                    .data(null)
                                    .build()
                    );
                });
    }

    @Override
    public ResponseEntity<UserResponse> sendOtpToUser(final String userName, final String type, final String action) {
        log.info("generate otp and send to user : {} ", userName);
        try {
            // Vérifier si l'utilisateur existe
            final var user = userRepository.getUsersByUsername(userName);
            if (user == null && (action.equals("login") || action.equals("resetEmergencyCode"))) {
                log.error("invalid account. User not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        UserResponse.builder()
                                .status("error")
                                .message("Invalid account. User not found")
                                .data(null)
                                .build()
                );
            } else if (user != null && action.equals("validateAccount")) {
                log.error("username exist in the system");
                return ResponseEntity.status(HttpStatus.CONFLICT).body(
                        UserResponse.builder()
                                .status("error")
                                .message("Username already exists in the system")
                                .data(null)
                                .build()
                );
            }

            log.info("Generate OTP for action: {}, type: {}", action, type);
            final var otp = getAndSendOtpMessage(userName);

            if ("EMAIL".equalsIgnoreCase(type)) {
                // Envoi par email via Microsoft Graph
                if (user == null || user.getEmail() == null || user.getEmail().isBlank()) {
                    log.error("Cannot send email OTP: user email not found for {}", userName);
                    return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body(
                            UserResponse.builder()
                                    .status("error")
                                    .message("User email not found")
                                    .data(null)
                                    .build()
                    );
                }
                try {
                    emailService.sendHtmlEmail(
                            user.getEmail(),
                            "Votre code de vérification Akuunda Pay",
                            buildOtpEmailHtml(String.valueOf(otp))
                    );
                    log.info("Email OTP sent to {} for user {}", user.getEmail(), userName);
                } catch (Exception e) {
                    log.error("Failed to send OTP email to {}: {}", user.getEmail(), e.getMessage());
                    return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body(
                            UserResponse.builder()
                                    .status("error")
                                    .message("Failed to send OTP via email")
                                    .data(null)
                                    .build()
                    );
                }
            } else {
                // Envoi par SMS / WhatsApp
                final var smsResponse = sendUserOtpSms(userName, String.valueOf(otp), type);
                if (smsResponse.getStatusCode() != HttpStatus.OK) {
                    log.error("Failed to send OTP via {}", type);
                    return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body(
                            UserResponse.builder()
                                    .status("error")
                                    .message("Failed to send OTP via " + type)
                                    .data(null)
                                    .build()
                    );
                }
            }

            return ResponseEntity.ok(
                    UserResponse.builder()
                            .status("success")
                            .message("OTP generated and sent successfully")
                            .data(null)
                            .build()
            );
        } catch (BadRequestException ex) {
            log.error("Invalid account. User probably hasn't verified email or username or password not good.", ex);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    UserResponse.builder()
                            .status("error")
                            .message("Invalid account. Please verify email or username.")
                            .data(null)
                            .build()
            );
        }
    }

    @Override
    public ResponseEntity<UserResponse> login(final LoginRequest loginRequest, String realmName) {
        log.info("login with username : {} and otpCode : {}", loginRequest.getUsername(), loginRequest.getOtpCode());

        try {
            Users user = userRepository.getUsersByUsername(loginRequest.getUsername());
            if (user == null) {
                return buildErrorResponse(HttpStatus.NOT_FOUND, "User not found");
            }

            log.info("User found in local database");

            if (user.getUserId() == null) {
                return buildErrorResponse(HttpStatus.NOT_FOUND, "User not found");
            }

            if (isDemoUser(user)) {
                return handleDemoUserLogin(user, loginRequest.getOtpCode());
            } else {
                return handleOtpLogin(user, loginRequest.getOtpCode());
            }
        } catch (BadRequestException ex) {
            log.error("Invalid account. User probably hasn't verified email or username.", ex);
            return buildErrorResponse(HttpStatus.BAD_REQUEST, "Invalid account. Please verify email or username.");
        }
    }

    private boolean isDemoUser(Users user) {
        return demoUserName.equals(user.getUsername());
    }

    private ResponseEntity<UserResponse> handleDemoUserLogin(Users user, String password) {
        if (!password.equals(user.getPassword())) {
            log.error("Invalid account. Password does not match.");
            return buildErrorResponse(HttpStatus.NOT_FOUND, "Invalid OTP code or not equal password");
        }

        log.info("Password is valid for demo user");
        return buildSuccessResponse(user);
    }

    private ResponseEntity<UserResponse> handleOtpLogin(Users user, String otpCode) {
        boolean isOtpValid = otpService.verifyOtp(otpCode, user.getUsername());
        if (!isOtpValid) {
            log.error("Invalid OTP code or already validated");
            return buildErrorResponse(HttpStatus.NOT_FOUND, "Invalid OTP code or already validated");
        }

        log.info("OTP code is valid");
        return buildSuccessResponse(user);
    }

    private ResponseEntity<UserResponse> buildSuccessResponse(Users user) {
        final var wallets = walletRepository.findWalletByUsers(user);
        UserDto result = UserMapper.mapUserToUserDto(user, wallets);
        return ResponseEntity.ok(
                UserResponse.builder()
                        .status("success")
                        .message("Login successful")
                        .data(result)
                        .build()
        );
    }

    private ResponseEntity<UserResponse> buildErrorResponse(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(
                UserResponse.builder()
                        .status("error")
                        .message(message)
                        .data(null)
                        .build()
        );
    }


    @Override
    public ResponseEntity<UserResponse> loginSocial(LoginSocialRequest loginRequest, String realmName) {
        // Vérifier si loginRequest est null
        if (loginRequest == null) {
            log.error("Login request is null");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    UserResponse.builder()
                            .status("error")
                            .message("Login request is null")
                            .data(null)
                            .build()
            );
        }

        // Vérifier si typeLogin est "google" ou "facebook"
        if (!loginRequest.getTypeLogin().equals(GOOGLE) && !loginRequest.getTypeLogin().equals(FACEBOOK)) {
            log.error("Invalid typeLogin");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    UserResponse.builder()
                            .status("error")
                            .message("Invalid typeLogin")
                            .data(null)
                            .build()
            );
        }

        Users users;

        // Vérifier l'utilisateur selon le type de connexion
        if (loginRequest.getTypeLogin().equals(GOOGLE)) {
            users = userRepository.getUsersByGoogleId(loginRequest.getUsername());
        } else {
            users = userRepository.getUsersByFacebookId(loginRequest.getUsername());
        }

        if (users != null) {
            log.info("User found in local");
            if (users.getUserId() != null) {
                log.info("User found in local with userId: {}", users.getUserId());
                final var wallets = walletRepository.findWalletByUsers(users);
                UserDto result = UserMapper.mapUserToUserDto(users, wallets);
                return ResponseEntity.ok(
                        UserResponse.builder()
                                .status("success")
                                .message("Login successful")
                                .data(result)
                                .build()
                );
            } else {
                log.error("Invalid account. User not found");
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        UserResponse.builder()
                                .status("error")
                                .message("User not found")
                                .data(null)
                                .build()
                );
            }
        } else {
            log.error("Invalid account. User not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    UserResponse.builder()
                            .status("error")
                            .message("User not found")
                            .data(null)
                            .build()
            );
        }
    }

    @Override
    public ResponseEntity<AfricanDto> checkIfUserIsAfrican(String mssdin, String realmName) {
        log.info("Check if user is African with mssdin : {} ", mssdin);
        log.debug("Check if user is African with mssdin : {} ", mssdin);
        if (mssdin == null || mssdin.isEmpty()) {
            log.error("MSSDIN is null or empty");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
        }
        return smsService.isAfrican(mssdin);
    }

    @Override
    public ResponseEntity<WalletResponse> getUserWalletBalance(String username) {
        log.info("Fetching wallet balance for user: {}", username);

        // 🔹 1️⃣ Vérification de l'utilisateur
        Users user = userRepository.getUsersByUsername(username);
        if (user == null) {
            log.error("User not found with username: {}", username);
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    WalletResponse.builder()
                            .status("error")
                            .message("User not found")
                            .data(null)
                            .build()
            );
        }

        // 🔹 2️⃣ Récupération du solde du wallet (USDC)
        var walletResponse = walletService.getWalletBalance(user.getUsername());
        if (!walletResponse.getStatusCode().is2xxSuccessful() || walletResponse.getBody() == null) {
            log.error("Failed to fetch wallet balance for user {}", user.getUsername());
            return ResponseEntity.status(HttpStatus.EXPECTATION_FAILED).body(
                    WalletResponse.builder()
                            .status("error")
                            .message("Failed to fetch wallet balance")
                            .data(null)
                            .build()
            );
        }

        double userBalance;
        try {
            userBalance = Double.parseDouble(walletResponse.getBody());
        } catch (NumberFormatException e) {
            log.error("Invalid wallet balance format for user {}", user.getUsername());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    WalletResponse.builder()
                            .status("error")
                            .message("Invalid wallet balance format")
                            .data(null)
                            .build()
            );
        }

        // 🔹 3️⃣ Récupération du wallet associé
        Wallet wallet = walletRepository.findByUsers(user);
        if (wallet == null) {
            log.error("Wallet not found for user {}", user.getUsername());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    WalletResponse.builder()
                            .status("error")
                            .message("Wallet not found")
                            .data(null)
                            .build()
            );
        }

        // 🔹 4️⃣ Conversion devise (USDC -> devise du wallet)
        String walletCurrency = wallet.getCurrency().getCurrencyCode();
        BigDecimal convertedAmountBD = BigDecimal.ZERO;
        String operatorUsed = null;

        BigDecimal userBalanceBD = BigDecimal.valueOf(userBalance);

        // ⚠️ Venly retourne déjà les montants en USD, on utilise directement Currency Freaks pour convertir USD → devise locale
        log.info("Conversion USD → {} pour wallet {} (montant brut: {} USD)", walletCurrency, wallet.getId(), userBalanceBD);

        var conversionResponse = freaksClientService.convertCurrency("USD", walletCurrency, userBalanceBD.doubleValue());
        if (conversionResponse.getStatusCode().is2xxSuccessful() && conversionResponse.getBody() != null) {
            try {
                String convertedAmountStr = conversionResponse.getBody().getConvertedAmount();
                convertedAmountBD = new BigDecimal(convertedAmountStr);
                operatorUsed = "currencyfreaks";
                log.info("✅ Conversion utilisant Currency Freaks : {} USD → {} {}",
                        userBalanceBD, convertedAmountBD, walletCurrency);
            } catch (NumberFormatException e) {
                log.warn("Invalid conversion amount format.");
                convertedAmountBD = BigDecimal.ZERO;
            }
        } else {
            log.error("❌ Erreur lors de la conversion Currency Freaks (USD → {}): status={}", 
                    walletCurrency, conversionResponse.getStatusCode());
            convertedAmountBD = BigDecimal.ZERO;
        }

        userBalance = userBalanceBD.setScale(2, RoundingMode.HALF_UP).doubleValue();
        double convertedAmount = convertedAmountBD.setScale(2, RoundingMode.HALF_UP).doubleValue();

        log.info("User balance: {} USD | Converted: {} {} (operator: {})",
                userBalance, convertedAmount, walletCurrency,
                operatorUsed != null ? operatorUsed : "unknown");

        // 🔹 6️⃣ Retour du résultat
        WalletBalanceDto result = new WalletBalanceDto(userBalance, convertedAmount);
        return ResponseEntity.ok(
                WalletResponse.builder()
                        .status("success")
                        .message("Wallet balance fetched successfully")
                        .data(result)
                        .build()
        );
    }

    /**
    * Met à jour l'acceptation des CGU (Conditions Générales d'Utilisation) pour tous les utilisateurs.
    * Si un utilisateur n'a pas encore accepté les CGU, cette méthode les marque comme acceptées
    * et enregistre la date d'acceptation comme étant la date de création de l'utilisateur.
    */
    @Override
    public ResponseEntity<String> updateUserCguAcceptation() {
        final var users = userRepository.findAll();
        users.forEach(user -> {
            if (!user.isCguAcceptation()) {
                user.setCguAcceptation(true);
                user.setDateCguAcceptation(user.getCreatedAt());
                userRepository.saveAndFlush(user);
            }
        });
        return ResponseEntity.ok("Mise à jour des utilisateurs terminée.");
    }

    @Override
    public ResponseEntity<UserDto> handleExistingUser(Users user) {
        log.info("Handling existing user: {}", user.getUsername());

        // 1️⃣ Récupération du wallet
        Wallet wallet = walletRepository.findByUsers(user);
        if (wallet == null) {
            log.error("Wallet not found for user {}", user.getUsername());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // 2️⃣ Appel blockchain pour le solde en temps réel (best-effort)
        WalletBalanceRaw walletBalanceRaw = getUserWalletBalanceRaw(user.getUsername());

        if (walletBalanceRaw != null) {
            // Mise à jour des soldes avec les montants BRUTS (non arrondis)
            wallet.setBalance(walletBalanceRaw.userBalanceRaw().doubleValue());
            wallet.setDeviseBalance(walletBalanceRaw.convertedAmountRaw().doubleValue());
            walletRepository.saveAndFlush(wallet);
        } else {
            // Blockchain indisponible — on retourne le dernier solde connu en DB
            log.warn("Blockchain balance unavailable for user {}, using cached DB balance", user.getUsername());
        }

        log.info("✅ Wallet mis à jour avec montants bruts pour user {}: balance={} USDC, deviseBalance={} {}", 
                user.getUsername(), 
                walletBalanceRaw.userBalanceRaw(), 
                walletBalanceRaw.convertedAmountRaw(), 
                wallet.getCurrency() != null ? wallet.getCurrency().getCurrencyCode() : "N/A");

        // 4️⃣ Mapping et retour DTO
        // Le convertedAmount est disponible dans wallets[0].balance
        // car wallet.getDeviseBalance() est mis à jour avec le montant brut converti
        UserDto userDto = UserMapper.mapUserToUserDto(user, List.of(wallet));
        return ResponseEntity.ok(userDto);
    }

    /**
     * Structure interne pour stocker les montants bruts (BigDecimal) avant arrondi
     */
    private record WalletBalanceRaw(
            BigDecimal userBalanceRaw,
            BigDecimal convertedAmountRaw,
            String operatorUsed,
            BigDecimal rateUsed
    ) {}

    /**
     * Récupère le solde du wallet avec les montants BRUTS (BigDecimal, non arrondis)
     * Utilise la même logique que getUserWalletBalance mais retourne les montants bruts
     * 
     * @param username Le nom d'utilisateur
     * @return WalletBalanceRaw avec les montants bruts, ou null en cas d'erreur
     */
    private WalletBalanceRaw getUserWalletBalanceRaw(String username) {
        log.info("Fetching raw wallet balance for user: {}", username);

        // 🔹 1️⃣ Vérification de l'utilisateur
        Users user = userRepository.getUsersByUsername(username);
        if (user == null) {
            log.error("User not found with username: {}", username);
            return null;
        }

        // 🔹 2️⃣ Récupération du solde du wallet (USDC)
        var walletResponse = walletService.getWalletBalance(user.getUsername());
        if (!walletResponse.getStatusCode().is2xxSuccessful() || walletResponse.getBody() == null) {
            log.error("Failed to fetch wallet balance for user {}", user.getUsername());
            return null;
        }

        BigDecimal userBalanceBD;
        try {
            userBalanceBD = new BigDecimal(walletResponse.getBody()); // Montant BRUT, toutes les décimales
        } catch (NumberFormatException e) {
            log.error("Invalid wallet balance format for user {}", user.getUsername());
            return null;
        }

        // 🔹 3️⃣ Récupération du wallet associé
        Wallet wallet = walletRepository.findByUsers(user);
        if (wallet == null) {
            log.error("Wallet not found for user {}", user.getUsername());
            return null;
        }

        // 🔹 4️⃣ Conversion devise (USD -> devise du wallet) avec montants BRUTS
        String walletCurrency = wallet.getCurrency().getCurrencyCode();
        BigDecimal convertedAmountBD = BigDecimal.ZERO;
        String operatorUsed = null;

        // ⚠️ Venly retourne déjà les montants en USD, on utilise directement Currency Freaks pour convertir USD → devise locale
        log.info("Conversion USD → {} pour wallet {} (montant brut: {} USD)", walletCurrency, wallet.getId(), userBalanceBD);

        var conversionResponse = freaksClientService.convertCurrency("USD", walletCurrency, userBalanceBD.doubleValue());
        if (conversionResponse.getStatusCode().is2xxSuccessful() && conversionResponse.getBody() != null) {
            try {
                String convertedAmountStr = conversionResponse.getBody().getConvertedAmount();
                convertedAmountBD = new BigDecimal(convertedAmountStr); // Valeur brute depuis Currency Freaks
                operatorUsed = "currencyfreaks";
                log.info("✅ Conversion utilisant Currency Freaks (BRUT) : {} USD → {} {} (valeur brute, non arrondie)", 
                        userBalanceBD, convertedAmountBD, walletCurrency);
            } catch (NumberFormatException e) {
                log.warn("Invalid conversion amount format for user {}", user.getUsername());
                convertedAmountBD = BigDecimal.ZERO;
            }
        } else {
            log.error("❌ Erreur lors de la conversion Currency Freaks (USD → {}): status={}", 
                    walletCurrency, conversionResponse.getStatusCode());
            convertedAmountBD = BigDecimal.ZERO;
        }

        log.info("Raw wallet balance for {}: {} USD | Converted: {} {} (opérateur: {})",
                user.getUsername(), userBalanceBD, convertedAmountBD, walletCurrency, 
                operatorUsed != null ? operatorUsed : "unknown");

        return new WalletBalanceRaw(userBalanceBD, convertedAmountBD, operatorUsed, null);
    }

    private Users createLocalUser(AddUserCommand command, UserRepresentation user,
                                  String userId, String reference, CountryCurrency countryCurrency) {

        final var verifiedUser = userRepository.getUsersByUsername(command.getUsername());
        if(verifiedUser != null) {
            log.info("username exist in the system");
            return null;
        }
        Users localUser = new Users();
        localUser.setFirstname(command.getFirstName());
        localUser.setDateNaissance(command.getDateNaissance());
        localUser.setLastname(command.getLastName());
        localUser.setEmail(command.getEmail());
        localUser.setCreatedAt(Timestamp.from(Instant.now()));
        localUser.setUsername(user.getUsername());
        localUser.setMobilePhone(command.getUsername());
        localUser.setUserId(userId);
        localUser.setEnabled(user.isEnabled());
        localUser.setEmailVerified(user.isEmailVerified());
        localUser.setAccountType(command.getTypeCompte());
        localUser.setAdresse(command.getAdresse());
        localUser.setSiret(command.getSiret());
        localUser.setFacebookId(command.getFacebookId());
        localUser.setGoogleId(command.getGoogleId());
        localUser.setRaisonSociale(command.getRaisonSociale());
        localUser.setDateCreation(command.getDateCreation());
        localUser.setReference(reference);
        localUser.setIdentyVerify(false);
        localUser.setCountryCurrency(countryCurrency);
        localUser.setCguAcceptation(command.isCguAcceptation());
        if (command.isCguAcceptation()) {
            localUser.setDateCguAcceptation(Timestamp.from(Instant.now()));
        }
        userRepository.save(localUser);
        log.info("Save user : {} ", localUser);
        return localUser;
    }

    private int generateOtp() {
        Random r = new Random(System.currentTimeMillis());
        return 10000 + r.nextInt(20000);
    }

    /**
     * Arrondit une valeur double à deux décimales de manière fiable.
     */
    private double roundToTwoDecimals(double value) {
        return BigDecimal
                .valueOf(value)
                .setScale(2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /**
     * Détermine l'opérateur selon le pays pour l'affichage du solde
     * Pays africains → YellowCard, autres → Guardarian
     */
    private String detectOperatorForBalance(String countryCode) {
        if (countryCode == null || countryCode.isEmpty()) {
            return "guardarian"; // Par défaut Guardarian si pas de pays
        }

        // Liste des pays africains (même logique que dans FeesCalculationServiceImpl)
        String[] africanCountries = {"CI", "SN", "ML", "BF", "NE", "TG", "BJ", "GN", "GW", "MR", 
                                     "CM", "TD", "CF", "CG", "CD", "GA", "GQ", "ST", "AO", "ZM",
                                     "ZW", "BW", "NA", "ZA", "LS", "SZ", "MW", "MZ", "MG", "MU",
                                     "SC", "KM", "KE", "UG", "RW", "BI", "TZ", "ET", "ER", "DJ",
                                     "SO", "SD", "SS", "EG", "LY", "TN", "DZ", "MA", "EH", "NG"};
        
        for (String african : africanCountries) {
            if (african.equalsIgnoreCase(countryCode)) {
                return "yellowcard";
            }
        }
        
        return "guardarian";
    }

    /**
     * Détermine le provider à utiliser pour récupérer le taux
     * Priorité: 1) lastProvider du wallet, 2) Détection automatique selon le pays
     */
    private String determineProvider(Wallet wallet, Users user) {
        // Priorité 1: Utiliser le provider sauvegardé dans le wallet
        if (wallet.getLastProvider() != null && !wallet.getLastProvider().isEmpty()) {
            log.debug("Provider déterminé depuis wallet.lastProvider: {}", wallet.getLastProvider());
            return wallet.getLastProvider().toLowerCase();
        }
        
        // Priorité 2: Détection automatique selon le pays
        String countryCode = null;
        if (user.getCountryCurrency() != null && user.getCountryCurrency().getCountryCode() != null) {
            countryCode = user.getCountryCurrency().getCountryCode();
        }
        String provider = detectOperatorForBalance(countryCode);
        log.debug("Provider déterminé automatiquement selon le pays {}: {}", countryCode, provider);
        return provider;
    }

    /**
     * Récupère le taux YellowCard en temps réel depuis l'API
     * @return Le taux buy (BigDecimal) ou null en cas d'erreur
     */
    private BigDecimal getYellowCardRateInRealTime(String currency, String countryCode) {
        try {
            ResponseEntity<String> ratesResponse = null;
            
            // Essayer d'abord avec channelId si disponible
            String channelId = null;
            if (countryCode != null && !countryCode.isEmpty()) {
                channelId = getFirstChannelIdForCountry(countryCode);
                if (channelId != null && !channelId.isEmpty()) {
                    log.debug("Récupération du taux YellowCard avec channelId: {}", channelId);
                    ratesResponse = yellowCardClientService.getRatesByChannelId(channelId, currency);
                }
            }
            
            // Fallback sur l'ancienne méthode si channelId n'est pas disponible ou si l'appel a échoué
            if (ratesResponse == null || 
                ratesResponse.getStatusCode() != HttpStatus.OK || 
                ratesResponse.getBody() == null) {
                log.debug("Fallback: Récupération du taux YellowCard sans channelId");
                ratesResponse = yellowCardClientService.getRates(currency);
            }
            
            if (ratesResponse.getStatusCode() == HttpStatus.OK && ratesResponse.getBody() != null) {
                try {
                    JsonNode ratesJson = objectMapper.readTree(ratesResponse.getBody());
                    
                    // Parser la réponse JSON pour extraire le taux "buy"
                    if (ratesJson.has("buy")) {
                        double buyRate = ratesJson.get("buy").asDouble();
                        log.info("✅ Taux YellowCard buy récupéré en temps réel: {} pour devise {}", buyRate, currency);
                        return BigDecimal.valueOf(buyRate);
                    } else {
                        log.warn("⚠️ Champ 'buy' non trouvé dans la réponse YellowCard: {}", ratesResponse.getBody());
                    }
                } catch (Exception e) {
                    log.error("❌ Erreur lors du parsing de la réponse YellowCard: {}", e.getMessage(), e);
                }
            } else {
                log.warn("⚠️ Échec de la récupération du taux YellowCard: status={}, body={}", 
                        ratesResponse != null ? ratesResponse.getStatusCode() : "null",
                        ratesResponse != null ? ratesResponse.getBody() : "null");
            }
        } catch (Exception e) {
            log.error("❌ Erreur lors de la récupération du taux YellowCard en temps réel: {}", e.getMessage(), e);
        }
        
        return null;
    }

    /**
     * Récupère le taux Guardarian en temps réel depuis l'API estimate
     * @return Le taux estimated_exchange_rate (BigDecimal) ou null en cas d'erreur
     */
    private BigDecimal getGuardarianRateInRealTime(String currency) {
        try {
            // Pour l'affichage du solde, on convertit USDC → devise locale
            // Exemple: 1 USDC → X EUR (où X est le taux)
            // Guardarian utilise from_currency=USDC, to_currency=devise locale, from_amount=1.0
            EstimateRequest estimateRequest = new EstimateRequest(
                    "USDC",    // from (crypto)
                    currency,  // to (devise locale, ex: EUR)
                    1.0        // from_amount (1 USDC pour obtenir le taux)
            );
            
            log.debug("Récupération du taux Guardarian pour USDC → {}", currency);
            ResponseEntity<EstimateResponse> estimateResponse = guardarianClientService.getEstimate(estimateRequest);
            
            if (estimateResponse != null && 
                estimateResponse.getStatusCode() == HttpStatus.OK && 
                estimateResponse.getBody() != null) {
                
                EstimateResponse response = estimateResponse.getBody();
                double rate = response.rate();
                
                if (rate > 0) {
                    log.info("✅ Taux Guardarian estimated_exchange_rate récupéré en temps réel: {} pour USDC → {}", rate, currency);
                    return BigDecimal.valueOf(rate);
                } else {
                    log.warn("⚠️ Taux Guardarian invalide (rate <= 0): {}", rate);
                }
            } else {
                log.warn("⚠️ Échec de la récupération du taux Guardarian: status={}, body={}", 
                        estimateResponse != null ? estimateResponse.getStatusCode() : "null",
                        estimateResponse != null ? estimateResponse.getBody() : "null");
            }
        } catch (Exception e) {
            log.error("❌ Erreur lors de la récupération du taux Guardarian en temps réel: {}", e.getMessage(), e);
        }
        
        return null;
    }

    /**
     * Récupère le premier channelId disponible pour un pays
     */
    private String getFirstChannelIdForCountry(String countryCode) {
        try {
            var channelsResponse = yellowCardClientService.getChannels(countryCode);
            if (channelsResponse.getStatusCode().is2xxSuccessful() && channelsResponse.getBody() != null) {
                JsonNode channelsJson = objectMapper.readTree(channelsResponse.getBody());
                if (channelsJson.isArray() && channelsJson.size() > 0) {
                    JsonNode firstChannel = channelsJson.get(0);
                    if (firstChannel.has("id")) {
                        return firstChannel.get("id").asText();
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Erreur lors de la récupération des channels pour le pays {}: {}", countryCode, e.getMessage());
        }
        return null;
    }

    /**
     * Récupère le taux YellowCard buy actuel pour la conversion du solde
     */
    private Double getCurrentYellowCardBuyRate(String currency, String countryCode) {
        try {
            ResponseEntity<String> ratesResponse = null;
            
            // Essayer d'abord avec channelId si disponible
            if (countryCode != null && !countryCode.isEmpty()) {
                var channelsResponse = yellowCardClientService.getChannels(countryCode);
                if (channelsResponse.getStatusCode().is2xxSuccessful() && channelsResponse.getBody() != null) {
                    try {
                        JsonNode channelsJson = objectMapper.readTree(channelsResponse.getBody());
                        if (channelsJson.isArray() && channelsJson.size() > 0) {
                            JsonNode firstChannel = channelsJson.get(0);
                            if (firstChannel.has("id")) {
                                String channelId = firstChannel.get("id").asText();
                                ratesResponse = yellowCardClientService.getRatesByChannelId(channelId, currency);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Erreur lors du parsing des channels pour country {}: {}", countryCode, e.getMessage());
                    }
                }
            }
            
            // Fallback si channelId n'est pas disponible
            if (ratesResponse == null || 
                ratesResponse.getStatusCode() != HttpStatus.OK || 
                ratesResponse.getBody() == null) {
                ratesResponse = yellowCardClientService.getRates(currency);
            }
            
            if (ratesResponse.getStatusCode() == HttpStatus.OK && ratesResponse.getBody() != null) {
                try {
                    JsonNode ratesJson = objectMapper.readTree(ratesResponse.getBody());
                    if (ratesJson.has("buy")) {
                        double buyRate = ratesJson.get("buy").asDouble();
                        if (buyRate > 0) {
                            return buyRate;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Erreur lors du parsing du taux YellowCard: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Erreur lors de la récupération du taux YellowCard pour currency {}: {}", currency, e.getMessage());
        }
        return null;
    }

    /**
     * Récupère le taux Guardarian estimated_exchange_rate actuel pour la conversion du solde
     * Pour l'affichage du solde, on convertit USDC → devise locale
     */
    private Double getCurrentGuardarianExchangeRate(String walletCurrency) {
        try {
            // Pour l'affichage du solde, on veut convertir USDC → devise locale
            // On utilise un montant de référence (1 USDC) pour obtenir le taux
            EstimateRequest estimateRequest = new EstimateRequest("USDC", walletCurrency, 1.0);
            ResponseEntity<EstimateResponse> estimateResponse = guardarianClientService.getEstimate(estimateRequest);
            
            if (estimateResponse != null && 
                estimateResponse.getStatusCode().is2xxSuccessful() && 
                estimateResponse.getBody() != null) {
                EstimateResponse estimate = estimateResponse.getBody();
                if (estimate != null) {
                    double exchangeRate = estimate.rate(); // estimated_exchange_rate
                    if (exchangeRate > 0) {
                        return exchangeRate;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("Erreur lors de la récupération du taux Guardarian pour currency {}: {}", walletCurrency, e.getMessage());
        }
        return null;
    }

    private static String convertToHex(byte[] encodePasswordByteArray) {
        char [] hexChars = new char[encodePasswordByteArray.length * 2];
        for (int i = 0; i< encodePasswordByteArray.length; i++){
            int v = encodePasswordByteArray[i] & 0xFF;
            hexChars[i * 2] = hexArray[v >>> 4];
            hexChars[i * 2 + 1] = hexArray[v & 0x0F];
        }
        return new  String(hexChars);
    }

    private String getEncodedPassword(String password) {
        String leading = "*";
        byte[] passwordByteArray = password.getBytes(StandardCharsets.UTF_8);
        byte[] encodePasswordByteArray = DigestUtils.sha1(passwordByteArray);
        return leading + convertToHex(encodePasswordByteArray).toUpperCase();
    }

    private ResponseEntity<String> sendUserOtpSms(String userName, String message, String type) {

        log.info("Send sms to user to  valid account");
        List<String> destinataireList = new ArrayList<>();
        destinataireList.add(userName);
        return smsService.sendSms(message, destinataireList, type);
    }

    private Integer getAndSendOtpMessage(String username) {
        Otp otp = new Otp();
        final var otpCode = generateOtp();
        otp.setOtpCode(String.valueOf(otpCode));
        otp.setUserName(username);
        otp.setStatus(StatusTypeEnum.ATTENTE.toString());

        Timestamp dateDay = Timestamp.from(Instant.now());
        long dateExpired = dateDay.getTime() + (10 * 60 * 1000); // expire in 10 minutes
        otp.setDateCreate(dateDay);
        otp.setExpiredDate(new Timestamp(dateExpired));
        otpRepository.save(otp);
        return otpCode;
    }

    private String buildOtpEmailHtml(String otpCode) {
        return """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:0 auto;padding:32px;background:#f9f9fb;border-radius:12px;">
                  <div style="text-align:center;margin-bottom:24px;">
                    <img src="https://akuunda-pay.io/logo.png" alt="Akuunda Pay" style="height:48px;" />
                  </div>
                  <div style="background:#ffffff;border-radius:10px;padding:32px;box-shadow:0 2px 8px rgba(0,0,0,0.06);">
                    <h2 style="color:#4B0056;margin:0 0 8px;">Code de vérification</h2>
                    <p style="color:#555;font-size:14px;margin:0 0 24px;">
                      Utilisez ce code pour confirmer votre identité sur Akuunda Pay.<br/>
                      Il expire dans <strong>10 minutes</strong>.
                    </p>
                    <div style="background:#f0e8f5;border-radius:8px;padding:20px;text-align:center;letter-spacing:8px;font-size:32px;font-weight:bold;color:#4B0056;">
                      %s
                    </div>
                    <p style="color:#999;font-size:12px;margin:24px 0 0;text-align:center;">
                      Si vous n'êtes pas à l'origine de cette demande, ignorez cet email.
                    </p>
                  </div>
                  <p style="text-align:center;color:#ccc;font-size:11px;margin-top:16px;">© Akuunda Pay</p>
                </div>
                """.formatted(otpCode);
    }

    private JSONObject getCreateUserBody(String type, String value, String reference) {
        JSONObject requestBody = new JSONObject();
        JSONObject signingMethod = new JSONObject();

        signingMethod.put("type", type);
        signingMethod.put("value", value);

        requestBody.put("signingMethod", signingMethod);
        requestBody.put("reference", reference);
        return requestBody;
    }

    // ── Vérification des tokens sociaux (server-side) ───────────────────────

    public String verifyGoogleToken(String idToken) {
        try {
            String url = "https://oauth2.googleapis.com/tokeninfo?id_token=" + idToken;
            org.springframework.web.client.RestTemplate rt = new org.springframework.web.client.RestTemplate();
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> body = rt.getForObject(url, java.util.Map.class);
            if (body == null) return null;
            String error = (String) body.get("error_description");
            if (error != null) return null;
            return (String) body.get("sub");
        } catch (Exception e) {
            log.warn("Google token verification failed: {}", e.getMessage());
            return null;
        }
    }

    public String verifyFacebookToken(String accessToken) {
        // iOS Facebook Limited Login returns an OIDC JWT (starts with "eyJ")
        // Android returns a regular opaque access token
        if (accessToken.startsWith("eyJ")) {
            return verifyFacebookLimitedLoginJwt(accessToken);
        }
        try {
            String url = "https://graph.facebook.com/me?access_token=" + accessToken + "&fields=id";
            org.springframework.web.client.RestTemplate rt = new org.springframework.web.client.RestTemplate();
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> body = rt.getForObject(url, java.util.Map.class);
            if (body == null) return null;
            return (String) body.get("id");
        } catch (Exception e) {
            log.warn("Facebook token verification failed: {}", e.getMessage());
            return null;
        }
    }

    private String verifyFacebookLimitedLoginJwt(String idToken) {
        try {
            com.nimbusds.jwt.SignedJWT jwt = com.nimbusds.jwt.SignedJWT.parse(idToken);
            String kid = jwt.getHeader().getKeyID();
            log.info("Facebook Limited Login JWT - kid={}", kid);

            com.nimbusds.jose.jwk.JWKSet jwkSet = com.nimbusds.jose.jwk.JWKSet
                    .load(new java.net.URL("https://limited.facebook.com/.well-known/oauth/openid/jwks/"));
            log.info("Facebook JWKS loaded - keys={}", jwkSet.getKeys().stream().map(k -> k.getKeyID()).toList());

            com.nimbusds.jose.jwk.RSAKey rsaKey = (com.nimbusds.jose.jwk.RSAKey) jwkSet.getKeyByKeyId(kid);
            if (rsaKey == null) {
                log.warn("Facebook Limited Login JWT - no JWKS key found for kid={}", kid);
                return null;
            }

            com.nimbusds.jose.crypto.RSASSAVerifier verifier = new com.nimbusds.jose.crypto.RSASSAVerifier(rsaKey);
            if (!jwt.verify(verifier)) {
                log.warn("Facebook Limited Login JWT - signature verification failed");
                return null;
            }

            com.nimbusds.jwt.JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (claims.getExpirationTime().before(new java.util.Date())) {
                log.warn("Facebook Limited Login JWT - token expired at {}", claims.getExpirationTime());
                return null;
            }

            log.info("Facebook Limited Login JWT - verified OK, sub={}", claims.getSubject());
            return claims.getSubject();
        } catch (Exception e) {
            log.warn("Facebook Limited Login JWT verification failed: {}", e.getMessage(), e);
            return null;
        }
    }

    public String verifyAppleToken(String identityToken) {
        try {
            com.nimbusds.jwt.SignedJWT jwt = com.nimbusds.jwt.SignedJWT.parse(identityToken);
            String kid = jwt.getHeader().getKeyID();
            com.nimbusds.jose.jwk.JWKSet jwkSet = com.nimbusds.jose.jwk.JWKSet
                    .load(new java.net.URL("https://appleid.apple.com/auth/keys"));
            com.nimbusds.jose.jwk.RSAKey rsaKey = (com.nimbusds.jose.jwk.RSAKey) jwkSet.getKeyByKeyId(kid);
            if (rsaKey == null) return null;
            com.nimbusds.jose.crypto.RSASSAVerifier verifier =
                    new com.nimbusds.jose.crypto.RSASSAVerifier(rsaKey);
            if (!jwt.verify(verifier)) return null;
            com.nimbusds.jwt.JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (claims.getExpirationTime().before(new java.util.Date())) return null;
            return claims.getSubject();
        } catch (Exception e) {
            log.warn("Apple token verification failed: {}", e.getMessage());
            return null;
        }
    }
}
