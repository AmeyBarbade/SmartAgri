package com.agrioptima.integration;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ML service not running (connection refused): clean 503 problem, nothing stored, no internal URL exposed. */
@TestPropertySource(properties = {"app.ml.base-url=http://127.0.0.1:9", "app.ml.connect-timeout=1s"})
class RecommendationMlUnavailableTest extends IntegrationTestSupport {

    @Test
    void mlServiceDownIs503() throws Exception {
        String token = registerUser();
        long farm = createFarm(token, "Farm");
        String crops = mvc.perform(auth(get("/api/crops"), token)).andReturn().getResponse().getContentAsString();
        Number maize = com.jayway.jsonpath.JsonPath.<java.util.List<Number>>read(crops, "$[?(@.code=='MAIZE')].id").get(0);
        String stages = mvc.perform(auth(get("/api/crops/" + maize + "/stages"), token)).andReturn().getResponse()
                .getContentAsString();
        Number sowing = com.jayway.jsonpath.JsonPath.<java.util.List<Number>>read(stages, "$[?(@.code=='SOWING')].id").get(0);
        long field = idOf(mvc.perform(json(post("/api/farms/" + farm + "/fields"), token, """
                        {"name":"Maize","areaHa":1.2,"irrigationType":"RAINFED","cropId":%s,"growthStageId":%s}
                        """.formatted(maize, sowing)))
                .andExpect(status().isCreated()).andReturn());

        String body = mvc.perform(auth(post("/api/fields/" + field + "/recommendations"), token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.type").value("urn:agrioptima:problem:ml-service-unavailable"))
                .andExpect(jsonPath("$.detail").value("The ML service is not reachable (optimization). Try again later."))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("127.0.0.1").doesNotContain(":9").doesNotContain("Connection refused");

        mvc.perform(auth(get("/api/fields/" + field + "/recommendations"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
