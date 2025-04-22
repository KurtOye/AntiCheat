package me.kurtoye.anticheat.checks.movement;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.handlers.CheatReportHandler;
import me.kurtoye.anticheat.handlers.SuspicionHandler;
import me.kurtoye.anticheat.utilities.*;
import me.kurtoye.anticheat.handlers.TeleportHandler;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 🚀 Refined JesusCheck:
 * - Integrates incremental suspicion for water-walking, sprint-jumping, and bouncing exploits.
 * - Uses config-based thresholds and suspicion increments for adaptive detection.
 * - Minimizes false positives by tracking acceleration, vertical velocity, and environment checks.
 */
public class JesusCheck implements Listener {

    private final Anticheat plugin;
    private final TeleportHandler teleportHandler;

    // Tracking maps
    private final Map<UUID, Long> waterWalkStartTime = new HashMap<>();
    private final Map<UUID, Double> lastYVelocity = new HashMap<>();
    private final Map<UUID, Long> lastVelocityChangeTime = new HashMap<>();
    private final Map<UUID, Long> lastTeleport = new HashMap<>();

    // Config-based thresholds
    private final long minLiquidWalkTime;      // e.g., 2500 ms
    private final double maxAcceleration;      // e.g., 3.5

    // Suspicion increments
    private final int sprintJumpSuspicion;
    private final int liquidWalkSuspicion;

    // Bouncing thresholds
    private static final double MIN_BOUNCE_VELOCITY = -0.12;
    private static final double MAX_BOUNCE_VELOCITY =  0.12;

    public JesusCheck(Anticheat plugin, TeleportHandler teleportHandler) {
        this.plugin = plugin;
        this.teleportHandler = teleportHandler;

        FileConfiguration config = plugin.getConfig();
        this.minLiquidWalkTime  = config.getLong("jesuscheck.min_liquid_walk_time", 2500);   // Default: 2.5s
        this.maxAcceleration    = config.getDouble("jesuscheck.max_acceleration", 3.5);

        // Suspicion increments (config-based)
        this.sprintJumpSuspicion = config.getInt("jesuscheck.sprintjump_suspicion_points", 3);
        this.liquidWalkSuspicion = config.getInt("jesuscheck.liquidwalk_suspicion_points", 2);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Current system time & player's vertical velocity
        long currentTime = System.currentTimeMillis();
        double verticalVelocity = player.getVelocity().getY();

        // 1) Validate if we should skip checks (e.g. Creative, knockback, recent teleport)
        if (MovementUtil.shouldIgnoreMovement(player, teleportHandler, lastVelocityChangeTime, lastTeleport)) {
            return;
        }

        // 2) Ignore natural bouncing & floating
        if (verticalVelocity >= MIN_BOUNCE_VELOCITY && verticalVelocity <= MAX_BOUNCE_VELOCITY) {
            return;
        }

        // 3) Ignore known safe conditions: Frost Walker, boats, Depth Strider, etc.
        if (WaterMovementUtil.isPlayerUsingFrostWalker(player)
                || WaterMovementUtil.isPlayerInBoat(player)
                || WaterMovementUtil.isPlayerUsingDepthStrider(player)) {
            return;
        }

        // 4) Detect sprint-jumping on water exploit
        if (WaterMovementUtil.isPlayerSprintJumpingOnWater(player)) {
            double lastStoredY = lastYVelocity.getOrDefault(playerId, verticalVelocity);
            if (verticalVelocity < -0.08) {
                // Record if they sank below -0.08 once
                lastStoredY = verticalVelocity;
                lastYVelocity.put(playerId, lastStoredY);
            }
            // Flag if they consistently maintain jump velocity above water
            if (lastStoredY > -0.08 && verticalVelocity >= 0.08) {
                int suspicion = SuspicionHandler.addSuspicionPoints(playerId, sprintJumpSuspicion, "JesusCheck (Sprint-Jump Exploit)", plugin);
                CheatReportHandler.handleSuspicionPunishment(player, plugin, "Jesus Hack (Sprint-Jumping)", suspicion);
            }
            return;
        }

        // 5) Reset tracking if player left water or used ladders/vines
        if (!WaterMovementUtil.isPlayerRunningOnWater(player)) {
            waterWalkStartTime.remove(playerId);
            return;
        }

        // 6) Start tracking water-walking time if not already
        if (!waterWalkStartTime.containsKey(playerId)) {
            waterWalkStartTime.put(playerId, currentTime);
            return;
        }

        // 7) Ping & TPS compensation to avoid lag-based false positives
        double pingFactor = PingUtil.getPingCompensationFactor(player);
        double tpsFactor  = TpsUtil.getTpsCompensationFactor();
        double adjustedTimeThreshold = minLiquidWalkTime * pingFactor * tpsFactor;

        // 8) Check vertical acceleration to avoid spurious flags
        double acceleration = VelocityUtil.getAcceleration(player);
        if (acceleration > maxAcceleration) {
            return;
        }

        // 9) If the player has been on water too long, suspect liquid-walk
        long startedWalking = waterWalkStartTime.get(playerId);
        if ((currentTime - startedWalking) > adjustedTimeThreshold) {
            int suspicion = SuspicionHandler.addSuspicionPoints(playerId, liquidWalkSuspicion, "JesusCheck (Liquid Walking)", plugin);
            CheatReportHandler.handleSuspicionPunishment(player, plugin, "Jesus Hack (Water-Walking)", suspicion);
        }
    }

    // 10) Possibly track velocity changes from knockback
    // or entityDamageEvent if needed, similar to SpeedCheck


}
