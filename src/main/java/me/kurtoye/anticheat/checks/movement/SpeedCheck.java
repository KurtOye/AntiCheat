package me.kurtoye.anticheat.checks.movement;

import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SpeedCheck detects abnormal player movement speeds with ping/TPS compensation,
 * teleport/knockback resets, and progressive suspicion scoring using configurable thresholds.
 */
public class SpeedCheck implements Listener {
    private final Anticheat plugin;
    private final TeleportHandler teleportHandler;

    // State trackers
    private final Map<UUID, Vector> lastPosition = new HashMap<>();
    private final Map<UUID, Long> lastCheckTime = new HashMap<>();
    private final Map<UUID, Double> lastSpeed = new HashMap<>();
    private final Map<UUID, Long> lastVelocityChangeTime = new HashMap<>();
    private final Map<UUID, Long> lastTeleport = new HashMap<>();

    // Configurable parameters
    private final double violationLeeway;
    private final int suspicionPoints;

    public SpeedCheck(Anticheat plugin, TeleportHandler teleportHandler) {
        this.plugin = plugin;
        this.teleportHandler = teleportHandler;
        FileConfiguration config = plugin.getConfig();
        this.violationLeeway = config.getDouble("speedcheck.violation_leeway", 1.10);
        this.suspicionPoints = config.getInt("speedcheck.suspicion_points", 3);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Module toggle
        if (!plugin.getConfig().getBoolean("speedcheck.enabled", true)) return;
        // Only survival
        if (player.getGameMode() != GameMode.SURVIVAL) return;
        // Bypass permission
        if (player.hasPermission("anticheat.bypass")) return;
        // Skip after teleport or knockback
        if (MovementUtil.shouldIgnoreMovement(player, teleportHandler, lastVelocityChangeTime, lastTeleport)) return;

        // Calculate time delta
        long now = System.currentTimeMillis();
        long lastTime = lastCheckTime.getOrDefault(uuid, now);
        double timeDelta = (now - lastTime) / 1000.0; // seconds
        lastCheckTime.put(uuid, now);

        // Get positions for horizontal speed
        Vector from = lastPosition.getOrDefault(uuid, event.getFrom().toVector());
        Vector to = event.getTo().toVector();
        from.setY(0); to.setY(0);
        double distance = to.distance(from);
        lastPosition.put(uuid, to);

        // Compute speed (blocks/sec)
        double speed = (timeDelta > 0) ? distance / timeDelta : 0;
        double lastSpd = lastSpeed.getOrDefault(uuid, 0.0);
        lastSpeed.put(uuid, speed);

        // Calculate dynamic threshold
        double maxAllowed = MovementUtil.getMaxAllowedSpeed(player)
                * PingUtil.getPingCompensationFactor(player)
                * TpsUtil.getTpsCompensationFactor()
                * violationLeeway;

        // Extra leeway on high ping
        if (PingUtil.getPing(player) > 300) maxAllowed *= 1.2;

        // Flag if speed exceeds threshold
        if (speed > maxAllowed) {
            int sus = SuspicionHandler.addSuspicionPoints(uuid, suspicionPoints, "SpeedCheck", plugin);
            CheatReportHandler.handleSuspicionPunishment(player, plugin, "Speed Hack", sus);
            plugin.getLogger().fine(String.format(
                    "[SpeedCheck] %s speed=%.2f > allowed=%.2f", player.getName(), speed, maxAllowed));
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            lastVelocityChangeTime.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }
}
