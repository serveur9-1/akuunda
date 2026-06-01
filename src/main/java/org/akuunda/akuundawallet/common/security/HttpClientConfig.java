package org.akuunda.akuundawallet.common.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class HttpClientConfig {

    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)) // Timeout configurable
                .version(HttpClient.Version.HTTP_2)     // HTTP/2 par défaut
                .build();
    }
}
