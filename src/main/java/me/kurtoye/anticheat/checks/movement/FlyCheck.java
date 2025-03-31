package me.kurtoye.anticheat.checks.movement;
import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.handlers.CheatReportHandler;
import me.kurtoye.anticheat.handlers.SuspicionHandler;
import me.kurtoye.anticheat.handlers.TeleportHandler;
import me.kurtoye.anticheat.utilities.MovementUtil;
import me.kurtoye.anticheat.utilities.PingUtil;
import me.kurtoye.anticheat.utilities.TpsUtil;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class FlyCheck implements Listener {

    private final Anticheat plugin;
    private final TeleportHandler teleportHandler;

    // Base allowed air time before flagging (in ms)
    private final long baseMaxAirTime;
    // Tolerance for natural vertical velocity changes (suggesting natural falling)
    private final double velocityTolerance;
    // Points to add per violation
    private final int suspicionPoints;
    // Tolerance for horizontal movement; if too low, it may indicate hovering
    private final double horizontalSpeedTolerance;

    // Tracking: when the player first left the ground and their last vertical velocity.
    private final Map<UUID, Long> airStartTimes = new HashMap<>();
    private final Map<UUID, Double> lastVerticalVelocity = new HashMap<>();

    public FlyCheck(Anticheat plugin, TeleportHandler teleportHandler) {
        this.plugin = plugin;
        this.teleportHandler = teleportHandler;
        FileConfiguration config = plugin.getConfig();
        // Base values read from config.yml (with sensible defaults)
        this.baseMaxAirTime = config.getLong("flycheck.max_air_time", 3000); // default 3000ms
        this.velocityTolerance = config.getDouble("flycheck.velocity_tolerance", 0.1); // default 0.1
        this.suspicionPoints = config.getInt("flycheck.suspicion_points", 3); // default 3 points
        // New configuration for horizontal movement tolerance (to detect hovering)
        this.horizontalSpeedTolerance = config.getDouble("flycheck.horizontal_speed_tolerance", 0.05); // default 0.05
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // Skip checks for legitimate conditions:
        if (player.getGameMode() == GameMode.CREATIVE ||
                player.getGameMode() == GameMode.SPECTATOR ||
                player.isGliding() || player.isFlying()) {
            resetPlayer(playerId);
            return;
        }

        // Ignore checks if movement should be skipped (e.g., recent teleport/knockback)
        if (MovementUtil.shouldIgnoreMovement(player, teleportHandler, null, null)) {
            resetPlayer(playerId);
            return;
        }

        // If the player is on the ground, reset our tracking data.
        if (player.isOnGround()) {
            resetPlayer(playerId);
            return;
        }

        long currentTime = System.currentTimeMillis();
        // Dynamically adjust allowed air time based on server lag
        double adjustedMaxAirTime = baseMaxAirTime * PingUtil.getPingCompensationFactor(player)
                * TpsUtil.getTpsCompensationFactor();

        // Record initial air time and vertical velocity if not already tracking
        if (!airStartTimes.containsKey(playerId)) {
            airStartTimes.put(playerId, currentTime);
            lastVerticalVelocity.put(playerId, player.getVelocity().getY());
            return;
        }

        long timeInAir = currentTime - airStartTimes.get(playerId);
        double currentYVelocity = player.getVelocity().getY();
        double previousYVelocity = lastVerticalVelocity.getOrDefault(playerId, currentYVelocity);
        double velocityChange = Math.abs(currentYVelocity - previousYVelocity);
        lastVerticalVelocity.put(playerId, currentYVelocity);

        // Calculate horizontal speed (ignoring vertical component)
        Vector horizontalVelocity = player.getVelocity().clone();
        horizontalVelocity.setY(0);
        double horizontalSpeed = horizontalVelocity.length();

        // Determine if the player is hovering:
        // They exhibit minimal vertical change and minimal horizontal movement.
        boolean isHovering = (velocityChange < velocityTolerance) && (horizontalSpeed < horizontalSpeedTolerance);

        // If the player has been airborne too long and is hovering, flag as flying.
        if (timeInAir > adjustedMaxAirTime && isHovering) {
            int newSuspicion = SuspicionHandler.addSuspicionPoints(playerId, suspicionPoints, "FlyCheck", plugin);
            CheatReportHandler.handleSuspicionPunishment(player, plugin, "Fly Hack Detected", newSuspicion);
            // Reset the air start time to avoid immediate repeated flags
            airStartTimes.put(playerId, currentTime);
        }
    }

    private void resetPlayer(UUID playerId) {
        airStartTimes.remove(playerId);
        lastVerticalVelocity.remove(playerId);
    }
}
