package co.wethinkcode.healthsafe;

/*
 * ASSUMED CONTRACT — adjust to match your actual class/method/field names.
 *
 * A class `IngestionCleaningPipeline` (or wherever your "parse raw rows ->
 * cleaned records" logic lives) with:
 *
 *   List<CleanWardRecord> clean(List<String> csvDataLines)
 *       -> takes raw CSV data lines (no header), returns cleaned records.
 *          Must never throw on a malformed line — skip/flag it and keep going.
 *
 * A `CleanWardRecord` with (at minimum) these accessors:
 *   String  getWardId()
 *   String  getWing()
 *   String  getDepartment()
 *   Integer getBedsAvailable()   // null when unparseable
 *   String  getNotes()           // non-null explanation when something was flagged
 *
 * These tests deliberately use small inline CSV snippets rather than the
 * bundled wards-outdated.csv, so they stay fast, readable, and independent
 * of the real file's exact row count/contents. Add a separate smoke test
 * against the real resource file once this passes (see bottom of file).
 *

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IngestionCleaningPipelineTest {

    private final IngestionCleaningPipeline pipeline = new IngestionCleaningPipeline();

    @Test
    @DisplayName("the documented example row cleans to the documented shape")
    void documentedExampleRowCleansCorrectly() {
        List<String> rawLines = List.of("w-05,east wing ,PAEDIATRICS,five");

        List<CleanWardRecord> result = pipeline.clean(rawLines);

        assertEquals(1, result.size());
        CleanWardRecord record = result.get(0);
        assertEquals("W-05", record.getWardId());
        assertEquals("East Wing", record.getWing());
        assertEquals("Paediatrics", record.getDepartment());
        assertNull(record.getBedsAvailable(), "non-numeric bed count should be null, not 0 or crash");
        assertNotNull(record.getNotes(), "unparseable field should be flagged in notes");
    }

    @Test
    @DisplayName("a single malformed row does not abort parsing of the rest of the file")
    void malformedRowDoesNotCrashWholeParse() {
        List<String> rawLines = List.of(
            "w-01,west wing,Cardiology,10",
            "this,row,has,too,many,fields,for,the,schema",   // malformed
            "w-02,north wing,Oncology,8"
        );

        List<CleanWardRecord> result = assertDoesNotThrow(() -> pipeline.clean(rawLines));

        // exactly the two well-formed rows should have made it through
        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(r -> "W-01".equals(r.getWardId())));
        assertTrue(result.stream().anyMatch(r -> "W-02".equals(r.getWardId())));
    }

    @Test
    @DisplayName("a completely empty line is skipped, not treated as a malformed row error")
    void blankLineIsSkipped() {
        List<String> rawLines = List.of(
            "w-01,west wing,Cardiology,10",
            "",
            "w-02,north wing,Oncology,8"
        );

        List<CleanWardRecord> result = assertDoesNotThrow(() -> pipeline.clean(rawLines));
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("near-duplicate records for the same ward (differing ID casing/fields) collapse to one")
    void duplicateRecordsAreMerged() {
        List<String> rawLines = List.of(
            "W-05,East Wing,Paediatrics,5",
            "w-05,east  wing,paediatrics,unknown"   // same real ward, messier data
        );

        List<CleanWardRecord> result = pipeline.clean(rawLines);

        assertEquals(1, result.size(),
            "both rows describe the same real-world ward and should merge, not duplicate");
        assertEquals("W-05", result.get(0).getWardId());
    }

    @Test
    @DisplayName("genuinely distinct wards are never accidentally merged")
    void distinctWardsAreNotMerged() {
        List<String> rawLines = List.of(
            "W-05,East Wing,Paediatrics,5",
            "W-06,East Wing,Paediatrics,5"   // different ID, otherwise identical
        );

        List<CleanWardRecord> result = pipeline.clean(rawLines);

        assertEquals(2, result.size(), "different ward IDs must not be treated as duplicates");
    }

    @Test
    @DisplayName("an empty file produces an empty list, not an exception")
    void emptyFileProducesEmptyList() {
        List<CleanWardRecord> result = assertDoesNotThrow(() -> pipeline.clean(List.of()));
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("negative bed counts are flagged as invalid rather than accepted at face value")
    void negativeBedCountIsFlaggedNotAccepted() {
        List<String> rawLines = List.of("w-09,south wing,Neurology,-4");

        CleanWardRecord record = pipeline.clean(rawLines).get(0);

        assertNull(record.getBedsAvailable());
        assertNotNull(record.getNotes());
    }

    // ------------------------------------------------------------------
    // Smoke test against the real bundled file. Loose on purpose: it just
    // guards against "the whole pipeline throws on the actual input," which
    // is the single most important behaviour per the stage-1 brief.
    // Wire up the real file path once your resource loading is in place.
    // ------------------------------------------------------------------
    // @Test
    // @DisplayName("the real wards-outdated.csv parses fully without throwing")
    // void realFileParsesWithoutThrowing() throws Exception {
    //     List<String> lines = Files.readAllLines(
    //         Path.of("src/main/resources/wards-outdated.csv"));
    //     List<CleanWardRecord> result = assertDoesNotThrow(() -> pipeline.clean(lines));
    //     assertFalse(result.isEmpty());
    // }
}*/