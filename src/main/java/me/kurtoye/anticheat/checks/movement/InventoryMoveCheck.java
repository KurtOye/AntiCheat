package me.kurtoye.anticheat.checks.movement;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.handlers.CheatReportHandler;
import me.kurtoye.anticheat.handlers.SuspicionHandler;
import me.kurtoye.anticheat.handlers.TeleportHandler;
import me.kurtoye.anticheat.utilities.*;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerMoveEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Detects players moving while an external inventory GUI is open (e.g., chest, furnace).
 * This behavior is disallowed in vanilla Minecraft but enabled by InventoryMove cheats.
 * The check applies latency and TPS compensation and avoids false positives from common exemptions.
 */
public class InventoryMoveCheck implements Listener {

    private final Anticheat plugin;
    private final TeleportHandler teleportHandler;

    private final Map<UUID, Location> lastPosition = new HashMap<>();
    private final Map<UUID, Long> lastVelocity = new HashMap<>();
    private final Map<UUID, Long> lastTeleport = new HashMap<>();

    private final boolean enabled;
    private final double minMovementThreshold;
    private final double leeway;
    private final int suspicionPoints;

    public InventoryMoveCheck(Anticheat plugin, TeleportHandler teleportHandler) {
        this.plugin = plugin;
        this.teleportHandler = teleportHandler;

        FileConfiguration cfg = plugin.getConfig();
        this.enabled = cfg.getBoolean("inventorymove.enabled", true);
        this.minMovementThreshold = cfg.getDouble("inventorymove.min_movement_threshold", 0.2);
        this.leeway = cfg.getDouble("inventorymove.distance_leeway", 1.0);
        this.suspicionPoints = cfg.getInt("inventorymove.suspicion_points", 3);
    }

    /**
     * Monitors movement during GUI interactions. Flags players who move significantly while
     * an external inventory (e.g., chest, furnace) is open — which is not possible in vanilla Minecraft.
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();

        if (!enabled || player.getGameMode() != GameMode.SURVIVAL) return;

        if (MovementUtil.shouldIgnoreMovement(player, teleportHandler, lastVelocity, lastTeleport)) return;

        // Only detect if the player is interacting with a non-player inventory
        InventoryType open = player.getOpenInventory().getTopInventory().getType();
        if (open == InventoryType.CRAFTING || open == InventoryType.PLAYER) {
            lastPosition.remove(id);
            return;
        }

        // Skip known valid edge cases
        if (player.isInsideVehicle()
                || WaterMovementUtil.isLegitWaterMovement(player)
                || WaterMovementUtil.isPlayerUsingDepthStrider(player)
                || WaterMovementUtil.isPlayerUsingAquaAffinity(player)) {
            lastPosition.remove(id);
            return;
        }

        // Skip ladders and vines
        Material below = player.getLocation().subtract(0, 1, 0).getBlock().getType();
        if (below == Material.LADDER || below == Material.VINE) {
            lastPosition.remove(id);
            return;
        }

        // Calculate horizontal movement distance
        Location from = event.getFrom().clone();
        from.setY(0);
        Location to = event.getTo().clone();
        to.setY(0);

        if (!lastPosition.containsKey(id)) {
            lastPosition.put(id, from);
            return;
        }

        double distance = lastPosition.get(id).distance(to);
        lastPosition.put(id, to);

        double threshold = minMovementThreshold
                * leeway
                * PingUtil.getPingCompensationFactor(player)
                * TpsUtil.getTpsCompensationFactor();

        if (distance > threshold) {
            int suspicion = SuspicionHandler.addSuspicionPoints(id, suspicionPoints, "InventoryMoveCheck", plugin);
            CheatReportHandler.handleSuspicionPunishment(player, plugin, "Inventory Movement Detected", suspicion);
        }
    }

    /**
     * Tracks knockback events to prevent false flags during post-hit movement.
     */
    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p) {
            lastVelocity.put(p.getUniqueId(), System.currentTimeMillis());
        }
    }
}
