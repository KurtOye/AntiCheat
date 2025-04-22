package me.kurtoye.anticheat.checks.movement;

import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.handlers.CheatReportHandler;
import me.kurtoye.anticheat.handlers.SuspicionHandler;
import me.kurtoye.anticheat.handlers.TeleportHandler;
import me.kurtoye.anticheat.utilities.MovementUtil;
import me.kurtoye.anticheat.utilities.PingUtil;
import me.kurtoye.anticheat.utilities.TpsUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * FlyCheck detects players hovering or flying by exceeding allowed air time.
 * - Applies ping/TPS compensation and configurable leeway.
 * - Resets after knockback or teleport via MovementUtil.
 * - Uses progressive suspicion scoring.
 */
public class FlyCheck implements Listener {
    private final Anticheat plugin;
    private final TeleportHandler teleportHandler;

    // Tracking airborne start times and resets
    private final Map<UUID, Long> airStartTime = new HashMap<>();
    private final Map<UUID, Long> lastVelocityChangeTime = new HashMap<>();
    private final Map<UUID, Long> lastTeleport = new HashMap<>();

    // Configurable parameters
    private final long maxAirTime;         // Base max air time in ms
    private final double violationLeeway;  // Multiplier for leeway
    private final int suspicionPoints;     // Points per violation

    public FlyCheck(Anticheat plugin, TeleportHandler teleportHandler) {
        this.plugin = plugin;
        this.teleportHandler = teleportHandler;
        FileConfiguration cfg = plugin.getConfig();
        this.maxAirTime       = cfg.getLong("flycheck.max_air_time", 1000);
        this.violationLeeway  = cfg.getDouble("flycheck.leeway", 1.0);
        this.suspicionPoints  = cfg.getInt("flycheck.suspicion_points", 3);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Toggle, gamemode, and bypass
        if (!plugin.getConfig().getBoolean("flycheck.enabled", true)) return;
        if (player.getGameMode() != GameMode.SURVIVAL) return;
        if (player.hasPermission("anticheat.bypass")) return;

        // Skip after teleports/knockback
        if (MovementUtil.shouldIgnoreMovement(player, teleportHandler, lastVelocityChangeTime, lastTeleport)) {
            airStartTime.remove(uuid);
            return;
        }

        // If on ground, reset
        if (player.isOnGround()) {
            airStartTime.remove(uuid);
            return;
        }

        long now = System.currentTimeMillis();
        // Initialize tracking
        if (!airStartTime.containsKey(uuid)) {
            airStartTime.put(uuid, now);
            return;
        }

        long start = airStartTime.get(uuid);
        // Compute adjusted limit
        double pingFactor = PingUtil.getPingCompensationFactor(player);
        double tpsFactor  = TpsUtil.getTpsCompensationFactor();
        long adjustedMax = (long)(maxAirTime * pingFactor * tpsFactor * violationLeeway);

        // If airborne too long, flag
        if (now - start > adjustedMax) {
            int sus = SuspicionHandler.addSuspicionPoints(uuid, suspicionPoints, "FlyCheck", plugin);
            CheatReportHandler.handleSuspicionPunishment(player, plugin, "Fly Hack", sus);
            plugin.getLogger().fine(String.format(
                    "[FlyCheck] %s airborne for %dms > %dms",
                    player.getName(), now - start, adjustedMax));
            // Reset to avoid repeat spam
            airStartTime.put(uuid, now);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p) {
            lastVelocityChangeTime.put(p.getUniqueId(), System.currentTimeMillis());
        }
    }
}
