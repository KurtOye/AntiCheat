package me.kurtoye.anticheat.utilities;

import me.kurtoye.anticheat.Anticheat;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * Utility class for detecting velocity anomalies.
 *
 * Used for:
 * - Knockback grace windows (to prevent false speed flags after hits)
 * - Identifying abnormal acceleration (e.g., Speed, Fly cheats)
 */
public class VelocityUtil {


    private static long KNOCKBACK_GRACE_PERIOD;

    /**
     * Loads velocity detection thresholds from configuration.
     */
    public static void init(Anticheat plugin) {
        FileConfiguration config = plugin.getConfig();
        KNOCKBACK_GRACE_PERIOD = config.getLong("velocity.knockback_grace_period_ms", 1200);
    }

    /**
     * Checks if the player was recently affected by knockback or velocity change.
     */
    public static boolean wasRecentlyHit(UUID playerId, long currentTime, Map<UUID, Long> lastVelocityChangeTime) {
        if (lastVelocityChangeTime == null) return false;
        long lastHitTime = lastVelocityChangeTime.getOrDefault(playerId, 0L);
        return (currentTime - lastHitTime) < KNOCKBACK_GRACE_PERIOD;
    }
}

