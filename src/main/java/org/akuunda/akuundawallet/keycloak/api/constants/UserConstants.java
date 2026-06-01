package org.akuunda.akuundawallet.keycloak.api.constants;

import lombok.experimental.UtilityClass;

@UtilityClass
public class UserConstants {

    public static final String PINE_CODE = "pincode";
    public static final String DESCRIPTION = "description";
    public static final String IDENTIFIER = "identifier";
    public static final String SECRET_TYPE = "secretType";
    public static final String WALLET_TYPE = "walletType";

    public static final String MESSAGE_VALIDATE_ACCOUNT = """
            AKUUNDA PAY :\s
            Utilisez ce code pour valider votre compte.\s
            Le code expire dans 10 minutes :\s
            """;

    public static final String MESSAGE_UPDATE_ACCOUNT = """
            AKUNNDA PAY :\s
            Utilisez ce code pour changer le mot de passe de votre compte. \s
            Le code expire dans 10 minutes :\s
            """;

    public static final String MESSAGE_LOGIN = """
            AKUNNDA PAY :\s
            Utilisez ce code pour vous connecter.\s
            Le code expire dans 10 minutes :\s
            """;
}
