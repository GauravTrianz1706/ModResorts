package com.acme.modres;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;
import org.springframework.context.annotation.Bean;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;

/**
 * Spring Boot application class for cloud-native deployment.
 * This enables executable JAR packaging with embedded Tomcat,
 * eliminating the need for external application servers.
 * 
 * Features:
 * - Embedded Tomcat for containerized deployment on GKE/Cloud Run
 * - Redis-based session management via Memorystore
 * - HikariCP connection pooling for Cloud SQL
 * - Servlet component scanning for existing servlets
 */
@SpringBootApplication
@ServletComponentScan(basePackages = "com.acme.modres")
@EnableRedisHttpSession
public class ModResortsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ModResortsApplication.class, args);
    }

    /**
     * Configures HikariCP DataSource for Cloud SQL connectivity.
     * Connection details are externalized via environment variables.
     */
    @Bean
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();
        
        // Cloud SQL connection configuration from environment variables
        String dbUrl = System.getenv().getOrDefault("DB_URL", 
            "jdbc:postgresql://localhost:5432/modresorts");
        String dbUser = System.getenv().getOrDefault("DB_USER", "postgres");
        String dbPassword = System.getenv().getOrDefault("DB_PASSWORD", "");
        
        config.setJdbcUrl(dbUrl);
        config.setUsername(dbUser);
        config.setPassword(dbPassword);
        
        // HikariCP optimal settings for cloud environments
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);
        
        // Cloud SQL specific settings
        config.addDataSourceProperty("cachePrepStmts", "true");
        config.addDataSourceProperty("prepStmtCacheSize", "250");
        config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        
        return new HikariDataSource(config);
    }
}
