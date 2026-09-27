package com.agrioptima.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Alice owns a farm -> field -> soil record. Bob must not be able to read, modify, delete or attach
 * anything to them. Foreign resources answer 404 so their existence is not disclosed.
 */
class OwnershipIntegrationTest extends IntegrationTestSupport {

    String alice;
    String bob;
    long aliceFarm;
    long aliceField;
    long aliceSoil;

    @BeforeEach
    void setUp() throws Exception {
        alice = registerUser();
        bob = registerUser();
        aliceFarm = createFarm(alice, "Alice Farm");
        aliceField = createField(alice, aliceFarm, "North plot");
        aliceSoil = createSoilRecord(alice, aliceField, "2026-09-01", 240);
    }

    @Test
    void listingsOnlyShowOwnFarms() throws Exception {
        createFarm(bob, "Bob Farm");
        mvc.perform(auth(get("/api/farms"), bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Bob Farm"));
        mvc.perform(auth(get("/api/farms"), alice))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Alice Farm"))
                .andExpect(jsonPath("$[0].fieldCount").value(1));
    }

    @Test
    void otherUsersFarmIsInvisibleAndImmutable() throws Exception {
        mvc.perform(auth(get("/api/farms/" + aliceFarm), bob)).andExpect(status().isNotFound());
        mvc.perform(json(put("/api/farms/" + aliceFarm), bob, "{\"name\":\"Hijacked\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(auth(delete("/api/farms/" + aliceFarm), bob)).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/farms/" + aliceFarm + "/fields"), bob)).andExpect(status().isNotFound());
        mvc.perform(json(post("/api/farms/" + aliceFarm + "/fields"), bob, "{\"name\":\"Intruder\",\"areaHa\":1}"))
                .andExpect(status().isNotFound());

        // Alice's farm is untouched.
        mvc.perform(auth(get("/api/farms/" + aliceFarm), alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice Farm"))
                .andExpect(jsonPath("$.fieldCount").value(1));
    }

    @Test
    void otherUsersFieldIsInvisibleAndImmutable() throws Exception {
        mvc.perform(auth(get("/api/fields/" + aliceField), bob)).andExpect(status().isNotFound());
        mvc.perform(json(put("/api/fields/" + aliceField), bob, "{\"name\":\"Hijacked\",\"areaHa\":9}"))
                .andExpect(status().isNotFound());
        mvc.perform(auth(delete("/api/fields/" + aliceField), bob)).andExpect(status().isNotFound());

        mvc.perform(auth(get("/api/fields/" + aliceField), alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("North plot"))
                .andExpect(jsonPath("$.farmId").value(aliceFarm));
    }

    @Test
    void otherUsersSoilRecordsAreInvisibleAndCannotBeAdded() throws Exception {
        mvc.perform(auth(get("/api/fields/" + aliceField + "/soil-records"), bob)).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/fields/" + aliceField + "/soil-records/latest"), bob))
                .andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/soil-records/" + aliceSoil), bob)).andExpect(status().isNotFound());
        mvc.perform(auth(delete("/api/soil-records/" + aliceSoil), bob)).andExpect(status().isNotFound());
        mvc.perform(json(post("/api/fields/" + aliceField + "/soil-records"), bob, """
                        {"sampleDate":"2026-09-02","nitrogen":1,"phosphorus":1,"potassium":1,"ph":7}
                        """))
                .andExpect(status().isNotFound());

        mvc.perform(auth(get("/api/fields/" + aliceField + "/soil-records"), alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void nonexistentAndForeignIdsGiveTheSameResponse() throws Exception {
        String foreign = mvc.perform(auth(get("/api/fields/" + aliceField), bob))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        mvc.perform(auth(get("/api/fields/999999"), bob))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Resource not found"));
        org.assertj.core.api.Assertions.assertThat(foreign).contains("\"title\":\"Resource not found\"");
    }
}
