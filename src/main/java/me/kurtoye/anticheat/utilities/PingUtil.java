package me.kurtoye.anticheat.utilities;

import org.bukkit.entity.Player;

public class PingUtil {

    // Retrieve the ping of a player
    public static double getPingCompensationFactor(Player player) {
        int ping = getPing(player);
        return 1.0 + (ping / 1000.0); // Example: 100ms ping adds 10% tolerance
    }

    // Get the ping of the player (version-specific)
    private static int getPing(Player player) {
        try {
            // Use reflection to get player's ping
            Object handle = player.getClass().getMethod("getHandle").invoke(player);
            return (int) handle.getClass().getField("ping").get(handle);
        } catch (Exception e) {
            e.printStackTrace();
            return 0; // Default ping if reflection fails
        }
    }
}