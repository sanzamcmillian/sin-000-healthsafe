package co.wethinkcode.healthsafe;

/*
 * ASSUMED CONTRACT — adjust names to match your actual implementation.
 *
 * A class `AlertLevelStore` (or wherever the in-memory state + validation
 * logic lives, separate from the Javalin route handler) with:
 *
 *   AlertLevelStore()                 // starts at a default, assumed 0 below
 *   int getLevel()
 *   boolean setLevel(int newLevel)    // returns true if accepted & applied,
 *                                     // false if rejected (out of range) —
 *                                     // adjust if you instead throw an
 *                                     // exception on invalid input; either
 *                                     // is defensible, just be consistent
 *                                     // with what the route handler expects.
 *
 * Valid range assumed inclusive: 0–8.
 */

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class AlertLevelStoreTest {

    private AlertLevelStore store;

    @BeforeEach
    void setUp() {
        store = new AlertLevelStore();
    }

    @Test
    @DisplayName("default level on startup is a valid, defined value")
    void defaultLevelIsValid() {
        int level = store.getLevel();
        assertTrue(level >= 0 && level <= 8, "default level should already be in valid range");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 4, 7, 8})
    @DisplayName("every value within 0-8 inclusive is accepted")
    void inRangeValuesAreAccepted(int level) {
        boolean accepted = store.setLevel(level);

        assertTrue(accepted);
        assertEquals(level, store.getLevel());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, -100, 9, 15, Integer.MAX_VALUE, Integer.MIN_VALUE})
    @DisplayName("values outside 0-8 are rejected and do not mutate state")
    void outOfRangeValuesAreRejected(int invalidLevel) {
        store.setLevel(5); // known-good baseline
        boolean accepted = store.setLevel(invalidLevel);

        assertFalse(accepted);
        assertEquals(5, store.getLevel(), "state must be unchanged after a rejected update");
    }

    @Test
    @DisplayName("boundary values 0 and 8 are both valid, not off-by-one excluded")
    void boundaryValuesAreInclusive() {
        assertTrue(store.setLevel(0));
        assertEquals(0, store.getLevel());

        assertTrue(store.setLevel(8));
        assertEquals(8, store.getLevel());
    }

    @Test
    @DisplayName("repeated valid updates each take effect in sequence")
    void sequentialValidUpdatesApplyInOrder() {
        store.setLevel(2);
        assertEquals(2, store.getLevel());

        store.setLevel(6);
        assertEquals(6, store.getLevel());

        store.setLevel(0);
        assertEquals(0, store.getLevel());
    }

    @Test
    @DisplayName("a rejected update following a valid one leaves the last valid value intact")
    void rejectedUpdateAfterValidOneIsNoOp() {
        store.setLevel(3);
        store.setLevel(42); // rejected

        assertEquals(3, store.getLevel());
    }
}
