package com.prospectportal.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis connection used by the outreach queue.
 *
 * The development profile intentionally disables Redis auto-configuration.
 * Keeping this small explicit configuration makes the queue available in every
 * runtime profile while yielding to Spring Boot if Redis is configured elsewhere.
 */
@Configuration
public class OutreachRedisConfig {

    @Bean
    @ConditionalOnMissingBean(RedisConnectionFactory.class)
    RedisConnectionFactory outreachRedisConnectionFactory(
            @Value("${spring.data.redis.host:localhost}") String host,
            @Value("${spring.data.redis.port:6379}") int port) {
        return new LettuceConnectionFactory(host, port);
    }

    @Bean
    @ConditionalOnMissingBean(StringRedisTemplate.class)
    StringRedisTemplate outreachStringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }
}
