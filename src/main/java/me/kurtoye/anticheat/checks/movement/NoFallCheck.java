package me.kurtoye.anticheat.checks.movement;

import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.handlers.CheatReportHandler;
import me.kurtoye.anticheat.handlers.SuspicionHandler;
import me.kurtoye.anticheat.handlers.TeleportHandler;
import me.kurtoye.anticheat.utilities.MovementUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * NoFallCheck detects suspicious zero fall damage events after substantial drops.
 * It integrates teleport/knockback resets, ignores non-survival modes, and applies
 * progressive suspicion scoring based on configurable thresholds.
 */
public class NoFallCheck implements Listener {
    private final Anticheat plugin;
    private final TeleportHandler teleportHandler;

    // Tracking recent velocity and teleport events
    private final Map<UUID, Long> lastVelocityChange = new HashMap<>();
    private final Map<UUID, Long> lastTeleport = new HashMap<>();

    // Configuration parameters
    private final double minFallDistance;
    private final int suspicionPoints;

    public NoFallCheck(Anticheat plugin, TeleportHandler teleportHandler) {
        this.plugin = plugin;
        this.teleportHandler = teleportHandler;
        FileConfiguration cfg = plugin.getConfig();
        this.minFallDistance = cfg.getDouble("nofall.min_fall_distance", 3.5);
        this.suspicionPoints = cfg.getInt("nofall.suspicion_points", 2);
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (event.getCause() != DamageCause.FALL) return;
        // Only active in survival
        if (player.getGameMode() != GameMode.SURVIVAL) return;
        // Toggleable module
        if (!plugin.getConfig().getBoolean("nofall.enabled", true)) return;

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        // Skip immediately after teleports or knockbacks
        if (MovementUtil.shouldIgnoreMovement(player, teleportHandler, lastVelocityChange, lastTeleport)) {
            return;
        }

        // Check fall distance vs threshold
        double fallDist = player.getFallDistance();
        if (fallDist < minFallDistance) return;

        // If no final damage on a significant fall, flag suspicion
        if (event.getFinalDamage() <= 0) {
            int sus = SuspicionHandler.addSuspicionPoints(
                    uuid, suspicionPoints, "NoFallCheck", plugin);
            CheatReportHandler.handleSuspicionPunishment(
                    player, plugin, "NoFall", sus);
            plugin.getLogger().info(String.format(
                    "[NoFallCheck] %s fell %.2f blocks, took 0 damage, sus=%d",
                    player.getName(), fallDist, sus));
        }
    }
}
