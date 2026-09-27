package com.agrioptima.ml;

import com.agrioptima.ml.MlContracts.OptimizeRequest;
import com.agrioptima.ml.MlContracts.OptimizeResponse;
import com.agrioptima.ml.MlContracts.PredictRequest;
import com.agrioptima.ml.MlContracts.PredictResponse;
import com.agrioptima.ml.MlContracts.WeatherInfo;
import com.agrioptima.ml.MlServiceException.Kind;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpTimeoutException;
import java.util.function.Predicate;

/** HTTP client for the FastAPI ML service: {@code POST /optimize}, {@code POST /predict-yield} and {@code GET /weather}. */
@Component
public class MlServiceClient {

    private static final Logger log = LoggerFactory.getLogger(MlServiceClient.class);
    private static final String PROBLEM_PREFIX = "urn:agrioptima:problem:";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public MlServiceClient(RestClient.Builder builder, MlServiceProperties properties, ObjectMapper objectMapper) {
        // java.net.http client: HttpURLConnection silently re-sends a POST after a read timeout; this one does not
        HttpClient http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(properties.readTimeout());
        this.restClient = builder.baseUrl(properties.baseUrl()).requestFactory(factory).build();
        this.objectMapper = objectMapper;
    }

    public OptimizeResponse optimize(OptimizeRequest request) {
        OptimizeResponse response = post("/optimize", "optimization", request, OptimizeResponse.class);
        require(response, r -> r.status() != null && r.plans() != null && r.requirementKgHa() != null,
                "optimization", "missing status/plans/requirement");
        return response;
    }

    public PredictResponse predictYield(PredictRequest request) {
        PredictResponse response = post("/predict-yield", "yield prediction", request, PredictResponse.class);
        require(response, r -> r.modelVersion() != null && r.predictions() != null
                        && r.predictions().size() == request.scenarios().size()
                        && r.predictions().stream().allMatch(p -> p.predictedYieldTHa() != null
                        && Double.isFinite(p.predictedYieldTHa()) && p.predictedYieldTHa() >= 0
                        && p.index() >= 0 && p.index() < request.scenarios().size()),
                "yield prediction", "missing model version, wrong number of predictions or invalid yield/index");
        return response;
    }

    public WeatherInfo fetchWeather(double lat, double lon) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/weather").queryParam("lat", lat).queryParam("lon", lon).build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(WeatherInfo.class);
        } catch (Exception e) {
            log.warn("Failed to fetch weather from ML service for ({}, {}): {}", lat, lon, e.getMessage());
            return null;
        }
    }

    public MlContracts.SoilGridsResponse fetchSoilGrids(double lat, double lon) {
        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/soilgrids").queryParam("lat", lat).queryParam("lon", lon).build())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(MlContracts.SoilGridsResponse.class);
        } catch (Exception e) {
            log.warn("Failed to fetch soilgrids from ML service for ({}, {}): {}", lat, lon, e.getMessage());
            return null;
        }
    }

    public byte[] generatePdf(String jsonPayload) {
        try {
            return restClient.post()
                    .uri("/generate-pdf")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_PDF)
                    .body(jsonPayload)
                    .retrieve()
                    .body(byte[].class);
        } catch (Exception e) {
            log.error("Failed to generate PDF from ML service: {}", e.getMessage());
            throw new RuntimeException("Failed to generate prescription PDF", e);
        }
    }

    private <T> T post(String path, String operation, Object body, Class<T> type) {
        try {
            return restClient.post().uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        String type_ = problemType(res.getBody());
                        throw new MlServiceException(Kind.ERROR_RESPONSE, operation, res.getStatusCode().value(),
                                type_, "ML " + path + " returned " + res.getStatusCode().value() + " " + type_, null);
                    })
                    .body(type);
        } catch (MlServiceException e) {
            log.warn("{}", e.logDetail());
            throw e;
        } catch (ResourceAccessException e) {
            boolean timeout = hasCause(e, SocketTimeoutException.class) || hasCause(e, HttpTimeoutException.class);
            Kind kind = timeout ? Kind.TIMEOUT : Kind.UNAVAILABLE;
            log.warn("ML {} failed: {} ({})", path, kind, e.getMessage());
            throw new MlServiceException(kind, operation, 0, null, e.getMessage(), e);
        } catch (RestClientException e) {
            log.warn("ML {} returned an unreadable body: {}", path, e.getMessage());
            throw new MlServiceException(Kind.INVALID_RESPONSE, operation, 0, null, e.getMessage(), e);
        }
    }

    private static <T> void require(T response, Predicate<T> valid, String operation, String what) {
        if (response == null || !valid.test(response)) {
            log.warn("ML {} response invalid: {}", operation, what);
            throw new MlServiceException(Kind.INVALID_RESPONSE, operation, 200, null, what, null);
        }
    }

    private String problemType(InputStream body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            String type = node == null ? null : node.path("type").asText(null);
            return type != null && type.startsWith(PROBLEM_PREFIX) ? type.substring(PROBLEM_PREFIX.length()) : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static boolean hasCause(Throwable t, Class<? extends Throwable> type) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (type.isInstance(c)) {
                return true;
            }
        }
        return false;
    }
}
