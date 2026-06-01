package org.akuunda.akuundawallet.common.constants;

import lombok.experimental.UtilityClass;

import java.io.Serial;
import java.io.Serializable;

@UtilityClass
public class ApiConstants implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    public static final String SWAGGER_BASIC_SECURITY_SCHEME = "bearerAuth";
}
