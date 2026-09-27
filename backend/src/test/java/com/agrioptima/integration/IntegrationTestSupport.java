package com.agrioptima.integration;

import com.jayway.jsonpath.JsonPath;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Full application context against in-memory H2 (MySQL mode) with the real Flyway migrations.
 * Tests share one database, so every test creates its own uniquely-named users.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
abstract class IntegrationTestSupport {

    @Autowired
    protected MockMvc mvc;

    protected static String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    /** Registers a fresh user and returns its bearer token. */
    protected String registerUser() throws Exception {
        return registerUser(uniqueEmail(), "secret123");
    }

    protected String registerUser(String email, String password) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Test Farmer","email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isCreated())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    protected static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder, String token) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    protected static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder builder, String token,
                                                        String body) {
        return auth(builder, token).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    protected long createFarm(String token, String name) throws Exception {
        MvcResult r = mvc.perform(json(post("/api/farms"), token, """
                        {"name":"%s","locationName":"Karnal, Haryana","latitude":29.685600,"longitude":76.990500}
                        """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(r);
    }

    protected long createField(String token, long farmId, String name) throws Exception {
        MvcResult r = mvc.perform(json(post("/api/farms/" + farmId + "/fields"), token, """
                        {"name":"%s","areaHa":2.5,"irrigationType":"IRRIGATED","season":"RABI"}
                        """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(r);
    }

    protected long createSoilRecord(String token, long fieldId, String sampleDate, double nitrogen) throws Exception {
        MvcResult r = mvc.perform(json(post("/api/fields/" + fieldId + "/soil-records"), token, """
                        {"sampleDate":"%s","nitrogen":%s,"phosphorus":18.5,"potassium":210,"ph":7.2,
                         "organicCarbon":0.55}
                        """.formatted(sampleDate, nitrogen)))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(r);
    }

    protected static long idOf(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }
}
