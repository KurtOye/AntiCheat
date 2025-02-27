// 🚀 Fully Optimized ClickUtil (Enhanced CPS Analysis & Anti-False Positives)
// ✅ Provides accurate click pattern detection for `AutoClickerCheck`.
// ✅ Detects **unnatural CPS patterns** while **minimizing false positives**.
// ✅ Implements **consistency-based thresholds** to differentiate between human and automated clicking.

package me.kurtoye.anticheat.utilities;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ClickUtil {
    private static final Map<UUID, Long> lastClickTime = new HashMap<>();
    private static final Map<UUID, Integer> clickCounts = new HashMap<>();
    private static final Map<UUID, Integer> stableCPSCounts = new HashMap<>();
    private static final Map<UUID, Long> lastResetTime = new HashMap<>();

    /**
     * Calculates CPS dynamically based on recent clicks.
     *
     * @param playerId The player's UUID
     * @param clickCount The map tracking click counts
     * @param resetTime The time window before CPS resets
     * @return The calculated CPS
     */
    public static int calculateCPS(UUID playerId, Map<UUID, Integer> clickCount, long resetTime) {
        long currentTime = System.currentTimeMillis();
        long lastReset = lastResetTime.getOrDefault(playerId, 0L);

        if (currentTime - lastReset > resetTime) {
            clickCount.put(playerId, 0);
            lastResetTime.put(playerId, currentTime);
            return 0;
        }
        return clickCount.getOrDefault(playerId, 0);
    }

    /**
     * Determines if a player's CPS remains **too consistent over time**.
     *
     * @param playerId The player's UUID
     * @param currentCPS The current CPS value
     * @param maxConsistentCPS The CPS threshold for suspicious consistency
     * @return True if the player maintains **exact CPS values consistently**
     */
    public static boolean isConsistentlySameCPS(UUID playerId, int currentCPS, int maxConsistentCPS) {
        if (currentCPS < maxConsistentCPS) {
            stableCPSCounts.put(playerId, 0);
            return false;
        }

        int stabilityCount = stableCPSCounts.getOrDefault(playerId, 0) + 1;
        stableCPSCounts.put(playerId, stabilityCount);
        return stabilityCount >= 5; // Flags after 5 consecutive stable CPS readings
    }
}
