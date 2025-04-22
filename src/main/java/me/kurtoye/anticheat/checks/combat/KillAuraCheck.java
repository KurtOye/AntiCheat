package me.kurtoye.anticheat.checks.combat;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.handlers.CheatReportHandler;
import me.kurtoye.anticheat.handlers.SuspicionHandler;
import me.kurtoye.anticheat.handlers.TeleportHandler;
import me.kurtoye.anticheat.utilities.ClickUtil;
import me.kurtoye.anticheat.utilities.MovementUtil;
import me.kurtoye.anticheat.utilities.PingUtil;
import me.kurtoye.anticheat.utilities.TpsUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * KillAuraCheck detects extended reach and rhythmic attack patterns (auto-aim/auto-click),
 * integrating MovementUtil for teleport/knockback resets, latency/TPS compensation,
 * and progressive suspicion scoring based on config values.
 */
public class KillAuraCheck implements Listener {
    private final Anticheat plugin;
    private final TeleportHandler teleportHandler;

    // Tracking recent velocity and teleport events
    private final Map<UUID, Long> lastVelocityChangeTime = new HashMap<>();
    private final Map<UUID, Long> lastTeleport = new HashMap<>();

    // Configuration parameters
    private final double baseReach;
    private final int reachSuspicion;
    private final int maxHitCPS;
    private final int rhythmSuspicion;
    private final long rhythmResetTime;

    // Per-player hit counts for CPS tracking
    private final Map<UUID, Integer> hitCounts = new HashMap<>();

    public KillAuraCheck(Anticheat plugin, TeleportHandler teleportHandler) {
        this.plugin = plugin;
        this.teleportHandler = teleportHandler;
        FileConfiguration config = plugin.getConfig();

        // Reach detection config
        this.baseReach = config.getDouble("killauracheck.base_reach", 3.0);
        this.reachSuspicion = config.getInt("killauracheck.reach_suspicion", 5);

        // Rhythmic attack config
        this.maxHitCPS = config.getInt("killauracheck.max_hit_cps", 20);
        this.rhythmSuspicion = config.getInt("killauracheck.rhythm_suspicion", 3);
        this.rhythmResetTime = config.getLong("killauracheck.rhythm_reset_time", 10000);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        // Toggleable module
        if (!plugin.getConfig().getBoolean("killauracheck.enabled", true)) return;

        UUID uuid = player.getUniqueId();
        // Ignore during recent teleport or knockback
        if (MovementUtil.shouldIgnoreMovement(player, teleportHandler, lastVelocityChangeTime, lastTeleport)) {
            return;
        }

        // ===== Reach Detection =====
        double distance = player.getLocation().distance(target.getLocation());
        double allowedReach = baseReach
                * PingUtil.getPingCompensationFactor(player)
                * TpsUtil.getTpsCompensationFactor();
        if (distance > allowedReach) {
            int suspicion = SuspicionHandler.addSuspicionPoints(
                    uuid, reachSuspicion, "KillAura/Reach", plugin);
            CheatReportHandler.handleSuspicionPunishment(player, plugin, "KillAura/Reach", suspicion);
        }

        // ===== Rhythmic Attack Detection =====
        hitCounts.put(uuid, hitCounts.getOrDefault(uuid, 0) + 1);
        int currentCPS = ClickUtil.calculateCPS(uuid, hitCounts, rhythmResetTime);
        if (ClickUtil.isConsistentlySameCPS(uuid, currentCPS, maxHitCPS)) {
            int suspicion = SuspicionHandler.addSuspicionPoints(
                    uuid, rhythmSuspicion, "KillAura/Rhythm", plugin);
            CheatReportHandler.handleSuspicionPunishment(player, plugin, "KillAura/Rhythm", suspicion);
            // Reset hit count after suspicion flagged
            hitCounts.put(uuid, 0);
        }
    }
}
