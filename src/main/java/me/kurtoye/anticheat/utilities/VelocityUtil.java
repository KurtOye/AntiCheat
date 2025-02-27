// 🚀 Fully Optimized VelocityUtil (Performance, Accuracy & Maintainability)
// ✅ Handles acceleration tracking and knockback validation efficiently.
// ✅ Minimal overhead; suitable for real-time checks in large Minecraft servers.

package me.kurtoye.anticheat.utilities;

import org.bukkit.entity.Player;
import java.util.Map;
import java.util.UUID;

/**
 * VelocityUtil manages:
 * 1) Knockback Validation (recent hits).
 * 2) Acceleration Threshold Checks (approx. speed bursts).
 *
 * The class name "VelocityUtil" remains for historical reasons; note that
 * getAcceleration() actually returns the player's velocity magnitude,
 * which we compare to a threshold to detect unnatural speed spikes.
 */
public class VelocityUtil {

    /** Upper limit beyond which motion is flagged as suspicious. */
    private static final double ACCELERATION_THRESHOLD = 6.5;

    /** Grace period (ms) after which a knockback event no longer affects movement checks. */
    private static final long KNOCKBACK_GRACE_PERIOD = 1200;

    /**
     * Checks if a player was recently subjected to knockback or similar velocity changes.
     * This helps anti-cheat checks to skip or reduce suspicion for sudden forced movements.
     *
     * @param playerId             The unique ID of the player.
     * @param currentTime          System current time in ms.
     * @param lastVelocityChangeTime  Map storing the last recorded knockback event time for players.
     * @return true if the player's last knockback event is still within the grace period.
     */
    public static boolean wasRecentlyHit(UUID playerId, long currentTime, Map<UUID, Long> lastVelocityChangeTime) {
        if (lastVelocityChangeTime == null) return false;
        long lastHitTime = lastVelocityChangeTime.getOrDefault(playerId, 0L);

        return lastHitTime + KNOCKBACK_GRACE_PERIOD > currentTime;
    }

    /**
     * Determines if a player's current velocity (labeled "acceleration" in code) is abnormally high.
     * Typically used in speed checks or combos with external logic to confirm suspicious behavior.
     *
     * @param acceleration The player's current velocity magnitude, as derived from getAcceleration().
     * @return true if acceleration exceeds the ACCELERATION_THRESHOLD, indicating potential speed hacking.
     */
    public static boolean isAbnormalAcceleration(double acceleration) {
        return acceleration > ACCELERATION_THRESHOLD;
    }

    /**
     * Returns the player's velocity magnitude (speed). Although named "getAcceleration," it
     * serves as a quick approximation to detect motion bursts. If actual acceleration is required
     * (delta-velocity per time), store old velocities and compare.
     *
     * @param player The player whose velocity we measure.
     * @return The magnitude (length) of the player's current velocity vector.
     */
    public static double getAcceleration(Player player) {
        return player.getVelocity().length();
    }
}
