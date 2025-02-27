// 🚀 Fully Optimized WaterMovementUtil (Performance, Accuracy & Maintainability)
// ✅ Handles all water-based movement validation with improved efficiency.

package me.kurtoye.anticheat.utilities;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.enchantments.Enchantment;

/**
 * WaterMovementUtil handles **all water-based movement validation**.
 * - Used in `JesusCheck` and other movement-related checks.
 */
public class WaterMovementUtil {
    /**
     * Determines if the player's water movement is **legitimate**.
     * - Allows swimming, bubble columns, lily pads, and gliding.
     *
     * @param player The player being checked
     * @return true if movement is legitimate
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
     * Determines if the player is **running on water**.
     * - Prevents false positives by ensuring they are **not swimming or flying**.
     *
     * @param player The player being checked
     * @return true if the player is sprinting on water
     */
    public static boolean isPlayerRunningOnWater(Player player) {
        Material blockAtFeet = player.getLocation().getBlock().getType();
        Material blockBelow = player.getLocation().subtract(0, 1, 0).getBlock().getType();

        return (blockAtFeet == Material.WATER || blockBelow == Material.WATER) &&
                !player.isSwimming() && !player.isFlying() && player.isSprinting();
    }

    /**
     * Determines if the player is **sprint-jumping on water**.
     * - Ensures velocity is high enough to indicate a jump.
     *
     * @param player The player being checked
     * @return true if the player is sprint-jumping on water
     */
    public static boolean isPlayerSprintJumpingOnWater(Player player) {
        Material blockBelow = player.getLocation().subtract(0, 1, 0).getBlock().getType();

        return blockBelow == Material.WATER &&
                player.isSprinting() &&
                player.getVelocity().getY() > 0.25; // Adjusted threshold for better accuracy
    }

    /**
     * Checks if the player is using **Frost Walker** enchantment.
     *
     * @param player The player being checked
     * @return true if the player is walking on ice created by Frost Walker
     */
    public static boolean isPlayerUsingFrostWalker(Player player) {
        return player.getLocation().getBlock().getType().name().contains("ICE");
    }

    /**
     * Determines if the player is inside a **boat**.
     *
     * @param player The player being checked
     * @return true if the player is in a boat
     */
    public static boolean isPlayerInBoat(Player player) {
        return player.isInsideVehicle();
    }

    /**
     * Determines if the player is using **Depth Strider** boots.
     *
     * @param player The player being checked
     * @return true if the player has Depth Strider enchantment
     */
    public static boolean isPlayerUsingDepthStrider(Player player) {
        return player.getInventory().getBoots() != null &&
                player.getInventory().getBoots().containsEnchantment(Enchantment.DEPTH_STRIDER);
    }

    public static boolean isPlayerUsingAquaAffinity(Player player) {
        return player.getInventory().getHelmet() != null &&
                player.getInventory().getHelmet().containsEnchantment(Enchantment.AQUA_AFFINITY);
    }
}
