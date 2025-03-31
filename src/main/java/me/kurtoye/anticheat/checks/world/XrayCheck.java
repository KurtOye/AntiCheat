package me.kurtoye.anticheat.checks.world;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.handlers.CheatReportHandler;
import me.kurtoye.anticheat.handlers.SuspicionHandler;
import me.kurtoye.anticheat.utilities.PingUtil;
import me.kurtoye.anticheat.utilities.TpsUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.util.BlockIterator;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class XrayCheck implements Listener {

    private final Anticheat plugin;
    private final int suspicionPoints;
    private final int xrayBlockThreshold;
    private final long timeWindow; // in milliseconds
    private final double lagCompensation;

    // Tracking per-player: count of valuable blocks mined and timestamp of the first event in a sequence.
    private final Map<UUID, Integer> valuableBlockCount = new HashMap<>();
    private final Map<UUID, Long> firstValuableBlockTime = new HashMap<>();

    public XrayCheck(Anticheat plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        this.suspicionPoints = config.getInt("xray.suspicion_points", 5);
        this.xrayBlockThreshold = config.getInt("xray.block_threshold", 30);
        this.timeWindow = config.getLong("xray.time_window", 30000); // default 30 seconds
        this.lagCompensation = config.getDouble("xray.lag_compensation", 1.0);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        // Skip if the player is in Creative or Spectator mode.
        if (player.getGameMode() == org.bukkit.GameMode.CREATIVE ||
                player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
            return;
        }

        Material blockType = event.getBlock().getType();
        // Process only valuable blocks.
        if (!isValuableBlock(blockType)) {
            return;
        }

        // Check if the block is naturally exposed (i.e. visible to the player).
        if (isNaturallyExposed(event.getBlock(), player)) {
            return;
        }

        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        // Adjust time window based on player's lag and TPS compensation.
        double adjustedTimeWindow = timeWindow * PingUtil.getPingCompensationFactor(player)
                * TpsUtil.getTpsCompensationFactor() * lagCompensation;

        // If this is the first valuable block in the sequence, record the time and start count.
        if (!firstValuableBlockTime.containsKey(playerId)) {
            firstValuableBlockTime.put(playerId, currentTime);
            valuableBlockCount.put(playerId, 1);
        } else {
            long firstTime = firstValuableBlockTime.get(playerId);
            if (currentTime - firstTime <= adjustedTimeWindow) {
                int count = valuableBlockCount.getOrDefault(playerId, 0) + 1;
                valuableBlockCount.put(playerId, count);

                // If the count exceeds the threshold, add suspicion and handle punishment.
                if (count >= xrayBlockThreshold) {
                    int newSuspicion = SuspicionHandler.addSuspicionPoints(playerId, suspicionPoints, "XrayCheck", plugin);
                    CheatReportHandler.handleSuspicionPunishment(player, plugin, "X-Ray Mining Detected", newSuspicion);
                    // Reset counters to avoid immediate repeated flags.
                    valuableBlockCount.put(playerId, 0);
                    firstValuableBlockTime.put(playerId, currentTime);
                }
            } else {
                // If outside the time window, reset the counter.
                firstValuableBlockTime.put(playerId, currentTime);
                valuableBlockCount.put(playerId, 1);
            }
        }
    }

    /**
     * Determines if a block type is considered valuable (i.e. typically targeted by x-ray hacks).
     */
    private boolean isValuableBlock(Material material) {
        switch (material) {
            case DIAMOND_ORE:
            case GOLD_ORE:
            case EMERALD_ORE:
            case REDSTONE_ORE:
            case LAPIS_ORE:
            case NETHER_QUARTZ_ORE:
                return true;
            default:
                return false;
        }
    }

    /**
     * Checks if the block is naturally exposed to air (i.e., visible to the player).
     * This method first checks if any adjacent blocks (up, down, north, south, east, west) are AIR.
     * If not, it performs a simple ray trace from the player's eye location to the block's center.
     * Returns true if the block is likely visible; false if it is hidden.
     */
    private boolean isNaturallyExposed(Block block, Player player) {
        // Check adjacent blocks.
        if (block.getRelative(0, 1, 0).getType() == Material.AIR ||
                block.getRelative(0, -1, 0).getType() == Material.AIR ||
                block.getRelative(1, 0, 0).getType() == Material.AIR ||
                block.getRelative(-1, 0, 0).getType() == Material.AIR ||
                block.getRelative(0, 0, 1).getType() == Material.AIR ||
                block.getRelative(0, 0, -1).getType() == Material.AIR) {
            return true;
        }

        // Ray trace from the player's eye location to the block's center.
        Location eyeLocation = player.getEyeLocation();
        Location blockCenter = block.getLocation().add(0.5, 0.5, 0.5);
        double distance = eyeLocation.distance(blockCenter);
        BlockIterator iterator = new BlockIterator(eyeLocation, 0, (int) distance);
        while (iterator.hasNext()) {
            Block current = iterator.next();
            // If the ray reaches the target block (within a margin), consider it exposed.
            if (current.getLocation().distance(block.getLocation()) < 0.5) {
                return true;
            }
            // If a non-transparent block obstructs the ray, the block is likely hidden.
            if (!isTransparent(current.getType())) {
                return false;
            }
        }
        return false;
    }

    /**
     * Checks if a given material is transparent.
     */
    private boolean isTransparent(Material material) {
        switch (material) {
            case AIR:
            case TORCH:
            case WATER:
            case LAVA:
            case GLASS:
            case VINE:
                return true;
            default:
                return false;
        }
    }
}
