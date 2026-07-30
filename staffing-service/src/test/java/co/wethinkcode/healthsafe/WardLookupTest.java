package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.CleanWardRecord;
import co.wethinkcode.healthsafe.IngestionCleaningPipeline;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class WardLookupTest {

    private WardRepository repository;

    @BeforeEach
    void setUp() {
        IngestionCleaningPipeline pipeline = new IngestionCleaningPipeline();
        List<CleanWardRecord> wards = pipeline.clean(List.of(
                "W-05, East Wing, Paediatrics, 5",
                "W-06, West Wing, Cardiology, 8",
                "W-07, North Wing, Oncology, null"
        ));
        repository = new WardRepository(wards);
    }

    @Test
    @DisplayName("an existing ward ID resolves to the correct ward")
    void findsExistingWardById() {
        Optional<CleanWardRecord> result = repository.findById("W-05");

        assertTrue(result.isPresent());
        assertEquals("W-05", result.get().wardId());
        assertEquals("East Wing", result.get().wing());
        assertEquals("Paediatrics", result.get().department());
        assertEquals(5, result.get().bedsAvailable());
    }

    @Test
    @DisplayName("a ward with a null bedsAvailable (carried over from stage 1) is still returned, not hidden")
    void findsWardWithNullBedsAvailable() {
        Optional<CleanWardRecord> result = repository.findById("W-07");

        assertTrue(result.isPresent());
        assertNull(result.get().bedsAvailable());
    }

    @Test
    @DisplayName("an unknown ward ID returns empty, not null and not an exception")
    void unknownWardIdReturnsEmpty() {
        Optional<CleanWardRecord> result = assertDoesNotThrow(() -> repository.findById("W-99"));

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"w-05", "W-05", "w-05 ", " W-05"})
    @DisplayName("lookup is tolerant of case and surrounding whitespace, matching stage-1 canonicalization")
    void lookupIsCaseAndWhitespaceInsensitive(String requestedId) {
        Optional<CleanWardRecord> result = repository.findById(requestedId);

        assertTrue(result.isPresent(), "expected \"" + requestedId + "\" to resolve to W-05");
        assertEquals("W-05", result.get().wardId());
    }

    @NullAndEmptySource
    @ParameterizedTest
    @DisplayName("null or blank ID is treated as not-found, not an exception")
    void nullOrBlankIdIsNotFoundNotAnException(String requestedId) {
        Optional<CleanWardRecord> result = assertDoesNotThrow(() -> repository.findById(requestedId));
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("a completely empty ward list never throws on lookup")
    void emptyRepositoryLookupDoesNotThrow() {
        WardRepository empty = new WardRepository(List.of());

        Optional<CleanWardRecord> result = assertDoesNotThrow(() -> empty.findById("W-05"));
        assertTrue(result.isEmpty());
    }
}
