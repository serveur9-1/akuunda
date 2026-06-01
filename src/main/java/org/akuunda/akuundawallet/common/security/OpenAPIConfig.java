package org.akuunda.akuundawallet.common.security;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;

@OpenAPIDefinition(
        info = @Info(
                contact = @Contact(
                        name = "Akuunda Wallet",
                        email = "contact@akunnda-pay.io",
                        url = "https://akuunda-pay.io/"),
                description = "Documentation for Akuunda Wallet",
                title = "Akuunda Wallet",
                version = "1.0",
                license = @License(
                        name = "Licence name",
                        url = "https://akunnda-pay.com/license"),
                termsOfService = "Terms of service"
        ),
        servers = {
                @Server(description = "Local ENV",
                        url = "http://localhost:8089"),
                @Server(description = "DEV ENV", url = "https://walletdev.akuunda-pay.io"),
                @Server(description = "PREPROD ENV", url = "https://walletpreprod.akuunda-pay.io"),
                @Server(description = "PROD ENV", url = "https://walletprod.akuunda-pay.io"),
        },
        security = {@SecurityRequirement(
                        name = "bearerAuth")
        }
)
@SecurityScheme(
        name = "bearerAuth",
        description = "JWT auth description",
        scheme = "bearer",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        in = SecuritySchemeIn.HEADER
)
public class OpenAPIConfig {
}
