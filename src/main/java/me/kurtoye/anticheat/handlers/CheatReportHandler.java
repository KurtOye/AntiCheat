package me.kurtoye.anticheat.handlers;

import me.kurtoye.anticheat.Anticheat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles cheat reporting, warnings, and punishments across all checks.
 * Integrates with SuspicionManager to apply staged punishments:
 *  Stage 1: Warning
 *  Stage 2: Kick
 *  Stage 3: Temporary ban
 */
public class CheatReportHandler {

    // Keep these if you still want chat/log cooldown for messages in chat/console
    private static final Map<UUID, Long> lastWarningTime = new HashMap<>();
    private static final long CHAT_COOLDOWN = 5000; // 5 seconds between warnings
    private static final long LOG_COOLDOWN = 10000; // 10 seconds between logs

    // Example staging thresholds based on suspicion points
    // You can also load these from config if you like
    private static final int STAGE1_THRESHOLD = 10;  // Suspicion level at which we do a warning
    private static final int STAGE2_THRESHOLD = 20; // Suspicion level at which we do a kick
    private static final int STAGE3_THRESHOLD = 30; // Suspicion level at which we do a temp ban

    /**
     * Called when a check notices suspicious behavior and has incremented suspicion points.
     * We then see if the new suspicion crosses a threshold that triggers a punishment.
     *
     * @param player The player
     * @param plugin Main plugin instance
     * @param reason The reason or hack type
     * @param newSuspicion The updated suspicion points after increment
     */
    public static void handleSuspicionPunishment(Player player, Anticheat plugin, String reason, int newSuspicion) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // Check each stage in ascending order.
        // e.g., crossing from below 5 to 5 or more triggers Stage 1.
        if (newSuspicion >= STAGE3_THRESHOLD) {
            // Stage 3 => Ban
            broadcastCheat(player, plugin, reason, currentTime);
            player.sendMessage(ChatColor.RED + "[AntiCheat] " + reason + " detected! (Stage 3 => Ban)");
            // Temp ban or perm ban logic
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tempban " + player.getName() + " 30m " + reason);
            // Reset suspicion after punishment
            SuspicionHandler.resetPoints(playerId);
            return;
        } else if (newSuspicion >= STAGE2_THRESHOLD) {
            // Stage 2 => Kick
            broadcastCheat(player, plugin, reason, currentTime);
            player.sendMessage(ChatColor.RED + "[AntiCheat] " + reason + " detected! (Stage 2 => Kick)");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "kick " + player.getName() + " " + reason + " - Stop cheating!");
            // Optionally keep suspicion or partially reset
            SuspicionHandler.resetPoints(playerId);
            return;
        } else if (newSuspicion >= STAGE1_THRESHOLD) {
            // Stage 1 => Warning
            broadcastCheat(player, plugin, reason, currentTime);
            maybeWarnPlayer(player, plugin, reason, currentTime);
            // Do NOT reset suspicion yet, so if they keep cheating they'll eventually cross Stage 2
            return;
        }
        // If below Stage1 threshold => do nothing special except maybe a minimal log
    }

    /**
     * Used to broadcast or log cheat triggers, respecting cooldown for spam.
     */
    private static void broadcastCheat(Player player, Anticheat plugin, String reason, long currentTime) {
        UUID playerId = player.getUniqueId();

        // If lastWarningTime is older than the cooldown, allow broadcast
        Long lastWarning = lastWarningTime.getOrDefault(playerId, 0L);
        if ((currentTime - lastWarning) > LOG_COOLDOWN) {
            // Log in console
            plugin.getLogger().warning("⚠️ Player " + player.getName() + " triggered " + reason);
            lastWarningTime.put(playerId, currentTime);
        }
    }

    /**
     * Possibly warns the player in chat if the chat cooldown is up.
     */
    private static void maybeWarnPlayer(Player player, Anticheat plugin, String reason, long currentTime) {
        UUID playerId = player.getUniqueId();
        long lastWarn = lastWarningTime.getOrDefault(playerId, 0L);
        if ((currentTime - lastWarn) > CHAT_COOLDOWN) {
            player.sendMessage(ChatColor.RED + "[AntiCheat] " + reason + " detected! (Stage 1 => Warning)");
            lastWarningTime.put(playerId, currentTime);
        }
    }
}
