package co.wethinkcode.healthsafe;

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class WardEndpointTest {

    private Javalin app;
    private HttpClient client;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        IngestionCleaningPipeline pipeline = new IngestionCleaningPipeline();
        List<CleanWardRecord> records = pipeline.clean(List.of("W-05, East Wing, Paediatrics, 5"));
        WardRepository repository = new WardRepository(records);

        app = StaffingServiceApp.create(repository);
        app.start(0); // ephemeral port, avoids clashing with a real 7031 instance
        baseUrl = "http://localhost:" + app.port();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        app.stop();
    }

    @Test
    @DisplayName("GET /wards/{id} returns 200 and the correct ward for a known ID")
    void returnsWardForKnownId() throws Exception {
        HttpResponse<String> response = get("/wards/W-05");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("W-05"));
        assertTrue(response.body().contains("East Wing"));
        assertTrue(response.body().contains("Paediatrics"));
    }

    @Test
    @DisplayName("GET /wards/{id} is case-insensitive on the path parameter, consistent with stage-1 canonicalization")
    void returnsWardForDifferentlyCasedId() throws Exception {
        HttpResponse<String> response = get("/wards/w-05");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("W-05"));
    }

    @Test
    @DisplayName("GET /wards/{id} returns 404 for an unknown ward ID")
    void returns404ForUnknownId() throws Exception {
        HttpResponse<String> response = get("/wards/W-99");

        assertEquals(404, response.statusCode());
    }

    @Test
    @DisplayName("response content-type is JSON")
    void responseContentTypeIsJson() throws Exception {
        HttpResponse<String> response = get("/wards/W-05");

        String contentType = response.headers().firstValue("Content-Type").orElse("");
        assertTrue(contentType.contains("application/json"),
            "expected JSON content-type but got: " + contentType);
    }

    @Test
    @DisplayName("a malformed/empty path segment does not crash the server")
    void malformedIdDoesNotCrashServer() throws Exception {
        // trailing slash / empty id segment — behaviour is your call
        // (400 vs 404 are both defensible), but it must not be a 500 or a hang.
        HttpResponse<String> response = get("/wards/%20");

        assertNotEquals(500, response.statusCode());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .timeout(java.time.Duration.ofSeconds(2))
            .GET()
            .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
