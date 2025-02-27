package me.kurtoye.anticheat.checks.world;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.handlers.CheatReportHandler;
import me.kurtoye.anticheat.utilities.PingUtil;
import me.kurtoye.anticheat.utilities.TpsUtil;
import me.kurtoye.anticheat.utilities.WaterMovementUtil;
import me.kurtoye.anticheat.handlers.SuspicionHandler;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;

import java.util.*;

public class FastBreakCheck implements Listener {
    private final Map<UUID, Long> lastBreakTime = new HashMap<>();
    private final Map<UUID, List<Long>> borderlineBreaks = new HashMap<>();

    private final Anticheat plugin;
    private final long minBreakTime;
    private final double breakAllowance;
    private final int suspicionPoints;

    private final Map<Material, Double> blockHardness = new HashMap<>();

    public FastBreakCheck(Anticheat plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        this.minBreakTime   = config.getLong("fastbreak.min_break_time", 30);
        this.breakAllowance = config.getDouble("fastbreak.break_allowance", 1.10);
        this.suspicionPoints = config.getInt("fastbreak.suspicion_points", 3); // new config key
        loadBlockHardness();
    }

    /**
     * Pre-load approximate block hardness values for reference in getAdjustedBreakSpeed().
     * This helps compute typical break times for comparison.
     */
    private void loadBlockHardness() {
        blockHardness.put(Material.STONE,       1.5);
        blockHardness.put(Material.IRON_ORE,    3.0);
        blockHardness.put(Material.DIAMOND_ORE, 3.0);
        blockHardness.put(Material.GOLD_ORE,    3.0);
        blockHardness.put(Material.DEEPSLATE,   3.5);
        blockHardness.put(Material.OBSIDIAN,    50.0);
        blockHardness.put(Material.BASALT,      1.25);
        blockHardness.put(Material.NETHERRACK,  0.4);
        blockHardness.put(Material.END_STONE,   3.0);
        blockHardness.put(Material.SAND,        0.5);
        blockHardness.put(Material.GRAVEL,      0.6);
        blockHardness.put(Material.COBBLESTONE, 2.0);
        blockHardness.put(Material.BRICKS,      2.0);
        blockHardness.put(Material.GLASS,       0.3);
    }

    /**
     * Calculates the player's adjusted break time for a given block type, factoring in
     * tool efficiency, potion effects, environment (water, airborne), etc.
     */
    private double getAdjustedBreakSpeed(Player player, Material blockType) {
        double hardness = blockHardness.getOrDefault(blockType, 2.0);
        double speedMultiplier = 1.5;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool != null && tool.containsEnchantment(Enchantment.EFFICIENCY)) {
            int efficiencyLevel = tool.getEnchantmentLevel(Enchantment.EFFICIENCY);
            speedMultiplier += efficiencyLevel * efficiencyLevel + 1;
        }

        if (player.hasPotionEffect(PotionEffectType.HASTE)) {
            int hasteLevel = player.getPotionEffect(PotionEffectType.HASTE).getAmplifier() + 1;
            speedMultiplier *= (1 + (0.2 * hasteLevel));
        }

        if (player.hasPotionEffect(PotionEffectType.SLOWNESS)) {
            // For "Mining Fatigue", also known as SLOW_DIGGING
            int fatigueLevel = Math.min(player.getPotionEffect(PotionEffectType.SLOWNESS).getAmplifier(), 4);
            speedMultiplier *= Math.pow(0.3, fatigueLevel);
        }

        if (WaterMovementUtil.isLegitWaterMovement(player) && !WaterMovementUtil.isPlayerUsingAquaAffinity(player)) {
            speedMultiplier /= 5;
        }

        if (!player.isOnGround()) {
            speedMultiplier /= 5;
        }

        // The default formula: breakTime = (hardness * 1.5) / speedMultiplier
        double rawBreakTime = (hardness * 1.5) / speedMultiplier;

        // Multiply by breakAllowance to allow minor fluctuations
        double finalBreakTime = rawBreakTime * breakAllowance;

        // Prevent negative or sub-0.05 times
        return Math.max(finalBreakTime, 0.05);
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // No checks for creative
        if (player.getGameMode() == GameMode.CREATIVE) return;

        Material blockType = event.getBlock().getType();
        long lastTime = lastBreakTime.getOrDefault(playerId, 0L);
        long timeSinceLastBreak = currentTime - lastTime;

        // Compute the permissible break time dynamically
        double pingFactor = PingUtil.getPingCompensationFactor(player);
        double tpsFactor  = TpsUtil.getTpsCompensationFactor();
        long adjustedMinBreakTime = (long) (getAdjustedBreakSpeed(player, blockType) * pingFactor * tpsFactor * 30);

        // If the time since last break is below threshold => borderline event
        if (timeSinceLastBreak < adjustedMinBreakTime) {
            // Rolling window approach
            borderlineBreaks.computeIfAbsent(playerId, k -> new ArrayList<>()).add(currentTime);
            pruneOldBreaks(playerId, currentTime, 8000); // e.g., 8s window

            int newSuspicion = SuspicionHandler.addSuspicionPoints(playerId, suspicionPoints, "FastBreak", plugin);
            CheatReportHandler.handleSuspicionPunishment(player, plugin, "FastBreak Hack (Block: " + blockType + ")", newSuspicion);

            // (Optional) Data logging
            plugin.getLogger().info("[DATA-LOG] " + player.getName() + " borderline fast break on " + blockType + " at " + currentTime);
        }

        // Update last break time
        lastBreakTime.put(playerId, currentTime);
    }

    /**
     * Removes borderline break timestamps older than windowMs from the player's list.
     */
    private void pruneOldBreaks(UUID playerId, long currentTime, long windowMs) {
        List<Long> breaks = borderlineBreaks.getOrDefault(playerId, Collections.emptyList());
        breaks.removeIf(timestamp -> (currentTime - timestamp) > windowMs);
    }
}
