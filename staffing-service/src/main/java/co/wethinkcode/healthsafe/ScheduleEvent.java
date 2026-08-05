package co.wethinkcode.healthsafe;

public record ScheduleEvent(String getWardId,
                            int getAlertLevel,
                            int getDoctorCount,
                            boolean isSupervisorRequired) {}
