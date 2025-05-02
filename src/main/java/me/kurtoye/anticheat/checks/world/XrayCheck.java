package me.kurtoye.anticheat.checks.world;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.handlers.CheatReportHandler;
import me.kurtoye.anticheat.handlers.SuspicionHandler;
import me.kurtoye.anticheat.utilities.PingUtil;
import me.kurtoye.anticheat.utilities.TpsUtil;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.util.BlockIterator;

import java.util.*;

/**
 * Detects potential X-ray cheating by tracking rapid valuable ore mining
 * in unexposed underground areas. Adjusts thresholds using ping and TPS compensation.
 */
public class XrayCheck implements Listener {

    private final Anticheat plugin;
    private final boolean enabled;
    private final int suspicionPoints;
    private final int blockThreshold;
    private final long timeWindow;
    private final double lagComp;

    private final Map<UUID, Integer> blockCount = new HashMap<>();
    private final Map<UUID, Long> firstBreakTime = new HashMap<>();

    public XrayCheck(Anticheat plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();

        this.enabled          = config.getBoolean("xray.enabled", true);
        this.suspicionPoints  = config.getInt("xray.suspicion_points", 5);
        this.blockThreshold   = config.getInt("xray.block_threshold", 30);
        this.timeWindow       = config.getLong("xray.time_window", 30000L);
        this.lagComp          = config.getDouble("xray.lag_compensation", 1.0);
    }

    /**
     * Tracks valuable ore breaking and flags when a suspicious quantity is reached
     * within a short timeframe, without visual exposure to air blocks.
     */
    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (!enabled || player.getGameMode() != GameMode.SURVIVAL || player.hasPermission("anticheat.bypass")) return;

        Block block = event.getBlock();
        if (!isValuable(block.getType()) || isExposed(block, player)) return;

        double adjustedWindow = timeWindow
                * PingUtil.getPingCompensationFactor(player)
                * TpsUtil.getTpsCompensationFactor()
                * lagComp;

        if (!firstBreakTime.containsKey(id)) {
            firstBreakTime.put(id, now);
            blockCount.put(id, 1);
            return;
        }

        long first = firstBreakTime.get(id);
        int count = blockCount.getOrDefault(id, 0) + 1;

        if (now - first <= adjustedWindow) {
            blockCount.put(id, count);
            if (count >= blockThreshold) {
                int sus = SuspicionHandler.addSuspicionPoints(id, suspicionPoints, "XrayCheck", plugin);
                CheatReportHandler.handleSuspicionPunishment(player, plugin, "Xray: " + count + " ores in time window", sus);
                plugin.getLogger().fine(String.format("[XrayCheck] %s broke %d ores < %dms", player.getName(), count, (long) adjustedWindow));
                blockCount.put(id, 0);
                firstBreakTime.put(id, now);
            }
        } else {
            firstBreakTime.put(id, now);
            blockCount.put(id, 1);
        }
    }

    private boolean isValuable(Material material) {
        return switch (material) {
            case DIAMOND_ORE, GOLD_ORE, EMERALD_ORE, REDSTONE_ORE, LAPIS_ORE, IRON_ORE -> true;
            default -> false;
        };
    }

    private boolean isExposed(Block block, Player player) {
        // Check for surrounding air
        for (int[] offset : new int[][] {
                {0, 1, 0}, {0, -1, 0}, {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1}
        }) {
            if (block.getRelative(offset[0], offset[1], offset[2]).getType() == Material.AIR) return true;
        }

        // Raytrace from player's eyes to check direct exposure
        Location eye = player.getEyeLocation();
        Location center = block.getLocation().add(0.5, 0.5, 0.5);
        double distance = eye.distance(center);

        BlockIterator ray = new BlockIterator(eye, 0, (int) distance);
        while (ray.hasNext()) {
            Block b = ray.next();
            if (b.getLocation().distance(block.getLocation()) < 0.5) return true;
            if (!isTransparent(b.getType())) return false;
        }

        return false;
    }

    private boolean isTransparent(Material material) {
        return switch (material) {
            case AIR, TORCH, WATER, LAVA, GLASS, VINE -> true;
            default -> false;
        };
    }
}
