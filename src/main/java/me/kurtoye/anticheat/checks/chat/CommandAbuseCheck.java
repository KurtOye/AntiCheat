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
 * Detects excessive or unauthorized command usage by players.
 *
 * The check covers two main types of command abuse:
 * 1. Repeatedly sending restricted commands (e.g., /msg, /home).
 * 2. Spamming commands faster than an allowed cooldown interval.
 *
 * Applies configurable suspicion scoring and integrates with the punishment handler.
 */
public class CommandAbuseCheck implements Listener {

    private final Anticheat plugin;

    // Configurable thresholds
    private final boolean enabled;
    private final long cooldownMs;
    private final int maxSpam;
    private final String[] restrictedCmds;
    private final int cooldownPoints;
    private final int restrictedPoints;

    // Tracking recent usage
    private final Map<UUID, Long> lastCommand = new HashMap<>();
    private final Map<UUID, Integer> spamCount = new HashMap<>();

    /**
     * Initializes command abuse detection and loads all thresholds from config.yml.
     */
    public CommandAbuseCheck(Anticheat plugin) {
        this.plugin = plugin;
        FileConfiguration cfg = plugin.getConfig();

        this.enabled = cfg.getBoolean("commandabuse.enabled", true);
        this.cooldownMs = cfg.getLong("commandabuse.cooldown", 50);
        this.maxSpam = cfg.getInt("commandabuse.max_repeats", 4);
        this.restrictedCmds = cfg.getStringList("commandabuse.restricted_commands").toArray(new String[0]);
        this.cooldownPoints = cfg.getInt("commandabuse.cooldown_suspicion_points", 2);
        this.restrictedPoints = cfg.getInt("commandabuse.restricted_suspicion_points", 3);
    }

    /**
     * Detects and handles players sending restricted or excessively frequent commands.
     * Flags repeated abuse patterns using progressive suspicion logic.
     */
    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        String cmd = event.getMessage().toLowerCase();

        if (!enabled) return;

        // --- 1) Detect Restricted Commands ---
        for (String restricted : restrictedCmds) {
            if (cmd.startsWith(restricted)) {
                int count = spamCount.getOrDefault(id, 0) + 1;
                spamCount.put(id, count);

                if (count >= maxSpam) {
                    int sus = SuspicionHandler.addSuspicionPoints(id, restrictedPoints, "CommandAbuse(Restricted)", plugin);
                    CheatReportHandler.handleSuspicionPunishment(p, plugin, "Command Abuse: " + cmd, sus);
                    event.setCancelled(true);
                    spamCount.put(id, 0);          // Reset after flag
                    lastCommand.put(id, now);
                }
                return;
            }
        }

        // --- 2) Detect Cooldown Spam ---
        if (lastCommand.containsKey(id) && (now - lastCommand.get(id)) < cooldownMs) {
            int sus = SuspicionHandler.addSuspicionPoints(id, cooldownPoints, "CommandAbuse(Cooldown)", plugin);
            CheatReportHandler.handleSuspicionPunishment(p, plugin, "Command Spam (Cooldown)", sus);
            event.setCancelled(true);
            return;
        }

        // --- 3) Record Command Timestamp ---
        lastCommand.put(id, now);
    }
}
