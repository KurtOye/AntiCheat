package me.kurtoye.anticheat.utilities;

import org.bukkit.Bukkit;

/**
 * Utility class to apply server TPS compensation.
 *
 * Used to normalize timing-based checks such as speed or break intervals
 * when the server is lagging or under heavy load.
 */
public class TpsUtil {

    /**
     * Returns a multiplier to scale detection thresholds based on current TPS.
     * - TPS 20 = x1.0
     * - TPS < 20 = adjusted linearly to avoid false positives
     */
    public static double getTpsCompensationFactor() {
        double tps = Bukkit.getServer().getTPS()[0];
        return Math.max(0.75, (0.6 + (tps / 33.0)));
    }
}
