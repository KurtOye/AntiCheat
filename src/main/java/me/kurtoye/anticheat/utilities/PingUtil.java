package me.kurtoye.anticheat.utilities;

import org.bukkit.entity.Player;

import java.lang.reflect.Field;

public class PingUtil {

    // Retrieve the ping of a player
    public static double getPingCompensationFactor(Player player) {
        int ping = getPing(player);
        if (ping < 0) return 1.0;

        // Apply a logarithmic scaling instead of linear
        return 1.0 + (Math.log10(ping + 1) / 2.0);
    }

    // Get the ping of the player (version-specific)
    public static int getPing(Player player) {
        try {
            return player.getPing(); // ✅ This works on PaperMC 1.18+
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }
}