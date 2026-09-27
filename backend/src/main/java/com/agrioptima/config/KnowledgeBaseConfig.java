package com.agrioptima.config;

import com.agrioptima.engine.NutrientRequirementEngine;
import com.agrioptima.engine.knowledge.KnowledgeBase;
import com.agrioptima.engine.knowledge.KnowledgeBaseLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** Loads and validates the nutrient knowledge base once at startup; an invalid file stops the application. */
@Configuration
public class KnowledgeBaseConfig {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseConfig.class);

    @Bean
    public KnowledgeBase knowledgeBase(
            @Value("${app.knowledge-base.location:classpath:knowledge/nutrient-kb-v1.json}") Resource location) {
        try (InputStream in = location.getInputStream()) {
            KnowledgeBase kb = KnowledgeBaseLoader.load(in);
            log.info("Loaded nutrient knowledge base {} v{} ({}) from {}", kb.id(), kb.version(), kb.status(),
                    location.getDescription());
            return kb;
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read nutrient knowledge base " + location.getDescription(), e);
        }
    }

    @Bean
    public NutrientRequirementEngine nutrientRequirementEngine(KnowledgeBase knowledgeBase) {
        return new NutrientRequirementEngine(knowledgeBase);
    }
}
