package me.kurtoye.anticheat.handlers;

import me.kurtoye.anticheat.Anticheat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.UUID;

public class CheatReportHandler {

    // Configurable key points – loaded from config.yml
    private static int STAGE1_THRESHOLD;
    private static int STAGE2_THRESHOLD;
    private static int STAGE3_THRESHOLD;
    private static int EXTRA_SUSPICION_POINTS;
    private static int CHEAT_LIFETIME_THRESHOLD;

    /**
     * Call this method during plugin initialization to load key values.
     */
    public static void init(Anticheat plugin) {
        STAGE1_THRESHOLD = plugin.getConfig().getInt("cheatreport.stage1_threshold", 10);
        STAGE2_THRESHOLD = plugin.getConfig().getInt("cheatreport.stage2_threshold", 20);
        STAGE3_THRESHOLD = plugin.getConfig().getInt("cheatreport.stage3_threshold", 30);
        EXTRA_SUSPICION_POINTS = plugin.getConfig().getInt("cheatreport.extra_suspicion_points", 5);
        CHEAT_LIFETIME_THRESHOLD = plugin.getConfig().getInt("cheatreport.cheat_lifetime_threshold", 100);
    }

    public static void handleSuspicionPunishment(Player player, Anticheat plugin, String reason, int newSuspicion) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // Retrieve lifetime suspicion for this cheat type from the history handler.
        int lifetime = plugin.getHistoryHandler().getLifetimeSuspicionForCheat(playerId, reason);

        // If lifetime suspicion exceeds the threshold, add extra suspicion points.
        if (lifetime > CHEAT_LIFETIME_THRESHOLD && newSuspicion >= STAGE1_THRESHOLD && newSuspicion < STAGE2_THRESHOLD) {
            plugin.getLogger().info("[INFO] " + player.getName() + " is escalated for " + reason
                    + " due to high lifetime suspicion (" + lifetime + ").");
            newSuspicion += EXTRA_SUSPICION_POINTS;
        }

        // Apply staged punishments based on the updated short-term suspicion value.
        if (newSuspicion >= STAGE3_THRESHOLD) {
            // Log to server console
            plugin.getLogger().warning("Stage 3 reached: " + player.getName() + " flagged for " + reason);
            // Send in-game feedback to the player being punished
            player.sendMessage(ChatColor.RED + "[AntiCheat] " + reason + " detected! (Stage 3 => Ban)");
            // Execute the ban command via the server console (30m ban)
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tempban " + player.getName() + " 30m " + reason);
            // Clear the player’s lifetime suspicion history for this cheat — prevents further automatic punishment
            plugin.getHistoryHandler().resetLifetimeSuspicionForCheat(playerId, reason);
            // Exit this method to prevent duplicate processing
            return;
        } else if (newSuspicion >= STAGE2_THRESHOLD) {
            plugin.getLogger().warning("Stage 2 reached: " + player.getName() + " flagged for " + reason);
            player.sendMessage(ChatColor.RED + "[AntiCheat] " + reason + " detected! (Stage 2 => Kick)");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "kick " + player.getName() + " " + reason + " - Stop cheating!");
            plugin.getHistoryHandler().resetLifetimeSuspicionForCheat(playerId, reason);
            return;
        } else if (newSuspicion >= STAGE1_THRESHOLD) {
            plugin.getLogger().info("Stage 1 reached: " + player.getName() + " warned for " + reason);
            maybeWarnPlayer(player, plugin, reason, currentTime);
            // Do not reset lifetime suspicion so that continued offenses will escalate.
        }
    }

    private static void maybeWarnPlayer(Player player, Anticheat plugin, String reason, long currentTime) {
        player.sendMessage(ChatColor.RED + "[AntiCheat] " + reason + " detected! (Stage 1 => Warning)");
    }
}
