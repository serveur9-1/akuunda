package org.akuunda.akuundawallet.transfert.impl.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.akuunda.akuundawallet.common.constants.ApiConstants;
import org.akuunda.akuundawallet.transfert.api.request.TransfertRequest;
import org.akuunda.akuundawallet.transfert.impl.service.TransfertService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@CrossOrigin("*")
@RequestMapping(path = AkunndaTransfertController.API_SERVICES_ROOT, produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(
        name = "Akuunda - Transfert",
        description = """
        Endpoints internes pour la gestion des transferts d'argent entre portefeuilles Akuunda.  
        Ces opérations permettent d'envoyer ou de recevoir de l'argent entre utilisateurs enregistrés.
        """
)
@SecurityRequirement(name = ApiConstants.SWAGGER_BASIC_SECURITY_SCHEME)
@RequiredArgsConstructor
public class AkunndaTransfertController {

    public static final String API_SERVICES_ROOT = "/api/internal/v1/transfer";

    private final TransfertService transfertService;

    // ------------------------------------------------------------------------
    // ✅ TRANSFERT ENTRE PORTEFEUILLES
    // ------------------------------------------------------------------------
    @PostMapping("/execute")
    @Operation(
            operationId = "executeGasTransfert",
            summary = "Effectuer un transfert d'argent entre portefeuilles",
            description = """
                    Effectue un transfert de fonds entre deux comptes utilisateurs (expéditeur et destinataire).  
                    Le transfert prend en compte le montant, la devise et le PIN de signature pour validation.
                    """
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Transfert exécuté avec succès",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "status": "SUCCESS",
                                      "message": "Transfert effectué avec succès"
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Requête invalide (ex: montant négatif, données manquantes)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "status": "ERROR",
                                      "message": "Montant invalide ou utilisateur introuvable"
                                    }
                                    """
                            )
                    )
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Accès refusé (ex: PIN incorrect ou fonds insuffisants)",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(
                                    value = """
                                    {
                                      "status": "ERROR",
                                      "message": "PIN de signature incorrect"
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
                                      "status": "ERROR",
                                      "message": "Erreur lors de l'exécution du transfert"
                                    }
                                    """
                            )
                    )
            )
    })
    public ResponseEntity<String> executeGasTransfert(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Détails du transfert à exécuter",
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = TransfertRequest.class),
                            examples = @ExampleObject(
                                    name = "Exemple de transfert",
                                    value = """
                                    {
                                      "senderUsername": "alice",
                                      "receiverUsername": "bob",
                                      "amount": 2500.00,
                                      "gasAmount": 0.5,
                                      "devise": "USD",
                                      "signingPin": "1234"
                                    }
                                    """
                            )
                    )
            )
            @RequestBody TransfertRequest transfertRequest) {

        log.info(
                "TransfertController - executeGasTransfert: sender={}, receiver={}, amount={}, devise={}",
                transfertRequest.getSenderUsername(),
                transfertRequest.getReceiverUsername(),
                transfertRequest.getAmount(),
                transfertRequest.getDevise()
        );

        log.debug("TransfertController - transfertRequest: {}", transfertRequest);

        return transfertService.executeGasTransfert(
                transfertRequest.getSenderUsername(),
                transfertRequest.getReceiverUsername(),
                transfertRequest.getAmount(),
                transfertRequest.getGasAmount(),
                transfertRequest.getSigningPin(),
                transfertRequest.getDevise()
        );
    }
}
