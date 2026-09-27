package com.agrioptima.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Flyway applied all migrations and Hibernate's ddl-auto=validate accepted the entities (the context
 * would not start otherwise). Seeded reference data and the OpenAPI document are reachable.
 */
class SchemaAndReferenceDataIntegrationTest extends IntegrationTestSupport {

    @Autowired
    Flyway flyway;

    @Test
    void allMigrationsApplied() {
        var info = flyway.info();
        assertThat(info.pending()).isEmpty();
        assertThat(info.applied()).extracting(m -> m.getVersion().getVersion()).contains("1", "2");
    }

    @Test
    void fertilizerGradesMatchSeededSpecification() throws Exception {
        String token = registerUser();
        mvc.perform(auth(get("/api/fertilizers"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[?(@.code=='UREA')].nPct").value(46.0))
                .andExpect(jsonPath("$[?(@.code=='DAP')].nPct").value(18.0))
                .andExpect(jsonPath("$[?(@.code=='DAP')].p2o5Pct").value(46.0))
                .andExpect(jsonPath("$[?(@.code=='MOP')].k2oPct").value(60.0))
                .andExpect(jsonPath("$[?(@.code=='NPK_10_26_26')].k2oPct").value(26.0))
                .andExpect(jsonPath("$[?(@.code=='SSP')].p2o5Pct").value(16.0));
    }

    @Test
    void cropsHaveOrderedGrowthStages() throws Exception {
        String token = registerUser();
        mvc.perform(auth(get("/api/crops"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", org.hamcrest.Matchers.containsInAnyOrder("RICE", "WHEAT", "MAIZE")));

        String crops = mvc.perform(auth(get("/api/crops"), token)).andReturn().getResponse().getContentAsString();
        java.util.List<Number> riceIds = com.jayway.jsonpath.JsonPath.read(crops, "$[?(@.code=='RICE')].id");
        mvc.perform(auth(get("/api/crops/" + riceIds.get(0) + "/stages"), token))
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].seq").value(1))
                .andExpect(jsonPath("$[4].code").value("MATURITY"));

        mvc.perform(auth(get("/api/crops/999999/stages"), token)).andExpect(status().isNotFound());
    }

    @Test
    void referenceDataRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/crops")).andExpect(status().isUnauthorized());
    }

    @Test
    void openApiDocumentIsPublicAndDescribesBearerAuth() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("AgriOptima API"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.paths['/api/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/farms/{farmId}']").exists())
                .andExpect(jsonPath("$.paths['/api/fields/{fieldId}/soil-records/latest']").exists())
                // login/register are documented as not needing a token
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security", hasSize(0)));
    }
}
