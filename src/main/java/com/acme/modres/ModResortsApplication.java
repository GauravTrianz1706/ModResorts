package com.acme.modres;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * Spring Boot Application class for ModResorts containerized deployment.
 * 
 * Enables:
 * - Servlet component scanning for @WebServlet annotations
 * - Distributed caching with Spring Cache abstraction (Redis)
 * - Spring Boot Actuator for health checks and metrics
 */
@SpringBootApplication
@ServletComponentScan
@EnableCaching
public class ModResortsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModResortsApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
