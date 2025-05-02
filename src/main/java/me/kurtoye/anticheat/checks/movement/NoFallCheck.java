package me.kurtoye.anticheat.checks.movement;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.handlers.CheatReportHandler;
import me.kurtoye.anticheat.handlers.SuspicionHandler;
import me.kurtoye.anticheat.handlers.TeleportHandler;
import me.kurtoye.anticheat.utilities.MovementUtil;

import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Detects players avoiding natural fall damage through NoFall cheats.
 * Suspicion is triggered when a player falls beyond a threshold height
 * but receives no final damage.
 */
public class NoFallCheck implements Listener {

    private final Anticheat plugin;
    private final TeleportHandler teleportHandler;

    // Recent velocity or teleport resets
    private final Map<UUID, Long> lastVelocity = new HashMap<>();
    private final Map<UUID, Long> lastTeleport = new HashMap<>();

    private final boolean enabled;
    private final double minFallDistance;
    private final int suspicionPoints;

    public NoFallCheck(Anticheat plugin, TeleportHandler teleportHandler) {
        this.plugin = plugin;
        this.teleportHandler = teleportHandler;

        FileConfiguration cfg = plugin.getConfig();
        this.enabled = cfg.getBoolean("nofall.enabled", true);
        this.minFallDistance = cfg.getDouble("nofall.min_fall_distance", 3.5);
        this.suspicionPoints = cfg.getInt("nofall.suspicion_points", 2);
    }

    /**
     * Flags players who fall beyond a configured distance
     * and receive zero final damage (possible NoFall bypass).
     */
    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (!enabled || player.getGameMode() != GameMode.SURVIVAL || player.hasPermission("anticheat.bypass")) return;
        if (MovementUtil.shouldIgnoreMovement(player, teleportHandler, lastVelocity, lastTeleport)) return;

        double fallDist = player.getFallDistance();
        if (fallDist < minFallDistance || event.getFinalDamage() > 0) return;

        int sus = SuspicionHandler.addSuspicionPoints(id, suspicionPoints, "NoFallCheck", plugin);
        CheatReportHandler.handleSuspicionPunishment(player, plugin, "NoFall", sus);
        plugin.getLogger().info(String.format("[NoFallCheck] %s fell %.2f blocks with 0 damage, sus=%d",
                player.getName(), fallDist, sus));
    }
}
