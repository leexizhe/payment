package com.ledger;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base for controller-level integration tests: boots the full app on a random
 * port against the Testcontainers Postgres (Liquibase applies schema + seed
 * data), sends raw JSON over HTTP, and asserts on the wire response.
 * 4xx/5xx never throw — tests assert on the ResponseEntity instead.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.tracing.sampling.probability=0")
public abstract class BaseControllerIT extends TestContainerConfig {

    protected static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP =
            new ParameterizedTypeReference<>() {
            };

    @LocalServerPort
    private int port;

    protected RestClient http;

    @BeforeEach
    void initRestClient() {
        http = RestClient.builder()
                .baseUrl(serverUrl(controllerBasePath()))
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    // deliberately empty: never throw, let the test assert the status
                })
                .build();
    }

    /** e.g. "/api/payments" — relative endpoints in helpers resolve under it. */
    protected abstract String controllerBasePath();

    protected String serverUrl(String path) {
        return "http://localhost:" + port + path;
    }

    protected <T> ResponseEntity<T> getRequest(String endpoint, ParameterizedTypeReference<T> type) {
        return http.get().uri(endpoint).retrieve().toEntity(type);
    }

    protected <T> ResponseEntity<T> postRequest(String endpoint, String jsonBody,
                                                ParameterizedTypeReference<T> type) {
        return postRequest(endpoint, jsonBody, Map.of(), type);
    }

    protected <T> ResponseEntity<T> postRequest(String endpoint, String jsonBody,
                                                Map<String, String> headers,
                                                ParameterizedTypeReference<T> type) {
        return http.post().uri(endpoint)
                .headers(h -> headers.forEach(h::add))
                .contentType(MediaType.APPLICATION_JSON)
                .body(jsonBody)
                .retrieve()
                .toEntity(type);
    }

    protected static void assertSuccess(ResponseEntity<?> response) {
        assertThat(response.getStatusCode().is2xxSuccessful())
                .as("expected 2xx but got %s with body %s", response.getStatusCode(), response.getBody())
                .isTrue();
    }

    /** Asserts HTTP status and the RFC-9457 ProblemDetail body shape in one call. */
    protected static void assertProblem(ResponseEntity<Map<String, Object>> response, HttpStatus expected) {
        assertThat(response.getStatusCode()).isEqualTo(expected);
        assertThat(response.getBody())
                .as("ProblemDetail body")
                .isNotNull()
                .containsEntry("status", expected.value())
                .containsKeys("title", "detail");
    }
}
