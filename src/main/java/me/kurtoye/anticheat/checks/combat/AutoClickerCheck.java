package me.kurtoye.anticheat.checks.combat;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.handlers.CheatReportHandler;
import me.kurtoye.anticheat.handlers.SuspicionHandler;
import me.kurtoye.anticheat.handlers.TeleportHandler;
import me.kurtoye.anticheat.utilities.ClickUtil;
import me.kurtoye.anticheat.utilities.MovementUtil;
import me.kurtoye.anticheat.utilities.PingUtil;
import me.kurtoye.anticheat.utilities.TpsUtil;

import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AutoClickerCheck monitors player click frequency to detect automated input tools.
 *
 * It flags users who either:
 * - Exceed a maximum click-per-second (CPS) threshold.
 * - Maintain highly consistent CPS values over time (a pattern not typical of human input).
 *
 * The check compensates for ping and TPS variation, and integrates with suspicion scoring.
 */
public class AutoClickerCheck implements Listener {

    private final Anticheat plugin;
    private final TeleportHandler teleportHandler;

    // Tracks player click counts for calculating CPS
    private final Map<UUID, Integer> clickCounts = new HashMap<>();

    // Tracks repeated identical CPS values for consistency detection
    private final Map<UUID, Integer> consistencyViolations = new HashMap<>();

    // Tracks knockback or teleport events to exclude false positives
    private final Map<UUID, Long> lastVelocity = new HashMap<>();
    private final Map<UUID, Long> lastTeleport = new HashMap<>();

    // Configurable parameters
    private final boolean enabled;
    private final long resetInterval;
    private final int maxCps;
    private final int consistentLimit;
    private final int consistentViolations;
    private final int highCpsPoints;
    private final int consistencyPoints;

    /**
     * Loads AutoClickerCheck configuration and links shared handlers/utilities.
     */
    public AutoClickerCheck(Anticheat plugin, TeleportHandler teleportHandler) {
        this.plugin = plugin;
        this.teleportHandler = teleportHandler;

        FileConfiguration cfg = plugin.getConfig();
        this.enabled = cfg.getBoolean("autoclicker.enabled", true);
        this.resetInterval = cfg.getLong("autoclicker.reset_interval_ms", 1000);
        this.maxCps = cfg.getInt("autoclicker.max_cps_threshold", 20);
        this.consistentLimit = cfg.getInt("autoclicker.consistency_cps_limit", 15);
        this.consistentViolations = cfg.getInt("autoclicker.consistency_violations", 5);
        this.highCpsPoints = cfg.getInt("autoclicker.high_cps_suspicion_points", 3);
        this.consistencyPoints = cfg.getInt("autoclicker.consistency_suspicion_points", 4);
    }

    /**
     * Detects high CPS and consistent CPS behavior from left clicks.
     * Applies latency and server performance compensation.
     */
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!enabled) return;

        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        Action a = event.getAction();

        // Only monitor left-click actions in survival mode
        if (!(a == Action.LEFT_CLICK_AIR || a == Action.LEFT_CLICK_BLOCK)) return;
        if (p.getGameMode() != GameMode.SURVIVAL || p.hasPermission("anticheat.bypass")) return;

        // Skip detection if movement should be ignored (e.g. knockback or teleport grace)
        if (MovementUtil.shouldIgnoreMovement(p, teleportHandler, lastVelocity, lastTeleport)) return;

        // Count this click
        clickCounts.put(id, clickCounts.getOrDefault(id, 0) + 1);

        // Calculate current CPS within reset interval
        int cps = ClickUtil.calculateCPS(id, clickCounts, resetInterval);

        // Apply latency/tick rate compensation
        double pingFactor = PingUtil.getPingCompensationFactor(p);
        double tpsFactor = TpsUtil.getTpsCompensationFactor();

        int allowedCps = (int) Math.ceil(maxCps * pingFactor * tpsFactor);
        int consistentCps = (int) Math.ceil(consistentLimit * pingFactor * tpsFactor);

        // Flag if CPS exceeds allowed limit
        if (cps > allowedCps) {
            int sus = SuspicionHandler.addSuspicionPoints(id, highCpsPoints, "AutoClicker(HighCPS)", plugin);
            CheatReportHandler.handleSuspicionPunishment(p, plugin, "AutoClicker: CPS " + cps, sus);
            plugin.getLogger().fine(String.format("[AutoClickerCheck] %s CPS=%d > allowed=%d", p.getName(), cps, allowedCps));
        }

        // Check for consistent (non-human) CPS behavior over multiple intervals
        if (ClickUtil.isConsistentlySameCPS(id, cps, consistentCps)) {
            int count = consistencyViolations.getOrDefault(id, 0) + 1;
            consistencyViolations.put(id, count);

            if (count >= consistentViolations) {
                int sus = SuspicionHandler.addSuspicionPoints(id, consistencyPoints, "AutoClicker(Consistency)", plugin);
                CheatReportHandler.handleSuspicionPunishment(p, plugin, "AutoClicker: stable CPS", sus);
                plugin.getLogger().fine(String.format("[AutoClickerCheck] %s consistency flag #%d", p.getName(), count));
                consistencyViolations.put(id, 0); // Reset after flag
            }
        }
    }

    /**
     * Tracks when the player takes damage to allow a temporary movement grace period.
     */
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p) {
            lastVelocity.put(p.getUniqueId(), System.currentTimeMillis());
        }
    }
}
