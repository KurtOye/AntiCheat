package me.kurtoye.anticheat.handlers;

import me.kurtoye.anticheat.Anticheat;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;


/**
 * Manages real-time suspicion scoring logic.
 * Applies point-based accumulation and automatic decay for progressive cheat detection.
 */
public class SuspicionHandler {

    private static final Map<UUID, Integer> suspicionPoints = new HashMap<>();
    private static final Map<UUID, Long> lastSuspicionTime = new HashMap<>();

    private static long DECAY_INTERVAL_MS;
    private static int DECAY_AMOUNT;

    /**
     * Loads configurable thresholds and decay rules.
     * Called during plugin startup via Anticheat.onEnable().
     */
    public static void init(Anticheat plugin) {
        FileConfiguration config = plugin.getConfig();
        DECAY_INTERVAL_MS = config.getLong("suspicion.decay_interval_ms", 1000);
        DECAY_AMOUNT      = config.getInt("suspicion.decay_amount", 1);
    }

    /**
     * Adds suspicion points for a given player and cheat type.
     * Triggers decay check and logs the event to PlayerHistoryHandler.
     */
    public static int addSuspicionPoints(UUID playerId, int points, String reason, Anticheat plugin) {
        decaySuspicionIfNeeded(playerId);
        int updated = suspicionPoints.getOrDefault(playerId, 0) + points;
        suspicionPoints.put(playerId, updated);
        lastSuspicionTime.put(playerId, System.currentTimeMillis());

        plugin.getLogger().info(String.format(
                "[SuspicionHandler] %s now has %d suspicion points after %s (+%d)",
                Bukkit.getOfflinePlayer(playerId).getName(), updated, reason, points
        ));

        plugin.getHistoryHandler().addLifetimeSuspicionForCheat(playerId, reason, points);
        return updated;
    }

    /**
     * Returns whether a player's suspicion level exceeds a custom threshold.anti
     */
    public static boolean isOverThreshold(UUID playerId, int customThreshold) {
        decaySuspicionIfNeeded(playerId);
        return suspicionPoints.getOrDefault(playerId, 0) >= customThreshold;
    }

    /**
     * Manually resets a player's suspicion score to zero.
     */
    public static void resetPoints(UUID playerId) {
        suspicionPoints.put(playerId, 0);
        lastSuspicionTime.put(playerId, System.currentTimeMillis());
    }

    /**
     * Applies decay to a player's suspicion points based on elapsed time.
     */
    private static void decaySuspicionIfNeeded(UUID playerId) {
        if (!lastSuspicionTime.containsKey(playerId)) return;

        long now = System.currentTimeMillis();
        long elapsed = now - lastSuspicionTime.get(playerId);

        // Prevent division by 0
        if (DECAY_INTERVAL_MS <= 0 || DECAY_AMOUNT <= 0) {
            return;
        }

        if (elapsed >= DECAY_INTERVAL_MS) {
            long intervals = elapsed / DECAY_INTERVAL_MS;
            int current = suspicionPoints.getOrDefault(playerId, 0);
            int reduced = current - (int) (intervals * DECAY_AMOUNT);
            suspicionPoints.put(playerId, Math.max(reduced, 0));
            lastSuspicionTime.put(playerId, now);
        }
    }

}
