package org.akuunda.akuundawallet.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DevDataSourceEnvironmentPostProcessorTest {

    @Test
    void replacesBlankPasswordOnDevProfile() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        env.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "spring.datasource.password", ""
        )));

        new DevDataSourceEnvironmentPostProcessor().postProcessEnvironment(env, new SpringApplication());

        assertEquals(DevDataSourceEnvironmentPostProcessor.DEV_DATASOURCE_PASSWORD,
                env.getProperty("spring.datasource.password"));
    }

    @Test
    void doesNotOverrideNonBlankPassword() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        env.getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "spring.datasource.password", "custom-secret"
        )));

        new DevDataSourceEnvironmentPostProcessor().postProcessEnvironment(env, new SpringApplication());

        assertEquals("custom-secret", env.getProperty("spring.datasource.password"));
    }
}
