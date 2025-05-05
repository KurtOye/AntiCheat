package me.kurtoye.anticheat.checks.movement;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.handlers.CheatReportHandler;
import me.kurtoye.anticheat.handlers.SuspicionHandler;
import me.kurtoye.anticheat.handlers.TeleportHandler;
import me.kurtoye.anticheat.utilities.*;

import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SpeedCheck detects unnatural player movement exceeding allowed thresholds.
 * <p>
 * It considers player environment, latency, TPS, sprinting, terrain, and potion effects,
 * and additionally detects sprint-jumping abuse. It uses suspicion scoring and resets
 * gracefully after teleport or knockback to prevent false positives.
 */
public class SpeedCheck implements Listener {

    private final Anticheat plugin;
    private final TeleportHandler teleportHandler;

    // Per-player tracking maps
    private final Map<UUID, Vector> lastPosition = new HashMap<>();
    private final Map<UUID, Long> lastCheck = new HashMap<>();
    private final Map<UUID, Double> lastSpeed = new HashMap<>();
    private final Map<UUID, Long> lastVelocity = new HashMap<>();
    private final Map<UUID, Long> lastTeleport = new HashMap<>();

    // Configurable parameters
    private final boolean enabled;
    private final double leeway;
    private final int suspicionPoints;

    public SpeedCheck(Anticheat plugin, TeleportHandler teleportHandler) {
        this.plugin = plugin;
        this.teleportHandler = teleportHandler;

        FileConfiguration config = plugin.getConfig();
        this.enabled = config.getBoolean("speedcheck.enabled");
        this.leeway = config.getDouble("speedcheck.violation_leeway");
        this.suspicionPoints = config.getInt("speedcheck.suspicion_points");
    }

    /**
     * Handles movement detection per player move event.
     * Tracks speed and flags excessive movement, including sprint-jumping.
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (!enabled || player.getGameMode() != GameMode.SURVIVAL || player.hasPermission("anticheat.bypass")) return;

        if (MovementUtil.shouldIgnoreMovement(player, teleportHandler, lastVelocity, lastTeleport)) return;

        // Calculate elapsed time
        long last = lastCheck.getOrDefault(playerId, now);
        double elapsed = (now - last) / 1000.0;
        lastCheck.put(playerId, now);

        // 2D horizontal speed calculation
        Vector from = lastPosition.getOrDefault(playerId, event.getFrom().toVector());
        Vector to = event.getTo().toVector();
        from.setY(0); to.setY(0);
        double distance = to.distance(from);
        double speed = (elapsed > 0) ? distance / elapsed : 0;

        lastPosition.put(playerId, to);
        lastSpeed.put(playerId, speed);

          // Calculate maximum allowed speed
        double maxAllowed = MovementUtil.getMaxAllowedSpeed(player)
                * PingUtil.getPingCompensationFactor(player)
                * TpsUtil.getTpsCompensationFactor()
                * leeway;

        // Sprint-jump detection: fast airborne + sprint
        boolean isSprintJumping = player.isSprinting()
                && !player.isOnGround()
                && player.getFallDistance() < 1.0;

        double sprintJumpAllowance = maxAllowed * 1.5;

        if (isSprintJumping && speed > sprintJumpAllowance) {
            int suspicion = SuspicionHandler.addSuspicionPoints(playerId, suspicionPoints + 1, "SpeedCheck(SprintJump)", plugin);
            CheatReportHandler.handleSuspicionPunishment(player, plugin, "Speed Hack (Sprint-Jump)", suspicion);

            plugin.getLogger().fine(String.format(
                    "[SpeedCheck] %s sprint-jump speed=%.2f > limit=%.2f",
                    player.getName(), speed, sprintJumpAllowance));
        } else if (speed > maxAllowed) {
            int suspicion = SuspicionHandler.addSuspicionPoints(playerId, suspicionPoints, "SpeedCheck", plugin);
            CheatReportHandler.handleSuspicionPunishment(player, plugin, "Speed Hack", suspicion);

            plugin.getLogger().fine(String.format(
                    "[SpeedCheck] %s speed=%.2f > limit=%.2f",
                    player.getName(), speed, maxAllowed));
        }
    }

    /**
     * Registers last damage time to allow for knockback grace period.
     */
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            lastVelocity.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }
}
