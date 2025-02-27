// 🚀 Fully Optimized TpsUtil (Performance, Accuracy & Maintainability)
// ✅ Ensures smooth TPS-based compensation without excessive leniency.

package me.kurtoye.anticheat.utilities;

import org.bukkit.Bukkit;

public class TpsUtil {
    /**
     * Retrieves the TPS compensation factor.
     * - Adjusts movement thresholds based on server performance.
     * - Prevents drastic speed increases while allowing minor leniency.
     *
     * @return Compensation factor based on server TPS.
     */
    public static double getTpsCompensationFactor() {
        double tps = Bukkit.getServer().getTPS()[0]; // Retrieve the latest TPS reading
        return Math.max(0.75, (0.6 + (tps / 33.0))); // Slightly refined scaling
    }
}
