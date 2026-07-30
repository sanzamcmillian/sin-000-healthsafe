package co.wethinkcode.healthsafe;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class IngestionCleaningPipeline {

    /**
     * Parses raw CSV data lines (no header), normalizes values, and deduplicates records.
     * Malformed rows are safely skipped or flagged without aborting execution.
     */
    public List<CleanWardRecord> clean(List<String> csvDataLines) {
        if (csvDataLines == null || csvDataLines.isEmpty()) {
            return new ArrayList<>();
        }

        // Using LinkedHashMap keeps the final output ordered by first occurrence of a ward ID
        Map<String, CleanWardRecord> mergedRecords = new LinkedHashMap<>();

        for (String line : csvDataLines) {
            // Rule 1: A completely empty line is skipped, not treated as a malformed row error
            if (line == null || line.trim().isEmpty()) {
                continue;
            }

            // Simple split logic matching standard inline test formats
            String[] tokens = line.split(",");

            // Rule 2: A row with unexpected structure/field counts is completely malformed and skipped
            if (tokens.length != 4) {
                continue;
            }

            // Normalize the key identifiers using our normalizer rules
            String rawWardId = tokens[0];
            String normalizedWardId = WardDataNormalizer.normalizeText(WardDataNormalizer.normalizePlaceholder(rawWardId));

            // If the primary identifying key is fundamentally missing/null, skip the record
            if (normalizedWardId == null || normalizedWardId.isEmpty()) {
                continue;
            }

            // Process and normalize other fields
            String normalizedWing = WardDataNormalizer.normalizeText(WardDataNormalizer.normalizePlaceholder(tokens[1]));
            String normalizedDept = WardDataNormalizer.normalizeText(WardDataNormalizer.normalizePlaceholder(tokens[2]));

            // Numbers can fail parsing safely to null via normalizer wrapper
            String rawBeds = tokens[3];
            Integer normalizedBeds = WardDataNormalizer.normalizeNumber(WardDataNormalizer.normalizePlaceholder(rawBeds));

            // Establish flags and notes if values were unparseable or negative
            StringBuilder notesBuilder = new StringBuilder();
            if (normalizedBeds == null && WardDataNormalizer.normalizePlaceholder(rawBeds) != null) {
                notesBuilder.append("Unparseable or invalid bed count flagged: '").append(rawBeds.trim()).append("'.");
            }

            String finalNotes = !notesBuilder.isEmpty() ? notesBuilder.toString() : null;

            CleanWardRecord currentRecord = new CleanWardRecord(
                    normalizedWardId,
                    normalizedWing,
                    normalizedDept,
                    normalizedBeds,
                    finalNotes
            );

            // Rule 3: Near-duplicate records collapse to one. Newer rows overwrite or merge into previous ones.
            // If the incoming data point contains cleaner fields, we preserve the highest quality info.
            if (mergedRecords.containsKey(normalizedWardId)) {
                CleanWardRecord existingRecord = mergedRecords.get(normalizedWardId);

                // Merge logic: resolve null/missing metrics with the best version between the duplicates
                Integer resolvedBeds = (existingRecord.bedsAvailable() != null) ? existingRecord.bedsAvailable() : currentRecord.bedsAvailable();
                String resolvedNotes = (existingRecord.notes() != null) ? existingRecord.notes() : currentRecord.notes();

                CleanWardRecord mergedRecord = new CleanWardRecord(
                        normalizedWardId,
                        existingRecord.wing() != null ? existingRecord.wing() : normalizedWing,
                        existingRecord.department() != null ? existingRecord.department() : normalizedDept,
                        resolvedBeds,
                        resolvedNotes
                );
                mergedRecords.put(normalizedWardId, mergedRecord);
            } else {
                mergedRecords.put(normalizedWardId, currentRecord);
            }
        }

        return new ArrayList<>(mergedRecords.values());
    }
}
