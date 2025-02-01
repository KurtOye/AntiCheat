package me.kurtoye.anticheat.utilities;

import me.kurtoye.anticheat.handlers.TeleportHandler;
import org.bukkit.GameMode;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import java.util.Map;
import java.util.UUID;

/**
 * MovementUtil provides **general movement validation** shared across all anti-cheat checks.
 * - Used in **SpeedCheck, JesusCheck, and future movement-related checks**.
 * - Delegates **water movement** to `WaterMovementUtil`.
 * - Delegates **knockback/acceleration** to `VelocityUtil`.
 */
public class MovementUtil {

    /**
     * Determines whether movement checks should be **ignored** based on various conditions.
     * - **Ignores** creative/spectator mode, riding entities, teleportation, knockback.
     *
     * @param player The player being checked
     * @param teleportHandler Shared teleport handler
     * @param lastVelocityChangeTime Tracks knockback history
     * @param lastTeleport Tracks teleport history
     * @return true if movement should be ignored
     */
    public static boolean shouldIgnoreMovement(Player player, TeleportHandler teleportHandler,
                                               Map<UUID, Long> lastVelocityChangeTime,
                                               Map<UUID, Long> lastTeleport) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        return player.getGameMode() == GameMode.CREATIVE ||
                player.getGameMode() == GameMode.SPECTATOR ||
                player.isFlying() ||
                isRidingMount(player) ||
                teleportHandler.isRecentTeleport(playerId, currentTime) ||
                VelocityUtil.wasRecentlyHit(playerId, currentTime, lastVelocityChangeTime) ||
                wasRecentlyTeleported(playerId, currentTime, lastTeleport);
    }

    /**
     * Checks if the player was recently teleported.
     * - Ensures movement detection resets **after teleportation**.
     *
     * @param playerId The player's UUID
     * @param currentTime Current system time
     * @param lastTeleport Map tracking recent teleports
     * @return true if the player was teleported recently
     */
    private static boolean wasRecentlyTeleported(UUID playerId, long currentTime, Map<UUID, Long> lastTeleport) {
        return lastTeleport.containsKey(playerId) && (currentTime - lastTeleport.get(playerId)) < 3000;
    }

    /**
     * Determines if the player is riding an entity.
     * - Prevents movement checks when the player is in **boats, horses, minecarts, etc.**.
     *
     * @param player The player being checked
     * @return true if the player is riding a mount
     */
    private static boolean isRidingMount(Player player) {
        Entity vehicle = player.getVehicle();
        if (vehicle == null) return false;

        EntityType type = vehicle.getType();
        return type == EntityType.HORSE || type == EntityType.PIG || type == EntityType.CAMEL ||
                type == EntityType.RAVAGER || type == EntityType.MINECART || type == EntityType.STRIDER ||
                type == EntityType.BOAT;
    }

    /**
     * Calculates the **maximum allowed speed** for a player.
     * - Considers movement type, enchantments, terrain effects.
     *
     * @param player The player being checked
     * @return The maximum allowed speed for the player
     */
    public static double getMaxAllowedSpeed(Player player) {
        double baseSpeed = 5.5;
        if (player.isSprinting()) {
            baseSpeed = 7.0;
            if (!player.isOnGround()) {
                baseSpeed = 8.5;
            }
        }

        return applyEnvironmentModifiers(player, baseSpeed);
    }

    /**
     * Applies **terrain modifiers** to the player's movement speed.
     * - Adjusts speed for **ice, soul sand, mud, honey blocks**.
     *
     * @param player The player being checked
     * @param baseSpeed The base speed before modifications
     * @return The adjusted speed after terrain effects
     */
    private static double applyEnvironmentModifiers(Player player, double baseSpeed) {
        switch (player.getLocation().getBlock().getType()) {
            case ICE, PACKED_ICE, BLUE_ICE -> baseSpeed *= 1.6;
            case SOUL_SAND, MUD -> baseSpeed *= 0.65;
            case HONEY_BLOCK -> baseSpeed *= 0.35;
        }
        return baseSpeed;
    }
}
