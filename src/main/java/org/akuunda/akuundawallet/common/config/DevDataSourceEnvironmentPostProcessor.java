package org.akuunda.akuundawallet.common.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;
import java.util.Map;

/**
 * Coolify / Docker définissent souvent {@code SPRING_DATASOURCE_PASSWORD=} (vide).
 * Spring Boot lie cette variable à {@code spring.datasource.password} et ignore
 * la valeur par défaut des fichiers {@code .properties} → auth Postgres en échec.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DevDataSourceEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String DEV_DATASOURCE_PASSWORD =
            "7Grw2pHPKqL215Q1mckl3FcMfUmJa1ZdA4ko07rQoDNen4zdqzktNhRQH9bqczek";

    static final String DEV_DATASOURCE_URL =
            "jdbc:postgresql://209.38.213.114:5433/akuundadb?tcpKeepAlive=true&socketTimeout=30&connectTimeout=30";

    private static final String PROPERTY_SOURCE = "devDataSourceFix";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!isDevProfile(environment)) {
            return;
        }

        Map<String, Object> overrides = new HashMap<>();
        boolean changed = false;

        String password = environment.getProperty("spring.datasource.password");
        if (password == null || password.isBlank()) {
            overrides.put("spring.datasource.password", DEV_DATASOURCE_PASSWORD);
            changed = true;
        }

        String url = environment.getProperty("spring.datasource.url");
        if (url == null || url.isBlank()) {
            overrides.put("spring.datasource.url", DEV_DATASOURCE_URL);
            changed = true;
        }

        String username = environment.getProperty("spring.datasource.username");
        if (username == null || username.isBlank()) {
            overrides.put("spring.datasource.username", "postgres");
            changed = true;
        }

        if (changed) {
            environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE, overrides));
        }
    }

    private static boolean isDevProfile(ConfigurableEnvironment environment) {
        String[] active = environment.getActiveProfiles();
        if (active.length == 0) {
            return "dev".equals(environment.getProperty("spring.profiles.active", "dev"));
        }
        for (String profile : active) {
            if ("dev".equals(profile)) {
                return true;
            }
        }
        return false;
    }
}
