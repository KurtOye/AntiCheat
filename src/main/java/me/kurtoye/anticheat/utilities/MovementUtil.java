package me.kurtoye.anticheat.utilities;

import me.kurtoye.anticheat.handlers.TeleportHandler;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;

/**
 * MovementUtil provides generalized movement validation shared across all anti-cheat checks.
 * <p>
 * Features:
 * 1. Checks for game modes (Creative, Spectator).
 * 2. Ignores recent teleports and knockback (to reduce false positives).
 * 3. Distinguishes riding states (horses, boats, etc.) from normal movement.
 * 4. Applies environment-based speed modifiers (ice, soul sand, honey, etc.).
 *
 * Future/Optional Enhancements:
 * - Phase detection logic (detect partial block collisions).
 * - Additional block scanning for bridging or climb checks.
 * - Smoother integration with a suspicion system for incremental scoring.
 */
public final class MovementUtil {

    private MovementUtil() {
        // Private constructor to prevent instantiation;
        // this class is purely static utility.
    }

    /**
     * Determines whether movement checks should be ignored based on various conditions:
     * <ul>
     *   <li>Ignores Creative/Spectator mode.</li>
     *   <li>Ignores flight or riding mounts (boats, horses, etc.).</li>
     *   <li>Ignores recent teleports (see TeleportHandler).</li>
     *   <li>Ignores recent knockback hits (see VelocityUtil).</li>
     * </ul>
     *
     * @param player               The player being checked.
     * @param teleportHandler      Shared teleport handler for recent teleports.
     * @param lastVelocityChangeTime Tracks knockback history (UUID -> last time in ms).
     * @param lastTeleport         Tracks teleport history (UUID -> last time in ms).
     * @return true if movement checks should be skipped for this player.
     */
    public static boolean shouldIgnoreMovement(
            Player player,
            TeleportHandler teleportHandler,
            Map<UUID, Long> lastVelocityChangeTime,
            Map<UUID, Long> lastTeleport
    ) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();

        // GameMode or flight-based ignore checks
        if (player.getGameMode() == GameMode.CREATIVE
                || player.getGameMode() == GameMode.SPECTATOR
                || player.isFlying()) {
            return true;
        }

        // Riding logic: includes boats, horses, minecarts, etc.
        if (isRidingMount(player)) {
            return true;
        }

        // Teleport-based ignore
        if (teleportHandler.isRecentTeleport(playerId, now)) {
            return true;
        }

        // Knockback-based ignore
        if (VelocityUtil.wasRecentlyHit(playerId, now, lastVelocityChangeTime)) {
            return true;
        }

        // If none of the "ignore" conditions met, proceed with movement checks
        return false;
    }

    /**
     * Checks if the player was recently teleported by referencing
     * the time stored in {@code lastTeleport} maps.
     * <p>
     * Currently used by {@link #shouldIgnoreMovement} if you want to do inline checks
     * or expansions in the future.
     *
     * @param playerId     The UUID of the player
     * @param currentTime  Current system time in ms
     * @param lastTeleport Map tracking the last teleport time for each player
     * @return true if the player was teleported in the last 3 seconds
     */
    public static boolean wasRecentlyTeleported(
            UUID playerId,
            long currentTime,
            Map<UUID, Long> lastTeleport
    ) {
        final long TELEPORT_IGNORE_TIME = 3000; // 3 seconds
        if (!lastTeleport.containsKey(playerId)) return false;
        return (currentTime - lastTeleport.get(playerId)) < TELEPORT_IGNORE_TIME;
    }

    /**
     * Determines if the player is riding an entity (e.g. boat, horse, pig, etc.).
     *
     * @param player The player in question
     * @return true if the player is on a recognized mount
     */
    public static boolean isRidingMount(Player player) {
        Entity vehicle = player.getVehicle();
        if (vehicle == null) return false;

        return switch (vehicle.getType()) {
            case HORSE, PIG, CAMEL, RAVAGER, MINECART, STRIDER, BOAT -> true;
            default -> false;
        };
    }

    /**
     * Calculates the maximum permissible speed for a player based on:
     * <ul>
     *   <li>Base Speed (sprinting vs. walking, on-ground vs. airborne)</li>
     *   <li>Terrain modifiers (ice, soul sand, honey, etc.)</li>
     * </ul>
     * You can further expand or refactor for advanced checks:
     * - block scanning for partial collisions
     * - synergy with Depth Strider or other enchantments
     *
     * @param player The player whose speed to calculate
     * @return The maximum allowed speed for that player's current environment
     */
    public static double getMaxAllowedSpeed(Player player) {
        // Basic logic: walking ~5.5, sprinting ~7.0. If not on ground, default to 8.5
        double baseSpeed;
        if (player.isSprinting()) baseSpeed = 7.0;
        else baseSpeed = 5.5;

        if (!player.isOnGround()) {
            // E.g., mid-air sprint or jump
            baseSpeed = 8.5;
        }

        return applyEnvironmentModifiers(player, baseSpeed);
    }

    /**
     * Applies terrain-based adjustments:
     * <ul>
     *   <li>Ice-based blocks => increased speed (1.6x)</li>
     *   <li>Soul Sand or Mud => slower speed (0.65x)</li>
     *   <li>Honey => very slow movement (0.35x)</li>
     * </ul>
     *
     * @param player    The player, used to get current block type
     * @param baseSpeed The speed prior to terrain changes
     * @return Adjusted speed after environment factors
     */
    public static double applyEnvironmentModifiers(Player player, double baseSpeed) {
        Material blockType = player.getLocation().getBlock().getType();
        // Potential expansions: deeper block scanning for partial collisions

        return switch (blockType) {
            case ICE, PACKED_ICE, BLUE_ICE -> baseSpeed * 1.6;
            case SOUL_SAND, MUD -> baseSpeed * 0.65;
            case HONEY_BLOCK -> baseSpeed * 0.35;
            default -> baseSpeed;
        };
    }
}
