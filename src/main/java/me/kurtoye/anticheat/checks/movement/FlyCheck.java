package me.kurtoye.anticheat.checks.movement;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.handlers.CheatReportHandler;
import me.kurtoye.anticheat.handlers.SuspicionHandler;
import me.kurtoye.anticheat.handlers.TeleportHandler;
import me.kurtoye.anticheat.utilities.*;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * FlyCheck detects players who remain airborne for longer than allowed,
 * while excluding edge cases like Elytra, tridents, swimming, and climbing.
 *
 * Thresholds adjust based on ping, TPS, and config values. Uses suspicion scoring.
 */
public class FlyCheck implements Listener {

    private final Anticheat plugin;
    private final TeleportHandler teleportHandler;

    private final Map<UUID, Long> airStart = new HashMap<>();
    private final Map<UUID, Long> lastVelocity = new HashMap<>();
    private final Map<UUID, Long> lastTeleport = new HashMap<>();

    // Configurable detection parameters
    private final boolean enabled;
    private final long maxAirTime;
    private final double leeway;
    private final int suspicionPoints;

    public FlyCheck(Anticheat plugin, TeleportHandler teleportHandler) {
        this.plugin = plugin;
        this.teleportHandler = teleportHandler;

        FileConfiguration cfg = plugin.getConfig();
        this.enabled = cfg.getBoolean("flycheck.enabled", true);
        this.maxAirTime = cfg.getLong("flycheck.max_air_time", 1000); // in ms
        this.leeway = cfg.getDouble("flycheck.leeway", 1.0);
        this.suspicionPoints = cfg.getInt("flycheck.suspicion_points", 1);
    }

    /**
     * Detects unnatural sustained air time, excluding climbing, elytra, and valid water states.
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Bypass cases
        if (!enabled || player.getGameMode() != GameMode.SURVIVAL)
            return;

        // Check grace conditions (knockback, teleport, mounts)
        if (MovementUtil.shouldIgnoreMovement(player, teleportHandler, lastVelocity, lastTeleport)) {
            airStart.remove(id);
            return;
        }

        // Always reset if grounded
        if (player.isOnGround()) {
            airStart.remove(id);
            return;
        }

        if (WaterMovementUtil.isLegitWaterMovement(player)){
            return;
        }

        // Edge Case Exemptions — Don't flag if player is:
        if (
                player.isGliding() ||                                       // Elytra
                        player.hasPotionEffect(org.bukkit.potion.PotionEffectType.SLOW_FALLING) || // Slow fall
                        player.getLocation().getBlock().getType() == Material.BUBBLE_COLUMN ||     // Bubble column
                        MovementUtil.isNearClimbable(player) ||              // Ladder, vine, scaffold
                        WaterMovementUtil.isPlayerInBoat(player) ||               // Boat
                        MovementUtil.isUsingRiptide(player) ||         // Trident riptide
                        player.isSwimming()                                       // Swimming upward
        ) {
            airStart.remove(id);
            return;
        }

        // Begin or continue air time tracking
        airStart.putIfAbsent(id, now);
        long elapsed = now - airStart.get(id);

        long adjustedLimit = (long) (maxAirTime
                * PingUtil.getPingCompensationFactor(player)
                * TpsUtil.getTpsCompensationFactor()
                * leeway);

        // Flag if exceeded
        if (elapsed > adjustedLimit) {
            int suspicion = SuspicionHandler.addSuspicionPoints(id, suspicionPoints, "FlyCheck", plugin);
            CheatReportHandler.handleSuspicionPunishment(player, plugin, "Fly Hack Detected", suspicion);

            plugin.getLogger().fine(String.format("[FlyCheck] %s airTime=%dms > allowed=%dms",
                    player.getName(), elapsed, adjustedLimit));

            airStart.put(id, now); // Reset after flag
        }
    }

    /**
     * Records last time the player received knockback to prevent false positives.
     */
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            lastVelocity.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }
}
