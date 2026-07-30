package co.wethinkcode.healthsafe;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WardDataNormalizerTest {

    // ---------- normalizePlaceholder ----------

    @ParameterizedTest(name = "\"{0}\" is treated as a missing-value placeholder")
    @ValueSource(strings = {"N/A", "n/a", "TBD", "unknown", "-", "NaN", "", "   "})
    @DisplayName("recognizes every documented placeholder token")
    void placeholderTokensBecomeNull(String raw) {
        assertNull(WardDataNormalizer.normalizePlaceholder(raw));
    }

    @NullAndEmptySource
    @ParameterizedTest
    @DisplayName("null input is treated as missing, not a crash")
    void nullInputIsMissing(String raw) {
        assertNull(WardDataNormalizer.normalizePlaceholder(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {"Active", "PAEDIATRICS", "5"})
    @DisplayName("real values pass through placeholder check untouched")
    void realValuesAreNotFlaggedAsPlaceholders(String raw) {
        assertEquals(raw, WardDataNormalizer.normalizePlaceholder(raw));
    }

    // ---------- normalizeText ----------

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource({
        "'east wing ',        'East Wing'",
        "'  west  wing',      'West Wing'",
        "'ICU  Ward',         'Icu Ward'",   // double space collapsed regardless of casing rule
        "'PAEDIATRICS',       'Paediatrics'",
        "'paediatrics',       'Paediatrics'",
    })
    @DisplayName("trims, collapses double spaces, applies one consistent case")
    void textNormalizationIsConsistent(String raw, String expected) {
        assertEquals(expected, WardDataNormalizer.normalizeText(raw));
    }

    @ParameterizedTest(name = "ward id \"{0}\" -> \"{1}\"")
    @CsvSource({
        "w-05,  W-05",
        "W-05,  W-05",
        "w-05 , W-05",
    })
    @DisplayName("ward IDs normalize to one canonical case regardless of input casing")
    void wardIdCasingIsCanonical(String raw, String expected) {
        assertEquals(expected, WardDataNormalizer.normalizeText(raw));
    }

    // ---------- normalizeNumber ----------

    @ParameterizedTest
    @ValueSource(strings = {"five", "full", "many", "N/A", "-", ""})
    @DisplayName("non-numeric / spelled-out values become null, never throw")
    void nonNumericValuesBecomeNull(String raw) {
        assertNull(WardDataNormalizer.normalizeNumber(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {"-3", "-1"})
    @DisplayName("negative bed counts are invalid and flagged as null, not silently accepted")
    void negativeCountsAreRejected(String raw) {
        assertNull(WardDataNormalizer.normalizeNumber(raw));
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
        "5,   5",
        "05,  5",
        "0,   0",
        " 12, 12",
    })
    @DisplayName("valid numeric strings parse correctly, including zero and leading zero")
    void validNumbersParseCorrectly(String raw, int expected) {
        assertEquals(expected, WardDataNormalizer.normalizeNumber(raw));
    }

    // ---------- normalizeBoolean ----------

    @ParameterizedTest
    @ValueSource(strings = {"Y", "y", "yes", "YES", "true", "TRUE", "1"})
    @DisplayName("all truthy variants normalize to Boolean.TRUE")
    void truthyVariantsNormalizeToTrue(String raw) {
        assertEquals(Boolean.TRUE, WardDataNormalizer.normalizeBoolean(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {"N", "n", "no", "NO", "false", "FALSE", "0"})
    @DisplayName("all falsy variants normalize to Boolean.FALSE")
    void falsyVariantsNormalizeToFalse(String raw) {
        assertEquals(Boolean.FALSE, WardDataNormalizer.normalizeBoolean(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {"maybe", "N/A", "", "2"})
    @DisplayName("unrecognized boolean-ish values become null rather than a guess")
    void unrecognizedBooleanValuesBecomeNull(String raw) {
        assertNull(WardDataNormalizer.normalizeBoolean(raw));
    }

    // ---------- normalizeDate ----------

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource({
        "2024-03-07,   2024-03-07",
        "03/07/2024,   2024-03-07",   // MM/DD/YYYY
        "07-03-2024,   2024-03-07",   // DD-MM-YYYY
        "2024-3-7,     2024-03-07",   // one-digit month/day
        "9/4/2024,     2024-09-04",
    })
    @DisplayName("recognizes every documented date format and normalizes to one internal representation")
    void knownDateFormatsParse(String raw, String expectedIso) {
        assertEquals(LocalDate.parse(expectedIso), WardDataNormalizer.normalizeDate(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2024-13-40", "31/02/2024", "not-a-date", "0000-00-00", ""})
    @DisplayName("invalid or nonsensical dates return null instead of throwing")
    void invalidDatesReturnNullNotException(String raw) {
        assertNull(WardDataNormalizer.normalizeDate(raw));
    }
}
