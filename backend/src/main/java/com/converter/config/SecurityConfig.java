package com.converter.config;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;

@Configuration
public class SecurityConfig {

    private final AppProperties appProperties;

    public SecurityConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(@NonNull CorsRegistry registry) {
                String[] origins = appProperties.cors().allowedOrigins().toArray(new String[0]);
                registry.addMapping("/api/**")
                        .allowedOrigins(origins)
                        .allowedMethods("GET", "POST", "OPTIONS")
                        .allowedHeaders("Content-Type", "Accept", "Origin", "X-Requested-With")
                        .maxAge(3600);
            }
        };
    }

    @Bean
    public Filter securityHeadersFilter() {
        return new Filter() {
            @Override
            public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
                    throws IOException, ServletException {
                if (response instanceof HttpServletResponse httpResponse) {
                    httpResponse.setHeader("X-Content-Type-Options", "nosniff");
                    httpResponse.setHeader("X-Frame-Options", "DENY");
                    httpResponse.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
                    httpResponse.setHeader("Content-Security-Policy", "default-src 'self'");
                    httpResponse.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");
                    httpResponse.setHeader("X-Permitted-Cross-Domain-Policies", "none");
                    if (request.isSecure()) {
                        httpResponse.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains");
                    }
                }
                chain.doFilter(request, response);
            }
        };
    }
}
