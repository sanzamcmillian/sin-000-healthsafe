package co.wethinkcode.healthsafe;

public class StaffingInfo {
    private final int alertLevel;
    private final int doctorCount;
    private final boolean supervisorRequired;

    public StaffingInfo(int alertLevel, int doctorCount, boolean supervisorRequired) {
        this.alertLevel = alertLevel;
        this.doctorCount = doctorCount;
        this.supervisorRequired = supervisorRequired;
    }

    public int getAlertLevel() {
        return alertLevel;
    }

    public int getDoctorCount() {
        return doctorCount;
    }

    public boolean isSupervisorRequired() {
        return supervisorRequired;
    }

    @Override
    public String toString() {
        return "StaffingInfo{" +
                "alertLevel=" + alertLevel +
                ", doctorCount=" + doctorCount +
                ", supervisorRequired=" + supervisorRequired +
                '}';
    }
}