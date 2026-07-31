package co.wethinkcode.healthsafe;

/*
 * ASSUMED CONTRACT — adjust to match your actual app setup.
 *
 *   Javalin app = StaffingServiceApp.create(
 *       wardServiceBaseUrl, alertLevelServiceBaseUrl, requestTimeout);
 *
 * (the Duration timeout param is a recommended addition if you don't
 * have one yet — without it, the "downstream unreachable" tests below
 * either hang or take as long as your hardcoded timeout for real.
 * Keeping it short, e.g. 300ms, keeps this whole suite fast.)
 *
 * Route under test: GET /schedule/{wardId}
 *   200 {"wardId": ..., "alertLevel": ..., "doctorCount": ...,
 *        "supervisorRequired": ...}          on success
 *   404                                       when ward-service 404s
 *   502 or 503                                when either downstream is
 *                                              unreachable, times out, or
 *                                              returns malformed JSON
 *
 * Fake ward-service is expected to expose GET /wards/{id} (matching
 * ward-service's real contract); fake alert-level-service exposes
 * GET /alert-level. Adjust paths/response shapes if yours differ.
 */

import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class StaffingServiceIntegrationTest {

    private static final Duration TEST_TIMEOUT = Duration.ofMillis(300);

    private Javalin fakeWardService;
    private Javalin fakeAlertLevelService;
    private Javalin staffingApp;
    private final HttpClient client = HttpClient.newHttpClient();

    @AfterEach
    void tearDown() {
        if (fakeWardService != null) fakeWardService.stop();
        if (fakeAlertLevelService != null) fakeAlertLevelService.stop();
        if (staffingApp != null) staffingApp.stop();
    }

    @Test
    @DisplayName("happy path: known ward + valid alert level produces a correct schedule")
    void happyPathReturnsSchedule() throws Exception {
        fakeWardService = fakeServer(app ->
            app.get("/wards/{id}", ctx -> ctx.json("""
                {"id":"W-05","wing":"East Wing","department":"Paediatrics","bedsAvailable":5}
                """)));

        fakeAlertLevelService = fakeServer(app ->
            app.get("/alert-level", ctx -> ctx.json("{\"level\": 6}")));

        startStaffingApp();

        HttpResponse<String> response = get("/schedule/W-05");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"doctorCount\":3") || response.body().contains("\"doctorCount\": 3"));
        assertTrue(response.body().toLowerCase().contains("supervisorrequired"));
    }

    @Test
    @DisplayName("ward-service 404 is passed through as staffing-service 404")
    void wardNotFoundPassesThrough404() throws Exception {
        fakeWardService = fakeServer(app ->
            app.get("/wards/{id}", ctx -> ctx.status(404)));

        fakeAlertLevelService = fakeServer(app ->
            app.get("/alert-level", ctx -> ctx.json("{\"level\": 2}")));

        startStaffingApp();

        HttpResponse<String> response = get("/schedule/W-99");

        assertEquals(404, response.statusCode());
    }

    @Test
    @DisplayName("ward-service unreachable returns a downstream-failure status, not a 500 or a hang")
    void wardServiceUnreachableReturnsDownstreamFailure() throws Exception {
        // never started -> nothing listening on this port
        int deadPort = findFreePort();
        fakeAlertLevelService = fakeServer(app ->
            app.get("/alert-level", ctx -> ctx.json("{\"level\": 2}")));

        staffingApp = StaffingServiceApp.create(
            "http://localhost:" + deadPort,
            "http://localhost:" + fakeAlertLevelService.port(),
            TEST_TIMEOUT);
        staffingApp.start(0);

        HttpResponse<String> response = get(staffingApp, "/schedule/W-05");

        assertTrue(response.statusCode() == 502 || response.statusCode() == 503,
            "expected 502/503 for unreachable ward-service, got " + response.statusCode());
    }

    @Test
    @DisplayName("alert-level-service unreachable returns a downstream-failure status, not a 500 or a hang")
    void alertLevelServiceUnreachableReturnsDownstreamFailure() throws Exception {
        fakeWardService = fakeServer(app ->
            app.get("/wards/{id}", ctx -> ctx.json("""
                {"id":"W-05","wing":"East Wing","department":"Paediatrics","bedsAvailable":5}
                """)));
        int deadPort = findFreePort();

        staffingApp = StaffingServiceApp.create(
            "http://localhost:" + fakeWardService.port(),
            "http://localhost:" + deadPort,
            TEST_TIMEOUT);
        staffingApp.start(0);

        HttpResponse<String> response = get(staffingApp, "/schedule/W-05");

        assertTrue(response.statusCode() == 502 || response.statusCode() == 503,
            "expected 502/503 for unreachable alert-level-service, got " + response.statusCode());
    }

    @Test
    @DisplayName("a downstream response that times out returns a downstream-failure status, not a hang")
    void downstreamTimeoutReturnsDownstreamFailure() throws Exception {
        fakeWardService = fakeServer(app ->
            app.get("/wards/{id}", ctx -> {
                try {
                    Thread.sleep(TEST_TIMEOUT.toMillis() * 5); // deliberately exceeds staffing-service's timeout
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
                ctx.json("""
                    {"id":"W-05","wing":"East Wing","department":"Paediatrics","bedsAvailable":5}
                    """);
            }));

        fakeAlertLevelService = fakeServer(app ->
            app.get("/alert-level", ctx -> ctx.json("{\"level\": 2}")));

        startStaffingApp();

        HttpResponse<String> response = get("/schedule/W-05");

        assertTrue(response.statusCode() == 502 || response.statusCode() == 503,
            "expected 502/503 on downstream timeout, got " + response.statusCode());
    }

    @Test
    @DisplayName("malformed JSON from a downstream service returns a downstream-failure status, not a 500")
    void malformedDownstreamJsonReturnsDownstreamFailure() throws Exception {
        fakeWardService = fakeServer(app ->
            app.get("/wards/{id}", ctx -> ctx.result("this is not json").contentType("application/json")));

        fakeAlertLevelService = fakeServer(app ->
            app.get("/alert-level", ctx -> ctx.json("{\"level\": 2}")));

        startStaffingApp();

        HttpResponse<String> response = get("/schedule/W-05");

        assertNotEquals(500, response.statusCode());
        assertTrue(response.statusCode() == 502 || response.statusCode() == 503,
            "expected 502/503 on malformed downstream body, got " + response.statusCode());
    }

    // ---------- helpers ----------

    private Javalin fakeServer(java.util.function.Consumer<Javalin> routes) {
        Javalin app = Javalin.create();
        routes.accept(app);
        app.start(0);
        return app;
    }

    private void startStaffingApp() {
        staffingApp = StaffingServiceApp.create(
            "http://localhost:" + fakeWardService.port(),
            "http://localhost:" + fakeAlertLevelService.port(),
            TEST_TIMEOUT);
        staffingApp.start(0);
    }

    private HttpResponse<String> get(String path) throws Exception {
        return get(staffingApp, path);
    }

    private HttpResponse<String> get(Javalin targetApp, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://localhost:" + targetApp.port() + path))
            .timeout(Duration.ofSeconds(3)) // generous client-side timeout; staffing-service's own
                                             // internal downstream timeout is what's under test
            .GET()
            .build();

        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private int findFreePort() throws Exception {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
