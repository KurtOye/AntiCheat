// 🚀 Fully Optimized PingUtil (Performance, Accuracy & Maintainability)
// ✅ Uses logarithmic scaling for better lag compensation.
// ✅ Ensures version compatibility and prevents unnecessary errors.

package me.kurtoye.anticheat.utilities;

import org.bukkit.entity.Player;

public class PingUtil {
    /**
     * Retrieves the ping compensation factor.
     * - Uses logarithmic scaling instead of linear for better lag adjustment.
     *
     * @param player The player whose ping is being checked.
     * @return Compensation factor for movement checks.
     */
    public static double getPingCompensationFactor(Player player) {
        int ping = getPing(player);
        if (ping < 0) return 1.0;

        return 1.0 + (Math.log10(ping + 1) / 2.0);
    }

    /**
     * Retrieves the ping of the player.
     * - Works for PaperMC 1.18+.
     * - Prevents unnecessary stack traces on failure.
     *
     * @param player The player whose ping is being retrieved.
     * @return The player's ping or -1 if retrieval fails.
     */
    public static int getPing(Player player) {
        try {
            return player.getPing();
        } catch (Exception ignored) {
            return -1;
        }
    }
}
