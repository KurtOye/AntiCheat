package me.kurtoye.anticheat.handlers;

import me.kurtoye.anticheat.Anticheat;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SuspicionHandler {

    private static final Map<UUID, Integer> suspicionPoints = new HashMap<>();
    private static final Map<UUID, Long> lastSuspicionTime = new HashMap<>();
    private static int DEFAULT_THRESHOLD;
    private static long DECAY_INTERVAL_MS;
    private static int DECAY_AMOUNT;

    /**
     * Call this method during plugin initialization to load decay settings.
     */
    public static void init(Anticheat plugin) {
        FileConfiguration config = plugin.getConfig();
        DEFAULT_THRESHOLD = config.getInt("suspicion.default_threshold", 5);
        DECAY_INTERVAL_MS = config.getLong("suspicion.decay_interval_ms", 5000);
        DECAY_AMOUNT = config.getInt("suspicion.decay_amount", 1);
    }

    public static int addSuspicionPoints(UUID playerId, int points, String reason, Anticheat plugin) {
        decaySuspicionIfNeeded(playerId);
        int oldValue = suspicionPoints.getOrDefault(playerId, 0);
        int newValue = oldValue + points;
        suspicionPoints.put(playerId, newValue);
        lastSuspicionTime.put(playerId, System.currentTimeMillis());
        // Record this suspicion event in the lifetime history.
        plugin.getHistoryHandler().addLifetimeSuspicionForCheat(playerId, reason, points);
        CheatReportHandler.handleSuspicionPunishment(player, plugin, reason, points);
        return newValue;
    }

    public static boolean isOverThreshold(UUID playerId, int customThreshold) {
        decaySuspicionIfNeeded(playerId);
        int current = suspicionPoints.getOrDefault(playerId, 0);
        return current >= customThreshold;
    }

    public static boolean isOverThreshold(UUID playerId) {
        return isOverThreshold(playerId, DEFAULT_THRESHOLD);
    }

    public static void resetPoints(UUID playerId) {
        suspicionPoints.put(playerId, 0);
        lastSuspicionTime.put(playerId, System.currentTimeMillis());
    }

    private static void decaySuspicionIfNeeded(UUID playerId) {
        if (!lastSuspicionTime.containsKey(playerId)) return;
        long now = System.currentTimeMillis();
        long lastUpdate = lastSuspicionTime.get(playerId);
        long elapsed = now - lastUpdate;
        if (elapsed >= DECAY_INTERVAL_MS) {
            long intervals = elapsed / DECAY_INTERVAL_MS;
            int oldValue = suspicionPoints.getOrDefault(playerId, 0);
            int newValue = oldValue - (int)(intervals * DECAY_AMOUNT);
            if (newValue < 0) newValue = 0;
            suspicionPoints.put(playerId, newValue);
            lastSuspicionTime.put(playerId, now);
        }
    }
}
