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
    private final Map<UUID, Double> lastSpeed = new HashMap<>();

    private final TeleportHandler teleportHandler;
    private final Anticheat plugin;

    public SpeedCheck(Anticheat plugin, TeleportHandler teleportHandler) {
        this.plugin = plugin;
        this.teleportHandler = teleportHandler;
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR || player.isFlying()) {
            return; // Ignore Creative, Spectator, and flying players
        }

        if (player.isInsideVehicle() || player.isGliding()) {
            return; // Ignore boats, minecarts, Elytra
        }

        Vector currentPosition = event.getTo().toVector();
        currentPosition.setY(0f);
        long currentTime = System.currentTimeMillis();

        if (lastPosition.containsKey(playerId)) {
            long elapsedTime = currentTime - lastCheckTime.getOrDefault(playerId, currentTime);

            if (elapsedTime >= 1000) { // Check every second
                Vector lastPos = lastPosition.get(playerId);
                lastPos.setY(0f);
                double distance = currentPosition.distance(lastPos);

                double timeInSeconds = elapsedTime / 1000.0;
                double speed = distance / timeInSeconds; // Calculate speed in m/s

                lastPosition.put(playerId, currentPosition);
                lastCheckTime.put(playerId, currentTime);

                if (teleportHandler.isRecentTeleport(playerId, currentTime)) {
                    return; // Ignore movement checks for 3s after teleport
                }

                if (lastVelocityChangeTime.containsKey(playerId)) {
                    long velocityChangeTime = lastVelocityChangeTime.get(playerId);
                    if (currentTime - velocityChangeTime < 1200) {
                        return; // Ignore movement for 1.2s after knockback
                    }
                }

                double baseSpeed = getMaxAllowedSpeed(player);
                double pingFactor = PingUtil.getPingCompensationFactor(player);
                double tpsFactor = TpsUtil.getTpsCompensationFactor();

                double adjustedMaxSpeed = baseSpeed * pingFactor * tpsFactor * 1.10; // 10% leeway buffer

                // Acceleration check
                double lastRecordedSpeed = lastSpeed.getOrDefault(playerId, 0.0);
                double acceleration = Math.abs(speed - lastRecordedSpeed);
                lastSpeed.put(playerId, speed);

                // ✅ **FIX: Ignore small movement fluctuations**
                if (speed < 1.0) {
                    return; // Ignore speeds below 1.0 m/s
                }

                // ✅ **FIX: Increase acceleration tolerance**
                if (speed > adjustedMaxSpeed || acceleration > 6.5) { // Previously 5.0
                    player.sendMessage("§c[AntiCheat] Speed hack detected!");
                    plugin.getLogger().warning("⚠️ Player " + player.getName() + " exceeded speed limits: " +
                            speed + " m/s (max: " + adjustedMaxSpeed + " m/s)");
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
        double baseSpeed = 5.5; // Increased from 4.8 for accuracy
        if (player.isSprinting()) {
            baseSpeed = 7.0; // Sprinting speed
            if (player.getVelocity().getY() > 0 || !player.isOnGround()) {
                baseSpeed = 8.5; // Mid-air sprinting adjustment
            }
        }

        // Adjust for terrain-based movement effects
        Material material = player.getLocation().getBlock().getType();
        if (material == Material.ICE || material == Material.PACKED_ICE || material == Material.BLUE_ICE) {
            baseSpeed *= 1.6;
        } else if (material == Material.SOUL_SAND || material == Material.MUD) {
            baseSpeed *= 0.65;
        } else if (material == Material.HONEY_BLOCK) {
            baseSpeed *= 0.35;
        }

        // Adjust for potion effects
        if (player.hasPotionEffect(PotionEffectType.SPEED)) {
            int amplifier = Objects.requireNonNull(player.getPotionEffect(PotionEffectType.SPEED)).getAmplifier();
            baseSpeed *= (1.2 + (0.22 * amplifier));
        }

        if (player.hasPotionEffect(PotionEffectType.SLOWNESS)) {
            int amplifier = Objects.requireNonNull(player.getPotionEffect(PotionEffectType.SLOWNESS)).getAmplifier();
            baseSpeed *= (1.0 - (0.12 * amplifier));
        }

        // Adjust for enchantments (Soul Speed, Depth Strider)
        if (player.getInventory().getBoots() != null) {
            if (player.getInventory().getBoots().containsEnchantment(Enchantment.SOUL_SPEED)) {
                int level = player.getInventory().getBoots().getEnchantmentLevel(Enchantment.SOUL_SPEED);
                baseSpeed *= (1.1 + (0.18 * level));
            }
            if (player.getInventory().getBoots().containsEnchantment(Enchantment.DEPTH_STRIDER)) {
                int level = player.getInventory().getBoots().getEnchantmentLevel(Enchantment.DEPTH_STRIDER);
                baseSpeed *= (1.2 + (0.12 * level));
            }
        }

        return baseSpeed;
    }
}
