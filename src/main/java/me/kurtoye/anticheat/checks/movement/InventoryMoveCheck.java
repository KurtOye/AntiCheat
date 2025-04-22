package me.kurtoye.anticheat.checks.movement;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerMoveEvent;


import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.handlers.CheatReportHandler;
import me.kurtoye.anticheat.handlers.SuspicionHandler;
import me.kurtoye.anticheat.handlers.TeleportHandler;
import me.kurtoye.anticheat.utilities.MovementUtil;
import me.kurtoye.anticheat.utilities.PingUtil;
import me.kurtoye.anticheat.utilities.TpsUtil;
import me.kurtoye.anticheat.utilities.WaterMovementUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * InventoryMoveCheck detects unauthorized player movement while inventory GUI is open.
 * - Integrates MovementUtil for teleport/knockback resets.
 * - Skips legitimate movement (water, mounting, climbing).
 * - Applies ping/TPS compensation and progressive suspicion scoring.
 */
public class InventoryMoveCheck implements Listener {
    private final Anticheat plugin;
    private final TeleportHandler teleportHandler;

    private final Map<UUID, Location> lastPosition = new HashMap<>();
    private final Map<UUID, Long> lastVelocityChangeTime = new HashMap<>();
    private final Map<UUID, Long> lastTeleport = new HashMap<>();

    // Configurable parameters
    private final double minMovementThreshold;
    private final double distanceLeeway;
    private final int suspicionPoints;

    public InventoryMoveCheck(Anticheat plugin, TeleportHandler teleportHandler) {
        this.plugin = plugin;
        this.teleportHandler = teleportHandler;
        var cfg = plugin.getConfig();
        this.minMovementThreshold = cfg.getDouble("inventorymove.min_movement_threshold", 0.1);
        this.distanceLeeway      = cfg.getDouble("inventorymove.distance_leeway", 1.0);
        this.suspicionPoints     = cfg.getInt("inventorymove.suspicion_points", 3);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // Module toggle
        if (!plugin.getConfig().getBoolean("inventorymove.enabled", true)) return;
        // Only SURVIVAL mode
        if (player.getGameMode() != GameMode.SURVIVAL) return;
        // Bypass permission
        if (player.hasPermission("anticheat.bypass")) return;
        // Skip after teleport or knockback
        if (MovementUtil.shouldIgnoreMovement(player, teleportHandler, lastVelocityChangeTime, lastTeleport)) return;
        // Skip if player is inside a vehicle or not in inventory GUI
        if (player.isInsideVehicle()) return;
        if (player.getOpenInventory().getType() == InventoryType.CRAFTING) return;
        // Skip if legitimate water movement or using enchantments
        if (WaterMovementUtil.isLegitWaterMovement(player)
                || WaterMovementUtil.isPlayerUsingDepthStrider(player)
                || WaterMovementUtil.isPlayerUsingAquaAffinity(player)) {
            lastPosition.remove(uuid);
            return;
        }
        // Skip if standing on a climbable block (ladder/vine)
        Material blockBelow = player.getLocation().subtract(0,1,0).getBlock().getType();
        if (blockBelow == Material.LADDER || blockBelow == Material.VINE) {
            lastPosition.remove(uuid);
            return;
        }

        // Position tracking
        Location from = event.getFrom().clone();
        Location to   = event.getTo().clone();
        from.setY(0);
        to.setY(0);

        // Initialize lastPosition
        if (!lastPosition.containsKey(uuid)) {
            lastPosition.put(uuid, from);
            return;
        }

        // Compute horizontal distance
        double distance = lastPosition.get(uuid).distance(to);
        lastPosition.put(uuid, to);

        // Dynamic threshold with ping/TPS compensation
        double threshold = minMovementThreshold * distanceLeeway
                * PingUtil.getPingCompensationFactor(player)
                * TpsUtil.getTpsCompensationFactor();

        if (distance > threshold) {
            int sus = SuspicionHandler.addSuspicionPoints(uuid, suspicionPoints, "InventoryMove", plugin);
            CheatReportHandler.handleSuspicionPunishment(player, plugin, "Inventory Move Exploit", sus);
        }
    }

    @EventHandler
    public void onEntityDamageEvent(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p) {
            UUID id = p.getUniqueId();
            lastVelocityChangeTime.put(id, System.currentTimeMillis());
        }
    }
}
