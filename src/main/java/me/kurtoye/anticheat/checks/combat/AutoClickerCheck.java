package me.kurtoye.anticheat.checks.combat;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.handlers.CheatReportHandler;
import me.kurtoye.anticheat.utilities.ClickUtil;
import me.kurtoye.anticheat.handlers.SuspicionHandler;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AutoClickerCheck implements Listener {
    private final Map<UUID, Long> lastClickTime = new HashMap<>();
    private final Map<UUID, Integer> clickCount  = new HashMap<>();
    private final Map<UUID, Integer> consistentClickViolations = new HashMap<>();
    private final Anticheat plugin;
    private final int maxCPS;
    private final int maxConsistentCPS;
    private final int consistencyViolationThreshold;
    private final long violationResetTime;

    // Suspicion points to add for each violation scenario
    private final int highCpsPoints;         // Points for exceeding maxCPS
    private final int perfectStabilityPoints; // Points for sustained perfect CPS

    public AutoClickerCheck(Anticheat plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        this.maxCPS = config.getInt("autoclicker.max_cps", 20);
        this.maxConsistentCPS = config.getInt("autoclicker.max_consistent_cps", 12);
        this.consistencyViolationThreshold = config.getInt("autoclicker.consistency_violation_threshold", 5);
        this.violationResetTime = config.getLong("autoclicker.violation_reset_time", 10000);

        this.highCpsPoints = config.getInt("autoclicker.points_highCPS", 3);
        this.perfectStabilityPoints = config.getInt("autoclicker.points_perfectStability", 2);
    }

    @EventHandler
    public void onPlayerClick(PlayerInteractEvent event) {
        // If the player is left-clicking a block (i.e., breaking blocks), skip auto-click detection.
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        long lastClick = lastClickTime.getOrDefault(playerId, currentTime);
        long timeSinceLastClick = currentTime - lastClick;
        // Ignore clicks that are faster than physically possible (less than 50ms apart)
        if (timeSinceLastClick < 50) return;

        lastClickTime.put(playerId, currentTime);

        // Increase the player's click count.
        int updatedClickCount = clickCount.getOrDefault(playerId, 0) + 1;
        clickCount.put(playerId, updatedClickCount);

        // Calculate current CPS using ClickUtil's helper (which resets count after violationResetTime).
        int currentCPS = ClickUtil.calculateCPS(playerId, clickCount, violationResetTime);

        // 1) High CPS detection: If CPS exceeds the threshold, add suspicion points.
        if (currentCPS > maxCPS) {
            int newSuspicion = SuspicionHandler.addSuspicionPoints(playerId, highCpsPoints, "AutoClickerCheck (HighCPS)", plugin);
            CheatReportHandler.handleSuspicionPunishment(player, plugin,
                    "AutoClicker Detected (CPS: " + currentCPS + ")", newSuspicion);
            clickCount.put(playerId, 0); // Reset count after flagging
            plugin.getLogger().info("[DATA-LOG] " + player.getName() + " flagged for highCPS => " + currentCPS);
            return;
        }

        // 2) Consistent CPS detection: Check if CPS remains exactly stable over multiple intervals.
        if (ClickUtil.isConsistentlySameCPS(playerId, currentCPS, maxConsistentCPS)) {
            int oldViolations = consistentClickViolations.getOrDefault(playerId, 0);
            int newViolations = oldViolations + 1;
            consistentClickViolations.put(playerId, newViolations);

            if (newViolations >= consistencyViolationThreshold) {
                int newSuspicion = SuspicionHandler.addSuspicionPoints(playerId, perfectStabilityPoints, "AutoClickerCheck (Consistency)", plugin);
                CheatReportHandler.handleSuspicionPunishment(player, plugin,
                        "AutoClicker Detected (Perfect CPS Stability: " + currentCPS + ")", newSuspicion);
                consistentClickViolations.put(playerId, 0);
                plugin.getLogger().info("[DATA-LOG] " + player.getName() + " flagged for perfect stable CPS => " + currentCPS);
            }
        } else {
            // Reset violation counter if CPS is not consistently high.
            consistentClickViolations.put(playerId, 0);
        }
    }
}
