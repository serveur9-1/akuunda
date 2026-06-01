package org.akuunda.akuundawallet.common.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;

//@Configuration
@RequiredArgsConstructor
@Slf4j
public class AppStartupConfig {


    private final EnvironmentConfig environmentConfig;

    public void onStartup() {
        String activeProfile = environmentConfig.getActiveProfile();
        if (activeProfile != null) {
            log.debug("Profil actif au démarrage : " + activeProfile);
            log.info("Profil actif au démarrage : " + activeProfile);
        }
    }

   // @Bean
    public ApplicationRunner applicationRunner() {
        return args -> onStartup();
    }
}
