package com.converter.rate.service;

import com.converter.rate.config.RateProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Component
public class BnmClient {

    private static final Logger log = LoggerFactory.getLogger(BnmClient.class);
    private static final DateTimeFormatter BNM_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final RestClient restClient;
    private final String baseUrl;

    public BnmClient(RestClient bnmRestClient, RateProperties properties) {
        this.restClient = bnmRestClient;
        this.baseUrl = properties.baseUrl();
    }

    public String fetchBulletinXml(LocalDate date) {
        String formattedDate = date.format(BNM_DATE_FORMAT);
        log.info("Fetching BNM exchange rates XML for date {}", formattedDate);
        try {
            return restClient.get()
                    .uri(baseUrl + "?get_xml=1&date=" + formattedDate)
                    .retrieve()
                    .body(String.class);
        } catch (Exception ex) {
            log.warn("Failed to fetch bulletin from BNM for date {}: {}", formattedDate, ex.getMessage());
            throw new IllegalStateException("Failed to communicate with BNM: " + ex.getMessage(), ex);
        }
    }
}
