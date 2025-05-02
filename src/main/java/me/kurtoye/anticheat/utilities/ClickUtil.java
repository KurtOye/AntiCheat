package me.kurtoye.anticheat.utilities;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Utility class for detecting autoclickers via CPS tracking and consistency detection.
 */
public class ClickUtil {


    private static final Map<UUID, Integer> stableCPSCounts = new HashMap<>();
    private static final Map<UUID, Long> lastResetTime = new HashMap<>();

    /**
     * Calculates current clicks per second (CPS) over a rolling window.
     */
    public static int calculateCPS(UUID playerId, Map<UUID, Integer> clickCount, long resetTime) {
        long now = System.currentTimeMillis();
        long lastReset = lastResetTime.getOrDefault(playerId, 0L);

        if (now - lastReset > resetTime) {
            clickCount.put(playerId, 0);
            lastResetTime.put(playerId, now);
            return 0;
        }

        return clickCount.getOrDefault(playerId, 0);
    }

    /**
     * Detects if a player's CPS has remained constant at a high level for too long.
     * Flags if CPS has not varied over 5+ intervals.
     */
    public static boolean isConsistentlySameCPS(UUID playerId, int currentCPS, int maxConsistentCPS) {
        if (currentCPS < maxConsistentCPS) {
            stableCPSCounts.put(playerId, 0);
            return false;
        }

        int consistency = stableCPSCounts.getOrDefault(playerId, 0) + 1;
        stableCPSCounts.put(playerId, consistency);
        return consistency >= 5;
    }
}
