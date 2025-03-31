
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
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SpeedCheck detects excessive movement speed in Minecraft players.
 * - Uses VelocityUtil for acceleration tracking and knockback handling.
 * - Uses CheatReportUtil for consistent cheat logging.
 * - Uses MovementUtil for fundamental movement mechanics.
 * - Prevents false positives while accurately detecting speed hacks.
 * - Now integrates with SuspicionManager for progressive suspicion scoring.
 */
public class SpeedCheck implements Listener {

    // Tracks each player's last known position (for distance-based speed measurement)
    private final Map<UUID, Vector> lastPosition = new HashMap<>();
    // Tracks the last time a speed check was performed on this player
    private final Map<UUID, Long> lastCheckTime = new HashMap<>();
    // Records the player's last recorded speed to measure acceleration changes
    private final Map<UUID, Double> lastSpeed = new HashMap<>();
    // Tracks when the player was last affected by knockback
    private final Map<UUID, Long> lastVelocityChangeTime = new HashMap<>();
    // Records when the player was last teleported (to ignore movement checks briefly)
    private final Map<UUID, Long> lastTeleport = new HashMap<>();

    private final TeleportHandler teleportHandler;
    private final Anticheat plugin;
    private final double violationLeeway;


    public SpeedCheck(Anticheat plugin, TeleportHandler teleportHandler) {
        this.plugin = plugin;
        this.teleportHandler = teleportHandler;
        FileConfiguration config = plugin.getConfig();
        // Leeway for adjusting maxAllowedSpeed from config
        this.violationLeeway = config.getDouble("speedcheck.violation_leeway", 1.10);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Ignore valid states (creative, spectator, recent teleport, knockback, etc.)
        if (MovementUtil.shouldIgnoreMovement(player, teleportHandler, lastVelocityChangeTime, lastTeleport)) {
            return;
        }

        Vector currentPosition = event.getTo().toVector();
        // Remove vertical dimension from speed calculations
        currentPosition.setY(0);
        long currentTime = System.currentTimeMillis();

        // First time storing player's position
        if (!lastPosition.containsKey(playerId)) {
            lastPosition.put(playerId, currentPosition);
            lastCheckTime.put(playerId, currentTime);
            return;
        }

        long elapsedTime = currentTime - lastCheckTime.get(playerId);
        // Check once per second for simpler average speed
        if (elapsedTime < 1000) {
            return;
        }

        Vector previousPosition = lastPosition.get(playerId).clone();
        previousPosition.setY(0);
        double distance = currentPosition.distance(previousPosition);
        double speed = distance / (elapsedTime / 1000.0);

        // Update stored info
        lastPosition.put(playerId, currentPosition);
        lastCheckTime.put(playerId, currentTime);

        // Calculate maximum allowed speed with ping & TPS compensation
        double maxAllowedSpeed = MovementUtil.getMaxAllowedSpeed(player)
                * PingUtil.getPingCompensationFactor(player)
                * TpsUtil.getTpsCompensationFactor()
                * violationLeeway;

        // Additional tolerance if player's ping > 300ms
        if (PingUtil.getPing(player) > 300) {
            maxAllowedSpeed *= 1.2;
        }

        // Track acceleration
        double lastRecordedSpeed = lastSpeed.getOrDefault(playerId, 0.0);
        double acceleration = Math.abs(speed - lastRecordedSpeed);
        lastSpeed.put(playerId, speed);


        // Core detection: if the speed is above threshold, increment suspicion
        if (speed > maxAllowedSpeed) {
            // Instead of local violation logic, increment suspicion points
            int suspicion = SuspicionHandler.addSuspicionPoints(playerId, 3, "SpeedCheck", plugin);
            CheatReportHandler.handleSuspicionPunishment(player, plugin, "Speed Hack", suspicion);
        }
    }

    /**
     * Registers velocity changes (knockback handling).
     * In case of entity damage, store a knockback grace period.
     */
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            UUID playerId = player.getUniqueId();
            lastVelocityChangeTime.put(playerId, System.currentTimeMillis());
        }
    }
}
