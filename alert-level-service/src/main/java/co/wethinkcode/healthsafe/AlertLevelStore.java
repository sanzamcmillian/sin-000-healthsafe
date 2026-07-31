package co.wethinkcode.healthsafe;

import java.util.concurrent.atomic.AtomicInteger;

public class AlertLevelStore {
    private final AtomicInteger currentLevel = new AtomicInteger(1);

    public boolean setLevel(int newLevel) {
        if (newLevel < 0 || newLevel > 8) {
            return false;
        }
        currentLevel.set(newLevel);
        return true;
    }

    public int getLevel() {
        return currentLevel.get();
    }

}
