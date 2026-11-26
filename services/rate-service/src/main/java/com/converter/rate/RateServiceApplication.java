package com.converter.rate;

import com.converter.rate.config.RateProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@SpringBootApplication
@EnableConfigurationProperties(RateProperties.class)
public class RateServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(RateServiceApplication.class, args);
    }

    @Bean
    public RestClient bnmRestClient(RateProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(properties.connectTimeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(properties.readTimeoutSeconds()));
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
