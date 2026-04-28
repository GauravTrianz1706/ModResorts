package com.acme.modres.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Spring Cache configuration using Amazon ElastiCache (Redis)
 * Replaces singleton-based state storage with distributed caching
 * for horizontal scaling in containerized environments (ECS/EKS)
 */
@Configuration
@EnableCaching
public class CacheConfig {

  /**
   * Configure Redis connection factory using environment variables
   * REDIS_HOST: ElastiCache endpoint (e.g., my-cluster.abc123.0001.use1.cache.amazonaws.com)
   * REDIS_PORT: Redis port (default: 6379)
   */
  @Bean
  public RedisConnectionFactory redisConnectionFactory() {
    String redisHost = System.getenv("REDIS_HOST");
    String redisPort = System.getenv("REDIS_PORT");
    
    // Default values for local development
    if (redisHost == null || redisHost.isEmpty()) {
      redisHost = "localhost";
    }
    if (redisPort == null || redisPort.isEmpty()) {
      redisPort = "6379";
    }
    
    RedisStandaloneConfiguration redisConfig = new RedisStandaloneConfiguration();
    redisConfig.setHostName(redisHost);
    redisConfig.setPort(Integer.parseInt(redisPort));
    
    // Optional: Configure password if using AUTH
    String redisPassword = System.getenv("REDIS_PASSWORD");
    if (redisPassword != null && !redisPassword.isEmpty()) {
      redisConfig.setPassword(redisPassword);
    }
    
    return new LettuceConnectionFactory(redisConfig);
  }

  /**
   * Configure Redis cache manager with TTL and serialization
   */
  @Bean
  public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
    RedisCacheConfiguration cacheConfig = RedisCacheConfiguration.defaultCacheConfig()
        .entryTtl(Duration.ofHours(1)) // Cache entries expire after 1 hour
        .serializeValuesWith(
            RedisSerializationContext.SerializationPair.fromSerializer(
                new GenericJackson2JsonRedisSerializer()
            )
        )
        .disableCachingNullValues();

    return RedisCacheManager.builder(connectionFactory)
        .cacheDefaults(cacheConfig)
        .build();
  }
}
