package me.kurtoye.anticheat.checks.combat;

import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;

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
 * Enhanced AutoClickerCheck:
 * - Tracks clicks per interval, flags high CPS and perfect stability.
 * - Applies ping/TPS compensation to CPS thresholds.
 * - Skips after teleport/knockback, non-survival, and bypass permission.
 * - Uses progressive suspicion scoring and debug logs for tuning.
 */
public class AutoClickerCheck implements Listener {
    private final Anticheat plugin;
    private final TeleportHandler teleportHandler;

    // Click tracking and violation counters
    private final Map<UUID, Integer> clickCounts = new HashMap<>();
    private final Map<UUID, Integer> consistencyViolations = new HashMap<>();
    private final Map<UUID, Long> lastVelocityChangeTime = new HashMap<>();
    private final Map<UUID, Long> lastTeleportTime = new HashMap<>();

    // Config parameters
    private final boolean enabled;
    private final long resetIntervalMs;
    private final int maxCPSThreshold;
    private final int consistencyCPSLimit;
    private final int consistencyViolationThreshold;
    private final int highCpsSuspicion;
    private final int consistencySuspicion;

    public AutoClickerCheck(Anticheat plugin, TeleportHandler teleportHandler) {
        this.plugin = plugin;
        this.teleportHandler = teleportHandler;
        FileConfiguration cfg = plugin.getConfig();
        this.enabled = cfg.getBoolean("autoclicker.enabled", true);
        this.resetIntervalMs = cfg.getLong("autoclicker.reset_interval_ms", 1000);
        this.maxCPSThreshold = cfg.getInt("autoclicker.max_cps_threshold", 20);
        this.consistencyCPSLimit = cfg.getInt("autoclicker.consistency_cps_limit", 15);
        this.consistencyViolationThreshold = cfg.getInt("autoclicker.consistency_violations", 5);
        this.highCpsSuspicion = cfg.getInt("autoclicker.high_cps_suspicion_points", 3);
        this.consistencySuspicion = cfg.getInt("autoclicker.consistency_suspicion_points", 4);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent ev) {
        if (!enabled) return;
        if (!(ev.getAction() == Action.LEFT_CLICK_AIR || ev.getAction() == Action.LEFT_CLICK_BLOCK)) return;

        Player player = ev.getPlayer();
        UUID id = player.getUniqueId();

        // Skip non-survival, bypass, teleport/knockback
        if (player.getGameMode() != GameMode.SURVIVAL) return;
        if (player.hasPermission("anticheat.bypass")) return;
        if (MovementUtil.shouldIgnoreMovement(player, teleportHandler, lastVelocityChangeTime, lastTeleportTime)) {
            clickCounts.remove(id);
            consistencyViolations.remove(id);
            return;
        }

        // Increment raw click count
        clickCounts.put(id, clickCounts.getOrDefault(id, 0) + 1);

        // Calculate CPS with reset mechanism
        int currentCPS = ClickUtil.calculateCPS(id, clickCounts, resetIntervalMs);

        // Adjust thresholds by ping/TPS
        double pingFactor = PingUtil.getPingCompensationFactor(player);
        double tpsFactor  = TpsUtil.getTpsCompensationFactor();

        int adjustedMaxCPS = (int) Math.ceil(maxCPSThreshold * pingFactor * tpsFactor);
        int adjustedConsistencyLimit = (int) Math.ceil(consistencyCPSLimit * pingFactor * tpsFactor);

        // Flag high CPS spikes
        if (currentCPS > adjustedMaxCPS) {
            int sus = SuspicionHandler.addSuspicionPoints(id, highCpsSuspicion, "AutoClickerCheck(HighCPS)", plugin);
            CheatReportHandler.handleSuspicionPunishment(player, plugin,
                    String.format("AutoClicker: CPS %d > %d", currentCPS, adjustedMaxCPS), sus);
            plugin.getLogger().fine(String.format(
                    "[AutoClickerCheck] %s CPS=%d > max=%d", player.getName(), currentCPS, adjustedMaxCPS));
        }

        // Flag perfectly consistent CPS
        if (ClickUtil.isConsistentlySameCPS(id, currentCPS, adjustedConsistencyLimit)) {
            int violations = consistencyViolations.getOrDefault(id, 0) + 1;
            consistencyViolations.put(id, violations);
            if (violations >= consistencyViolationThreshold) {
                int sus = SuspicionHandler.addSuspicionPoints(id, consistencySuspicion, "AutoClickerCheck(Consistency)", plugin);
                CheatReportHandler.handleSuspicionPunishment(player, plugin,
                        String.format("AutoClicker: %d stable CPS >= %d", currentCPS, adjustedConsistencyLimit), sus);
                plugin.getLogger().fine(String.format(
                        "[AutoClickerCheck] %s stable CPS violation #%d", player.getName(), violations));
                consistencyViolations.put(id, 0);
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent ev) {
        if (ev.getEntity() instanceof Player p) {
            lastVelocityChangeTime.put(p.getUniqueId(), System.currentTimeMillis());
        }
    }
}
