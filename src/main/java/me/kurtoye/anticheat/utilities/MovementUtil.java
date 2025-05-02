package me.kurtoye.anticheat.utilities;

import me.kurtoye.anticheat.handlers.TeleportHandler;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;

/**
 * Utility class for shared player movement logic.
 * Handles edge-case detection, terrain and potion modifiers, and legit state skipping.
 */
public final class MovementUtil {

    private MovementUtil() {}

    /**
     * Determines whether movement checks should be skipped due to legit conditions.
     */
    public static boolean shouldIgnoreMovement(
            Player player,
            TeleportHandler teleportHandler,
            Map<UUID, Long> lastVelocityChangeTime,
            Map<UUID, Long> lastTeleport
    ) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        // GameMode or active abilities
        if (player.getGameMode() != GameMode.SURVIVAL
                || player.isFlying()
                || player.isGliding()
                || isUsingRiptide(player)
                || player.isInsideVehicle()
                || isRidingMount(player)
                || player.isClimbing()
                || player.isSwimming()) return true;

        // Knockback / teleport protection
        if (VelocityUtil.wasRecentlyHit(id, now, lastVelocityChangeTime)) return true;
        if (teleportHandler.isRecentTeleport(id, now)) return true;

        return false;
    }

    /**
     * Checks for Riptide trident propulsion.
     */
    public static boolean isUsingRiptide(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        return player.isInWater() && item.containsEnchantment(Enchantment.RIPTIDE);
    }

    /**
     * Checks if player is riding a mount or vehicle.
     */
    public static boolean isRidingMount(Player player) {
        Entity vehicle = player.getVehicle();
        if (vehicle == null) return false;

        return switch (vehicle.getType()) {
            case HORSE, CAMEL, PIG, STRIDER, RAVAGER, MINECART, BOAT -> true;
            default -> false;
        };
    }

    /**
     * Calculates the player's max allowed speed with environment, terrain, and potion effects.
     */
    public static double getMaxAllowedSpeed(Player player) {
        double baseSpeed = player.isSprinting() ? 8.5 : 6.5;

        // Slow sneaking movement
        if (player.isSneaking()) {
            baseSpeed *= 0.3;
        }

        // Midair jumping bounce (e.g. small hops)
        if (!player.isOnGround() && player.getFallDistance() < 0.5) {
            baseSpeed = 8.5; // minor bounce speed
        } else if (!player.isOnGround()) {
            baseSpeed = 10; // normal airborne movement
        }

        baseSpeed = applyTerrainModifiers(player, baseSpeed);
        baseSpeed = applyPotionModifiers(player, baseSpeed);

        return baseSpeed;
    }

    /**
     * Applies terrain block effects like ice, honey, mud.
     */
    public static double applyTerrainModifiers(Player player, double baseSpeed) {
        Material block = player.getLocation().subtract(0, 0.1, 0).getBlock().getType();

        return switch (block) {
            case ICE, PACKED_ICE, BLUE_ICE -> baseSpeed * 1.6;
            case SOUL_SAND, MUD            -> baseSpeed * 0.65;
            case HONEY_BLOCK               -> baseSpeed * 0.35;
            default                        -> baseSpeed;
        };
    }

    /**
     * Applies potion effects (Speed, Slowness) to movement.
     */
    public static double applyPotionModifiers(Player player, double baseSpeed) {
        for (PotionEffect effect : player.getActivePotionEffects()) {
            if (effect.getType() == PotionEffectType.SPEED) {
                baseSpeed *= 1.0 + 0.2 * (effect.getAmplifier() + 1);
            } else if (effect.getType() == PotionEffectType.SLOWNESS) {
                baseSpeed *= 1.0 - 0.15 * (effect.getAmplifier() + 1);
            }
        }
        return baseSpeed;
    }

    /**
     * Checks if the player is directly next to climbable blocks (ladder, vine, etc.).
     * Simplified to reduce overhead while preserving accuracy.
     */
    public static boolean isNearClimbable(Player player) {
        Location base = player.getLocation();
        World world = base.getWorld();

        if (world == null) return false;

        // Check 4 horizontal directions and one above
        Location[] adjacent = {
                base.clone().add(1, 0, 0),
                base.clone().add(-1, 0, 0),
                base.clone().add(0, 0, 1),
                base.clone().add(0, 0, -1),
                base.clone().add(0, 1, 0)
        };

        for (Location loc : adjacent) {
            Material type = world.getBlockAt(loc).getType();
            if (type == Material.LADDER
                    || type == Material.VINE
                    || type == Material.TWISTING_VINES
                    || type == Material.WEEPING_VINES
                    || type == Material.SCAFFOLDING) {
                return true;
            }
        }

        return false;
    }


}
