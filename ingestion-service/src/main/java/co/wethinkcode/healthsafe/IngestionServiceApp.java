package co.wethinkcode.healthsafe;

import io.javalin.Javalin;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public class IngestionServiceApp {
    private final Javalin app;
    private final String resourceFileName;

    // Default constructor for actual production execution
    public IngestionServiceApp() {
        this("wards-outdated.csv");
    }

    // Overloaded constructor allowing tests to supply distinct resource mock profiles
    public IngestionServiceApp(String resourceFileName) {
        this.resourceFileName = resourceFileName;
        this.app = Javalin.create();
        configureRoutes();
    }

    public void start(int port) {
        this.app.start(port);
    }

    public void stop() {
        this.app.stop();
    }

    private void configureRoutes() {
        List<CleanWardRecord> cleanedRecords = loadAndCleanRecords();

        app.get("/health", ctx -> ctx.result("OK"));
        app.get("/wards", ctx -> ctx.json(cleanedRecords));
    }

    private List<CleanWardRecord> loadAndCleanRecords() {
        IngestionCleaningPipeline pipeline = new IngestionCleaningPipeline();
        try (InputStream is = IngestionServiceApp.class.getClassLoader()
                .getResourceAsStream(resourceFileName)) {

            if (is == null) {
                throw new java.io.FileNotFoundException("Resource file '" + resourceFileName + "' not found!");
            }

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                List<String> rawLines = reader.lines().collect(Collectors.toList());
                if (!rawLines.isEmpty()) {
                    rawLines = rawLines.subList(1, rawLines.size());
                }
                return pipeline.clean(rawLines);
            }
        } catch (Exception e) {
            System.err.println("Fatal error loading resource file: " + e.getMessage());
            return List.of();
        }
    }

    public static void main(String[] args) {
        IngestionServiceApp service = new IngestionServiceApp();
        service.start(7030);
    }
}

