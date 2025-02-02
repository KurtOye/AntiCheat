package me.kurtoye.anticheat.checks.chat;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.utilities.CheatReportUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CommandAbuseCheck detects **excessive or restricted command usage**.
 * ✅ Uses `CheatReportUtil` for **warnings & cooldown tracking**.
 * ✅ Prevents **false positives by allowing normal command usage**.
 */
public class CommandAbuseCheck implements Listener {

    private final Map<UUID, Long> lastCommandTime = new HashMap<>();
    private final Map<UUID, Integer> commandCount = new HashMap<>();

    private static final long COMMAND_COOLDOWN = 3000; // 1 seconds
    private static final int MAX_COMMAND_SPAM_COUNT = 4; // Max repeated commands before flagging

    // List of commands that are commonly spammed or abused
    private static final String[] RESTRICTED_COMMANDS = {"/tpa", "/home", "/spawn", "/warp", "/msg", "/helpop"};

    private final Anticheat plugin;

    /**
     * Constructor for CommandAbuseCheck.
     *
     * @param plugin The main plugin instance.
     */
    public CommandAbuseCheck(Anticheat plugin) {
        this.plugin = plugin;
    }

    /**
     * Listens for player commands and detects spam or abuse patterns.
     */
    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        String command = event.getMessage().toLowerCase();

        // ✅ **Check for restricted command abuse**
        for (String restricted : RESTRICTED_COMMANDS) {
            if (command.startsWith(restricted)) {
                handleRestrictedCommand(player, playerId, command, currentTime, event);
                return;
            }
        }

        // ✅ **Check for general command spam**
        if (lastCommandTime.containsKey(playerId)) {
            long timeSinceLastCommand = currentTime - lastCommandTime.get(playerId);
            if (timeSinceLastCommand < COMMAND_COOLDOWN) {
                event.setCancelled(true);
                CheatReportUtil.reportCheat(player, plugin, "Command Abuse (Cooldown Violation)");
                return;
            }
        }

        // ✅ **Update last command usage tracking**
        lastCommandTime.put(playerId, currentTime);
    }

    /**
     * Handles restricted command abuse detection.
     */
    private void handleRestrictedCommand(Player player, UUID playerId, String command, long currentTime, PlayerCommandPreprocessEvent event) {
        int count = commandCount.getOrDefault(playerId, 0) + 1;
        commandCount.put(playerId, count);

        // ✅ **If the player spams restricted commands, flag them**
        if (count >= MAX_COMMAND_SPAM_COUNT) {
            event.setCancelled(true);
            CheatReportUtil.reportCheat(player, plugin, "Command Abuse (Restricted Command Spam: " + command + ")");
            commandCount.put(playerId, 0); // Reset count after flagging
        }

        // ✅ **Update command tracking**
        lastCommandTime.put(playerId, currentTime);
    }
}
