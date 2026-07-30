package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.CleanWardRecord;
import java.util.List;
import java.util.Optional;

public class WardRepository {
    private final List<CleanWardRecord> wards;

    public WardRepository(List<CleanWardRecord> wards) {
        this.wards = wards != null ? List.copyOf(wards) : List.of();
    }

    public Optional<CleanWardRecord> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }

        String canonicalId = id.trim().toUpperCase();
        Optional<CleanWardRecord> result = Optional.empty();

        if (!wards.isEmpty()) {
            result = wards.stream()
                    .filter(ward -> ward.wardId().equals(canonicalId))
                    .findFirst();
        }
        return result;
    }
}
