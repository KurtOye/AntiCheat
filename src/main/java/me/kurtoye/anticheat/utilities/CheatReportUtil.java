package me.kurtoye.anticheat.utilities;

import me.kurtoye.anticheat.Anticheat;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles cheat reporting, warnings, and punishments across all checks.
 * ✅ Prevents spam with cooldown-based logging.
 * ✅ Implements progressive punishment (Warning → Kick → Temp Ban).
 */
public class CheatReportUtil {

    private static final Map<UUID, Long> lastWarningTime = new HashMap<>();
    private static final Map<UUID, Integer> flaggedCounts = new HashMap<>();

    private static final long CHAT_COOLDOWN = 5000; // 5 seconds between warnings
    private static final long LOG_COOLDOWN = 10000; // 10 seconds between logs
    private static final int MAX_WARNINGS_BEFORE_PUNISHMENT = 3; // Max warnings before severe action

    public static void reportCheat(Player player, Anticheat plugin, String cheatName) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (!lastWarningTime.containsKey(playerId) || (currentTime - lastWarningTime.get(playerId)) > CHAT_COOLDOWN) {
            player.sendMessage(ChatColor.RED + "[AntiCheat] " + cheatName + " detected!");
            lastWarningTime.put(playerId, currentTime);
        }

        if (!lastWarningTime.containsKey(playerId) || (currentTime - lastWarningTime.get(playerId)) > LOG_COOLDOWN) {
            plugin.getLogger().warning("⚠️ Player " + player.getName() + " triggered " + cheatName);
            lastWarningTime.put(playerId, currentTime);
        }
    }

    public static void flagPlayer(Player player, Anticheat plugin, String reason) {
        UUID playerId = player.getUniqueId();
        int warnings = flaggedCounts.getOrDefault(playerId, 0) + 1;
        flaggedCounts.put(playerId, warnings);

        if (warnings >= MAX_WARNINGS_BEFORE_PUNISHMENT) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tempban " + player.getName() + " 30m " + reason);
            plugin.getLogger().warning("🚨 Player " + player.getName() + " has been temporarily banned: " + reason);
            flaggedCounts.put(playerId, 0);
        } else if (warnings == 2) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "kick " + player.getName() + " " + reason + " - Stop cheating!");
            plugin.getLogger().warning("⚠️ Player " + player.getName() + " was kicked: " + reason);
        } else {
            reportCheat(player, plugin, reason);
        }
    }

    public static void resetPlayerFlags(Player player) {
        UUID playerId = player.getUniqueId();
        flaggedCounts.remove(playerId);
        lastWarningTime.remove(playerId);
    }

    public static int getFlagCount(Player player) {
        UUID playerId = player.getUniqueId();
        return flaggedCounts.getOrDefault(playerId, 0);
    }
}
