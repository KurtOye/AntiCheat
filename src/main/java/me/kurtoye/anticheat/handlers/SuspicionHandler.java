package me.kurtoye.anticheat.handlers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.handlers.PlayerHistoryHandler;
import org.bukkit.plugin.java.JavaPlugin;

public class SuspicionHandler {


    // Stores each player's suspicion score
    private static final Map<UUID, Integer> suspicionPoints = new HashMap<>();

    // If you want to allow partial resets/decay, track last suspicion update
    private static final Map<UUID, Long> lastSuspicionTime = new HashMap<>();

    // Example threshold (tunable via config if you like)
    private static final int DEFAULT_THRESHOLD = 5;

    // Optionally store decay settings
    private static final long DECAY_INTERVAL_MS = 5000; // every 5s
    private static final int DECAY_AMOUNT = 1;

    /**
     * Adds suspicion points for a player and returns the updated total.
     * The 'reason' is optional but can help debugging which check incremented suspicion.
     */
    public static int addSuspicionPoints(UUID playerId, int points, String reason) {
        // 1. Possibly decay existing suspicion first
        decaySuspicionIfNeeded(playerId);

        int oldValue = suspicionPoints.getOrDefault(playerId, 0);
        int newValue = oldValue + points;
        suspicionPoints.put(playerId, newValue);

        lastSuspicionTime.put(playerId, System.currentTimeMillis());

        Anticheat instance = Anticheat.getInstance();
        instance.getHistoryHandler().addLifetimeSuspicion(playerId, points);

        return newValue;
    }

    /**
     * Checks if the player's suspicion crosses a certain threshold.
     * Returns true if they're above threshold, false otherwise.
     */
    public static boolean isOverThreshold(UUID playerId, int customThreshold) {
        decaySuspicionIfNeeded(playerId);
        int current = suspicionPoints.getOrDefault(playerId, 0);
        return current >= customThreshold;
    }

    /**
     * Overload for using a default threshold
     */
    public static boolean isOverThreshold(UUID playerId) {
        return isOverThreshold(playerId, DEFAULT_THRESHOLD);
    }

    /**
     * Resets a player's suspicion points to 0.
     */
    public static void resetPoints(UUID playerId) {
        suspicionPoints.put(playerId, 0);
        lastSuspicionTime.put(playerId, System.currentTimeMillis());
    }

    /**
     * Optional: Decays suspicion if enough time has passed since last increment.
     * Called automatically in addSuspicionPoints() or isOverThreshold().
     */
    private static void decaySuspicionIfNeeded(UUID playerId) {
        if (!lastSuspicionTime.containsKey(playerId)) return;

        long now = System.currentTimeMillis();
        long lastUpdate = lastSuspicionTime.get(playerId);
        long elapsed = now - lastUpdate;
        if (elapsed >= DECAY_INTERVAL_MS) {
            // Compute how many intervals have passed, decaying multiple times if needed
            long intervals = elapsed / DECAY_INTERVAL_MS;
            int oldValue = suspicionPoints.getOrDefault(playerId, 0);

            // Each interval: subtract DECAY_AMOUNT, but not below 0
            int newValue = oldValue - (int)(intervals * DECAY_AMOUNT);
            if (newValue < 0) newValue = 0;

            suspicionPoints.put(playerId, newValue);
            lastSuspicionTime.put(playerId, now);
        }
    }

}
