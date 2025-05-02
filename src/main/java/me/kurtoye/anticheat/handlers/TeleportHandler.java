package me.kurtoye.anticheat.handlers;

import me.kurtoye.anticheat.Anticheat;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles teleportation tracking for players.
 * Prevents false positives in movement checks by providing a grace period after teleport events.
 */
public class TeleportHandler {

    private final Map<UUID, Long> lastTeleport = new HashMap<>();
    private final long teleportIgnoreTime;

    public TeleportHandler(Anticheat plugin) {
        FileConfiguration config = plugin.getConfig();
        this.teleportIgnoreTime = config.getLong("teleport.ignore_time_ms", 3000L);
    }

    /**
     * Checks if the player has recently teleported.
     * Used to temporarily bypass movement-related checks.
     */
    public boolean isRecentTeleport(UUID playerId, long currentTime) {
        return lastTeleport.containsKey(playerId)
                && (currentTime - lastTeleport.get(playerId)) < teleportIgnoreTime;
    }
}
