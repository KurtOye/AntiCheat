package me.kurtoye.anticheat.utilities;

import org.bukkit.entity.Player;

import java.lang.reflect.Field;

public class PingUtil {

    // Retrieve the ping of a player
    public static double getPingCompensationFactor(Player player) {
        int ping = getPing(player);

        if (ping < 0) {
            return 1.0;
        }

        return 1.0 + (ping / 1000.0); // Example: 100ms ping adds 10% tolerance
    }

    // Get the ping of the player (version-specific)
    public static int getPing(Player player) {
        try {
            Object entityPlayer = player.getClass().getMethod("getHandle").invoke(player);
            Field pingField = entityPlayer.getClass().getDeclaredField("ping");
            return pingField.getInt(entityPlayer);
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }
}