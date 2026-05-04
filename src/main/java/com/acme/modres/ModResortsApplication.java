package com.acme.modres;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;

/**
 * Spring Boot application entry point for ModResorts.
 * Enables embedded Tomcat server for cloud-native deployment on AWS ECS/EKS/Fargate.
 */
@SpringBootApplication
@ServletComponentScan
public class ModResortsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModResortsApplication.class, args);
    }
}
