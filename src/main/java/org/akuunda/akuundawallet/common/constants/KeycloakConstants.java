package org.akuunda.akuundawallet.common.constants;

import lombok.experimental.UtilityClass;

@UtilityClass
public class KeycloakConstants {
    //roles
    public static final String ROLE_PREFIX = "role";
    public static final String SERVICE_PREFIX = "service";

    //To override default keycloak pagination
    public static final Integer LIST_PAGE_FIRST = 0;
    public static final Integer LIST_PAGE_SIZE = 10000;

    //
    public static final String ATTRIBUTE_PARENT_NAME = "KCParentName";
    public static final String ATTRIBUTE_CREATED_FROM = "CreatedFrom";
    public static final String ATTRIBUTE_HABILITATION = "habilitation";
    public static final String ATTRIBUTE_PHONE = "phone";
    public static final String ATTRIBUTE_LOCALE = "locale";

    //files
    public static final String FILES_DIRECTORY = "/akunnda/pj/";
}
