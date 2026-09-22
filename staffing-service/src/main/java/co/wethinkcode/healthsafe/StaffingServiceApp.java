package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.mq.MqConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.json.JavalinJackson;
import jakarta.jms.*;
import org.apache.activemq.ActiveMQConnectionFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class StaffingServiceApp {

    private static final String CLEANING_SERVICE_URL = "http://localhost:7030/wards";

    private static Connection mqConnection;
    private static Session mqSession;
    private static MessageProducer scheduleEventProducer;

    public static void main(String[] args) {
        try {
            initMqPubisher();
            System.out.println("staffingServiceApp ActiveMQ publisher initialized.");
        } catch (Exception e) {
            System.err.println("Failed to initialize ActiveMQ publisher: " + e.getMessage());
        }

        WardRepository repository = new WardRepository(List.of());

        System.out.println("Fetching canonical data from ingestion service...");
        String cleanJsonData = fetchCleanedData(CLEANING_SERVICE_URL);

        if (cleanJsonData != null) {
            repository.loadDataFromJson(cleanJsonData);
            System.out.println("Repository successfully populated from Service 1.");
        } else {
            System.err.println("Warning: Starting up with an empty repository due to fetch failure.");
        }

        Javalin app = StaffingServiceApp.create(
                "http://localhost:7031",
                "http://localhost:7032",
                Duration.ofSeconds(5)
        ).start(7033);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            closeMqPublisher();
            app.stop();
        }));

        // TODO (Provides on-call schedules for doctors based on ward and status.)
        // Add domain endpoints for staffing-service here.
    }

    private static void initMqPubisher() throws JMSException {
        ConnectionFactory factory  = new ActiveMQConnectionFactory(MqConfig.BROKER_URL);
        mqConnection = factory.createConnection();
        mqConnection.start();

        mqSession = mqConnection.createSession(false, Session.AUTO_ACKNOWLEDGE);
        Topic topic = mqSession.createTopic(MqConfig.TOPIC);
        scheduleEventProducer = mqSession.createProducer(topic);
    }

    private static void publishScheduleEvent(String wardId, int alertLevel, int doctorCount, boolean supervisorRequired) {
        if (mqSession == null || scheduleEventProducer == null) {
            System.err.println("MQ Publisher not ready, skipping broadcast.");
            return;
        }

        try {
            String payload = String.format(
                    "{\"wardId\":\"%s\",\"alertLevel\":%d,\"doctorCount\":%d,\"supervisorRequired\":%b,\"timestamp\":\"%s\"}",
                    wardId, alertLevel, doctorCount, supervisorRequired, Instant.now()
            );

            TextMessage message = mqSession.createTextMessage(payload);
            scheduleEventProducer.send(message);
            System.out.println("Published schedule update to topic for ward: " + wardId);
        } catch (Exception e) {
            System.err.println("Failed to publish schedule update: " + e.getMessage());
        }
    }

    private static void closeMqPublisher() {
        try {
            if ( scheduleEventProducer != null ) scheduleEventProducer.close();
            if ( mqSession != null ) mqSession.close();
            if ( mqConnection != null ) mqConnection.close();
        } catch (Exception e) {
            System.err.println("Error closing MQ producer: " + e.getMessage());
        }
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

    /**
     * Core application runner that coordinates downstream microservice workflows.
     */
    public static Javalin create(String wardServiceUrl, String alertLevelServiceUrl, Duration timeout) {
        ObjectMapper mapper = new ObjectMapper();

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(timeout)
                .build();

        Javalin app = Javalin.create(config -> {
            config.jsonMapper(new JavalinJackson(mapper));
        });

        // Main integration endpoint route under test
        app.get("/schedule/{wardId}", ctx -> {
            String wardId = ctx.pathParam("wardId");

            // 1. Query the downstream Ward Service
            String wardRawUrl = wardServiceUrl + "/wards/" + wardId;
            HttpResponse<String> wardResponse;
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(wardRawUrl))
                        .timeout(timeout)
                        .GET()
                        .build();
                wardResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                ctx.status(503).result("Ward service down or timed out");
                return;
            }

            // Propagate 404 errors transparently
            if (wardResponse.statusCode() == 404) {
                ctx.status(404).result("Ward not found");
                return;
            }

            // Fallback status for unexpected downstream failures
            if (wardResponse.statusCode() != 200) {
                ctx.status(502).result("Bad gateway from ward service");
                return;
            }

            // 2. Query the downstream Alert Level Service
            String alertRawUrl = alertLevelServiceUrl + "/alert-level";
            HttpResponse<String> alertResponse;
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(alertRawUrl))
                        .timeout(timeout)
                        .GET()
                        .build();
                alertResponse = client.send(request, HttpResponse.BodyHandlers.ofString());
            } catch (Exception e) {
                ctx.status(503).result("Alert service down or timed out");
                return;
            }

            if (alertResponse.statusCode() != 200) {
                ctx.status(502).result("Bad gateway from alert service");
                return;
            }

            // 3. Process data structures and map business rules
            try {
                JsonNode wardNode = mapper.readTree(wardResponse.body());
                JsonNode alertNode = mapper.readTree(alertResponse.body());

                // Guard against syntactically correct JSON that lacks our specific properties
                if (!wardNode.has("wardId") || !alertNode.has("level")) {
                    ctx.status(502).result("Incomplete fields from downstream payload");
                    return;
                }

                String confirmedWardId = wardNode.get("wardId").asText();
                int currentLevel = alertNode.get("level").asInt();

                // Generate business rules schedule plan
                StaffingPlan plan = StaffingScheduler.computeSchedule(currentLevel);

                publishScheduleEvent(
                        confirmedWardId,
                        currentLevel,
                        plan.getDoctorCount(),
                        plan.isSupervisorRequired()
                );

                // Build output payload view
                WardScheduleResponse combinedPlan = new WardScheduleResponse(
                        confirmedWardId,
                        currentLevel,
                        plan.getDoctorCount(),
                        plan.isSupervisorRequired()
                );

                ctx.json(combinedPlan);

            } catch (Exception parseException) {
                // Catch malformed json text structures safely to avoid returning 500 errors
                ctx.status(502).result("Malformed response schema error");
            }
        });

        return app;
    }
}


// MQ TODO: publishes to ActiveMQ topic MqConfig.TOPIC at MqConfig.BROKER_URL (see co.wethinkcode.healthsafe.mq.MqConfig)
