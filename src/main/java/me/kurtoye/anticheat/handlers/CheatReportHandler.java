package me.kurtoye.anticheat.handlers;

import me.kurtoye.anticheat.Anticheat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.UUID;

public class CheatReportHandler {

    // Staged thresholds – you can adjust these as needed.
    private static final int STAGE1_THRESHOLD = 10;
    private static final int STAGE2_THRESHOLD = 20;
    private static final int STAGE3_THRESHOLD = 30;
    private static final int EXTRA_SUSPICION_POINTS = 5;
    private static final int CHEAT_LIFETIME_THRESHOLD = 100;

    /**
     * Processes a suspicion event by checking lifetime suspicion for the given cheat type (reason)
     * and then applying a punishment based on the updated suspicion level.
     */
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
            plugin.getLogger().warning("Stage 3 reached: " + player.getName() + " flagged for " + reason);
            player.sendMessage(ChatColor.RED + "[AntiCheat] " + reason + " detected! (Stage 3 => Ban)");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tempban " + player.getName() + " 30m " + reason);
            plugin.getHistoryHandler().resetLifetimeSuspicionForCheat(playerId, reason);
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
            // Do not reset lifetime suspicion here so that continued offenses will escalate.
        }
    }

    private static void maybeWarnPlayer(Player player, Anticheat plugin, String reason, long currentTime) {
        player.sendMessage(ChatColor.RED + "[AntiCheat] " + reason + " detected! (Stage 1 => Warning)");
    }
}
