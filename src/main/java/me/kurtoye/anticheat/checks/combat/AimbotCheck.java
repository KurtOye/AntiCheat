package me.kurtoye.anticheat.checks.combat;

import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.handlers.CheatReportHandler;
import me.kurtoye.anticheat.handlers.SuspicionHandler;
import me.kurtoye.anticheat.handlers.TeleportHandler;

import me.kurtoye.anticheat.utilities.MovementUtil;
import me.kurtoye.anticheat.utilities.PingUtil;
import me.kurtoye.anticheat.utilities.TpsUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Complete AimbotCheck:
 * - Uses TeleportHandler injection.
 * - Tracks yaw changes to detect snap-angles on attacks.
 * - Applies ping/TPS compensation and configurable leeway.
 * - Skips after teleport/knockback, non-survival, or bypass permission.
 * - Uses progressive suspicion scoring and fine-level debug logs.
 */
public class AimbotCheck implements Listener {
    private final Anticheat plugin;
    private final TeleportHandler teleportHandler;

    private final Map<UUID, Float> lastYaw = new HashMap<>();
    private final Map<UUID, Long> lastVelocityChange = new HashMap<>();
    private final Map<UUID, Long> lastTeleport = new HashMap<>();

    private final double maxSnapAngle;
    private final int suspicionPoints;

    public AimbotCheck(Anticheat plugin, TeleportHandler teleportHandler) {
        this.plugin = plugin;
        this.teleportHandler = teleportHandler;

        FileConfiguration cfg = plugin.getConfig();
        this.maxSnapAngle    = cfg.getDouble("aimbot.max_snap_angle", 80.0);
        this.suspicionPoints = cfg.getInt("aimbot.suspicion_points", 4);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent ev) {
        Player p = ev.getPlayer();
        lastYaw.put(p.getUniqueId(), p.getLocation().getYaw());
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent ev) {
        lastTeleport.put(ev.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onEntityDamageEvent(EntityDamageEvent ev) {
        if (ev.getEntity() instanceof Player p) {
            lastVelocityChange.put(p.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onEntityDamageByEntity(EntityDamageByEntityEvent ev) {
        if (!(ev.getDamager() instanceof Player attacker)) return;
        UUID id = attacker.getUniqueId();

        if (!plugin.getConfig().getBoolean("aimbot.enabled", true)) return;
        if (attacker.getGameMode() != GameMode.SURVIVAL) return;
        if (attacker.hasPermission("anticheat.bypass")) return;
        if (MovementUtil.shouldIgnoreMovement(attacker, teleportHandler, lastVelocityChange, lastTeleport)) {
            return;
        }

        Float oldYaw = lastYaw.get(id);
        if (oldYaw == null) return;
        float newYaw = attacker.getLocation().getYaw();
        double delta = Math.abs(wrapAngle(newYaw - oldYaw));

        double threshold = maxSnapAngle
                * PingUtil.getPingCompensationFactor(attacker)
                * TpsUtil.getTpsCompensationFactor();

        if (delta > threshold) {
            int sus = SuspicionHandler.addSuspicionPoints(id, suspicionPoints, "AimbotCheck", plugin);
            CheatReportHandler.handleSuspicionPunishment(attacker, plugin, "Aimbot Snap", sus);
            plugin.getLogger().fine(String.format(
                    "[AimbotCheck] %s snapped %.1f° > allowed %.1f°",
                    attacker.getName(), delta, threshold));
        }
    }

    /**
     * Wraps any yaw delta into the range [-180, +180] degrees
     */
    private double wrapAngle(double angle) {
        angle %= 360;
        if (angle >= 180) angle -= 360;
        if (angle < -180) angle += 360;
        return angle;
    }
}