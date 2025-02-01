package me.kurtoye.anticheat.checks.movement;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.utilities.MovementUtil;
import me.kurtoye.anticheat.utilities.VelocityUtil;
import me.kurtoye.anticheat.utilities.CheatReportUtil;
import me.kurtoye.anticheat.utilities.PingUtil;
import me.kurtoye.anticheat.utilities.TpsUtil;
import me.kurtoye.anticheat.handlers.TeleportHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SpeedCheck detects excessive movement speed in Minecraft players.
 * - Uses `VelocityUtil` for **acceleration tracking** and **knockback handling**.
 * - Uses `CheatReportUtil` for **consistent cheat logging**.
 * - Uses `MovementUtil` for **fundamental movement mechanics**.
 * - Prevents false positives while accurately detecting speed hacks.
 */
public class SpeedCheck implements Listener {

    private final Map<UUID, Vector> lastPosition = new HashMap<>();
    private final Map<UUID, Long> lastCheckTime = new HashMap<>();
    private final Map<UUID, Double> lastSpeed = new HashMap<>();
    private final Map<UUID, Integer> violationCount = new HashMap<>();
    private final Map<UUID, Long> lastVelocityChangeTime = new HashMap<>();
    private final Map<UUID, Long> lastTeleport = new HashMap<>();

    private final TeleportHandler teleportHandler;
    private final Anticheat plugin;

    private static final int MAX_VIOLATIONS = 3; // Number of violations before flagging
    private static final long VIOLATION_RESET_TIME = 10000; // 10 seconds before violations reset

    /**
     * Constructor for SpeedCheck.
     *
     * @param plugin The main plugin instance.
     * @param teleportHandler The teleport handler used to track recent teleports.
     */
    public SpeedCheck(Anticheat plugin, TeleportHandler teleportHandler) {
        this.plugin = plugin;
        this.teleportHandler = teleportHandler;
    }

    /**
     * Detects speed hacks by measuring movement distance over time.
     *
     * ✅ Uses `VelocityUtil` for **knockback tracking & acceleration validation**.
     * ✅ Uses `CheatReportUtil` for **modular cheat reporting**.
     * ✅ Ensures **teleportation & lag compensation are handled properly**.
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // ✅ **Ignore movement checks if the player is in a valid state**
        if (MovementUtil.shouldIgnoreMovement(player, teleportHandler, lastVelocityChangeTime, lastTeleport)) {
            return;
        }

        Vector currentPosition = event.getTo().toVector();
        currentPosition.setY(0f);
        long currentTime = System.currentTimeMillis();

        if (!lastPosition.containsKey(playerId)) {
            lastPosition.put(playerId, currentPosition);
            lastCheckTime.put(playerId, currentTime);
            return;
        }

        long elapsedTime = currentTime - lastCheckTime.get(playerId);
        if (elapsedTime < 1000) {
            return;
        }

        Vector lastPos = lastPosition.get(playerId);
        lastPos.setY(0f);
        double distance = currentPosition.distance(lastPos);
        double speed = distance / (elapsedTime / 1000.0);

        lastPosition.put(playerId, currentPosition);
        lastCheckTime.put(playerId, currentTime);

        double maxAllowedSpeed = MovementUtil.getMaxAllowedSpeed(player) * PingUtil.getPingCompensationFactor(player) * TpsUtil.getTpsCompensationFactor() * 1.10;

        if (PingUtil.getPing(player) > 300) {
            maxAllowedSpeed *= 1.2;
        }

        double lastRecordedSpeed = lastSpeed.getOrDefault(playerId, 0.0);
        double acceleration = Math.abs(speed - lastRecordedSpeed);
        lastSpeed.put(playerId, speed);

        // ✅ **Acceleration tracking to catch gradual speed hacks**
        if (VelocityUtil.shouldIgnoreSpeedCheck(acceleration)) {
            CheatReportUtil.reportCheat(player, plugin, "Speed Hack (Abnormal Acceleration)");
            return;
        }

        // ✅ **Progressive violation tracking to avoid false positives**
        if (speed > maxAllowedSpeed) {
            int violations = violationCount.getOrDefault(playerId, 0) + 1;
            violationCount.put(playerId, violations);

            if (violations >= MAX_VIOLATIONS) {
                CheatReportUtil.reportCheat(player, plugin, "Speed Hack");
                violationCount.put(playerId, 0); // Reset violations after flagging
            }
        } else {
            violationCount.put(playerId, 0); // Reset violations if player moves normally
        }
    }

    /**
     * Registers velocity changes (knockback handling).
     */
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            UUID playerId = player.getUniqueId();
            lastVelocityChangeTime.put(playerId, System.currentTimeMillis());
        }
    }
}
