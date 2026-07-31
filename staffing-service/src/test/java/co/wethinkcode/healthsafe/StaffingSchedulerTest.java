package co.wethinkcode.healthsafe;

/*
 * ASSUMED CONTRACT — adjust names/thresholds to match your actual rule.
 *
 * A class `StaffingScheduler` with a pure static (or instance, doesn't
 * matter) method:
 *
 *   StaffingPlan computeSchedule(int alertLevel)
 *
 * A `StaffingPlan` with:
 *   int     getDoctorCount()
 *   boolean isSupervisorRequired()
 *
 * Illustrative rule assumed below (swap your own thresholds/counts,
 * the test structure stays the same either way):
 *   level 0-2  -> 1 doctor,  no supervisor
 *   level 3-5  -> 2 doctors, no supervisor
 *   level 6-8  -> 3 doctors, supervisor required
 *
 * computeSchedule is assumed to only ever be called with an already-
 * validated 0-8 level (alert-level-service enforces that range), so
 * out-of-range input isn't this class's job — that's already covered
 * by AlertLevelStoreTest. If you'd rather have this method defensively
 * validate too, add equivalent out-of-range cases here.
 */

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class StaffingSchedulerTest {

    @ParameterizedTest(name = "level {0} -> {1} doctor(s), supervisor={2}")
    @CsvSource({
        "0, 1, false",
        "1, 1, false",
        "2, 1, false",
        "3, 2, false",
        "4, 2, false",
        "5, 2, false",
        "6, 3, true",
        "7, 3, true",
        "8, 3, true",
    })
    @DisplayName("each level maps to the documented staffing plan")
    void levelMapsToExpectedPlan(int level, int expectedDoctors, boolean expectedSupervisor) {
        StaffingPlan plan = StaffingScheduler.computeSchedule(level);

        assertEquals(expectedDoctors, plan.getDoctorCount());
        assertEquals(expectedSupervisor, plan.isSupervisorRequired());
    }

    @Test
    @DisplayName("boundary between bands (2 vs 3, 5 vs 6) is not off-by-one")
    void bandBoundariesAreExact() {
        StaffingPlan lastOfBandOne = StaffingScheduler.computeSchedule(2);
        StaffingPlan firstOfBandTwo = StaffingScheduler.computeSchedule(3);
        assertNotEquals(lastOfBandOne.getDoctorCount(), firstOfBandTwo.getDoctorCount());

        StaffingPlan lastOfBandTwo = StaffingScheduler.computeSchedule(5);
        StaffingPlan firstOfBandThree = StaffingScheduler.computeSchedule(6);
        assertNotEquals(lastOfBandTwo.isSupervisorRequired(), firstOfBandThree.isSupervisorRequired());
    }

    @Test
    @DisplayName("the same level always produces the same plan (pure function, no hidden state)")
    void computationIsDeterministic() {
        StaffingPlan first = StaffingScheduler.computeSchedule(4);
        StaffingPlan second = StaffingScheduler.computeSchedule(4);

        assertEquals(first.getDoctorCount(), second.getDoctorCount());
        assertEquals(first.isSupervisorRequired(), second.isSupervisorRequired());
    }
}
