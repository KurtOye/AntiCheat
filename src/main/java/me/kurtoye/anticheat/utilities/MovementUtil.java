package me.kurtoye.anticheat.utilities;

import me.kurtoye.anticheat.handlers.TeleportHandler;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import java.util.Map;
import java.util.UUID;

/**
 * Shared movement validation utility for all anti-cheat checks.
 */
public class MovementUtil {

    /**
     * Determines if movement checks should be ignored based on various conditions.
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
                wasRecentlyHit(playerId, currentTime, lastVelocityChangeTime) ||
                wasRecentlyTeleported(playerId, currentTime, lastTeleport);
    }

    /**
     * Checks if the player was recently hit (knockback protection).
     */
    private static boolean wasRecentlyHit(UUID playerId, long currentTime, Map<UUID, Long> lastVelocityChangeTime) {
        return lastVelocityChangeTime.containsKey(playerId) && (currentTime - lastVelocityChangeTime.get(playerId)) < 1200;
    }

    /**
     * Checks if the player was recently teleported (teleport protection).
     */
    private static boolean wasRecentlyTeleported(UUID playerId, long currentTime, Map<UUID, Long> lastTeleport) {
        return lastTeleport.containsKey(playerId) && (currentTime - lastTeleport.get(playerId)) < 3000;
    }

    /**
     * Determines if the player is riding a mount (e.g., horse, boat, minecart).
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
     * Determines if the player's movement on water is legitimate.
     */
    public static boolean isLegitWaterMovement(Player player) {
        Material blockAtFeet = player.getLocation().getBlock().getType();
        Material blockBelow = player.getLocation().subtract(0, 1, 0).getBlock().getType();

        return player.isSwimming() ||
                blockAtFeet == Material.WATER ||
                blockBelow == Material.BUBBLE_COLUMN ||
                blockBelow == Material.LILY_PAD ||
                player.isGliding();
    }

    /**
     * Calculates the maximum allowed speed based on movement type and environment.
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
     * Adjusts speed based on terrain (e.g., ice, soul sand).
     */
    private static double applyEnvironmentModifiers(Player player, double baseSpeed) {
        Material material = player.getLocation().getBlock().getType();

        if (material == Material.ICE || material == Material.PACKED_ICE || material == Material.BLUE_ICE) {
            baseSpeed *= 1.6;
        } else if (material == Material.SOUL_SAND || material == Material.MUD) {
            baseSpeed *= 0.65;
        }

        return baseSpeed;
    }
}
