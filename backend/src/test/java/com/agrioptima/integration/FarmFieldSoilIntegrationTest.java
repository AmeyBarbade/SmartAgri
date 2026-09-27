package com.agrioptima.integration;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** CRUD and relationship behaviour of farm -> field -> soil record for a single owner. */
class FarmFieldSoilIntegrationTest extends IntegrationTestSupport {

    String token;
    long farmId;
    long fieldId;

    @BeforeEach
    void setUp() throws Exception {
        token = registerUser();
        farmId = createFarm(token, "Green Acres");
        fieldId = createField(token, farmId, "Plot A");
    }

    @Test
    void farmCrudRoundTrip() throws Exception {
        mvc.perform(json(post("/api/farms"), token, "{\"name\":\"  Riverside  \",\"locationName\":\" \"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/farms/")))
                .andExpect(jsonPath("$.name").value("Riverside"))
                .andExpect(jsonPath("$.locationName").doesNotExist())
                .andExpect(jsonPath("$.createdAt").exists());

        mvc.perform(json(put("/api/farms/" + farmId), token, """
                        {"name":"Green Acres East","latitude":30.1,"longitude":77.2}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Green Acres East"))
                .andExpect(jsonPath("$.latitude").value(30.1));

        mvc.perform(auth(get("/api/farms"), token)).andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void farmValidation() throws Exception {
        mvc.perform(json(post("/api/farms"), token, "{\"name\":\"X\",\"latitude\":95,\"longitude\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.latitude").exists());
        mvc.perform(json(post("/api/farms"), token, "{\"name\":\"X\",\"latitude\":20}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.coordinatePairComplete").exists());
        mvc.perform(json(post("/api/farms"), token, "{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    void fieldWithCropAndMatchingStage() throws Exception {
        long wheat = cropId("WHEAT");
        long cri = stageId(wheat, "CRI");

        mvc.perform(json(put("/api/fields/" + fieldId), token, """
                        {"name":"Plot A","areaHa":2.5,"cropId":%d,"growthStageId":%d,"season":"RABI",
                         "sowingDate":"2026-11-10","previousCrop":"Rice","soilType":"Alluvial loam"}
                        """.formatted(wheat, cri)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.crop.code").value("WHEAT"))
                .andExpect(jsonPath("$.growthStage.code").value("CRI"))
                .andExpect(jsonPath("$.growthStage.seq").value(2))
                .andExpect(jsonPath("$.previousCrop").value("Rice"));

        mvc.perform(auth(get("/api/farms/" + farmId + "/fields"), token))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].crop.code").value("WHEAT"));
    }

    @Test
    void fieldRejectsStageFromAnotherCropAndBadInput() throws Exception {
        long wheat = cropId("WHEAT");
        long riceTillering = stageId(cropId("RICE"), "TILLERING");

        mvc.perform(json(put("/api/fields/" + fieldId), token, """
                        {"name":"Plot A","areaHa":2.5,"cropId":%d,"growthStageId":%d}
                        """.formatted(wheat, riceTillering)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:agrioptima:problem:invalid-request"));

        mvc.perform(json(put("/api/fields/" + fieldId), token, "{\"name\":\"Plot A\",\"areaHa\":2.5,\"growthStageId\":1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.stageWithCrop").exists());

        mvc.perform(json(put("/api/fields/" + fieldId), token, "{\"name\":\"Plot A\",\"areaHa\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.areaHa").exists());

        mvc.perform(json(put("/api/fields/" + fieldId), token, "{\"name\":\"Plot A\",\"areaHa\":1,\"season\":\"MONSOON\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(json(put("/api/fields/" + fieldId), token, "{\"name\":\"Plot A\",\"areaHa\":1,\"cropId\":987654}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void soilHistoryIsNewestFirstAndLatestMatches() throws Exception {
        createSoilRecord(token, fieldId, "2025-03-15", 180);
        long newest = createSoilRecord(token, fieldId, "2026-08-20", 260);
        createSoilRecord(token, fieldId, "2025-10-01", 220);

        mvc.perform(auth(get("/api/fields/" + fieldId + "/soil-records"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].sampleDate").value("2026-08-20"))
                .andExpect(jsonPath("$[1].sampleDate").value("2025-10-01"))
                .andExpect(jsonPath("$[2].sampleDate").value("2025-03-15"))
                .andExpect(jsonPath("$[0].fieldId").value(fieldId));

        mvc.perform(auth(get("/api/fields/" + fieldId + "/soil-records/latest"), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(newest))
                .andExpect(jsonPath("$.nitrogen").value(260.0));

        mvc.perform(auth(delete("/api/soil-records/" + newest), token)).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/fields/" + fieldId + "/soil-records/latest"), token))
                .andExpect(jsonPath("$.sampleDate").value("2025-10-01"));
    }

    @Test
    void latestIs404WhenFieldHasNoSoilRecords() throws Exception {
        mvc.perform(auth(get("/api/fields/" + fieldId + "/soil-records/latest"), token))
                .andExpect(status().isNotFound());
    }

    @Test
    void soilValidationRejectsImplausibleValues() throws Exception {
        String future = LocalDate.now().plusDays(3).toString();
        mvc.perform(json(post("/api/fields/" + fieldId + "/soil-records"), token, """
                        {"sampleDate":"%s","nitrogen":-5,"phosphorus":20,"potassium":5000,"ph":14.5,
                         "organicCarbon":45,"moisture":120}
                        """.formatted(future)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.sampleDate").exists())
                .andExpect(jsonPath("$.errors.nitrogen").exists())
                .andExpect(jsonPath("$.errors.potassium").exists())
                .andExpect(jsonPath("$.errors.ph").exists())
                .andExpect(jsonPath("$.errors.organicCarbon").exists())
                .andExpect(jsonPath("$.errors.moisture").exists())
                .andExpect(jsonPath("$.errors.phosphorus").doesNotExist());

        mvc.perform(json(post("/api/fields/" + fieldId + "/soil-records"), token, "{\"sampleDate\":\"2026-01-01\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.nitrogen").exists())
                .andExpect(jsonPath("$.errors.ph").exists());
    }

    @Test
    void deletingFarmCascadesToFieldsAndSoilRecords() throws Exception {
        long soilId = createSoilRecord(token, fieldId, "2026-07-01", 250);

        mvc.perform(auth(delete("/api/farms/" + farmId), token)).andExpect(status().isNoContent());

        mvc.perform(auth(get("/api/farms/" + farmId), token)).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/fields/" + fieldId), token)).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/soil-records/" + soilId), token)).andExpect(status().isNotFound());
    }

    @Test
    void nonNumericIdIsBadRequest() throws Exception {
        mvc.perform(auth(get("/api/fields/abc"), token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid parameter"));
    }

    private long cropId(String code) throws Exception {
        String body = mvc.perform(auth(get("/api/crops"), token)).andReturn().getResponse().getContentAsString();
        java.util.List<Number> ids = JsonPath.read(body, "$[?(@.code=='" + code + "')].id");
        return ids.get(0).longValue();
    }

    private long stageId(long cropId, String code) throws Exception {
        String body = mvc.perform(auth(get("/api/crops/" + cropId + "/stages"), token))
                .andReturn().getResponse().getContentAsString();
        java.util.List<Number> ids = JsonPath.read(body, "$[?(@.code=='" + code + "')].id");
        return ids.get(0).longValue();
    }
}
