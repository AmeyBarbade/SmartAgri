package com.agrioptima.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/fields/{id}/recommendations through the real HTTP API, database, requirement engine and plan verifier.
 * The ML service is {@link FakeMlService}, which replays real optimizer output (Milestone 5 contract fixture).
 * The run against the real FastAPI service is {@code RecommendationLiveMlTest} plus scripts/demo_recommendation.py.
 */
class RecommendationIntegrationTest extends IntegrationTestSupport {

    static final FakeMlService ML = new FakeMlService();

    @DynamicPropertySource
    static void ml(DynamicPropertyRegistry registry) {
        registry.add("app.ml.base-url", ML::baseUrl);
        registry.add("app.ml.read-timeout", () -> "2s");
    }

    @AfterAll
    static void resetMl() {
        ML.reset();
    }

    String token;
    long farmId;
    final LocalDate today = LocalDate.now(ZoneOffset.UTC);
    final LocalDate sown = today.minusDays(25);

    @BeforeEach
    void setUp() throws Exception {
        ML.reset();
        token = registerUser();
        farmId = farm("Patna, Bihar");
    }

    // --- 1. valid wheat -------------------------------------------------------------------------------------------

    @Test
    void wheatRecommendationEndToEnd() throws Exception {
        long field = wheatCriWithBasalApplied(farmId);

        MvcResult r = mvc.perform(auth(post(recommend(field)), token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.status").value("OPTIMAL"))
                .andExpect(jsonPath("$.feasible").value(true))
                .andExpect(jsonPath("$.field.areaHa").value(2.0))
                .andExpect(jsonPath("$.crop.code").value("WHEAT"))
                .andExpect(jsonPath("$.growthStage.code").value("CRI"))
                .andExpect(jsonPath("$.soil.soilTestUsed").value(true))
                .andExpect(jsonPath("$.soil.nClass").value("MEDIUM"))
                .andExpect(jsonPath("$.requirement.dueNowKgHa.n").value(40.0))
                .andExpect(jsonPath("$.requirement.dueNowKgHa.p2o5").value(0.0))
                .andExpect(jsonPath("$.requirement.dueNowFieldKg.n").value(80.0))
                .andExpect(jsonPath("$.plans", hasSize(3)))
                .andExpect(jsonPath("$.plans[*].strategy").value(contains("LOWEST_COST", "MIN_EXCESS", "BALANCED")))
                .andExpect(jsonPath("$.plans[0].items[0].code").value("UREA"))
                .andExpect(jsonPath("$.plans[0].items[0].kgHa").value(86.957))
                .andExpect(jsonPath("$.plans[0].items[0].fieldKg").value(173.914))
                .andExpect(jsonPath("$.plans[0].items[0].fieldBags").value(3.9))
                .andExpect(jsonPath("$.plans[0].costPerHa").value(514.79))
                .andExpect(jsonPath("$.plans[0].fieldCost").value(1029.57))
                .andExpect(jsonPath("$.plans[0].feasible").value(true))
                .andExpect(jsonPath("$.plans[0].yield.available").value(true))
                .andExpect(jsonPath("$.selectedPlan.strategy").value("LOWEST_COST"))
                .andExpect(jsonPath("$.selectedPlan.reason", containsString("Tied on score")))
                .andExpect(jsonPath("$.scoring.mode").value("REVENUE_MINUS_COST_AND_EXCESS"))
                .andExpect(jsonPath("$.scoring.cropPriceInrPerTonne").value(24250))
                .andExpect(jsonPath("$.yieldPrediction.available").value(true))
                .andExpect(jsonPath("$.yieldPrediction.inputs[?(@.feature=='state')].value").value(hasItem("BIHAR")))
                .andExpect(jsonPath("$.yieldPrediction.inputs[?(@.feature=='fym_applied')].source")
                        .value(hasItem("PROTOTYPE_DEFAULT")))
                .andExpect(jsonPath("$.modelVersion").value(FakeMlService.MODEL_VERSION))
                .andExpect(jsonPath("$.knowledgeBase.version").value("1.0.0"))
                .andExpect(jsonPath("$.assumptions", hasItem(containsString("season totals"))))
                .andExpect(jsonPath("$.disclaimer").exists())
                .andReturn();

        // the optimizer received exactly the M4 requirement, the field area and the active catalogue with caps
        JsonNode sent = ML.optimizeRequests.get(0);
        assertThat(sent.path("requirement_kg_ha").path("n").asDouble()).isEqualTo(40.0);
        assertThat(sent.path("area_ha").asDouble()).isEqualTo(2.0);
        assertThat(sent.path("fertilizers")).hasSize(5);
        assertThat(sent.path("fertilizers").get(0).path("max_kg_ha").asDouble()).isEqualTo(500.0);

        // the yield model received the feature contract with mapped/derived inputs
        JsonNode scenario = ML.predictRequests.get(0).path("scenarios").get(0);
        assertThat(scenario.path("crop").asText()).isEqualTo("WHEAT");
        assertThat(scenario.path("state").asText()).isEqualTo("BIHAR");
        assertThat(scenario.path("sowing_date").asText()).isEqualTo(sown.toString());
        assertThat(scenario.path("soil_texture").asText()).isEqualTo("LIGHT");        // "Sandy loam"
        assertThat(scenario.path("irrigation_available").asBoolean()).isTrue();
        assertThat(scenario.path("previous_crop").asText()).isEqualTo("Rice");
        assertThat(scenario.has("variety_type")).isFalse();                              // not recorded -> imputed
        // season total N = applied 40 + plan 40.00022 + later split 40 (remaining 80 - due 40)
        assertThat(scenario.path("n_kg_ha").asDouble()).isCloseTo(120.00022, within(1e-6));
        assertThat(scenario.path("p2o5_kg_ha").asDouble()).isCloseTo(60.0, within(1e-6));

        // persisted and readable, unchanged
        String body = r.getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        mvc.perform(auth(get("/api/recommendations/" + id), token))
                .andExpect(status().isOk())
                .andExpect(content().json(body, true));
        mvc.perform(auth(get("/api/fields/" + field + "/recommendations"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].selectedStrategy").value("LOWEST_COST"))
                .andExpect(jsonPath("$[0].modelVersion").value(FakeMlService.MODEL_VERSION));
    }

    // --- 2. valid rice --------------------------------------------------------------------------------------------

    @Test
    void riceRecommendationAtPanicleInitiation() throws Exception {
        long field = field(farmId, "RICE", "PANICLE_INITIATION", "0.5", "Clay loam", "IRRIGATED", sown, "Wheat");
        soil(field, 600, 30, 300);

        mvc.perform(auth(post(recommend(field)), token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requirement.dueNowKgHa.n").value(67.5))
                .andExpect(jsonPath("$.requirement.dueNowKgHa.p2o5").value(45.0))
                .andExpect(jsonPath("$.requirement.dueNowKgHa.k2o").value(22.5))
                .andExpect(jsonPath("$.plans", hasSize(3)))
                .andExpect(jsonPath("$.plans[0].items[*].code").value(contains("DAP", "NPK_10_26_26", "UREA")))
                .andExpect(jsonPath("$.plans[0].costPerHa").value(4508.94))
                .andExpect(jsonPath("$.plans[0].fieldCost").value(2254.47))
                .andExpect(jsonPath("$.plans[0].yield.available").value(true))
                .andExpect(jsonPath("$.scoring.cropPriceInrPerTonne").value(23690));
        JsonNode scenario = ML.predictRequests.get(0).path("scenarios").get(0);
        assertThat(scenario.path("crop").asText()).isEqualTo("RICE");
        assertThat(scenario.path("soil_texture").asText()).isEqualTo("HEAVY");            // "Clay loam"
    }

    // --- 3. infeasible --------------------------------------------------------------------------------------------

    @Test
    void infeasibleRequirementReturnsReasonsAndSkipsYieldPrediction() throws Exception {
        long field = wheatCriWithBasalApplied(farmId);
        ML.optimize = req -> {
            ObjectNode r = FakeMlService.MAPPER.createObjectNode();
            r.put("status", "INFEASIBLE").put("feasible", false)
                    .put("infeasibility_reason", "N: requires 40 kg/ha but at most 0 kg/ha can be supplied")
                    .put("area_ha", req.path("area_ha").asDouble());
            r.set("requirement_kg_ha", req.path("requirement_kg_ha"));
            r.putArray("plans");
            r.putArray("warnings").add("No plan can meet the requirement with the available fertilizers and limits.");
            r.putArray("infeasibility").addObject().put("nutrient", "N").put("required_kg_ha", 40.0)
                    .put("max_supply_kg_ha", 0.0).put("shortfall_kg_ha", 40.0)
                    .put("reason", "no available fertilizer contains N");
            return FakeMlService.Reply.json(r);
        };

        MvcResult r = mvc.perform(auth(post(recommend(field)), token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("INFEASIBLE"))
                .andExpect(jsonPath("$.feasible").value(false))
                .andExpect(jsonPath("$.infeasibilityReason", containsString("N: requires 40")))
                .andExpect(jsonPath("$.infeasibility[0].nutrient").value("N"))
                .andExpect(jsonPath("$.infeasibility[0].reason").value("no available fertilizer contains N"))
                .andExpect(jsonPath("$.requirement.dueNowKgHa.n").value(40.0))
                .andExpect(jsonPath("$.plans", hasSize(0)))
                .andExpect(jsonPath("$.selectedPlan").doesNotExist())
                .andExpect(jsonPath("$.yieldPrediction.available").value(false))
                .andExpect(jsonPath("$.warnings", hasItem(containsString("No plan can meet"))))
                .andExpect(jsonPath("$.assumptions").isNotEmpty())
                .andReturn();
        assertThat(ML.predictRequests).isEmpty();
        long id = ((Number) JsonPath.read(r.getResponse().getContentAsString(), "$.id")).longValue();
        mvc.perform(auth(get("/api/recommendations/" + id), token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INFEASIBLE"));
    }

    // --- 4. ML failures -------------------------------------------------------------------------------------------

    @Test
    void mlErrorsBecomeProblemResponsesWithoutInternals() throws Exception {
        long field = wheatCriWithBasalApplied(farmId);

        ML.optimize = req -> FakeMlService.Reply.problem(500, "internal");
        problem(field, 502, "ml-service-error");

        ML.optimize = req -> new FakeMlService.Reply(200, "{not json");
        problem(field, 502, "ml-service-invalid-response");

        ML.optimize = req -> new FakeMlService.Reply(200, "{\"status\":\"OPTIMAL\"}");
        problem(field, 502, "ml-service-invalid-response");

        ML.optimize = req -> {  // answers for a different requirement
            ObjectNode resp = (ObjectNode) FakeMlService.tree(FakeMlService.contractReplay(req).body());
            ((ObjectNode) resp.path("requirement_kg_ha")).put("n", 39.0);
            return FakeMlService.Reply.json(resp);
        };
        problem(field, 502, "ml-service-invalid-response");

        ML.optimize = req -> {
            sleep(3000);  // read timeout is 2 s in this context
            return FakeMlService.contractReplay(req);
        };
        problem(field, 504, "ml-service-timeout");

        mvc.perform(auth(get("/api/fields/" + field + "/recommendations"), token))
                .andExpect(jsonPath("$", hasSize(0)));  // failed runs are not stored
    }

    @Test
    void yieldPredictionFailureDoesNotBlockTheRecommendation() throws Exception {
        long field = wheatCriWithBasalApplied(farmId);
        ML.predict = req -> FakeMlService.Reply.problem(503, "model-unavailable");
        mvc.perform(auth(post(recommend(field)), token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plans", hasSize(3)))
                .andExpect(jsonPath("$.yieldPrediction.available").value(false))
                .andExpect(jsonPath("$.yieldPrediction.unavailableReason", containsString("not loaded")))
                .andExpect(jsonPath("$.scoring.mode").value("COST_AND_EXCESS_ONLY"))
                .andExpect(jsonPath("$.modelVersion").doesNotExist());

        ML.predict = req -> new FakeMlService.Reply(200, """
                {"model_version":"m","predictions":[{"index":0,"crop":"WHEAT"}]}""");  // yield missing
        mvc.perform(auth(post(recommend(field)), token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.yieldPrediction.available").value(false))
                .andExpect(jsonPath("$.plans[0].yield.predictedYieldTHa").doesNotExist());
    }

    // --- 5. unsupported crop / missing inputs ---------------------------------------------------------------------

    @Test
    void maizeGetsPlansButNoYieldPrediction() throws Exception {
        long field = field(farmId, "MAIZE", "SOWING", "1.2", null, "RAINFED", sown, null);
        ML.predict = req -> "MAIZE".equals(req.path("scenarios").get(0).path("crop").asText())
                ? FakeMlService.Reply.problem(422, "unsupported-crop") : FakeMlService.predictions(req);

        mvc.perform(auth(post(recommend(field)), token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("OPTIMAL"))
                .andExpect(jsonPath("$.requirement.dueNowKgHa.n").value(30.0))
                .andExpect(jsonPath("$.plans", hasSize(3)))
                .andExpect(jsonPath("$.plans[0].yield.available").value(false))
                .andExpect(jsonPath("$.plans[0].score.expectedRevenuePerHa").doesNotExist())
                .andExpect(jsonPath("$.yieldPrediction.available").value(false))
                .andExpect(jsonPath("$.yieldPrediction.unavailableReason", containsString("does not support MAIZE")))
                .andExpect(jsonPath("$.scoring.mode").value("COST_AND_EXCESS_ONLY"))
                .andExpect(jsonPath("$.selectedPlan.strategy").value("LOWEST_COST"))
                .andExpect(jsonPath("$.warnings", hasItem(containsString("No yield prediction"))));
    }

    @Test
    void unknownStateMeansNoYieldPredictionRatherThanAGuess() throws Exception {
        long field = wheatCriWithBasalApplied(farm("Karnal"));
        mvc.perform(auth(post(recommend(field)), token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plans", hasSize(3)))
                .andExpect(jsonPath("$.yieldPrediction.available").value(false))
                .andExpect(jsonPath("$.yieldPrediction.unavailableReason", containsString("state")))
                .andExpect(jsonPath("$.yieldPrediction.inputs[?(@.feature=='state')].source")
                        .value(hasItem("NOT_RECORDED")));
        assertThat(ML.predictRequests).isEmpty();
    }

    // --- 6. ownership ---------------------------------------------------------------------------------------------

    @Test
    void recommendationsAreOwnerScoped() throws Exception {
        long field = wheatCriWithBasalApplied(farmId);
        String rec = mvc.perform(auth(post(recommend(field)), token)).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(rec, "$.id")).longValue();

        String other = registerUser();
        mvc.perform(auth(post(recommend(field)), other)).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/fields/" + field + "/recommendations"), other)).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/recommendations/" + id), other)).andExpect(status().isNotFound());
        mvc.perform(post(recommend(field))).andExpect(status().isUnauthorized());
        assertThat(ML.optimizeRequests).hasSize(1);  // foreign requests never reach the ML service
    }

    // --- 7. missing soil record / invalid field state -------------------------------------------------------------

    @Test
    void noSoilTestUsesTheEngineDefaultWithAWarning() throws Exception {
        long field = field(farmId, "MAIZE", "SOWING", "1.2", null, "RAINFED", sown, null);
        mvc.perform(auth(post(recommend(field)), token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.soil.soilTestUsed").value(false))
                .andExpect(jsonPath("$.soil.nClass").value("ASSUMED_MEDIUM"))
                .andExpect(jsonPath("$.warnings", hasItem(containsString("No soil test on record"))));
    }

    @Test
    void fieldWithoutCropOrStageIsRejectedBeforeCallingMl() throws Exception {
        long bare = createField(token, farmId, "Bare");
        mvc.perform(auth(post(recommend(bare)), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail", containsString("has no crop")));
        mvc.perform(auth(post(recommend(bare)).param("profile", "bad value!"), token))
                .andExpect(status().isBadRequest());
        mvc.perform(auth(post(recommend(999_999_999L)), token)).andExpect(status().isNotFound());
        assertThat(ML.optimizeRequests).isEmpty();
    }

    // --- 8. each plan gets its own prediction ---------------------------------------------------------------------

    @Test
    void everyPlanReceivesThePredictionForItsOwnScenario() throws Exception {
        long field = wheatPkOnly(farmId);
        String body = mvc.perform(auth(post(recommend(field)), token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.requirement.dueNowKgHa.p2o5").value(60.0))
                .andExpect(jsonPath("$.plans[*].strategy").value(contains("LOWEST_COST", "MIN_EXCESS", "BALANCED")))
                .andReturn().getResponse().getContentAsString();

        // the three plans differ (M5 conflict case), so their scenarios and predictions differ
        ArrayNode scenarios = (ArrayNode) ML.predictRequests.get(0).path("scenarios");
        assertThat(scenarios).hasSize(3);
        JsonNode doc = FakeMlService.MAPPER.readTree(body);
        double appliedN = doc.at("/requirement/alreadyAppliedKgHa/n").asDouble();
        for (int i = 0; i < 3; i++) {
            JsonNode plan = doc.path("plans").get(i);
            JsonNode scenario = scenarios.get(i);
            assertThat(scenario.path("n_kg_ha").asDouble())
                    .isCloseTo(appliedN + plan.at("/suppliedKgHa/n").asDouble(), within(0.01));
            assertThat(plan.at("/yield/predictedYieldTHa").asDouble())
                    .isCloseTo(FakeMlService.yieldOf(scenario), within(0.0005));
            assertThat(plan.at("/yield/seasonNutrientsKgHa/n").asDouble())
                    .isCloseTo(scenario.path("n_kg_ha").asDouble(), within(0.005));
        }
        assertThat(doc.at("/plans/0/yield/predictedYieldTHa").asDouble())
                .isNotEqualTo(doc.at("/plans/1/yield/predictedYieldTHa").asDouble());
    }

    // --- 9. deterministic, transparent selection ------------------------------------------------------------------

    @Test
    void selectionIsDeterministicAndFollowsTheDocumentedScore() throws Exception {
        long field = wheatPkOnly(farmId);
        JsonNode first = recommendation(field);
        JsonNode second = recommendation(field);
        assertThat(second.path("plans")).isEqualTo(first.path("plans"));
        assertThat(second.path("selectedPlan")).isEqualTo(first.path("selectedPlan"));

        double price = first.at("/scoring/cropPriceInrPerTonne").asDouble();
        double penalty = first.at("/scoring/excessPenaltyInrPerKg").asDouble();
        String best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (JsonNode p : first.path("plans")) {
            double expected = p.at("/yield/predictedYieldTHa").asDouble() * price - p.path("costPerHa").asDouble()
                    - penalty * p.path("totalExcessKgHa").asDouble();
            assertThat(p.at("/score/scorePerHa").asDouble()).isCloseTo(expected, within(0.02));
            if (expected > bestScore) {
                bestScore = expected;
                best = p.path("strategy").asText();
            }
            assertThat(p.path("selected").asBoolean()).isEqualTo(p.path("strategy").asText().equals(
                    first.at("/selectedPlan/strategy").asText()));
        }
        assertThat(first.at("/selectedPlan/strategy").asText()).isEqualTo(best);
        assertThat(first.at("/plans").findValues("rank").stream().map(JsonNode::asInt)).contains(1, 2, 3);
    }

    // --- 10. constraints ------------------------------------------------------------------------------------------

    @Test
    void plansThatBreakTheRequirementAreDiscardedByTheBackend() throws Exception {
        long field = wheatPkOnly(farmId);
        ML.optimize = req -> {
            JsonNode real = FakeMlService.tree(FakeMlService.contractReplay(req).body());
            ObjectNode tampered = real.deepCopy();
            // MIN_EXCESS: 10 % less MOP than the optimizer computed -> K2O short, totals inconsistent
            ObjectNode item = (ObjectNode) tampered.path("plans").get(1).path("items").get(0);
            item.put("kg_ha", item.path("kg_ha").asDouble() * 0.9);
            return FakeMlService.Reply.json(tampered);
        };
        mvc.perform(auth(post(recommend(field)), token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plans[*].strategy").value(contains("LOWEST_COST", "BALANCED")))
                .andExpect(jsonPath("$.selectedPlan.strategy").value(not("MIN_EXCESS")))
                .andExpect(jsonPath("$.warnings", hasItem(containsString("MIN_EXCESS plan from the optimizer failed"))));

        ML.optimize = req -> {  // every plan short of the requirement
            JsonNode real = FakeMlService.tree(FakeMlService.contractReplay(req).body());
            ObjectNode tampered = real.deepCopy();
            for (JsonNode plan : tampered.path("plans")) {
                for (JsonNode item : plan.path("items")) {
                    ((ObjectNode) item).put("kg_ha", item.path("kg_ha").asDouble() / 2);
                }
            }
            return FakeMlService.Reply.json(tampered);
        };
        problem(field, 502, "ml-service-invalid-response");
    }

    @Test
    void everyReturnedPlanMeetsTheRequirement() throws Exception {
        for (long field : List.of(wheatCriWithBasalApplied(farmId), wheatPkOnly(farmId))) {
            JsonNode doc = recommendation(field);
            JsonNode due = doc.at("/requirement/dueNowKgHa");
            for (JsonNode plan : doc.path("plans")) {
                assertThat(plan.path("feasible").asBoolean()).isTrue();
                for (String nutrient : List.of("n", "p2o5", "k2o")) {
                    assertThat(plan.at("/suppliedKgHa/" + nutrient).asDouble())
                            .isGreaterThanOrEqualTo(due.path(nutrient).asDouble());
                }
            }
        }
    }

    // --- helpers --------------------------------------------------------------------------------------------------

    private static String recommend(long fieldId) {
        return "/api/fields/" + fieldId + "/recommendations";
    }

    private JsonNode recommendation(long field) throws Exception {
        return FakeMlService.MAPPER.readTree(mvc.perform(auth(post(recommend(field)), token))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private void problem(long field, int status, String type) throws Exception {
        String body = mvc.perform(auth(post(recommend(field)), token))
                .andExpect(status().is(status))
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:agrioptima:problem:" + type))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("127.0.0.1").doesNotContain("Traceback").doesNotContain("Exception");
    }

    private long farm(String location) throws Exception {
        return idOf(mvc.perform(json(post("/api/farms"), token, """
                        {"name":"Farm","locationName":"%s"}""".formatted(location)))
                .andExpect(status().isCreated()).andReturn());
    }

    private long field(long farm, String crop, String stage, String area, String soilType, String irrigation,
                       LocalDate sowing, String previousCrop) throws Exception {
        long cropId = id("/api/crops", crop);
        ObjectNode node = FakeMlService.MAPPER.createObjectNode()
                .put("name", crop + " " + stage).put("areaHa", area).put("cropId", cropId)
                .put("growthStageId", id("/api/crops/" + cropId + "/stages", stage))
                .put("irrigationType", irrigation).put("sowingDate", sowing.toString());
        if (soilType != null) {
            node.put("soilType", soilType);
        }
        if (previousCrop != null) {
            node.put("previousCrop", previousCrop);
        }
        return idOf(mvc.perform(json(post("/api/farms/" + farm + "/fields"), token, node.toString()))
                .andExpect(status().isCreated()).andReturn());
    }

    /** M4 example 1: wheat CRI, 2 ha, medium soil, basal 40/60/40 kg/ha applied -> due 40/0/0. */
    private long wheatCriWithBasalApplied(long farm) throws Exception {
        long field = field(farm, "WHEAT", "CRI", "2.0", "Sandy loam", "IRRIGATED", sown, "Rice");
        soil(field, 300, 15, 200);
        apply(field, "DAP", "260.87");
        apply(field, "UREA", "71.83");
        apply(field, "MOP", "133.33");
        return field;
    }

    /** M4 example 6: wheat tillering, 1 ha, 400 kg urea applied -> due 0/60/40 (the three plans differ). */
    private long wheatPkOnly(long farm) throws Exception {
        long field = field(farm, "WHEAT", "TILLERING", "1.0", "Loam", "IRRIGATED", sown, "Rice");
        soil(field, 300, 15, 200);
        apply(field, "UREA", "400");
        return field;
    }

    private void soil(long field, double n, double p, double k) throws Exception {
        mvc.perform(json(post("/api/fields/" + field + "/soil-records"), token, """
                        {"sampleDate":"%s","nitrogen":%s,"phosphorus":%s,"potassium":%s,"ph":7.2}
                        """.formatted(sown.minusDays(10), n, p, k)))
                .andExpect(status().isCreated());
    }

    private void apply(long field, String fertilizer, String kg) throws Exception {
        mvc.perform(json(post("/api/fields/" + field + "/applications"), token, """
                        {"fertilizerId":%d,"appliedOn":"%s","quantityKg":%s}
                        """.formatted(id("/api/fertilizers", fertilizer), sown, kg)))
                .andExpect(status().isCreated());
    }

    private long id(String listPath, String code) throws Exception {
        String body = mvc.perform(auth(get(listPath), token)).andReturn().getResponse().getContentAsString();
        Number id = JsonPath.<List<Number>>read(body, "$[?(@.code=='" + code + "')].id").get(0);
        return id.longValue();
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
