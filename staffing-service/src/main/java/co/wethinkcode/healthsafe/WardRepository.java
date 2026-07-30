package co.wethinkcode.healthsafe;

import co.wethinkcode.healthsafe.CleanWardRecord;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

public class WardRepository {
    private List<CleanWardRecord> wards;
    private final ObjectMapper mapper = new ObjectMapper();

    public WardRepository(List<CleanWardRecord> wards) {
        this.wards = wards != null ? List.copyOf(wards) : List.of();
    }

    public void loadDataFromJson(String jsonString) {
        try {
            this.wards = mapper.readValue(jsonString, new TypeReference<List<CleanWardRecord>>() {});
        } catch (Exception e) {
            System.err.println("Failed to parse JSON from cleaning service: " + e.getMessage());
            this.wards = List.of(); // fallback to empty to avoid null pointers
        }
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
