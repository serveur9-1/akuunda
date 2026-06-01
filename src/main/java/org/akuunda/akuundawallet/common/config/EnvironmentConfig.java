package org.akuunda.akuundawallet.common.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class EnvironmentConfig {

    //@Value("${spring.profiles.active}")
    private String activeProfile;

}
