package org.akuunda.akuundawallet.backoffice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class BackofficeSecurityBeans {

    @Bean
    PasswordEncoder backofficePasswordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
}
