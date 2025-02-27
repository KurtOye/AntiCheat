package me.kurtoye.anticheat.checks.combat;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.handlers.CheatReportHandler;
import me.kurtoye.anticheat.utilities.PingUtil;
import me.kurtoye.anticheat.utilities.TpsUtil;
import me.kurtoye.anticheat.handlers.SuspicionHandler;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * AimbotCheck helps detect suspiciously automated aiming or rotation patterns
 * while allowing aim "recovery" (decay of suspicion) if the player shows normal,
 * legitimate aiming for several hits in a row.
 */
public class AimbotCheck implements Listener {

    private final Anticheat plugin;

    // Core config fields
    private final double maxSnapAngle;                // Large angle difference to be suspicious
    private final double consistentAngleThreshold;    // Very small angle differences => "smooth" aim suspicion
    private final int consistentAngleCountThreshold;  // How many consecutive suspicious angles trigger suspicion
    private final int suspicionSnapPoints;            // Suspicion points added on big snap
    private final int suspicionSmoothPoints;          // Suspicion points added on "smooth" aim
    private final int aimRecoveryHitCount;            // How many normal hits to reduce suspicion
    private final int aimRecoveryDeduction;           // How many suspicion points we remove upon each "recovery"

    // Per-player tracking
    private final Map<UUID, Float> lastYawMap = new HashMap<>();
    private final Map<UUID, Float> lastPitchMap = new HashMap<>();
    private final Map<UUID, Integer> consistentAngleCount = new HashMap<>();
    private final Map<UUID, Integer> normalAimStreak = new HashMap<>();

    public AimbotCheck(Anticheat plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();

        // Basic thresholds
        this.maxSnapAngle = config.getDouble("aimbot.max_snap_angle", 80.0);
        this.consistentAngleThreshold = config.getDouble("aimbot.consistent_angle_threshold", 2.5);
        this.consistentAngleCountThreshold = config.getInt("aimbot.consistent_angle_count_threshold", 5);

        // Suspicion increments
        this.suspicionSnapPoints = config.getInt("aimbot.suspicion_snap_points", 3);
        this.suspicionSmoothPoints = config.getInt("aimbot.suspicion_smooth_points", 2);

        // Aim Recovery
        this.aimRecoveryHitCount = config.getInt("aimbot.aim_recovery_hit_count", 3);
        this.aimRecoveryDeduction = config.getInt("aimbot.aim_recovery_deduction", 1);
    }

    /**
     * onEntityDamage tracks aim changes between hits.
     * If snap angles are too large or too small for multiple hits, suspicious points increment.
     * If normal aim is detected for enough hits in a row, we reduce suspicion points.
     */
    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;

        UUID playerId = player.getUniqueId();

        float currentYaw = player.getLocation().getYaw();
        float currentPitch = player.getLocation().getPitch();

        // If no reference angles yet, store and move on
        if (!lastYawMap.containsKey(playerId) || !lastPitchMap.containsKey(playerId)) {
            lastYawMap.put(playerId, currentYaw);
            lastPitchMap.put(playerId, currentPitch);
            return;
        }

        // Calculate angle differences
        double yawDiff = Math.abs(currentYaw - lastYawMap.get(playerId));
        double pitchDiff = Math.abs(currentPitch - lastPitchMap.get(playerId));

        // Correct wrap-around angles
        if (yawDiff > 180) yawDiff = 360 - yawDiff;
        if (pitchDiff > 180) pitchDiff = 360 - pitchDiff;

        // Adjust thresholds for ping & TPS
        double pingFactor = PingUtil.getPingCompensationFactor(player);
        double tpsFactor = TpsUtil.getTpsCompensationFactor();
        double adjustedMaxSnapAngle = maxSnapAngle * pingFactor * tpsFactor;

        // Sum of diffs for convenience
        double totalAngleDiff = yawDiff + pitchDiff;

        // --------------------------------------
        // 1) Check for big Snap angles
        // --------------------------------------
        if (totalAngleDiff > adjustedMaxSnapAngle) {
            // Snap angle suspicion
            int suspicion = SuspicionHandler.addSuspicionPoints(playerId, suspicionSnapPoints, "Aimbot - SnapAngle");
            CheatReportHandler.handleSuspicionPunishment(player, plugin, "AimBot SnapAngle", suspicion);

            // Reset any normal aim streak, because they did a big suspicious snap
            normalAimStreak.put(playerId, 0);
            // Also reset consistent angle count
            consistentAngleCount.put(playerId, 0);

        } else {
            // --------------------------------------
            // 2) Check for extremely consistent angles
            // --------------------------------------
            if (totalAngleDiff < consistentAngleThreshold) {
                int oldCount = consistentAngleCount.getOrDefault(playerId, 0) + 1;
                consistentAngleCount.put(playerId, oldCount);

                // Enough consecutive tiny angle differences => suspicious "smooth" aimbot
                if (oldCount >= consistentAngleCountThreshold) {
                    int suspicion = SuspicionHandler.addSuspicionPoints(playerId, suspicionSmoothPoints, "Aimbot - SmoothAngle");
                    CheatReportHandler.handleSuspicionPunishment(player, plugin, "AimBot SmoothAngle", suspicion);

                    // Reset any normal aim streak
                    normalAimStreak.put(playerId, 0);
                    // And reset consistent angle count
                    consistentAngleCount.put(playerId, 0);
                } else {
                    // Not triggered threshold yet
                    // Also reset normal aim streak since it's not normal
                    normalAimStreak.put(playerId, 0);
                }
            } else {
                // --------------------------------------
                // 3) Potentially normal aim => aim recovery
                // --------------------------------------
                int oldStreak = normalAimStreak.getOrDefault(playerId, 0) + 1;
                normalAimStreak.put(playerId, oldStreak);

                // Each time we detect normal aim, reset consistentAngleCount
                consistentAngleCount.put(playerId, 0);

                // If normal streak is large enough, we can reduce suspicion
                if (oldStreak >= aimRecoveryHitCount) {
                    // Remove some suspicion from the player
                   // SuspicionManager.removeSuspicionPoints(playerId, aimRecoveryDeduction, "Aimbot - AimRecovery");
                    // If you want, we can keep the streak going or reset
                    normalAimStreak.put(playerId, 0);
                }
            }
        }

        // Lastly, store angles for next time
        lastYawMap.put(playerId, currentYaw);
        lastPitchMap.put(playerId, currentPitch);
    }
}
