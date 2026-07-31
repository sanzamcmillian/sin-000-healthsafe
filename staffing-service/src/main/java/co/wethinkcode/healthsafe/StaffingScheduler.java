package co.wethinkcode.healthsafe;

/**
 * Pure utility class to calculate roster staffing metrics based on system alert states.
 */
public class StaffingScheduler {

    /**
     * Computes the staffing requirements deterministically for a validated alert level.
     * Maps levels 0-2, 3-5, and 6-8 exact boundaries to their respective resources.
     */
    public static StaffingPlan computeSchedule(int alertLevel) {
        return switch (alertLevel) {
            case 0, 1, 2 -> new StaffingPlan(1, false);
            case 3, 4, 5 -> new StaffingPlan(2, false);
            case 6, 7, 8 -> new StaffingPlan(3, true);
            default -> throw new IllegalArgumentException("Unexpected alert level configuration: " + alertLevel);
        };
    }
}
