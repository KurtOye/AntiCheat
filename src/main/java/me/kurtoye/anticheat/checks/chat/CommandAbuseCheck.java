package me.kurtoye.anticheat.checks.chat;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.handlers.CheatReportHandler;
import me.kurtoye.anticheat.handlers.SuspicionHandler;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 🚀 Refined CommandAbuseCheck:
 * - Integrates incremental suspicion scoring for spam/restricted commands.
 * - Uses config-based thresholds for cooldowns, restricted commands, and suspicion points.
 * - Minimizes false positives via incremental approach.
 */
public class CommandAbuseCheck implements Listener {

    private final Anticheat plugin;
    private final long commandCooldown;
    private final int maxCommandSpam;
    private final String[] restrictedCommands;

    // Suspicion increments
    private final int cooldownSuspicionPoints;
    private final int restrictedCmdSuspicionPoints;

    // Tracking
    private final Map<UUID, Long> lastCommandTime = new HashMap<>();
    private final Map<UUID, Integer> commandCount = new HashMap<>();

    public CommandAbuseCheck(Anticheat plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();

        // Basic config values
        this.commandCooldown = config.getLong("commandabuse.cooldown", 500);
        this.maxCommandSpam = config.getInt("commandabuse.max_repeats", 4);
        this.restrictedCommands = config.getStringList("commandabuse.restricted_commands").toArray(new String[0]);

        // Suspicion increments
        this.cooldownSuspicionPoints = config.getInt("commandabuse.cooldown_suspicion_points", 2);
        this.restrictedCmdSuspicionPoints = config.getInt("commandabuse.restricted_suspicion_points", 3);
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        String command = event.getMessage().toLowerCase();

        // 1) Check for restricted command usage
        for (String restricted : restrictedCommands) {
            if (command.startsWith(restricted)) {
                handleRestrictedCommand(player, playerId, command, currentTime, event);
                return;
            }
        }

        // 2) Check for command cooldown spam
        if (lastCommandTime.containsKey(playerId)) {
            long timeSinceLastCommand = currentTime - lastCommandTime.get(playerId);
            if (timeSinceLastCommand < commandCooldown) {
                // Suspicion increment instead of direct punishment
                int suspicion = SuspicionHandler.addSuspicionPoints(playerId, cooldownSuspicionPoints, "CommandAbuse (Cooldown)", plugin);
                CheatReportHandler.handleSuspicionPunishment(player, plugin, "Command Abuse (Cooldown Violation)", suspicion);

                // Cancel the event
                event.setCancelled(true);
                return;
            }
        }

        // 3) Record successful command usage time
        lastCommandTime.put(playerId, currentTime);
    }

    /**
     * Handles repeated or restricted command usage,
     * integrated with suspicion-based approach.
     */
    private void handleRestrictedCommand(Player player, UUID playerId, String command, long currentTime, PlayerCommandPreprocessEvent event) {
        int count = commandCount.getOrDefault(playerId, 0) + 1;
        commandCount.put(playerId, count);

        if (count >= maxCommandSpam) {
            // Instead of direct punishment, add suspicion
            int suspicion = SuspicionHandler.addSuspicionPoints(playerId, restrictedCmdSuspicionPoints, "CommandAbuse (Restricted Spam)", plugin);
            CheatReportHandler.handleSuspicionPunishment(player, plugin, "Command Abuse (Restricted Command Spam: " + command + ")", suspicion);

            // Cancel
            event.setCancelled(true);
            // Reset count
            commandCount.put(playerId, 0);

            // Update last command time
            lastCommandTime.put(playerId, currentTime);
        }
    }
}