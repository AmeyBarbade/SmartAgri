package com.agrioptima.integration;

import com.agrioptima.service.KnowledgeBaseConsistencyCheck;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Field -> soil test -> previous application -> nutrient requirement, through the real HTTP API and database. */
class NutrientRequirementIntegrationTest extends IntegrationTestSupport {

    @Autowired
    KnowledgeBaseConsistencyCheck consistencyCheck;

    String token;
    long fieldId;
    long wheat;
    long rice;
    final LocalDate today = LocalDate.now(ZoneOffset.UTC);
    final LocalDate sown = today.minusDays(25);

    @BeforeEach
    void setUp() throws Exception {
        token = registerUser();
        fieldId = createField(token, createFarm(token, "Requirement Farm"), "Wheat plot");
        wheat = cropId("WHEAT");
        rice = cropId("RICE");
        setStage(wheat, "CRI", "2.0");
    }

    @Test
    void knowledgeBaseMatchesTheSeededCropsAndStages() {
        assertThat(consistencyCheck.check()).isEmpty();
    }

    @Test
    void wheatAtCriWithMediumSoilAndBasalAlreadyAppliedNeedsTheSecondNSplit() throws Exception {
        soilTest(300, 15, 200, 7.2);
        // Basal for 2 ha: 80 kg N, 120 kg P2O5, 80 kg K2O = 40/60/40 per ha, supplied with DAP + urea + MOP.
        long dap = fertilizerId("DAP");
        long urea = fertilizerId("UREA");
        long mop = fertilizerId("MOP");
        apply(dap, "260.87", sown);          // 46.96 N, 120.00 P2O5
        apply(urea, "71.83", sown);          // 33.04 N
        apply(mop, "133.33", sown);          // 80.00 K2O

        mvc.perform(auth(get("/api/fields/" + fieldId + "/nutrient-requirement"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cropCode").value("WHEAT"))
                .andExpect(jsonPath("$.stage.code").value("CRI"))
                .andExpect(jsonPath("$.profile.code").value("IRRIGATED_TIMELY_SOWN"))
                .andExpect(jsonPath("$.profile.sourceId").value("IIWBR_EB52"))
                .andExpect(jsonPath("$.soil.soilTestUsed").value(true))
                .andExpect(jsonPath("$.nutrients[0].soilClass").value("MEDIUM"))
                .andExpect(jsonPath("$.previousApplications.applicationsCounted").value(3))
                .andExpect(jsonPath("$.previousApplications.appliedKgHa.n").value(40.0))
                .andExpect(jsonPath("$.previousApplications.appliedKgHa.p2o5").value(60.0))
                .andExpect(jsonPath("$.previousApplications.appliedKgHa.k2o").value(40.0))
                .andExpect(jsonPath("$.requirementForOptimizer.kgPerHa.n").value(40.0))
                .andExpect(jsonPath("$.requirementForOptimizer.kgPerHa.p2o5").value(0.0))
                .andExpect(jsonPath("$.requirementForOptimizer.kgPerHa.k2o").value(0.0))
                .andExpect(jsonPath("$.requirementForOptimizer.fieldKg.n").value(80.0))
                .andExpect(jsonPath("$.remainingSeasonKgHa.n").value(80.0))
                .andExpect(jsonPath("$.schedule", hasSize(3)))
                .andExpect(jsonPath("$.schedule[1].position").value("CURRENT"))
                .andExpect(jsonPath("$.knowledgeBase.version").value("1.0.0"))
                .andExpect(jsonPath("$.disclaimer").exists());
    }

    @Test
    void lowSoilAndNoSoilTestGiveDifferentRequirements() throws Exception {
        mvc.perform(auth(get("/api/fields/" + fieldId + "/nutrient-requirement"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.soil.soilTestUsed").value(false))
                .andExpect(jsonPath("$.nutrients[0].soilClass").value("ASSUMED_MEDIUM"))
                .andExpect(jsonPath("$.requirementForOptimizer.kgPerHa.n").value(80.0))
                .andExpect(jsonPath("$.warnings", hasItem(containsString("No soil test on record"))));

        soilTest(200, 5, 100, 5.0);
        mvc.perform(auth(get("/api/fields/" + fieldId + "/nutrient-requirement"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nutrients[0].soilClass").value("LOW"))
                .andExpect(jsonPath("$.requirementForOptimizer.kgPerHa.n").value(100.0))    // 150 x 2/3
                .andExpect(jsonPath("$.requirementForOptimizer.kgPerHa.p2o5").value(75.0))
                .andExpect(jsonPath("$.warnings", hasItem(containsString("strongly acidic"))));
    }

    @Test
    void explicitProfileAndRiceField() throws Exception {
        mvc.perform(auth(get("/api/fields/" + fieldId + "/nutrient-requirement")
                        .param("profile", "IRRIGATED_TIMELY_SOWN_NWPZ_NEPZ"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nutrients[0].generalRecommendation").value(150.0));

        setStage(rice, "TILLERING", "0.5");
        mvc.perform(auth(get("/api/fields/" + fieldId + "/nutrient-requirement"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profile.code").value("NRRI_GENERAL"))
                .andExpect(jsonPath("$.requirementForOptimizer.kgPerHa.n").value(60.0))       // 120 x 1/2
                .andExpect(jsonPath("$.requirementForOptimizer.fieldKg.n").value(30.0));      // x 0.5 ha
    }

    @Test
    void invalidRequestsAreRejected() throws Exception {
        mvc.perform(auth(get("/api/fields/" + fieldId + "/nutrient-requirement").param("profile", "NOPE"), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("Unknown profile NOPE")));
        mvc.perform(auth(get("/api/fields/" + fieldId + "/nutrient-requirement").param("profile", "bad value!"), token))
                .andExpect(status().isBadRequest());

        long bare = createField(token, createFarm(token, "Bare"), "No crop");
        mvc.perform(auth(get("/api/fields/" + bare + "/nutrient-requirement"), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("has no crop")));
        mvc.perform(json(put("/api/fields/" + bare), token, """
                        {"name":"No stage","areaHa":1,"cropId":%d}
                        """.formatted(wheat)))
                .andExpect(status().isOk());
        mvc.perform(auth(get("/api/fields/" + bare + "/nutrient-requirement"), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("has no growth stage")));
    }

    @Test
    void requirementIsOwnerScopedAndNeedsAuthentication() throws Exception {
        String other = registerUser();
        mvc.perform(auth(get("/api/fields/" + fieldId + "/nutrient-requirement"), other))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/fields/" + fieldId + "/nutrient-requirement"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void applicationsCrudAndValidation() throws Exception {
        long urea = fertilizerId("UREA");
        long id = apply(urea, "100", sown);
        mvc.perform(auth(get("/api/fields/" + fieldId + "/applications"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fertilizer.code").value("UREA"))
                .andExpect(jsonPath("$[0].nutrientsKg.n").value(46.0));

        String body = "{\"fertilizerId\":%d,\"appliedOn\":\"%s\",\"quantityKg\":%s%s}";
        mvc.perform(json(post("/api/fields/" + fieldId + "/applications"), token, body.formatted(urea, today, "0", "")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.quantityKg").exists());
        mvc.perform(json(post("/api/fields/" + fieldId + "/applications"), token,
                        body.formatted(urea, today.plusDays(1), "10", "")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.appliedOn").exists());
        mvc.perform(json(post("/api/fields/" + fieldId + "/applications"), token, body.formatted(999999, today, "10", "")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", containsString("Unknown fertilizerId")));
        long riceStage = stageId(rice, "TILLERING");
        mvc.perform(json(post("/api/fields/" + fieldId + "/applications"), token,
                        body.formatted(urea, today, "10", ",\"growthStageId\":" + riceStage)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("does not belong to the crop")));

        String other = registerUser();
        mvc.perform(auth(delete("/api/applications/" + id), other)).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/fields/" + fieldId + "/applications"), other)).andExpect(status().isNotFound());
        mvc.perform(auth(delete("/api/applications/" + id), token)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/fields/" + fieldId + "/applications"), token)).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void knowledgeBaseIsPublishedWithSourcesAndStatuses() throws Exception {
        mvc.perform(auth(get("/api/knowledge-base"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("1.0.0"))
                .andExpect(jsonPath("$.sources[*].id", hasItem("IIWBR_EB52")))
                .andExpect(jsonPath("$.soilTest.adjustmentFactors.status").value("PROTOTYPE_ASSUMPTION"))
                .andExpect(jsonPath("$.crops[0].schedules[0].splits[0].n").value("1/3"));
        mvc.perform(get("/api/knowledge-base")).andExpect(status().isUnauthorized());
    }

    // --- helpers

    private void setStage(long cropId, String stageCode, String areaHa) throws Exception {
        mvc.perform(json(put("/api/fields/" + fieldId), token, """
                        {"name":"Plot","areaHa":%s,"irrigationType":"IRRIGATED","cropId":%d,"growthStageId":%d,
                         "sowingDate":"%s"}
                        """.formatted(areaHa, cropId, stageId(cropId, stageCode), sown)))
                .andExpect(status().isOk());
    }

    private void soilTest(double n, double p, double k, double ph) throws Exception {
        mvc.perform(json(post("/api/fields/" + fieldId + "/soil-records"), token, """
                        {"sampleDate":"%s","nitrogen":%s,"phosphorus":%s,"potassium":%s,"ph":%s}
                        """.formatted(sown.minusDays(10), n, p, k, ph)))
                .andExpect(status().isCreated());
    }

    private long apply(long fertilizerId, String quantityKg, LocalDate on) throws Exception {
        MvcResult r = mvc.perform(json(post("/api/fields/" + fieldId + "/applications"), token, """
                        {"fertilizerId":%d,"appliedOn":"%s","quantityKg":%s}
                        """.formatted(fertilizerId, on, quantityKg)))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(r);
    }

    private long cropId(String code) throws Exception {
        String body = mvc.perform(auth(get("/api/crops"), token)).andReturn().getResponse().getContentAsString();
        Number id = JsonPath.<java.util.List<Number>>read(body, "$[?(@.code=='" + code + "')].id").get(0);
        return id.longValue();
    }

    private long stageId(long cropId, String code) throws Exception {
        String body = mvc.perform(auth(get("/api/crops/" + cropId + "/stages"), token)).andReturn().getResponse()
                .getContentAsString();
        Number id = JsonPath.<java.util.List<Number>>read(body, "$[?(@.code=='" + code + "')].id").get(0);
        return id.longValue();
    }

    private long fertilizerId(String code) throws Exception {
        String body = mvc.perform(auth(get("/api/fertilizers"), token)).andReturn().getResponse().getContentAsString();
        Number id = JsonPath.<java.util.List<Number>>read(body, "$[?(@.code=='" + code + "')].id").get(0);
        return id.longValue();
    }
}
