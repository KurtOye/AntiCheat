package me.kurtoye.anticheat.handlers;

import me.kurtoye.anticheat.Anticheat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.UUID;

/**
 * Handles final punishment decisions for cheating based on suspicion levels.
 * Escalates from warnings → kicks → temporary bans using threshold logic.
 */
public class CheatReportHandler {

    private static int STAGE1_THRESHOLD;
    private static int STAGE2_THRESHOLD;
    private static int STAGE3_THRESHOLD;


    /**
     * Loads all configurable thresholds from config.yml.
     */
    public static void init(Anticheat plugin) {
        STAGE1_THRESHOLD         = plugin.getConfig().getInt("cheatreport.stage1_threshold", 10);
        STAGE2_THRESHOLD         = plugin.getConfig().getInt("cheatreport.stage2_threshold", 20);
        STAGE3_THRESHOLD         = plugin.getConfig().getInt("cheatreport.stage3_threshold", 30);
    }

    /**
     * Applies punishment escalation depending on current and lifetime suspicion score.
     */
    public static void handleSuspicionPunishment(Player player, Anticheat plugin, String reason, int totalSuspicion) {
        init(plugin);
        UUID playerId = player.getUniqueId();

        // Escalate based on thresholds
        if (totalSuspicion > STAGE3_THRESHOLD) {
            plugin.getLogger().warning("Stage 3: " + player.getName() + " flagged for " + reason);
            player.sendMessage(ChatColor.RED + "[AntiCheat] " + reason + " detected! (Stage 3 ⇒ Ban)");
            SuspicionHandler.resetPoints(playerId);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "ban " + player.getName() + " " + reason);
            plugin.getHistoryHandler().resetLifetimeSuspicionForCheat(playerId, reason);
        } else if (totalSuspicion > STAGE2_THRESHOLD) {
            plugin.getLogger().warning("Stage 2: " + player.getName() + " flagged for " + reason);
            player.sendMessage(ChatColor.RED + "[AntiCheat] " + reason + " detected! (Stage 2 ⇒ Kick)");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "kick " + player.getName() + " " + reason);
        } else if (totalSuspicion > STAGE1_THRESHOLD) {
            plugin.getLogger().info("Stage 1: " + player.getName() + " warned for " + reason);
            player.sendMessage(ChatColor.RED + "[AntiCheat] " + reason + " detected! (Stage 1 ⇒ Warning)");
        }
    }
}
