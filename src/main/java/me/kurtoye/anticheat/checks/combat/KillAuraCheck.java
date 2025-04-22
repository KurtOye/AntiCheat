package me.kurtoye.anticheat.checks.combat;

import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.handlers.CheatReportHandler;
import me.kurtoye.anticheat.handlers.SuspicionHandler;
import me.kurtoye.anticheat.handlers.TeleportHandler;
import me.kurtoye.anticheat.utilities.MovementUtil;
import me.kurtoye.anticheat.utilities.PingUtil;
import me.kurtoye.anticheat.utilities.TpsUtil;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * KillAuraCheck detects:
 *   1) Attacks beyond human reach (max_reach),
 *   2) Unrealistically rapid multi‑target swings (multi_target_threshold per window_duration).
 *
 * Features:
 *  • Config‑driven parameters under killauracheck.*
 *  • Survival‑only, bypass‑permission gating.
 *  • Skips right after teleport/knockback (MovementUtil.shouldIgnoreMovement).
 *  • Ping/TPS compensation on all thresholds.
 *  • Progressive suspicion scoring + fine debug logs.
 */
public class KillAuraCheck implements Listener {
    private final Anticheat plugin;
    private final TeleportHandler teleportHandler;

    // Reset triggers
    private final Map<UUID, Long> lastVelocityChange = new HashMap<>();
    private final Map<UUID, Long> lastTeleport       = new HashMap<>();

    // Hit‑time window per player
    private final Map<UUID, Deque<Long>> hitTimestamps = new HashMap<>();

    // Configurable parameters
    private final boolean enabled;
    private final double  maxReach;
    private final int     reachPoints;
    private final int     multiTargetThreshold;
    private final long    windowDuration;
    private final int     multiTargetPoints;

    public KillAuraCheck(Anticheat plugin, TeleportHandler teleportHandler) {
        this.plugin = plugin;
        this.teleportHandler = teleportHandler;

        FileConfiguration cfg = plugin.getConfig();
        this.enabled               = cfg.getBoolean("killauracheck.enabled", true);
        this.maxReach              = cfg.getDouble("killauracheck.max_reach", 3.0);
        this.reachPoints           = cfg.getInt   ("killauracheck.reach_suspicion_points", 4);
        this.multiTargetThreshold  = cfg.getInt   ("killauracheck.multi_target_threshold", 3);
        this.windowDuration        = cfg.getLong  ("killauracheck.window_duration", 1000L);
        this.multiTargetPoints     = cfg.getInt   ("killauracheck.multi_target_suspicion_points", 3);
    }

    /** Record teleports to skip shortly after. */
    @EventHandler
    public void onTeleport(PlayerTeleportEvent ev) {
        lastTeleport.put(ev.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    /** Record knockback/velocity changes to skip shortly after. */
    @EventHandler
    public void onDamage(EntityDamageEvent ev) {
        if (ev.getEntity() instanceof Player p) {
            lastVelocityChange.put(p.getUniqueId(), System.currentTimeMillis());
        }
    }

    /**
     * On each hit, check:
     *  1) Reach: distance > maxReach * pingFactor * tpsFactor
     *  2) Multi‑target spam: > multiTargetThreshold hits in windowDuration
     */
    @EventHandler
    public void onHit(EntityDamageByEntityEvent ev) {
        if (!(ev.getDamager() instanceof Player attacker)) return;
        UUID id = attacker.getUniqueId();

        // Toggle & gating
        if (!enabled) return;
        if (attacker.getGameMode() != GameMode.SURVIVAL) return;
        if (attacker.hasPermission("anticheat.bypass")) return;
        // Skip after teleport/knockback
        if (MovementUtil.shouldIgnoreMovement(attacker, teleportHandler, lastVelocityChange, lastTeleport)) {
            // reset window to avoid carry‑over
            hitTimestamps.remove(id);
            return;
        }

        long now = System.currentTimeMillis();
        Entity target = ev.getEntity();

        // --- 1) Reach check ---
        double distance = attacker.getLocation().distance(target.getLocation());
        double pingFactor = PingUtil.getPingCompensationFactor(attacker);
        double tpsFactor  = TpsUtil.getTpsCompensationFactor();
        double allowedReach = maxReach * pingFactor * tpsFactor;

        if (distance > allowedReach) {
            int sus = SuspicionHandler.addSuspicionPoints(id, reachPoints, "KillAuraCheck(Reach)", plugin);
            CheatReportHandler.handleSuspicionPunishment(
                    attacker, plugin,
                    String.format("KillAura: reach %.2f > %.2f", distance, allowedReach),
                    sus
            );
            plugin.getLogger().fine(String.format(
                    "[KillAuraCheck] %s reach=%.2f > allowed=%.2f",
                    attacker.getName(), distance, allowedReach
            ));
        }

        // --- 2) Multi‑target spam check ---
        Deque<Long> dq = hitTimestamps.computeIfAbsent(id, k -> new ArrayDeque<>());
        // purge old hits
        while (!dq.isEmpty() && now - dq.peekFirst() > windowDuration) {
            dq.pollFirst();
        }
        dq.addLast(now);

        if (dq.size() > multiTargetThreshold) {
            int sus = SuspicionHandler.addSuspicionPoints(id, multiTargetPoints, "KillAuraCheck(MultiTarget)", plugin);
            CheatReportHandler.handleSuspicionPunishment(
                    attacker, plugin,
                    String.format("KillAura: %d hits in %dms", dq.size(), windowDuration),
                    sus
            );
            plugin.getLogger().fine(String.format(
                    "[KillAuraCheck] %s %d hits in %dms > %d",
                    attacker.getName(), dq.size(), windowDuration, multiTargetThreshold
            ));
            // reset for next spike
            dq.clear();
        }
    }
}
