package com.converter.common.config;

import com.converter.common.security.JwtAuthFilter;
import com.converter.common.security.JwtProperties;
import com.converter.common.security.JwtTokenService;
import com.converter.common.web.BaseProblemDetailsHandler;
import com.converter.common.web.RequestIdFilter;
import com.converter.common.web.SecurityHeadersFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

import java.time.Clock;

@AutoConfiguration
@EnableConfigurationProperties(JwtProperties.class)
public class CommonPlatformAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public Clock platformClock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public BaseProblemDetailsHandler baseProblemDetailsHandler() {
        return new BaseProblemDetailsHandler();
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistration(
            @Value("${spring.application.name:currency-service}") String serviceName
    ) {
        FilterRegistrationBean<RequestIdFilter> bean = new FilterRegistrationBean<>(new RequestIdFilter(serviceName));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public FilterRegistrationBean<SecurityHeadersFilter> securityHeadersFilterRegistration() {
        FilterRegistrationBean<SecurityHeadersFilter> bean = new FilterRegistrationBean<>(new SecurityHeadersFilter());
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return bean;
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "platform.jwt", name = "secret")
    public JwtTokenService jwtTokenService(JwtProperties jwtProperties, Clock clock) {
        return new JwtTokenService(jwtProperties, clock);
    }

    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnBean(JwtTokenService.class)
    @ConditionalOnProperty(prefix = "platform.jwt", name = "filter-enabled", havingValue = "true", matchIfMissing = true)
    public FilterRegistrationBean<JwtAuthFilter> jwtAuthFilterRegistration(
            JwtTokenService jwtTokenService,
            ObjectMapper objectMapper
    ) {
        FilterRegistrationBean<JwtAuthFilter> bean = new FilterRegistrationBean<>(
                new JwtAuthFilter(jwtTokenService, objectMapper)
        );
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return bean;
    }
}
