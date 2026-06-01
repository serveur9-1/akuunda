package org.akuunda.akuundawallet.common.utils;

public final class SigningMethodHeaderBuilder {

    private SigningMethodHeaderBuilder() {}

    public static String build(String signingMethodId, String signingValue) {
        if (signingMethodId == null || signingMethodId.isBlank() || signingValue == null) {
            throw new IllegalArgumentException("Invalid signing method id/value");
        }
        return signingMethodId + ":" + signingValue;
    }
}


