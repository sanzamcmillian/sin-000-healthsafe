package co.wethinkcode.healthsafe;

/**
 * An immutable plan representing the required staffing numbers.
 */
public record StaffingPlan(int getDoctorCount, boolean isSupervisorRequired) {}
