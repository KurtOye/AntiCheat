package me.kurtoye.anticheat.checks.combat;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.handlers.CheatReportHandler;
import me.kurtoye.anticheat.handlers.SuspicionHandler;
import me.kurtoye.anticheat.utilities.PingUtil;
import me.kurtoye.anticheat.utilities.TpsUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AimbotCheck implements Listener {

    private final Anticheat plugin;

    // Configurable thresholds and suspicion increments
    private final double maxSnapAngle;                // Angle difference threshold for a "snap"
    private final double consistentAngleThreshold;    // Angle difference threshold considered "too smooth"
    private final int consistentAngleCountThreshold;  // Count of consecutive small differences to trigger suspicion
    private final int suspicionSnapPoints;            // Points added on a big snap event
    private final int suspicionSmoothPoints;          // Points added for extremely consistent (smooth) aiming
    private final int aimRecoveryHitCount;            // Number of normal hits required for recovery
    private final int aimRecoveryDeduction;           // Points to remove on aim recovery

    // Recovery timeout: if too much time passes between hits, reset the normal aim streak.
    private final long recoveryTimeout; // in milliseconds (e.g., 5000ms)

    // Per-player tracking for angles and normal aim streaks
    private final Map<UUID, Float> lastYawMap = new HashMap<>();
    private final Map<UUID, Float> lastPitchMap = new HashMap<>();
    private final Map<UUID, Integer> consistentAngleCount = new HashMap<>();
    private final Map<UUID, Integer> normalAimStreak = new HashMap<>();
    private final Map<UUID, Long> lastHitTime = new HashMap<>();

    public AimbotCheck(Anticheat plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();

        // Load thresholds from config
        this.maxSnapAngle = config.getDouble("aimbot.max_snap_angle", 80.0);
        this.consistentAngleThreshold = config.getDouble("aimbot.consistent_angle_threshold", 2.5);
        this.consistentAngleCountThreshold = config.getInt("aimbot.consistent_angle_count_threshold", 5);

        // Load suspicion increments
        this.suspicionSnapPoints = config.getInt("aimbot.suspicion_snap_points", 3);
        this.suspicionSmoothPoints = config.getInt("aimbot.suspicion_smooth_points", 2);

        // Aim recovery configuration
        this.aimRecoveryHitCount = config.getInt("aimbot.aim_recovery_hit_count", 3);
        this.aimRecoveryDeduction = config.getInt("aimbot.aim_recovery_deduction", 1);

        // Recovery timeout in ms (reset normal aim streak if too much time passes between hits)
        this.recoveryTimeout = config.getLong("aimbot.recovery_timeout", 5000);
    }

    /**
     * Tracks aim changes between consecutive hits.
     * - Detects large snap angles.
     * - Detects extremely consistent (smooth) aim across multiple hits.
     * - Provides aim recovery if normal aiming is detected.
     */
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // Reset normal aim streak if the time since the last hit exceeds recoveryTimeout.
        if (lastHitTime.containsKey(playerId)) {
            long elapsed = currentTime - lastHitTime.get(playerId);
            if (elapsed > recoveryTimeout) {
                normalAimStreak.put(playerId, 0);
            }
        }
        lastHitTime.put(playerId, currentTime);

        float currentYaw = player.getLocation().getYaw();
        float currentPitch = player.getLocation().getPitch();

        // If this is the first hit, store the current angles and exit.
        if (!lastYawMap.containsKey(playerId) || !lastPitchMap.containsKey(playerId)) {
            lastYawMap.put(playerId, currentYaw);
            lastPitchMap.put(playerId, currentPitch);
            return;
        }

        // Calculate absolute differences in yaw and pitch.
        double yawDiff = Math.abs(currentYaw - lastYawMap.get(playerId));
        double pitchDiff = Math.abs(currentPitch - lastPitchMap.get(playerId));

        // Correct for wrap-around angles.
        if (yawDiff > 180) yawDiff = 360 - yawDiff;
        if (pitchDiff > 180) pitchDiff = 360 - pitchDiff;

        // Adjust the maximum snap angle using ping and TPS compensation.
        double pingFactor = PingUtil.getPingCompensationFactor(player);
        double tpsFactor = TpsUtil.getTpsCompensationFactor();
        double adjustedMaxSnapAngle = maxSnapAngle * pingFactor * tpsFactor;

        double totalAngleDiff = yawDiff + pitchDiff;

        // Case 1: Large snap angle detection.
        if (totalAngleDiff > adjustedMaxSnapAngle) {
            int suspicion = SuspicionHandler.addSuspicionPoints(playerId, suspicionSnapPoints, "Aimbot - SnapAngle", plugin);
            CheatReportHandler.handleSuspicionPunishment(player, plugin, "AimBot SnapAngle", suspicion);
            // Reset recovery counters.
            normalAimStreak.put(playerId, 0);
            consistentAngleCount.put(playerId, 0);
        }
        // Case 2: Extremely consistent (smooth) aim.
        else if (totalAngleDiff < consistentAngleThreshold) {
            int count = consistentAngleCount.getOrDefault(playerId, 0) + 1;
            consistentAngleCount.put(playerId, count);

            // If the count exceeds the threshold, flag as suspicious.
            if (count >= consistentAngleCountThreshold) {
                int suspicion = SuspicionHandler.addSuspicionPoints(playerId, suspicionSmoothPoints, "Aimbot - SmoothAngle", plugin);
                CheatReportHandler.handleSuspicionPunishment(player, plugin, "AimBot SmoothAngle", suspicion);
                // Reset normal aim streak and consistent count.
                normalAimStreak.put(playerId, 0);
                consistentAngleCount.put(playerId, 0);
            } else {
                // Reset normal aim streak since the aim is too consistent.
                normalAimStreak.put(playerId, 0);
            }
        }
        // Case 3: Normal aim - variable changes.
        else {
            // Reset consistent angle count.
            consistentAngleCount.put(playerId, 0);
            // Increment normal aim streak.
            int streak = normalAimStreak.getOrDefault(playerId, 0) + 1;
            normalAimStreak.put(playerId, streak);

            // If a normal aim streak is sustained, remove some suspicion points.
            if (streak >= aimRecoveryHitCount) {
                // Uncomment the following line if your SuspicionHandler supports suspicion removal:
                // SuspicionHandler.removeSuspicionPoints(playerId, aimRecoveryDeduction, "Aimbot - AimRecovery");
                normalAimStreak.put(playerId, 0);
            }
        }

        // Update last recorded angles for the next hit.
        lastYawMap.put(playerId, currentYaw);
        lastPitchMap.put(playerId, currentPitch);
    }
}
