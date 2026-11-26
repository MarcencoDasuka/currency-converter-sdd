package com.converter.alert;

import com.converter.alert.config.AlertProperties;
import com.converter.common.events.RabbitTopologyConstants;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AlertProperties.class)
public class AlertServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AlertServiceApplication.class, args);
    }

    @Bean
    public RestClient alertRateRestClient(AlertProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(properties.rateConnectTimeoutSeconds()));
        factory.setReadTimeout(Duration.ofSeconds(properties.rateReadTimeoutSeconds()));
        return RestClient.builder()
                .baseUrl(properties.rateServiceUrl())
                .requestFactory(factory)
                .build();
    }

    @Bean
    public TopicExchange alertEventsExchange() {
        return new TopicExchange(RabbitTopologyConstants.EXCHANGE_EVENTS, true, false);
    }
}
