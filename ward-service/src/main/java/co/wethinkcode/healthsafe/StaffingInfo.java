package co.wethinkcode.healthsafe;

import org.jetbrains.annotations.NotNull;

public record StaffingInfo(int alertLevel, int doctorCount, boolean supervisorRequired) {

    @Override
    public @NotNull String toString() {
        return "StaffingInfo{" +
                "alertLevel=" + alertLevel +
                ", doctorCount=" + doctorCount +
                ", supervisorRequired=" + supervisorRequired +
                '}';
    }
}