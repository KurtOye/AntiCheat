package me.kurtoye.anticheat.utilities;

import org.bukkit.Bukkit;

public class TpsUtil {

    // Retrieve the server's TPS
    public static double getTpsCompensationFactor() {
        double tps = getServerTPS(); // Get the server TPS
        return tps / 20.0; // Assume 20 TPS is normal
    }

    // Get the server TPS (0 index is the last 1 minute TPS)
    private static double getServerTPS() {
        return Bukkit.getServer().getTPS()[0]; // Obtain TPS from Bukkit
    }
}