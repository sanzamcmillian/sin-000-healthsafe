package co.wethinkcode.healthsafe;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.json.JavalinJackson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class StaffingServiceApp {

    private static final String CLEANING_SERVICE_URL = "http://localhost:7030/wards";

    public static void main(String[] args) {
        WardRepository repository = new WardRepository(List.of());

        System.out.println("Fetching canonical data from ingestion service...");
        String cleanJsonData = fetchCleanedData(CLEANING_SERVICE_URL);

        if (cleanJsonData != null) {
            repository.loadDataFromJson(cleanJsonData);
            System.out.println("Repository successfully populated from Service 1.");
        } else {
            System.err.println("Warning: Starting up with an empty repository due to fetch failure.");
        }

        Javalin app = StaffingServiceApp.create(repository).start(7033);

        // TODO (Provides on-call schedules for doctors based on ward and status.)
        // Add domain endpoints for staffing-service here.
    }

    /**
     * Standard Java HTTP client to safely query Service 1
     */
    private static String fetchCleanedData(String url) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                return response.body();
            } else {
                System.err.println("Cleaning service returned status: " + response.statusCode());
                return null;
            }
        } catch (Exception e) {
            System.err.println("Could not connect to cleaning service: " + e.getMessage());
            return null;
        }
    }

    public static Javalin create(WardRepository repository) {
        // Explicitly set up Jackson to ensure JSON keys match the record's getter names
        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(new ObjectMapper()));
        });

        // Register the routing endpoints
        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/wards/{id}", ctx -> handleGetWardById(ctx, repository));

        return app;
    }

    /**
     * Handles the GET /wards/{id} route.
     */
    private static void handleGetWardById(Context ctx, WardRepository repository) {
        String idParam = ctx.pathParam("id");

        // Return 404 immediately for explicitly empty, blank, or whitespace-only paths
        if (idParam.isBlank()) {
            ctx.status(404).result("Ward not found");
            return;
        }

        // Rely on repository implementation to handle case-insensitivity and trim checks safely
        Optional<CleanWardRecord> wardOpt = repository.findById(idParam);

        if (wardOpt.isPresent()) {
            // Automatically maps the record fields to matching JSON properties with a 200 OK status
            ctx.json(wardOpt.get());
        } else {
            ctx.status(404).result("Ward not found");
        }
    }
}

// MQ TODO: publishes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.healthsafe.mq.MqConfig)
