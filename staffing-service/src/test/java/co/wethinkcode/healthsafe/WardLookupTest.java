package co.wethinkcode.healthsafe;

/*
 * ASSUMED CONTRACT — adjust names to match your actual implementation.
 *
 * A class `WardRepository` (or wherever the in-memory cache of wards
 * fetched from ingestion-service lives) with:
 *
 *   WardRepository(List<Ward> wards)          // or however it's populated
 *   Optional<Ward> findById(String id)
 *
 * A `Ward` record/class with at least:
 *   String  getId()
 *   String  getWing()
 *   String  getDepartment()
 *   Integer getBedsAvailable()
 *
 * Since stage 1 canonicalizes ward IDs to one case (e.g. "W-05"), these
 * tests assume findById is tolerant of input casing too — i.e. a client
 * requesting /wards/w-05 should still resolve to the same ward as
 * /wards/W-05. If you decided lookups should be strictly case-sensitive
 * instead, drop the case-insensitivity tests and keep the rest.
 */

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
        repository = new WardRepository(List.of(
            new Ward("W-05", "East Wing", "Paediatrics", 5),
            new Ward("W-06", "West Wing", "Cardiology", 8),
            new Ward("W-07", "North Wing", "Oncology", null) // e.g. an unparseable bed count from stage 1
        ));
    }

    @Test
    @DisplayName("an existing ward ID resolves to the correct ward")
    void findsExistingWardById() {
        Optional<Ward> result = repository.findById("W-05");

        assertTrue(result.isPresent());
        assertEquals("W-05", result.get().getId());
        assertEquals("East Wing", result.get().getWing());
        assertEquals("Paediatrics", result.get().getDepartment());
        assertEquals(5, result.get().getBedsAvailable());
    }

    @Test
    @DisplayName("a ward with a null bedsAvailable (carried over from stage 1) is still returned, not hidden")
    void findsWardWithNullBedsAvailable() {
        Optional<Ward> result = repository.findById("W-07");

        assertTrue(result.isPresent());
        assertNull(result.get().getBedsAvailable());
    }

    @Test
    @DisplayName("an unknown ward ID returns empty, not null and not an exception")
    void unknownWardIdReturnsEmpty() {
        Optional<Ward> result = assertDoesNotThrow(() -> repository.findById("W-99"));

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"w-05", "W-05", "w-05 ", " W-05"})
    @DisplayName("lookup is tolerant of case and surrounding whitespace, matching stage-1 canonicalization")
    void lookupIsCaseAndWhitespaceInsensitive(String requestedId) {
        Optional<Ward> result = repository.findById(requestedId);

        assertTrue(result.isPresent(), "expected \"" + requestedId + "\" to resolve to W-05");
        assertEquals("W-05", result.get().getId());
    }

    @NullAndEmptySource
    @ParameterizedTest
    @DisplayName("null or blank ID is treated as not-found, not an exception")
    void nullOrBlankIdIsNotFoundNotAnException(String requestedId) {
        Optional<Ward> result = assertDoesNotThrow(() -> repository.findById(requestedId));
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("a completely empty ward list never throws on lookup")
    void emptyRepositoryLookupDoesNotThrow() {
        WardRepository empty = new WardRepository(List.of());

        Optional<Ward> result = assertDoesNotThrow(() -> empty.findById("W-05"));
        assertTrue(result.isEmpty());
    }
}
