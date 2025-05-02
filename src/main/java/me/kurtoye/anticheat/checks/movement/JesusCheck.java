package me.kurtoye.anticheat.checks.movement;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.handlers.CheatReportHandler;
import me.kurtoye.anticheat.handlers.SuspicionHandler;
import me.kurtoye.anticheat.handlers.TeleportHandler;
import me.kurtoye.anticheat.utilities.*;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JesusCheck detects when a player unnaturally stands or moves across the surface of water.
 * The core detection logic checks if the player is consistently over liquid without sinking.
 * Edge cases (boats, swimming, Frost Walker, etc.) are safely ignored.
 */
public class JesusCheck implements Listener {

    private final Anticheat plugin;
    private final TeleportHandler teleportHandler;

    // Tracking when players first started standing on water
    private final Map<UUID, Long> standingOnWaterSince = new HashMap<>();
    private final Map<UUID, Long> lastVelocityChange = new HashMap<>();
    private final Map<UUID, Long> lastTeleport = new HashMap<>();

    private final boolean enabled;
    private final long maxWaterStandTime;
    private final int suspicionPoints;

    public JesusCheck(Anticheat plugin, TeleportHandler teleportHandler) {
        this.plugin = plugin;
        this.teleportHandler = teleportHandler;

        FileConfiguration config = plugin.getConfig();
        this.enabled = config.getBoolean("jesus.enabled", true);
        this.maxWaterStandTime = config.getLong("jesus.max_water_stand_time", 1500); // ms
        this.suspicionPoints = config.getInt("jesus.suspicion_points", 2);
    }

    /**
     * Detects players standing on water without sinking.
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (!enabled || player.getGameMode() != GameMode.SURVIVAL)
            return;

        if (MovementUtil.shouldIgnoreMovement(player, teleportHandler, lastVelocityChange, lastTeleport))
            return;

        // Must be above water and not sinking
        Material below = player.getLocation().subtract(0, 0.1, 0).getBlock().getType();
        boolean isAboveLiquid = below == Material.WATER || below == Material.KELP || below == Material.SEAGRASS;
        boolean isInLiquid = player.getLocation().getBlock().getType() == Material.WATER;

        if (!isAboveLiquid || isInLiquid) {
            standingOnWaterSince.remove(id);
            return;
        }

        // Ignore legit conditions
        if (player.isSwimming()
                || WaterMovementUtil.isPlayerUsingFrostWalker(player)
                || WaterMovementUtil.isPlayerInBoat(player)
                || WaterMovementUtil.isPlayerUsingDepthStrider(player)
                || WaterMovementUtil.isInBubbleColumn(player)
                || MovementUtil.isNearClimbable(player)) {
            standingOnWaterSince.remove(id);
            return;
        }

        standingOnWaterSince.putIfAbsent(id, now);
        long standingTime = now - standingOnWaterSince.get(id);

        long threshold = (long) (maxWaterStandTime * PingUtil.getPingCompensationFactor(player)
                * TpsUtil.getTpsCompensationFactor());

        if (standingTime > threshold) {
            int suspicion = SuspicionHandler.addSuspicionPoints(id, suspicionPoints, "JesusCheck", plugin);
            CheatReportHandler.handleSuspicionPunishment(player, plugin, "Jesus Hack (Standing on Water)", suspicion);
            standingOnWaterSince.put(id, now); // reset to prevent constant reflagging
        }
    }

    /**
     * Resets timers when knockback or valid movement occurs.
     */
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            UUID id = player.getUniqueId();
            if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK)
                lastVelocityChange.put(id, System.currentTimeMillis());
            if (event.getCause() == EntityDamageEvent.DamageCause.FALL)
                standingOnWaterSince.remove(id);
        }
    }
}
