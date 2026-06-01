package org.akuunda.akuundawallet.keycloak.impl.controller;

import org.akuunda.akuundawallet.keycloak.api.dto.UpdateProfileRequest;
import org.akuunda.akuundawallet.keycloak.api.dto.UpdateProfileResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.keycloak.api.dao.UserRepository;
import org.akuunda.akuundawallet.keycloak.api.dto.*;
import org.akuunda.akuundawallet.keycloak.api.dto.external.UsernameRequest;
import org.akuunda.akuundawallet.keycloak.api.entities.Users;
import org.akuunda.akuundawallet.keycloak.api.service.KeycloakUserService;
import org.akuunda.akuundawallet.keycloak.api.service.UserService;
import org.akuunda.akuundawallet.wallet.api.service.WalletService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping(path = AkunndaUserController.API_SERVICES_ROOT, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Akunnda - Users", description = "Gestion des utilisateurs pour différents realms : login, création, mise à jour, vérification et wallet")
@RequiredArgsConstructor
public class AkunndaUserController {

    public static final String API_SERVICES_ROOT = "/api/internal/v1/users/{realmName}";
    private final UserService userService;
    private final WalletService walletService;
    private final KeycloakUserService keycloakUserService;
    private final UserRepository userRepository;

    // ------------------------------------------------------------------------
    // LOGIN
    // ------------------------------------------------------------------------
    @PostMapping("/login")
    @Operation(
            operationId = "userLogin",
            summary = "Login d'un utilisateur dans un realm spécifié",
            description = "Permet à un utilisateur de se connecter dans le realm spécifié.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "LoginRequest JSON",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LoginRequest.class),
                            examples = @ExampleObject(
                                    name = "Login Example",
                                    value = """
                                {
                                  "username": "john_doe",
                                  "otpCode": "12345"
                                }
                                """
                            )
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Connexion réussie",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Success Response",
                                    value = """
                                {
                                  "status": "success",
                                  "message": "Login successful",
                                  "data": {
                                    "userId": "ae39223a-3cd7-4f18-9e4b-51fb64c8f2db",
                                    "username": "john_doe",
                                    "roles": ["ROLE_USER"],
                                    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
                                  }
                                }
                                """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Requête invalide",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Bad Request Response",
                                    value = """
                                {
                                  "status": "error",
                                  "message": "Invalid request payload",
                                  "data": null
                                }
                                """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Nom d'utilisateur ou mot de passe introuvable",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Not Found Response",
                                    value = """
                                {
                                  "status": "error",
                                  "message": "Username or password not found",
                                  "data": null
                                }
                                """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Erreur interne du serveur",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Internal Server Error Response",
                                    value = """
                                {
                                  "status": "error",
                                  "message": "Internal server error",
                                  "data": null
                                }
                                """
                            )
                    )
            )
    })
    public ResponseEntity<UserResponse> login(@PathVariable String realmName,
                                              @RequestBody @Valid LoginRequest loginRequest) {
        log.info("User login request in realm {}", realmName);
        log.debug("Login details: {}", loginRequest);
        return userService.login(loginRequest, realmName);
    }

    @PostMapping("/loginSocial")
    @Operation(
            operationId = "userLoginSocial",
            summary = "Login social d'un utilisateur dans un realm spécifié",
            description = "Permet à un utilisateur de se connecter via un provider social.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "LoginSocialRequest JSON",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LoginSocialRequest.class),
                            examples = @ExampleObject(
                                    name = "LoginSocial Example",
                                    value = """
                                {
                                  "socialProvider": "GOOGLE",
                                  "token": "google_oauth_token"
                                }
                                """
                            )
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Connexion réussie",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Success Response",
                                    value = """
                                {
                                  "status": "success",
                                  "message": "Social login successful",
                                  "data": {
                                    "userId": "ae39223a-3cd7-4f18-9e4b-51fb64c8f2db",
                                    "username": "john_doe",
                                    "roles": ["ROLE_USER"],
                                    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
                                  }
                                }
                                """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Requête invalide",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Bad Request Response",
                                    value = """
                                {
                                  "status": "error",
                                  "message": "Invalid request payload",
                                  "data": null
                                }
                                """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Utilisateur introuvable",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Not Found Response",
                                    value = """
                                {
                                  "status": "error",
                                  "message": "User not found",
                                  "data": null
                                }
                                """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Erreur interne du serveur",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Internal Server Error Response",
                                    value = """
                                {
                                  "status": "error",
                                  "message": "Internal server error",
                                  "data": null
                                }
                                """
                            )
                    )
            )
    })
    public ResponseEntity<UserResponse> loginSocial(@PathVariable String realmName,
                                               @RequestBody @Valid LoginSocialRequest loginRequest) {
        log.info("User social login request in realm {}", realmName);
        log.debug("LoginSocial details: {}", loginRequest);
        return userService.loginSocial(loginRequest, realmName);
    }


    @PutMapping("/update-cgu-acceptation")
    @Operation(
            summary = "Met à jour l'acceptation des CGU pour tous les utilisateurs",
            description = "Cette méthode met à jour l'acceptation des CGU pour tous les utilisateurs. Si un utilisateur n'a pas encore accepté les CGU, elles sont marquées comme acceptées et la date d'acceptation est définie comme la date de création de l'utilisateur."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Mise à jour réussie",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Réponse réussie",
                                    value = """
                {
                  "status": "success",
                  "message": "Mise à jour des utilisateurs terminée."
                }
                """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Erreur interne du serveur",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Erreur interne",
                                    value = """
                {
                  "status": "error",
                  "message": "Erreur interne du serveur"
                }
                """
                            )
                    )
            )
    })
    public ResponseEntity<String> updateUserCguAcceptation() {
        return userService.updateUserCguAcceptation();
    }


    // ------------------------------------------------------------------------
    // USER CREATION
    // ------------------------------------------------------------------------
    @PostMapping("/users/enterprise")
    @Operation(
            operationId = "addUserEnterprise",
            summary = "Ajoute un nouvel utilisateur entreprise",
            description = "Crée un utilisateur de type entreprise dans le realm spécifié. " +
                    "Le PIN est automatiquement créé chez Venly et stocké en base de données locale. " +
                    "L'emergency code est optionnel et peut être créé en même temps si fourni.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "AddUserEntCommand JSON",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AddUserEntCommand.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Exemple avec emergency code",
                                            value = """
                                            {
                                              "username": "002250759146858",
                                              "firstName": "kone",
                                              "lastName": "souleymane",
                                              "countryCode": "CI",
                                              "mobilePhone": "002250777832982",
                                              "pinCode": "123456",
                                              "partialEmergencyCode": "ABC12",
                                              "email": "entreprise@example.com",
                                              "siret": "123456789",
                                              "raisonSociale": "Entreprise Test",
                                              "adresse": "Abidjan, Côte d'Ivoire",
                                              "dateCreation": "2023-10-01",
                                              "cguAcceptation": true
                                            }
                                            """
                                    ),
                                    @ExampleObject(
                                            name = "Exemple sans emergency code",
                                            value = """
                                            {
                                              "username": "002250759146858",
                                              "firstName": "kone",
                                              "lastName": "souleymane",
                                              "countryCode": "CI",
                                              "mobilePhone": "002250777832982",
                                              "pinCode": "123456",
                                              "partialEmergencyCode": "",
                                              "email": "entreprise@example.com",
                                              "siret": "123456789",
                                              "raisonSociale": "Entreprise Test",
                                              "adresse": "Abidjan, Côte d'Ivoire",
                                              "dateCreation": "2023-10-01",
                                              "cguAcceptation": true
                                            }
                                            """
                                    )
                            }
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Utilisateur créé avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "status": "success",
                                      "message": "User created successfully",
                                      "data": {
                                        "userId": "ae39223a-3cd7-4f18-9e4b-51fb64c8f2db",
                                        "username": "002250759146858",
                                        "firstName": "John",
                                        "lastName": "Doe"
                                      }
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Conflit - L'utilisateur existe déjà",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "status": "error",
                                      "message": "User already exists",
                                      "data": null
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Ressource non trouvée",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "status": "error",
                                      "message": "CountryCurrency not found",
                                      "data": null
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Erreur interne du serveur",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "status": "error",
                                      "message": "Internal server error",
                                      "data": null
                                    }
                                    """
                            )
                    )
            )
    })
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<UserCreateResponse> addUser(@PathVariable String realmName,
                                                      @RequestBody @Valid AddUserEntCommand command) {
        log.info("Add enterprise user in realm {}", realmName);
        return userService.createUserEnterprise(command, realmName);
    }

    /**
     * GET /api/internal/v1/users
     */
    @PostMapping(path = "/getUser", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(
            operationId = "getUser",
            summary = "Récupère un utilisateur par son nom d'utilisateur",
            description = """
                Permet de récupérer les informations d'un utilisateur à partir de son nom d'utilisateur
                dans le realm spécifié. Cet endpoint retourne les détails complets de l'utilisateur si trouvé.
                
                **⚠️ IMPORTANT - Conversion du solde :**
                - Le solde est récupéré en **USD** depuis Venly (wallet blockchain)
                - Venly retourne déjà les montants en USD, pas besoin de convertir avec les taux des opérateurs
                - Le solde USD est converti en devise locale (XOF, EUR, etc.) en utilisant **directement Currency Freaks**
                - **API utilisée :** `/api/internal/v1/currency/convert/deviseConvert?from=USD&to={devise}&amount={montant}`
                
                **Montants bruts :**
                - Les montants sont stockés en **montants bruts** (BigDecimal, toutes les décimales) dans le wallet
                - L'arrondi à 2 décimales est effectué uniquement pour l'affichage dans la réponse
                
                **Formule de conversion :**
                - `Solde en devise locale = Conversion Currency Freaks (USD → devise locale)`
                
                **Exemple :**
                - Solde USD brut : 3.11898703
                - Conversion Currency Freaks : USD → XOF
                - Solde en XOF brut : Résultat de Currency Freaks
                - Solde affiché : XOF (arrondi à 2 décimales)
                """
            ,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Requête contenant le nom d'utilisateur",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UsernameRequest.class),
                            examples = @ExampleObject(
                                    name = "GetUser Example",
                                    value = """
                                    {
                                      "username": "john_doe"
                                    }
                                    """
                            )
                    )
            )
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Utilisateur trouvé avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UserDto.class),
                            examples = @ExampleObject(
                                    name = "UserDto Example",
                                    value = """
                                    {
                                      "status": "success",
                                      "message": "User retrieved successfully",
                                      "data": {
                                        "id": "b12345f6-78cd-90ef-1234-567890abcdef",
                                        "username": "john_doe",
                                        "email": "john@example.com",
                                        "firstName": "John",
                                        "lastName": "Doe",
                                        "roles": ["ROLE_USER"]
                                      }
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Utilisateur non trouvé",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                            {
                              "status": "error",
                              "message": "User not found",
                              "data": null
                            }
                            """))
            ),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                            {
                              "status": "error",
                              "message": "Internal server error",
                              "data": null
                            }
                            """))
            )
    })
    public ResponseEntity<UserResponse> getUser(@PathVariable String realmName, @RequestBody @Valid UsernameRequest request) {
        log.info("keycloak controller - getUser {},  in realm {} ", request.getUsername(), realmName);
        return userService.getUser(request.getUsername());
    }


    @PostMapping("/users/particulier")
    @Operation(
            operationId = "addUserParticular",
            summary = "Ajoute un nouvel utilisateur particulier",
            description = "Crée un utilisateur de type particulier dans le realm spécifié. " +
                    "Le PIN est automatiquement créé chez Venly et stocké en base de données locale. " +
                    "L'emergency code est optionnel et peut être créé en même temps si fourni.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "AddUserPartCommand JSON",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AddUserPartCommand.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Exemple avec emergency code",
                                            value = """
                                    {
                                      "username": "002250759146858",
                                      "firstName": "kone",
                                      "lastName": "souleymane",
                                      "countryCode": "CI",
                                      "mobilePhone": "002250777832982",
                                      "pinCode": "123456",
                                      "partialEmergencyCode": "ABC12",
                                      "email": "",
                                      "dateNaissance": "",
                                      "facebookId": "",
                                      "googleId": "",
                                      "cguAcceptation": true
                                    }
                                    """
                                    ),
                                    @ExampleObject(
                                            name = "Exemple sans emergency code",
                                            value = """
                                    {
                                      "username": "002250759146858",
                                      "firstName": "kone",
                                      "lastName": "souleymane",
                                      "countryCode": "CI",
                                      "mobilePhone": "002250777832982",
                                      "pinCode": "123456",
                                      "partialEmergencyCode": "",
                                      "email": "",
                                      "dateNaissance": "",
                                      "facebookId": "",
                                      "googleId": "",
                                      "cguAcceptation": true
                                    }
                                    """
                                    )
                            }
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Utilisateur créé avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "success": true,
                                      "message": "User created successfully",
                                      "user": {
                                        "userId": "ae39223a-3cd7-4f18-9e4b-51fb64c8f2db",
                                        "username": "002250759146841",
                                        "firstName": "Franck",
                                        "lastName": "Sande"
                                      },
                                      "pin": {
                                        "created": true,
                                        "status": "PIN created "
                                      },
                                      "emergencyCode": {
                                        "created": true,
                                        "message": "Emergency code created successfully"
                                      }
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(responseCode = "409", description = "Utilisateur existe déjà"),
            @ApiResponse(responseCode = "417", description = "Échec de la création"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<UserCreateResponse> addUser(@PathVariable String realmName,
                                          @RequestBody @Valid AddUserPartCommand command) {
        log.info("Add particular user in realm {}", realmName);
        return userService.createUserPart(command, realmName);
    }

    // ------------------------------------------------------------------------
    // OTP
    // ------------------------------------------------------------------------
    @PostMapping("/sendOtp")
    @Operation(
            operationId = "sendUserOtp",
            summary = "Envoie un OTP à un utilisateur",
            description = "Permet d'envoyer un OTP à un utilisateur selon son nom et le type spécifié.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "RequestOtp JSON",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RequestOtp.class),
                            examples = @ExampleObject(
                                    name = "RequestOtp Example",
                                    value = """
                                {
                                  "username": "john_doe",
                                  "type": "LOGIN",
                                  "action": "SEND"
                                }
                                """
                            )
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "204",
                    description = "OTP envoyé",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "No Content Response",
                                    value = """
                                {
                                  "status": "success",
                                  "message": "OTP sent successfully"
                                }
                                """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Utilisateur introuvable",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Not Found Response",
                                    value = """
                                {
                                  "status": "error",
                                  "message": "User not found",
                                  "data": null
                                }
                                """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Erreur interne du serveur",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Internal Server Error Response",
                                    value = """
                                {
                                  "status": "error",
                                  "message": "Internal server error",
                                  "data": null
                                }
                                """
                            )
                    )
            )
    })
    public ResponseEntity<UserResponse> sendUserOtp(@PathVariable String realmName,
                                                    @RequestBody RequestOtp request) throws NotFoundException, BadRequestException {
        log.info("Send OTP to user {} in realm {} (type: {}, action: {})", request.getUsername(), realmName, request.getType(), request.getAction());
        return userService.sendOtpToUser(request.getUsername(), request.getType(), request.getAction());
    }

    // ------------------------------------------------------------------------
    // ACCOUNT
    // ------------------------------------------------------------------------
    // ------------------------------------------------------------------------
    // ✅ Récupérer le solde du portefeuille d'un utilisateur
    // ------------------------------------------------------------------------
    @GetMapping("/wallet-balance/{username}")
    @Operation(
            summary = "Récupérer le solde du portefeuille d'un utilisateur",
            description = """
                Retourne le solde du portefeuille pour le username spécifié.
                
                **Conversion en devise locale :**
                - Le solde est d'abord récupéré en USDC depuis le wallet blockchain
                - Le solde USDC est ensuite converti en devise locale (XOF, EUR, etc.) en utilisant le **taux d'achat sauvegardé** du partenaire (YellowCard ou Guardarian)
                - Le taux d'achat est sauvegardé lors du dernier dépôt (OnRamp) et utilisé jusqu'au prochain dépôt
                - Si aucun taux n'est sauvegardé, le système utilise Currency Freaks en fallback
                
                **Formule de conversion :**
                - `Solde en devise locale = Solde USDC × Taux d'achat sauvegardé`
                
                **Exemple :**
                - Solde USDC : 3.11898703
                - Taux d'achat sauvegardé (YellowCard) : 588.08
                - Solde en XOF : 3.11898703 × 588.08 = 1,833.96 XOF
                
                **Réponse :**
                - `balance`: Solde en USDC (arrondi à 2 décimales)
                - `convertedAmount`: Solde converti en devise locale (arrondi à 2 décimales)
                """
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Solde récupéré avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Success Response",
                                    value = """
                                {
                                  "status": "success",
                                  "message": "Wallet balance retrieved successfully",
                                  "data": {
                                    "balance": 3.12,
                                    "convertedAmount": 1833.96,
                                    "currency": "XOF"
                                  }
                                }
                                """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Utilisateur non trouvé ou wallet introuvable",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Not Found Response",
                                    value = """
                                {
                                  "status": "error",
                                  "message": "User or wallet not found",
                                  "data": null
                                }
                                """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Erreur interne du serveur",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Internal Server Error Response",
                                    value = """
                                {
                                  "status": "error",
                                  "message": "Internal server error",
                                  "data": null
                                }
                                """
                            )
                    )
            )
    })
    public ResponseEntity<WalletResponse> getUserWalletBalance(@PathVariable String username) {
        log.info("Récupération du solde du portefeuille pour l'utilisateur '{}'", username);
        log.debug("Appel de getUserWalletBalance pour username={}", username);

        return userService.getUserWalletBalance(username);
    }

    // ------------------------------------------------------------------------
    // DELETE USER PERMANENTLY
    // ------------------------------------------------------------------------
    @DeleteMapping("/{username}")
    @Operation(
            operationId = "deleteUserPermanently",
            summary = "Supprimer définitivement un utilisateur",
            description = """
            Supprime complètement un utilisateur de Keycloak et/ou de la base de données locale en cascade.

            **⚠️ ATTENTION : Cette opération est irréversible !**

            **Comportement :**
            - Si l'utilisateur existe dans Keycloak ET en local : suppression des deux
            - Si l'utilisateur existe seulement en local : suppression locale uniquement
            - Si l'utilisateur existe seulement dans Keycloak : suppression Keycloak uniquement
            - Si l'utilisateur n'existe nulle part : erreur 404

            **Données supprimées en cascade (spécifiques à l'utilisateur) :**
            - Utilisateur dans Keycloak (si présent)
            - Utilisateur dans la table users locale (si présent)
            - Tous les wallets de l'utilisateur
            - Tous les liens de paiement créés par l'utilisateur
            - Toutes les transactions de paiement (où l'utilisateur est payeur ou créateur)
            - Tous les PINs de l'utilisateur
            - Tous les codes d'urgence de l'utilisateur
            - Toutes les opérations de l'utilisateur
            - Tous les attachments de l'utilisateur

            **Protection des entités partagées :**
            - Les entités partagées du système (comme CountryCurrency) ne sont **PAS** supprimées
            - Seules les données spécifiques à l'utilisateur sont supprimées

            **Exemple d'usage :**
            ```bash
            DELETE /api/internal/v1/users/{realmName}/{username}
            ```
            """
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Utilisateur supprimé définitivement avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Success Response",
                                    value = """
                                {
                                  "status": "success",
                                  "message": "User permanently deleted from Keycloak and local database",
                                  "data": null
                                }
                                """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Utilisateur non trouvé dans Keycloak ET dans la base de données locale",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Not Found Response",
                                    value = """
                                {
                                  "status": "error",
                                  "message": "User 002250759146858 not found in realm akuunda-realm and local database",
                                  "data": null
                                }
                                """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Erreur interne du serveur",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Internal Server Error Response",
                                    value = """
                                {
                                  "status": "error",
                                  "message": "An unexpected error occurred while deleting the user",
                                  "data": null
                                }
                                """
                            )
                    )
            )
    })
    public ResponseEntity<UserResponse> deleteUserPermanently(
            @Parameter(description = "Nom du realm Keycloak", required = true, example = "akuunda-realm")
            @PathVariable String realmName,
            @Parameter(description = "Username (numéro de téléphone) de l'utilisateur à supprimer", required = true, example = "002250759146858")
            @PathVariable String username) {
        try {
            log.info("Request to permanently delete user: {} from realm: {}", username, realmName);
            keycloakUserService.deleteUserPermanently(realmName, username);

            UserResponse response = UserResponse.builder()
                    .status("success")
                    .message("User permanently deleted from Keycloak and local database")
                    .data(null)
                    .build();

            return ResponseEntity.ok(response);
        } catch (jakarta.ws.rs.NotFoundException e) {
            log.error("User not found: {} in realm: {}", username, realmName, e);
            UserResponse response = UserResponse.builder()
                    .status("error")
                    .message("User not found : " + e.getMessage())
                    .data(null)
                    .build();

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        } catch (Exception e) {
            log.error("Error permanently deleting user: {} from realm: {}", username, realmName, e);
            UserResponse response = UserResponse.builder()
                    .status("error")
                    .message("Internal server error : " + e.getMessage())
                    .data(null)
                    .build();

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // ------------------------------------------------------------------------
    // PREFERRED LANGUAGE
    // ------------------------------------------------------------------------
    @PatchMapping("/preferred-language/{username}")
    @Operation(
            operationId = "updatePreferredLanguage",
            summary = "Modifier la langue préférée",
            description = "Met à jour la langue préférée de l'utilisateur (fr ou en). Utilisée pour les notifications push."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Langue mise à jour",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Success Response",
                                    value = """
                                {
                                  "status": "success",
                                  "language": "fr"
                                }
                                """
                            )
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Langue invalide"),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable")
    })
    public ResponseEntity<Object> updatePreferredLanguage(
            @PathVariable String realmName,
            @PathVariable String username,
            @RequestBody Map<String, String> request) {

        log.info("🌐 Mise à jour langue pour {} → {}", username, request.get("language"));

        String lang = request.get("language");
        if (lang == null || (!lang.equals("fr") && !lang.equals("en"))) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", "Langue invalide. Valeurs acceptées : fr, en"
            ));
        }

        Users user = userRepository.getUsersByUsername(username);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", "error",
                    "message", "Utilisateur introuvable"
            ));
        }

        user.setPreferredLanguage(lang);
        userRepository.saveAndFlush(user);

        log.info("✅ Langue mise à jour pour {} : {}", username, lang);

        return ResponseEntity.ok(Map.of(
                "status", "success",
                "language", lang
        ));
    }

    @GetMapping("/preferred-language/{username}")
    @Operation(
            operationId = "getPreferredLanguage",
            summary = "Récupérer la langue préférée",
            description = "Retourne la langue préférée de l'utilisateur (fr ou en)."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Langue récupérée",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Success Response",
                                    value = """
                                {
                                  "language": "fr"
                                }
                                """
                            )
                    )
            ),
            @ApiResponse(responseCode = "404", description = "Utilisateur introuvable")
    })
    public ResponseEntity<Object> getPreferredLanguage(
            @PathVariable String realmName,
            @PathVariable String username) {

        Users user = userRepository.getUsersByUsername(username);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                    "status", "error",
                    "message", "Utilisateur introuvable"
            ));
        }

        String lang = user.getPreferredLanguage() != null ? user.getPreferredLanguage() : "fr";

        return ResponseEntity.ok(Map.of("language", lang));
    }

        // ------------------------------------------------------------------------
    // UPDATE USER PROFILE
    // ------------------------------------------------------------------------
    @PutMapping("/profile/{username}")
    @Operation(
            operationId = "updateUserProfile",
            summary = "Modifier le profil utilisateur",
            description = """
                Met à jour les informations du profil d'un utilisateur (particulier ou entreprise).
                
                **Champs modifiables pour tous les types de comptes :**
                - firstName (Nom)
                - lastName (Prénoms)
                - email
                - address (Adresse)
                - dateNaissance (formats `YYYY-MM-DD` ou `MM/DD/YYYY`)
                - idType / idNumber — pièce d'identité principale (requis YellowCard)
                - additionalIdType / additionalIdNumber — pièce secondaire (ex. BVN au Nigéria)
                
                **Champs supplémentaires pour les comptes Entreprise :**
                - siret
                - raisonSociale
                
                **KYC YellowCard.** Pour qu'un marchand puisse recevoir des paiements XOF / XAF / NGN
                via YellowCard, son profil doit contenir : `firstName`, `lastName`, `email`,
                `mobilePhone` (= username), `dateNaissance`, `idType` et `idNumber`.
                Le pays (`country`) est résolu automatiquement depuis le wallet du marchand
                (`wallets[i].countryCode`) — pas besoin de le renseigner ici.
                À défaut, l'API de paiement renvoie un `422 MERCHANT_KYC_INCOMPLETE` avec la liste
                exacte des champs manquants.
                
                **Note :** Le numéro de téléphone (username) ne peut pas être modifié via cet endpoint.
                """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "Données du profil à mettre à jour",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = UpdateProfileRequest.class),
                            examples = {
                                    @ExampleObject(
                                            name = "Exemple Particulier (KYC complet YellowCard)",
                                            value = """
                                            {
                                              "firstName": "JOE",
                                              "lastName": "Andre paul",
                                              "email": "Andre@gmail.com",
                                              "address": "Abidjan Cocody, Angré",
                                              "dateNaissance": "1990-05-15",
                                              "idType": "NATIONAL_ID",
                                              "idNumber": "CI001234567"
                                            }
                                            """
                                    ),
                                    @ExampleObject(
                                            name = "Exemple Entreprise",
                                            value = """
                                            {
                                              "firstName": "Jean",
                                              "lastName": "Dupont",
                                              "email": "contact@entreprise.com",
                                              "address": "123 Rue du Commerce, Abidjan",
                                              "siret": "123456789",
                                              "raisonSociale": "Ma Société SARL",
                                              "idType": "PASSPORT",
                                              "idNumber": "P12345678"
                                            }
                                            """
                                    ),
                                    @ExampleObject(
                                            name = "Exemple Nigéria (NATIONAL_ID + BVN)",
                                            value = """
                                            {
                                              "firstName": "Tunde",
                                              "lastName": "Adebayo",
                                              "email": "tunde@example.com",
                                              "dateNaissance": "1988-11-04",
                                              "idType": "NATIONAL_ID",
                                              "idNumber": "NIN12345678",
                                              "additionalIdType": "BVN",
                                              "additionalIdNumber": "22212345678"
                                            }
                                            """
                                    )
                            }
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Profil mis à jour avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Success Response",
                                    value = """
                                {
                                  "status": "success",
                                  "message": "Profil mis à jour avec succès",
                                  "data": {
                                    "userId": "ae39223a-3cd7-4f18-9e4b-51fb64c8f2db",
                                    "username": "002250777062160",
                                    "firstName": "JOE",
                                    "lastName": "Andre paul",
                                    "email": "Andre@gmail.com",
                                    "address": "Abidjan Cocody, Angré",
                                    "accountType": "particulier"
                                  }
                                }
                                """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Utilisateur non trouvé",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    name = "Not Found Response",
                                    value = """
                                {
                                  "status": "error",
                                  "message": "Utilisateur non trouvé",
                                  "data": null
                                }
                                """
                            )
                    )
            ),
            @ApiResponse(responseCode = "400", description = "Requête invalide"),
            @ApiResponse(responseCode = "500", description = "Erreur interne du serveur")
    })
    public ResponseEntity<UpdateProfileResponse> updateUserProfile(
            @Parameter(description = "Nom du realm Keycloak", required = true, example = "akuunda-realm-prod")
            @PathVariable String realmName,
            @Parameter(description = "Username (numéro de téléphone) de l'utilisateur", required = true, example = "002250777062160")
            @PathVariable String username,
            @RequestBody @Valid UpdateProfileRequest request) {
        
        log.info("📝 Mise à jour du profil pour l'utilisateur: {}", username);
        
        try {
            Users user = userRepository.getUsersByUsername(username);
            if (user == null) {
                log.warn("❌ Utilisateur non trouvé: {}", username);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        UpdateProfileResponse.builder()
                                .status("error")
                                .message("Utilisateur non trouvé")
                                .data(null)
                                .build()
                );
            }
            
            if (request.getFirstName() != null && !request.getFirstName().trim().isEmpty()) {
                user.setFirstname(request.getFirstName().trim());
            }
            if (request.getLastName() != null && !request.getLastName().trim().isEmpty()) {
                user.setLastname(request.getLastName().trim());
            }
            if (request.getEmail() != null && !request.getEmail().trim().isEmpty()) {
                user.setEmail(request.getEmail().trim());
            }
            if (request.getAddress() != null) {
                user.setAdresse(request.getAddress().trim());
            }
            if (request.getDateNaissance() != null) {
                user.setDateNaissance(request.getDateNaissance().trim());
            }

            // KYC YellowCard — pièce d'identité principale
            if (request.getIdType() != null) {
                user.setIdType(request.getIdType().trim().isEmpty() ? null : request.getIdType().trim());
            }
            if (request.getIdNumber() != null) {
                user.setIdNumber(request.getIdNumber().trim().isEmpty() ? null : request.getIdNumber().trim());
            }
            // KYC YellowCard — pièce d'identité secondaire (ex. BVN au Nigéria)
            if (request.getAdditionalIdType() != null) {
                user.setAdditionalIdType(request.getAdditionalIdType().trim().isEmpty() ? null : request.getAdditionalIdType().trim());
            }
            if (request.getAdditionalIdNumber() != null) {
                user.setAdditionalIdNumber(request.getAdditionalIdNumber().trim().isEmpty() ? null : request.getAdditionalIdNumber().trim());
            }
            // Pays du marchand : NON exposé ici car déjà porté par le wallet
            // (wallet.currency.country_code, visible dans wallets[i].countryCode).
            // Le service de paiement YellowCard le résout automatiquement.

            String accountType = user.getAccountType() != null ? user.getAccountType() : "particulier";
            if ("enterprise".equalsIgnoreCase(accountType) || "entreprise".equalsIgnoreCase(accountType)) {
                if (request.getSiret() != null) {
                    user.setSiret(request.getSiret().trim());
                }
                if (request.getRaisonSociale() != null) {
                    user.setRaisonSociale(request.getRaisonSociale().trim());
                }
            }
            
            userRepository.saveAndFlush(user);
            
            log.info("✅ Profil mis à jour avec succès pour: {}", username);
            
            UpdateProfileResponse.UserProfileData profileData = UpdateProfileResponse.UserProfileData.builder()
                    .userId(user.getUserId())
                    .username(user.getUsername())
                    .firstName(user.getFirstname())
                    .lastName(user.getLastname())
                    .email(user.getEmail())
                    .address(user.getAdresse())
                    .dateNaissance(user.getDateNaissance())
                    .siret(user.getSiret())
                    .raisonSociale(user.getRaisonSociale())
                    .accountType(accountType)
                    .idType(user.getIdType())
                    .idNumber(user.getIdNumber())
                    .additionalIdType(user.getAdditionalIdType())
                    .additionalIdNumber(user.getAdditionalIdNumber())
                    .build();
            
            return ResponseEntity.ok(
                    UpdateProfileResponse.builder()
                            .status("success")
                            .message("Profil mis à jour avec succès")
                            .data(profileData)
                            .build()
            );
            
        } catch (Exception e) {
            log.error("❌ Erreur lors de la mise à jour du profil pour {}: {}", username, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    UpdateProfileResponse.builder()
                            .status("error")
                            .message("Erreur interne: " + e.getMessage())
                            .data(null)
                            .build()
            );
        }
    }
}
