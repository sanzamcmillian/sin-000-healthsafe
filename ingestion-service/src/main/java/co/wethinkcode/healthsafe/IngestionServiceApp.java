package co.wethinkcode.healthsafe;

import io.javalin.Javalin;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

public class IngestionServiceApp {
    public static void main(String[] args) {
        // 1. Initialize the pipeline and load the file
        IngestionCleaningPipeline pipeline = new IngestionCleaningPipeline();
        List<CleanWardRecord> cleanedRecords;

        try (InputStream is = IngestionServiceApp.class.getClassLoader()
                .getResourceAsStream("wards-outdated.csv")) {

            if (is == null) {
                throw new java.io.FileNotFoundException("Resource file 'wards-outdated.csv' not found on the classpath!");
            }

            // Convert the stream of binary data cleanly into string data lines
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {

                List<String> rawLines = reader.lines().collect(Collectors.toList());
                // Ensure the file isn't completely empty first, then slice out the first element
                if (!rawLines.isEmpty()) {
                    rawLines = rawLines.subList(1, rawLines.size());
                }
                cleanedRecords = pipeline.clean(rawLines);

                System.out.println("Successfully clean-parsed " + cleanedRecords.size() + " records from resource file.");
            }
        } catch (Exception e) {
            System.err.println("Fatal error loading wards-outdated.csv file: " + e.getMessage());
            // Fallback to empty list so the service can still start up rather than crashing hard
            cleanedRecords = List.of();
        }

        // 2. Start the Javalin web framework server instance
        Javalin app = Javalin.create().start(7030);

        // Standard service health check endpoint
        app.get("/health", ctx -> ctx.result("OK"));

        // 3. Expose the cleaned datasets for consumer services
        // ctx.json() automatically transforms the list of records into a valid JSON array format
        List<CleanWardRecord> finalRecords = cleanedRecords;
        app.get("/wards", ctx -> ctx.json(finalRecords));
    }
}
