package com.agrioptima.config;

import com.agrioptima.security.JwtProperties;
import com.agrioptima.ml.MlServiceProperties;
import com.agrioptima.service.recommendation.RecommendationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;

@Configuration
@EnableJpaAuditing
@EnableConfigurationProperties({JwtProperties.class, MlServiceProperties.class, RecommendationProperties.class})
public class AppConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
