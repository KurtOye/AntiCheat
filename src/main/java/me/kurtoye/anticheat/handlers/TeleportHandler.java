package me.kurtoye.anticheat.handlers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * TeleportHandler ensures movement checks are ignored shortly after teleportation.
 * - Now resets movement tracking after a teleport.
 */
public class TeleportHandler {

    private final Map<UUID, Long> lastTeleport = new HashMap<>();
    private static final long TELEPORT_IGNORE_TIME = 3000; // 3 seconds

    /**
     * Registers a player's teleport and resets movement tracking.
     *
     * @param playerId The player's UUID
     * @param movementTracking The movement tracking map (e.g., lastPosition, lastCheckTime)
     */
    public void registerTeleport(UUID playerId, Map<UUID, ?> movementTracking) {
        lastTeleport.put(playerId, System.currentTimeMillis());

        // Reset movement tracking after teleport
        movementTracking.remove(playerId);
    }

    /**
     * Checks if the player has recently teleported.
     *
     * @param playerId The player's UUID
     * @param currentTime The current system time
     * @return true if the player has teleported recently
     */
    public boolean isRecentTeleport(UUID playerId, long currentTime) {
        return lastTeleport.containsKey(playerId) && (currentTime - lastTeleport.get(playerId)) < TELEPORT_IGNORE_TIME;
    }
}
