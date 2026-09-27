package com.agrioptima.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Against the REAL FastAPI ML service (real optimizer and yield model). Opt-in, because it needs the Python service:
 * <pre>
 *   .\scripts\ml.ps1 serve                                   # terminal 1
 *   $env:ML_LIVE_BASE_URL="http://localhost:8001"; .\scripts\backend.ps1 test   # terminal 2
 * </pre>
 */
@EnabledIfEnvironmentVariable(named = "ML_LIVE_BASE_URL", matches = "https?://.+")
class RecommendationLiveMlTest extends IntegrationTestSupport {

    @DynamicPropertySource
    static void ml(DynamicPropertyRegistry registry) {
        registry.add("app.ml.base-url", () -> System.getenv("ML_LIVE_BASE_URL"));
    }

    final ObjectMapper mapper = new ObjectMapper();

    @Test
    void realServicesProduceVerifiedScoredPlansWithRealYields() throws Exception {
        String token = registerUser();
        long farm = idOf(mvc.perform(json(post("/api/farms"), token, "{\"name\":\"Live\",\"locationName\":\"Patna, Bihar\"}"))
                .andExpect(status().isCreated()).andReturn());
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate sown = LocalDate.of(today.isBefore(LocalDate.of(today.getYear(), 11, 20)) ? today.getYear() - 1
                : today.getYear(), 11, 20);
        long wheat = id(token, "/api/crops", "WHEAT");
        long field = idOf(mvc.perform(json(post("/api/farms/" + farm + "/fields"), token, """
                        {"name":"Wheat","areaHa":1,"soilType":"Loam","irrigationType":"IRRIGATED","cropId":%d,
                         "growthStageId":%d,"sowingDate":"%s","previousCrop":"Rice"}
                        """.formatted(wheat, id(token, "/api/crops/" + wheat + "/stages", "TILLERING"), sown)))
                .andExpect(status().isCreated()).andReturn());
        mvc.perform(json(post("/api/fields/" + field + "/soil-records"), token, """
                        {"sampleDate":"%s","nitrogen":300,"phosphorus":15,"potassium":200,"ph":7.0}
                        """.formatted(sown.minusDays(10)))).andExpect(status().isCreated());
        mvc.perform(json(post("/api/fields/" + field + "/applications"), token, """
                        {"fertilizerId":%d,"appliedOn":"%s","quantityKg":400}
                        """.formatted(id(token, "/api/fertilizers", "UREA"), sown))).andExpect(status().isCreated());

        JsonNode rec = mapper.readTree(mvc.perform(auth(post("/api/fields/" + field + "/recommendations"), token))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        assertThat(rec.path("status").asText()).isEqualTo("OPTIMAL");
        assertThat(rec.path("modelVersion").asText()).startsWith("yield-lds2018-");
        assertThat(rec.path("plans")).hasSize(3);
        for (JsonNode plan : rec.path("plans")) {
            assertThat(plan.path("feasible").asBoolean()).isTrue();
            assertThat(plan.at("/yield/available").asBoolean()).isTrue();
            assertThat(plan.at("/yield/predictedYieldTHa").asDouble()).isBetween(0.5, 10.0);
        }
        // the M5 conflict case: three different plans (docs/OPTIMIZER.md)
        assertThat(rec.at("/plans/0/costPerHa").asDouble()).isEqualTo(5697.03);
        assertThat(rec.at("/plans/1/totalExcessKgHa").asDouble()).isLessThan(0.01);
        assertThat(rec.at("/selectedPlan/strategy").asText()).isIn("LOWEST_COST", "MIN_EXCESS", "BALANCED");
    }

    private long id(String token, String path, String code) throws Exception {
        String body = mvc.perform(auth(get(path), token)).andReturn().getResponse().getContentAsString();
        return JsonPath.<List<Number>>read(body, "$[?(@.code=='" + code + "')].id").get(0).longValue();
    }
}
