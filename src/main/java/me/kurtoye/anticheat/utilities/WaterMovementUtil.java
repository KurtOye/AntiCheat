package me.kurtoye.anticheat.utilities;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;

/**
 * Utility class for validating legitimate player movement across water surfaces.
 *
 * Supports detection logic used in anti-cheat modules by accounting for:
 * - Sprint-jumping behavior
 * - Frost Walker ice paths
 * - Boats and Depth Strider mechanics
 * - Bubble columns and swim states
 */
public class WaterMovementUtil {

    /**
     * Checks if the player is moving through water in a naturally valid way.
     */
    public static boolean isLegitWaterMovement(Player player) {
        Material feet = player.getLocation().getBlock().getType();
        Material below = player.getLocation().subtract(0, 1, 0).getBlock().getType();

        return player.isSwimming()
                || feet == Material.WATER
                || below == Material.BUBBLE_COLUMN
                || below == Material.LILY_PAD
                || player.isGliding();
    }

    /**
     * Checks whether the player is sprinting across the surface of water.
     */
    public static boolean isPlayerRunningOnWater(Player player) {
        Material feet = player.getLocation().getBlock().getType();
        Material below = player.getLocation().subtract(0, 1, 0).getBlock().getType();

        return (feet == Material.WATER || below == Material.WATER)
                && !player.isSwimming()
                && !player.isFlying();
    }

    /**
     * Detects high-velocity jumping over water, used to catch "Jesus" sprint-jump bypasses.
     */
    public static boolean isPlayerSprintJumpingOnWater(Player player) {
        Material below = player.getLocation().subtract(0, 1, 0).getBlock().getType();

        return below == Material.WATER
                && player.isSprinting()
                && player.getVelocity().getY() > 0.25;
    }

    public static boolean isPlayerUsingFrostWalker(Player player) {
        return player.getLocation().getBlock().getType().name().contains("ICE");
    }

    public static boolean isPlayerInBoat(Player player) {
        return player.isInsideVehicle();
    }

    public static boolean isPlayerUsingDepthStrider(Player player) {
        return player.getInventory().getBoots() != null
                && player.getInventory().getBoots().containsEnchantment(Enchantment.DEPTH_STRIDER);
    }

    public static boolean isPlayerUsingAquaAffinity(Player player) {
        return player.getInventory().getHelmet() != null
                && player.getInventory().getHelmet().containsEnchantment(Enchantment.AQUA_AFFINITY);
    }

    /**
     * Checks if the player is standing inside a bubble column.
     * Used to prevent flagging upward water movement as cheating.
     */
    public static boolean isInBubbleColumn(Player player) {
        Material blockType = player.getLocation().getBlock().getType();
        return blockType == Material.BUBBLE_COLUMN;
    }

}
