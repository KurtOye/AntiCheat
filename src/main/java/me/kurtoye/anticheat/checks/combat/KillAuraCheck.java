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
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.*;

/**
 * Detects KillAura behavior by analyzing two core cheat indicators:
 *
 * 1. Reach Violations — Hitting entities from distances beyond legitimate combat range.
 * 2. Multi-Target Spamming — Hitting multiple entities in rapid succession.
 *
 * This check integrates ping and TPS compensation, tracks recent hit timings, and
 * applies configurable suspicion thresholds to escalate potential cheating behavior.
 *
 * Compatible with all utility and handler modules, using progressive suspicion scoring.
 */
public class KillAuraCheck implements Listener {

    private final Anticheat plugin;
    private final TeleportHandler teleportHandler;

    // Track recent player states (used for MovementUtil exemptions)
    private final Map<UUID, Long> lastVelocity = new HashMap<>();
    private final Map<UUID, Long> lastTeleport = new HashMap<>();

    // Store timestamps of recent hits to analyze multi-target spam
    private final Map<UUID, Deque<Long>> hitTimestamps = new HashMap<>();

    // Configurable thresholds
    private final boolean enabled;
    private final double maxReach;
    private final int reachPoints;
    private final int multiTargetThreshold;
    private final long windowDuration;
    private final int multiTargetPoints;

    /**
     * Initializes KillAuraCheck with settings loaded from config.yml.
     */
    public KillAuraCheck(Anticheat plugin, TeleportHandler teleportHandler) {
        this.plugin = plugin;
        this.teleportHandler = teleportHandler;

        FileConfiguration cfg = plugin.getConfig();
        this.enabled = cfg.getBoolean("killauracheck.enabled", true);
        this.maxReach = cfg.getDouble("killauracheck.max_reach", 3.0);
        this.reachPoints = cfg.getInt("killauracheck.reach_suspicion_points", 4);
        this.multiTargetThreshold = cfg.getInt("killauracheck.multi_target_threshold", 3);
        this.windowDuration = cfg.getLong("killauracheck.window_duration", 1000L);
        this.multiTargetPoints = cfg.getInt("killauracheck.multi_target_suspicion_points", 3);
    }

    /**
     * Records teleport timestamp to allow movement-based grace periods.
     */
    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        lastTeleport.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Records knockback timestamps to prevent false positives.
     */
    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p) {
            lastVelocity.put(p.getUniqueId(), System.currentTimeMillis());
        }
    }

    /**
     * Main detection logic for KillAura:
     * - Step 1: Detects hits beyond configurable reach limit (with lag compensation).
     * - Step 2: Detects excessive hits within a small time window (multi-target spam).
     */
    @EventHandler
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        UUID id = attacker.getUniqueId();
        long now = System.currentTimeMillis();

        // Skip exempt players and bypass scenarios
        if (!enabled || attacker.getGameMode() != GameMode.SURVIVAL || attacker.hasPermission("anticheat.bypass")) return;

        // Exempt cases: knockback, recent teleport, etc.
        if (MovementUtil.shouldIgnoreMovement(attacker, teleportHandler, lastVelocity, lastTeleport)) {
            hitTimestamps.remove(id);
            return;
        }

        // --------- 1) Reach Detection ---------
        Entity target = event.getEntity();
        double distance = attacker.getLocation().distance(target.getLocation());

        // Apply ping and TPS adjustments
        double adjustedMaxReach = maxReach
                * PingUtil.getPingCompensationFactor(attacker)
                * TpsUtil.getTpsCompensationFactor();

        if (distance > adjustedMaxReach) {
            int suspicion = SuspicionHandler.addSuspicionPoints(id, reachPoints, "KillAuraCheck(Reach)", plugin);
            CheatReportHandler.handleSuspicionPunishment(attacker, plugin,
                    String.format("KillAura: reach %.2f > %.2f", distance, adjustedMaxReach), suspicion);
            plugin.getLogger().fine(String.format("[KillAuraCheck] %s reach=%.2f > %.2f",
                    attacker.getName(), distance, adjustedMaxReach));
        }

        // --------- 2) Multi-Target Detection ---------
        Deque<Long> timestamps = hitTimestamps.computeIfAbsent(id, k -> new ArrayDeque<>());
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowDuration) {
            timestamps.pollFirst();
        }
        timestamps.addLast(now);

        if (timestamps.size() > multiTargetThreshold) {
            int suspicion = SuspicionHandler.addSuspicionPoints(id, multiTargetPoints, "KillAuraCheck(MultiTarget)", plugin);
            CheatReportHandler.handleSuspicionPunishment(attacker, plugin,
                    String.format("KillAura: %d hits in %dms", timestamps.size(), windowDuration), suspicion);
            plugin.getLogger().fine(String.format("[KillAuraCheck] %s %d hits in %dms > %d",
                    attacker.getName(), timestamps.size(), windowDuration, multiTargetThreshold));
            timestamps.clear(); // reset after flag
        }
    }
}
