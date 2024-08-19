package me.kurtoye.anticheat.movement;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.utilities.PingUtil;
import me.kurtoye.anticheat.utilities.TeleportHandler;
import me.kurtoye.anticheat.utilities.TpsUtil;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class SpeedCheck implements Listener {

    private final Map<UUID, Vector> lastPosition = new HashMap<>();
    private final Map<UUID, Long> lastCheckTime = new HashMap<>();
    private final Map<UUID, Long> lastVelocityChangeTime = new HashMap<>();

    private final TeleportHandler teleportHandler;
    private final Anticheat plugin;

    public SpeedCheck(Anticheat plugin, TeleportHandler teleportHandler) {
        this.plugin = plugin;
        this.teleportHandler = teleportHandler; // Store the reference to TeleportHandler
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (player.getGameMode() != GameMode.SURVIVAL && player.getGameMode() != GameMode.ADVENTURE) {
            return; //Ignore players that aren't in survival/adventure mode
        }

        if (player.isFlying() || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return; // Ignore players flying or in creative/spectator mode
        }

        Vector currentPosition = event.getTo().toVector();
        currentPosition.setY(0f); // To remove vertical considerations from the calculation.
        long currentTime = System.currentTimeMillis();

        if (lastPosition.containsKey(playerId)) {
            long elapsedTime = currentTime - lastCheckTime.getOrDefault(playerId, currentTime);

            if (elapsedTime >= 1000) { // Check every second


                Vector lastPos = lastPosition.get(playerId);
                lastPos.setY(0f); // To remove vertical considerations from the calculation
                double distance = currentPosition.distance(lastPos);

                // Convert elapsed time from milliseconds to seconds
                double timeInSeconds = elapsedTime / 1000.0;

                // Calculate speed in meters per second (m/s)
                double speed = distance / timeInSeconds;

                // Update stored values
                lastPosition.put(playerId, currentPosition);
                lastCheckTime.put(playerId, currentTime);

                // Check for recent teleportation
                if (teleportHandler.isRecentTeleport(playerId, currentTime)) {
                    return;
                }

                // Check for recent velocity change
                if (lastVelocityChangeTime.containsKey(playerId)) {
                    long velocityChangeTime = lastVelocityChangeTime.get(playerId);
                    if (currentTime - velocityChangeTime < 1000) { // Ignore speed check for 1 second after velocity change
                        return;
                    }
                }

                // Perform speed check
                double maxSpeed = getMaxAllowedSpeed(player);
                // double pingCompensationFactor = PingUtil.getPingCompensationFactor(player); Need to fix and add with multiplier like TPS
                double tpsCompensationFactor = TpsUtil.getTpsCompensationFactor();

                // Calculate the maximum allowed speed considering ping and TPS
                double adjustedMaxSpeed = maxSpeed * tpsCompensationFactor;

                if (speed > adjustedMaxSpeed) {
                    // Handle the speed violation (e.g., notify staff, kick player, etc.)
                    player.sendMessage("Speed hack detected!");
                    plugin.getLogger().info("Player " + player.getName() + " speed: " + speed + " m/s (max allowed: " + adjustedMaxSpeed + " m/s)");
                }
            }
        } else {
            lastPosition.put(playerId, currentPosition);
            lastCheckTime.put(playerId, currentTime);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            UUID playerId = player.getUniqueId();
            lastVelocityChangeTime.put(playerId, System.currentTimeMillis());
        }
    }

    private double getMaxAllowedSpeed(Player player) {

        double baseSpeed = 4.8; // Default walking speed
        if (player.isSprinting()) {
            baseSpeed = 6.1; // Default sprinting speed
            if (player.getVelocity().getY() > 0 || !player.isOnGround()) {
                baseSpeed = 7.6;
            }
        }

        // Adjust for potion effects
        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            int amplifier = Objects.requireNonNull(player.getPotionEffect(PotionEffectType.SPEED)).getAmplifier();
            baseSpeed *= amplifier;
        }

        if (player.hasPotionEffect(PotionEffectType.SLOWNESS)) {
            int amplifier = Objects.requireNonNull(player.getPotionEffect(PotionEffectType.SLOWNESS)).getAmplifier();
            baseSpeed *= amplifier;
        }

        // Adjust for terrain
        Material material = player.getLocation().getBlock().getType();
        if (material == Material.ICE || material == Material.PACKED_ICE || material == Material.BLUE_ICE) {
            baseSpeed *= 1.6;
        } else if (material == Material.SOUL_SAND || material == Material.SOUL_SOIL) {
            baseSpeed *= 0.6;

            // Soul Speed
            if (player.getInventory().getBoots() != null && player.getInventory().getBoots().containsEnchantment(Enchantment.SOUL_SPEED)) {
                int level = player.getInventory().getBoots().getEnchantmentLevel(Enchantment.SOUL_SPEED);
                baseSpeed *= 1.2 + (0.2 * level);
            }
        }

        // Elytra
        if (player.isGliding()) {
            baseSpeed = 31; // Adjust as necessary for Elytra flight
        }

        // Depth Strider
        if (player.getInventory().getBoots() != null && player.getInventory().getBoots().containsEnchantment(Enchantment.DEPTH_STRIDER)) {
            int level = player.getInventory().getBoots().getEnchantmentLevel(Enchantment.DEPTH_STRIDER);
            baseSpeed *= 1.2 + (0.33 * level);
        }

        // Sneaking
        if (player.isSneaking()) {
            baseSpeed *= 0.5; // Adjust as necessary for sneaking
        }

        // Swimming
        if (player.isSwimming()) {
            baseSpeed *= 1.7; // Adjust as necessary for swimming
        }

        // Cobwebs
        if (material == Material.COBWEB) {
            baseSpeed *= 0.45; // Adjust as necessary for cobwebs
        }

        return baseSpeed;
    }
}
