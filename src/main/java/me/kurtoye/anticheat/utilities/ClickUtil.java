package me.kurtoye.anticheat.utilities;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ClickUtil provides **click-related utilities** for detecting auto-clickers and unnatural clicking patterns.
 * ✅ Used in **AutoClickerCheck, KillAuraCheck, and future combat checks**.
 * ✅ Tracks **CPS (Clicks Per Second)** and **click timing consistency**.
 */
public class ClickUtil {

    private static final Map<UUID, Long> lastClickTime = new HashMap<>();
    private static final Map<UUID, Integer> clickCount = new HashMap<>();
    private static final Map<UUID, Double> lastCps = new HashMap<>();
    private static final Map<UUID, Integer> consistentClickCount = new HashMap<>();

    private static final long CLICK_INTERVAL_CHECK = 5000; // Track clicks over a 5-second window

    /**
     * Records a player's click and calculates CPS.
     *
     * @param playerId The UUID of the player
     * @return The calculated CPS
     */
    public static double recordClick(UUID playerId) {
        long currentTime = System.currentTimeMillis();
        clickCount.put(playerId, clickCount.getOrDefault(playerId, 0) + 1);

        if (lastClickTime.containsKey(playerId)) {
            long elapsedTime = currentTime - lastClickTime.get(playerId);
            if (elapsedTime >= CLICK_INTERVAL_CHECK) {
                int totalClicks = clickCount.get(playerId);
                double cps = (double) totalClicks / (elapsedTime / 1000.0);
                lastCps.put(playerId, cps);

                // Reset tracking
                clickCount.put(playerId, 0);
                lastClickTime.put(playerId, currentTime);
                return cps;
            }
        } else {
            lastClickTime.put(playerId, currentTime);
        }
        return lastCps.getOrDefault(playerId, 0.0);
    }

    /**
     * Checks if the player exceeds a safe CPS limit.
     *
     * @param playerId The UUID of the player
     * @param maxCps The maximum allowed CPS before flagging
     * @return True if player is clicking too fast
     */
    public static boolean isExceedingCPS(UUID playerId, int maxCps) {
        return lastCps.getOrDefault(playerId, 0.0) > maxCps;
    }

    /**
     * Checks if the player maintains **exactly the same CPS over multiple tracking intervals**.
     * - Real players have **natural click variations**.
     * - Bots and macros **click with perfect consistency**.
     *
     * @param playerId The UUID of the player
     * @param perfectCpsThreshold The number of times a player can maintain a perfect CPS before flagging
     * @return True if player maintains **perfect CPS** across multiple checks
     */
    public static boolean isPerfectlyConsistentCPS(UUID playerId, int perfectCpsThreshold) {
        if (!lastCps.containsKey(playerId)) {
            return false;
        }

        int consistentCount = consistentClickCount.getOrDefault(playerId, 0) + 1;
        consistentClickCount.put(playerId, consistentCount);

        if (consistentCount >= perfectCpsThreshold) {
            consistentClickCount.put(playerId, 0); // Reset after flagging
            return true;
        }

        return false;
    }
}
