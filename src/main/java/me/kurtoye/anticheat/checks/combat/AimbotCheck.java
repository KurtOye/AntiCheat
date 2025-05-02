package me.kurtoye.anticheat.checks.combat;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.handlers.CheatReportHandler;
import me.kurtoye.anticheat.handlers.SuspicionHandler;
import me.kurtoye.anticheat.handlers.TeleportHandler;
import me.kurtoye.anticheat.utilities.MovementUtil;
import me.kurtoye.anticheat.utilities.PingUtil;
import me.kurtoye.anticheat.utilities.TpsUtil;

import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AimbotCheck monitors abnormal aim snapping behavior.
 *
 * It detects players who instantly rotate their camera by large angles between
 * attack events — a common indicator of aim-assist or KillAura modifications.
 *
 * The system applies latency and TPS compensation and logs yaw deltas for
 * progressive suspicion tracking. Detection is only triggered during PvP combat.
 */
public class AimbotCheck implements Listener {

    private final Anticheat plugin;
    private final TeleportHandler teleportHandler;

    // Tracks player yaw from last movement event
    private final Map<UUID, Float> lastYaw = new HashMap<>();

    // Grace periods after teleport/velocity to avoid false positives
    private final Map<UUID, Long> lastVelocity = new HashMap<>();
    private final Map<UUID, Long> lastTeleport = new HashMap<>();

    // Configurable thresholds
    private final boolean enabled;
    private final double maxSnapAngle;
    private final int suspicionPoints;

    /**
     * Loads AimbotCheck settings from config and links required utilities.
     */
    public AimbotCheck(Anticheat plugin, TeleportHandler teleportHandler) {
        this.plugin = plugin;
        this.teleportHandler = teleportHandler;

        FileConfiguration cfg = plugin.getConfig();
        this.enabled = cfg.getBoolean("aimbot.enabled", true);
        this.maxSnapAngle = cfg.getDouble("aimbot.max_snap_angle", 80.0);
        this.suspicionPoints = cfg.getInt("aimbot.suspicion_points", 2);
    }

    /**
     * Records player yaw on movement to later compare against attack angles.
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player p = event.getPlayer();
        lastYaw.put(p.getUniqueId(), p.getLocation().getYaw());
    }

    /**
     * Tracks teleport timestamps for temporary detection immunity.
     */
    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        lastTeleport.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Tracks knockback timestamps to avoid false aim detections after velocity.
     */
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p) {
            lastVelocity.put(p.getUniqueId(), System.currentTimeMillis());
        }
    }

    /**
     * Detects aim snapping behavior by comparing yaw before and after an attack.
     * Applies ping and TPS scaling to avoid misjudging rapid aim changes during combat.
     */
    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;

        UUID id = attacker.getUniqueId();

        // Skip players in creative, bypass permission, or when disabled
        if (!enabled || attacker.getGameMode() != GameMode.SURVIVAL || attacker.hasPermission("anticheat.bypass"))
            return;

        // Skip if player recently teleported or was knocked back
        if (MovementUtil.shouldIgnoreMovement(attacker, teleportHandler, lastVelocity, lastTeleport))
            return;

        Float previousYaw = lastYaw.get(id);
        if (previousYaw == null) return;

        float currentYaw = attacker.getLocation().getYaw();
        double snapDelta = Math.abs(wrapAngle(currentYaw - previousYaw));

        // Calculate dynamically adjusted threshold
        double threshold = maxSnapAngle
                * PingUtil.getPingCompensationFactor(attacker)
                * TpsUtil.getTpsCompensationFactor();

        // If snap is too sharp, flag suspicion
        if (snapDelta > threshold) {
            int sus = SuspicionHandler.addSuspicionPoints(id, suspicionPoints, "AimbotCheck", plugin);
            CheatReportHandler.handleSuspicionPunishment(attacker, plugin, "Aimbot Snap", sus);

            plugin.getLogger().fine(String.format(
                    "[AimbotCheck] %s snapped %.1f° > allowed %.1f°",
                    attacker.getName(), snapDelta, threshold));
        }
    }

    /**
     * Normalizes yaw angle to ±180° range for consistent snap comparisons.
     */
    private double wrapAngle(double angle) {
        angle %= 360;
        if (angle >= 180) angle -= 360;
        if (angle < -180) angle += 360;
        return angle;
    }
}
