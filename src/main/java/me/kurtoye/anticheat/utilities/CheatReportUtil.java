package me.kurtoye.anticheat.utilities;

import me.kurtoye.anticheat.Anticheat;
import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * CheatReportUtil provides a shared method for reporting detected cheats.
 * - Standardized across all checks (e.g., SpeedCheck, JesusCheck)
 * - Prevents duplicate cooldown tracking in individual checks.
 */
public class CheatReportUtil {

    private static final Map<UUID, Long> lastWarningTime = new HashMap<>();
    private static final Map<UUID, Long> lastLogTime = new HashMap<>();

    private static final long CHAT_COOLDOWN = 2500; // 5 seconds
    private static final long LOG_COOLDOWN = 5000; // 10 seconds

    /**
     * Reports a detected cheat with cooldowns to prevent spam.
     *
     * @param player The player being flagged
     * @param plugin The main plugin instance
     * @param cheatName The name of the detected cheat
     */
    public static void reportCheat(Player player, Anticheat plugin, String cheatName) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        if (!lastWarningTime.containsKey(playerId) || (currentTime - lastWarningTime.get(playerId)) > CHAT_COOLDOWN) {
            player.sendMessage("§c[AntiCheat] " + cheatName + " detected!");
            lastWarningTime.put(playerId, currentTime);
        }

        if (!lastLogTime.containsKey(playerId) || (currentTime - lastLogTime.get(playerId)) > LOG_COOLDOWN) {
            plugin.getLogger().warning("⚠️ Player " + player.getName() + " triggered " + cheatName);
            lastLogTime.put(playerId, currentTime);
        }
    }
}
