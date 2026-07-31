package co.wethinkcode.healthsafe;

/**
 * Data Transfer Object for the combined scheduling schema.
 */
public record WardScheduleResponse(
        String wardId,
        int alertLevel,
        int doctorCount,
        boolean isSupervisorRequired
) {}
