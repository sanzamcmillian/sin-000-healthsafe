package co.wethinkcode.healthsafe;

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
