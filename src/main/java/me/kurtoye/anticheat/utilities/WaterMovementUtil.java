package me.kurtoye.anticheat.utilities;

import org.bukkit.Material;
import org.bukkit.entity.Player;

/**
 * WaterMovementUtil handles **all water-based movement validation**.
 */
public class WaterMovementUtil {

    public static boolean isLegitWaterMovement(Player player) {
        Material blockAtFeet = player.getLocation().getBlock().getType();
        Material blockBelow = player.getLocation().subtract(0, 1, 0).getBlock().getType();

        return player.isSwimming() ||
                blockAtFeet == Material.WATER ||
                blockBelow == Material.BUBBLE_COLUMN ||
                blockBelow == Material.LILY_PAD ||
                player.isGliding();
    }

    public static boolean isPlayerRunningOnWater(Player player) {
        Material blockAtFeet = player.getLocation().getBlock().getType();
        Material blockBelow = player.getLocation().subtract(0, 1, 0).getBlock().getType();

        return (blockAtFeet == Material.WATER || blockBelow == Material.WATER) &&
                !player.isSwimming() && !player.isFlying() && player.isSprinting();
    }

    public static boolean isPlayerSprintJumpingOnWater(Player player) {
        Material blockBelow = player.getLocation().subtract(0, 1, 0).getBlock().getType();

        return blockBelow == Material.WATER &&
                player.isSprinting() &&
                player.getVelocity().getY() > 0.3;
    }

    public static boolean isPlayerUsingFrostWalker(Player player) {
        return player.getLocation().getBlock().getType().toString().contains("ICE");
    }

    public static boolean isPlayerInBoat(Player player) {
        return player.isInsideVehicle();
    }

    public static boolean isPlayerUsingDepthStrider(Player player) {
        return player.getInventory().getBoots() != null &&
                player.getInventory().getBoots().getEnchantments().toString().contains("DEPTH_STRIDER");
    }
}
