package me.kurtoye.anticheat.utilities;

import org.bukkit.entity.Player;

/**
 * Utility for retrieving player ping and applying network compensation.
 * Used to adjust detection thresholds based on lag and latency.
 */
public class PingUtil {

    /**
     * Returns a multiplier based on player's current ping.
     * Helps soften detection thresholds under high latency.
     */
    public static double getPingCompensationFactor(Player player) {
        int ping = getPing(player);
        if (ping < 0) return 1.0;

        return 1.0 + (Math.log10(ping + 1) / 2.0);
    }

    /**
     * Safely retrieves the ping of a player.
     * Compatible with PaperMC 1.18+.
     */
    public static int getPing(Player player) {
        try {
            return player.getPing();
        } catch (Exception ignored) {
            return -1;
        }
    }
}
