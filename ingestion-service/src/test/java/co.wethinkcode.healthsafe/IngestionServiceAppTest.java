package co.wethinkcode.healthsafe;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.*;

public class IngestionServiceAppTest {

    private IngestionServiceApp app;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private static final int TEST_PORT = 7031; // Using a distinct port for tests
    private static final String BASE_URL = "http://localhost:" + TEST_PORT;

    @BeforeEach
    void setUp() {
        // Starts the application context cleanly before each test execution
        app = new IngestionServiceApp();
        app.start(TEST_PORT);
    }

    @AfterEach
    void tearDown() {
        // Releases the network port so successive test cases don't throw bind exceptions
        if (app != null) {
            app.stop();
        }
    }

    @Test
    void testHealthEndpointReturnsOK() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/health"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("OK", response.body());
    }

    @Test
    void testWardsEndpointReturnsJsonArray() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/wards"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertNotNull(response.body());
        // Simple assertion matching JSON array structure fallback or content validation
        assertTrue(response.body().startsWith("["));
        assertTrue(response.body().endsWith("]"));
    }

    @Test
    void testMissingResourceFileFallback() throws Exception {
        // Stop the default running test app setup
        app.stop();

        // Boot a temporary test instance with a completely broken filename path
        IngestionServiceApp brokenApp = new IngestionServiceApp("missing-file.csv");
        brokenApp.start(TEST_PORT);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/wards"))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(200, response.statusCode());
            assertEquals("[]", response.body()); // Should safely fallback to an empty JSON list
        } finally {
            brokenApp.stop();
        }
    }
}
