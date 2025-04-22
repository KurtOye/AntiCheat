package me.kurtoye.anticheat.checks.movement;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.handlers.CheatReportHandler;
import me.kurtoye.anticheat.handlers.SuspicionHandler;
import me.kurtoye.anticheat.handlers.TeleportHandler;
import me.kurtoye.anticheat.utilities.MovementUtil;
import me.kurtoye.anticheat.utilities.PingUtil;
import me.kurtoye.anticheat.utilities.TpsUtil;
import me.kurtoye.anticheat.utilities.VelocityUtil;
import me.kurtoye.anticheat.utilities.WaterMovementUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Advanced JesusCheck with integrated simple water-walk detection.
 * - Simple horizontal/vertical checks for quick water-walk flags.
 * - Sprint-jump exploit detection.
 * - Sustained liquid-walk timing.
 * - Teleport/knockback resets, ping/TPS compensation, and progressive scoring.
 */
public class JesusCheck implements Listener {
    private final Anticheat plugin;
    private final TeleportHandler teleportHandler;

    // Tracking maps
    private final Map<UUID, Long> waterWalkStartTime = new HashMap<>();
    private final Map<UUID, Double> lastYVelocity = new HashMap<>();
    private final Map<UUID, Long> lastVelocityChangeTime = new HashMap<>();
    private final Map<UUID, Long> lastTeleport = new HashMap<>();

    // Simple detection thresholds
    private final double simpleMaxHorizontalSpeed;
    private final double simpleMaxVerticalOffset;
    private final int simpleSuspicionPoints;

    // Advanced detection thresholds
    private final long minLiquidWalkTime;      // ms before flagging sustained walking
    private final double maxAcceleration;      // vertical accel limit
    private final int sprintJumpSuspicion;     // points for sprint-jump exploit
    private final int liquidWalkSuspicion;     // points for sustained water-walking

    public JesusCheck(Anticheat plugin, TeleportHandler teleportHandler) {
        this.plugin = plugin;
        this.teleportHandler = teleportHandler;
        FileConfiguration cfg = plugin.getConfig();
        // Simple config
        this.simpleMaxHorizontalSpeed = cfg.getDouble("jesus.max_horizontal_speed", 1.5);
        this.simpleMaxVerticalOffset  = cfg.getDouble("jesus.max_vertical_offset", 0.1);
        this.simpleSuspicionPoints    = cfg.getInt("jesus.suspicion_points", 2);
        // Advanced config
        this.minLiquidWalkTime    = cfg.getLong("jesuscheck.min_liquid_walk_time", 2500);
        this.maxAcceleration      = cfg.getDouble("jesuscheck.max_acceleration", 3.5);
        this.sprintJumpSuspicion  = cfg.getInt("jesuscheck.sprintjump_suspicion_points", 3);
        this.liquidWalkSuspicion  = cfg.getInt("jesuscheck.liquidwalk_suspicion_points", 2);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();

        // Module toggle & mode/permission checks
        if (!plugin.getConfig().getBoolean("jesus.enabled", true)) return;
        if (player.getGameMode() != GameMode.SURVIVAL) return;
        if (player.hasPermission("anticheat.bypass")) return;
        // Skip after teleport or knockback
        if (MovementUtil.shouldIgnoreMovement(player, teleportHandler, lastVelocityChangeTime, lastTeleport)) {
            return;
        }

        // ----- Simple horizontal/vertical water-walk detection -----
        Vector from = event.getFrom().toVector().setY(0);
        Vector to   = event.getTo().toVector().setY(0);
        double horizontalDist = from.distance(to);
        double verticalOffset = Math.abs(event.getTo().getY() - event.getFrom().getY());
        // Check block below is water
        Material below = event.getFrom().clone().subtract(0,1,0).getBlock().getType();
        if (below == Material.WATER || below == Material.KELP) {
            double allowedSpeed = simpleMaxHorizontalSpeed
                    * PingUtil.getPingCompensationFactor(player)
                    * TpsUtil.getTpsCompensationFactor();
            if (horizontalDist > allowedSpeed || verticalOffset < simpleMaxVerticalOffset) {
                int sus = SuspicionHandler.addSuspicionPoints(
                        playerId, simpleSuspicionPoints, "JesusCheck(Simple)", plugin);
                CheatReportHandler.handleSuspicionPunishment(
                        player, plugin, "Jesus Hack (Simple)", sus);
                return;
            }
        }

        // ----- Sprint-jump exploit detection -----
        double vertVel = player.getVelocity().getY();
        if (WaterMovementUtil.isPlayerSprintJumpingOnWater(player)) {
            double lastY = lastYVelocity.getOrDefault(playerId, vertVel);
            if (vertVel < -0.08) {
                lastYVelocity.put(playerId, vertVel);
                lastY = vertVel;
            }
            if (lastY > -0.08 && vertVel >= 0.08) {
                int sus = SuspicionHandler.addSuspicionPoints(
                        playerId, sprintJumpSuspicion, "JesusCheck(SprintJump)", plugin);
                CheatReportHandler.handleSuspicionPunishment(
                        player, plugin, "Jesus Hack (Sprint-Jump)", sus);
                return;
            }
        }
        // ----- Sustained water-walking detection -----
        if (!WaterMovementUtil.isPlayerRunningOnWater(player)) {
            waterWalkStartTime.remove(playerId);
            return;
        }
        if (!waterWalkStartTime.containsKey(playerId)) {
            waterWalkStartTime.put(playerId, now);
            return;
        }
        // Acceleration check
        double accel = VelocityUtil.getAcceleration(player);
        if (accel > maxAcceleration) return;
        // Time threshold with compensation
        double timeFactor = minLiquidWalkTime
                * PingUtil.getPingCompensationFactor(player)
                * TpsUtil.getTpsCompensationFactor();
        if ((now - waterWalkStartTime.get(playerId)) > timeFactor) {
            int sus = SuspicionHandler.addSuspicionPoints(
                    playerId, liquidWalkSuspicion, "JesusCheck(Liquid)", plugin);
            CheatReportHandler.handleSuspicionPunishment(
                    player, plugin, "Jesus Hack (Liquid)", sus);
        }
    }

    @EventHandler
    public void onEntityDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p
                && event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            lastVelocityChangeTime.put(
                    p.getUniqueId(), System.currentTimeMillis());
        }
        if (event.getEntity() instanceof Player p
                && event.getCause() == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL) {
            // reset simple float detection when landing
            waterWalkStartTime.remove(p.getUniqueId());
        }
    }
}
