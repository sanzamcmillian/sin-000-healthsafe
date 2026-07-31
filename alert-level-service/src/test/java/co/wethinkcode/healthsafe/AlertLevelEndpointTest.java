package co.wethinkcode.healthsafe;

/*
 * ASSUMED CONTRACT — adjust to match your actual app setup.
 *
 *   Javalin app = AlertLevelServiceApp.create(alertLevelStore);
 *
 * Routes under test:
 *   GET /alert-level        -> 200 {"level": n}
 *   PUT /alert-level        -> body {"level": n}
 *                              200 {"level": n} on success
 *                              400 on out-of-range or malformed input,
 *                              store left unchanged
 *
 * If you chose POST instead of PUT, or a different body/response shape
 * (e.g. bare integer instead of {"level": n}), update the constants/
 * helper methods below — the test *behaviour* being checked stays the same.
 */

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class AlertLevelEndpointTest {

    private Javalin app;
    private HttpClient client;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        AlertLevelStore store = new AlertLevelStore();
        app = AlertLevelServiceApp.create(store);
        app.start(0); // ephemeral port, avoids clashing with a real 7032 instance
        baseUrl = "http://localhost:" + app.port();
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        app.stop();
    }

    @Test
    @DisplayName("GET /alert-level returns 200 and a valid level on a fresh service")
    void getReturnsDefaultLevel() throws Exception {
        HttpResponse<String> response = get();

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"level\""));
    }

    @Test
    @DisplayName("PUT /alert-level with a valid level updates state and is reflected by a subsequent GET")
    void putValidLevelUpdatesState() throws Exception {
        HttpResponse<String> putResponse = put("{\"level\": 6}");
        assertEquals(200, putResponse.statusCode());
        assertTrue(putResponse.body().contains("6"));

        HttpResponse<String> getResponse = get();
        assertTrue(getResponse.body().contains("6"));
    }

    @Test
    @DisplayName("PUT /alert-level with an out-of-range value returns 400 and does not change state")
    void putOutOfRangeLevelReturns400() throws Exception {
        put("{\"level\": 3}"); // known-good baseline

        HttpResponse<String> putResponse = put("{\"level\": 99}");
        assertEquals(400, putResponse.statusCode());

        HttpResponse<String> getResponse = get();
        assertTrue(getResponse.body().contains("3"), "state should still reflect the last VALID update");
    }

    @Test
    @DisplayName("PUT /alert-level with a negative value returns 400")
    void putNegativeLevelReturns400() throws Exception {
        HttpResponse<String> response = put("{\"level\": -1}");
        assertEquals(400, response.statusCode());
    }

    @Test
    @DisplayName("PUT /alert-level with malformed JSON returns 400, not 500")
    void putMalformedJsonReturns400NotServerError() throws Exception {
        HttpResponse<String> response = put("{not valid json");

        assertEquals(400, response.statusCode());
    }

    @Test
    @DisplayName("PUT /alert-level with a missing 'level' field returns 400, not 500")
    void putMissingLevelFieldReturns400() throws Exception {
        HttpResponse<String> response = put("{}");

        assertEquals(400, response.statusCode());
    }

    @Test
    @DisplayName("PUT /alert-level with a non-integer 'level' value returns 400, not 500")
    void putNonIntegerLevelReturns400() throws Exception {
        HttpResponse<String> response = put("{\"level\": \"high\"}");

        assertEquals(400, response.statusCode());
    }

    private HttpResponse<String> get() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/alert-level"))
            .timeout(Duration.ofSeconds(2))
            .GET()
            .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> put(String jsonBody) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/alert-level"))
            .timeout(Duration.ofSeconds(2))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(jsonBody))
            .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
