package co.wethinkcode.healthsafe;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class StaffingInfoStore {
    private final Map<String, StaffingInfo> store = new ConcurrentHashMap<>();

    public void updateStaffingInfo(String wardId, StaffingInfo info) {
        store.put(wardId, info);
    }

    public Optional<StaffingInfo> getStaffingInfo(String wardId) {
        return Optional.ofNullable(store.get(wardId));
    }
}