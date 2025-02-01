package me.kurtoye.anticheat.utilities;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * VelocityUtil handles **acceleration tracking** and **knockback validation**.
 */
public class VelocityUtil {

    private static final double ACCELERATION_THRESHOLD = 6.0; // Maximum allowed acceleration

    public static boolean wasRecentlyHit(UUID playerId, long currentTime, Map<UUID, Long> lastVelocityChangeTime) {
        return lastVelocityChangeTime.containsKey(playerId) && (currentTime - lastVelocityChangeTime.get(playerId)) < 1200;
    }

    public static boolean shouldIgnoreSpeedCheck(double acceleration) {
        return acceleration > ACCELERATION_THRESHOLD;
    }

    public static double getAcceleration(Player player) {
        return player.getVelocity().length();
    }
}
