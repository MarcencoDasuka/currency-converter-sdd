package com.converter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CurrencyConverterApplication {

    public static void main(String[] args) {
        SpringApplication.run(CurrencyConverterApplication.class, args);
    }
}
