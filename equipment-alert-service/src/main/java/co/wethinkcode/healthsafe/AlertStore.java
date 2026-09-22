package co.wethinkcode.healthsafe;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class AlertStore {
    private final List<String> receivedAlerts = new CopyOnWriteArrayList<>();

    public void addAlert(String alertPayLoad) {
        receivedAlerts.add(alertPayLoad);
    }

    public List<String> getReceivedAlerts() {
        return List.copyOf(receivedAlerts);
    }
}
