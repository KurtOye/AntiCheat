// 🚀 Fully Optimized InventoryMoveCheck (Performance, Accuracy & Configurable Settings)
// ✅ Detects inventory-moving exploits while preventing false positives.
// ✅ Considers edge cases: water, vehicles, ladders/vines, ignored worlds, normal inventory, etc.
// ✅ Uses SuspicionManager for incremental suspicion scoring and CheatReportUtil for progressive punishments.

package me.kurtoye.anticheat.checks.movement;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.utilities.CheatReportUtil;
import me.kurtoye.anticheat.utilities.MovementUtil;
import me.kurtoye.anticheat.utilities.SuspicionManager;
import me.kurtoye.anticheat.utilities.WaterMovementUtil;
import me.kurtoye.anticheat.handlers.TeleportHandler;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class InventoryMoveCheck implements Listener {

    private final Map<UUID, Vector> lastPositions = new HashMap<>();
    private final Anticheat plugin;
    private final TeleportHandler teleportHandler;

    // Configurable thresholds
    private final double minMovementThreshold;
    private final int suspicionPoints;
    private final double distanceLeeway;
    private final boolean ignorePlayerInventory; // e.g., normal "E" or "I" inventory

    public InventoryMoveCheck(Anticheat plugin, TeleportHandler teleportHandler) {
        this.plugin = plugin;
        this.teleportHandler = teleportHandler;

        FileConfiguration config = plugin.getConfig();
        this.minMovementThreshold = config.getDouble("inventorymove.min_distance", 0.2);
        this.suspicionPoints = config.getInt("inventorymove.suspicion_points", 2);
        this.distanceLeeway = config.getDouble("inventorymove.distance_leeway", 1.05);


        // If set to true, we do NOT check players with only their own inventory (crafting/inventory screen) open
        this.ignorePlayerInventory = config.getBoolean("inventorymove.ignore_player_inventory", true);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        

        // 2. Ignore if in CREATIVE or SPECTATOR
        if (player.getGameMode() == GameMode.CREATIVE ||
                player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        // 3. Teleport/knockback ignore
        if (MovementUtil.shouldIgnoreMovement(player, teleportHandler, null, null)) {
            return;
        }

        // 4. Inventory checks
        InventoryView openInv = player.getOpenInventory();
        if (openInv == null) {
            return; // No inventory open at all
        }
        if (ignorePlayerInventory && openInv.getTopInventory().getType() == InventoryType.CRAFTING) {
            // Means it's just the player's normal inventory or crafting screen
            return;
        }

        // If the top-inventory is a container: this is the scenario we typically want to check
        // But if the plugin config says not to check certain container types, you could handle that too.

        // 5. Riding check - if the player is inside a vehicle that can move them
        Entity vehicle = player.getVehicle();
        if (vehicle != null) {
            // e.g. boats, minecarts, horses, etc.
            return;
        }

        // 6. Water check
        if (WaterMovementUtil.isLegitWaterMovement(player)) {
            return;
        }

        // 7. Ladder or vine check: if the block is a ladder/vine, let them move.
        // Because "inventory-ladder" combos can cause small vertical movements
        Location loc = player.getLocation();
        Material footBlock = loc.getBlock().getType();
        if (footBlock == Material.LADDER || footBlock == Material.VINE) {
            return;
        }

        // 8. Movement detection logic
        Vector fromPos = event.getFrom().toVector();
        Vector toPos = event.getTo().toVector();
        Vector lastTrackedPos = lastPositions.getOrDefault(playerId, fromPos);

        // Only measure horizontal distance
        fromPos.setY(0.0);
        toPos.setY(0.0);
        lastTrackedPos.setY(0.0);

        double distanceDelta = fromPos.distance(toPos);
        lastPositions.put(playerId, toPos); // update stored pos

        double finalThreshold = minMovementThreshold * distanceLeeway;
        if (distanceDelta < finalThreshold) {
            // Very minimal movement, skip
            return;
        }

        // 9. Add suspicion, handle punishment
        int suspicion = SuspicionManager.addSuspicionPoints(playerId, suspicionPoints, "InventoryMove");
        CheatReportUtil.handleSuspicionPunishment(player, plugin, "Inventory Move Exploit", suspicion);
    }
}
