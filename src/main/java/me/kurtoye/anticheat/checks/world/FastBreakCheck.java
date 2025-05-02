package me.kurtoye.anticheat.checks.world;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.handlers.CheatReportHandler;
import me.kurtoye.anticheat.handlers.SuspicionHandler;
import me.kurtoye.anticheat.utilities.PingUtil;
import me.kurtoye.anticheat.utilities.TpsUtil;
import me.kurtoye.anticheat.utilities.WaterMovementUtil;

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

/**
 * Detects players breaking blocks faster than allowed based on:
 * - Tool efficiency
 * - Potion effects
 * - Water environment and movement state
 * - TPS and ping compensation
 */
public class FastBreakCheck implements Listener {

    private final Anticheat plugin;

    private final Map<UUID, Long> lastBreak = new HashMap<>();
    private final Map<UUID, List<Long>> borderlineBreaks = new HashMap<>();
    private final Map<Material, Double> blockHardness = new HashMap<>();

    private final boolean enabled;
    private final long minBreakTime;
    private final double allowance;
    private final int suspicionPoints;

    public FastBreakCheck(Anticheat plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();

        this.enabled          = config.getBoolean("fastbreak.enabled", true);
        this.minBreakTime     = config.getLong("fastbreak.min_break_time", 2);
        this.allowance        = config.getDouble("fastbreak.break_allowance", 1.10);
        this.suspicionPoints  = config.getInt("fastbreak.suspicion_points", 1);

        loadBlockHardness();
    }

    private void loadBlockHardness() {
        blockHardness.put(Material.STONE, 1.5);
        blockHardness.put(Material.IRON_ORE, 3.0);
        blockHardness.put(Material.DIAMOND_ORE, 3.0);
        blockHardness.put(Material.GOLD_ORE, 3.0);
        blockHardness.put(Material.DEEPSLATE, 3.5);
        blockHardness.put(Material.OBSIDIAN, 50.0);
        blockHardness.put(Material.BASALT, 1.25);
        blockHardness.put(Material.NETHERRACK, 0.4);
        blockHardness.put(Material.END_STONE, 3.0);
        blockHardness.put(Material.SAND, 0.5);
        blockHardness.put(Material.GRAVEL, 0.6);
        blockHardness.put(Material.COBBLESTONE, 2.0);
        blockHardness.put(Material.BRICKS, 2.0);
        blockHardness.put(Material.GLASS, 0.3);
    }

    private double getAdjustedBreakTime(Player player, Material block) {
        double hardness = blockHardness.getOrDefault(block, 2.0);
        double speed = 1.5;

        ItemStack tool = player.getInventory().getItemInMainHand();
        if (tool.containsEnchantment(Enchantment.EFFICIENCY)) {
            int level = tool.getEnchantmentLevel(Enchantment.EFFICIENCY);
            speed += level * level + 1;
        }

        if (player.hasPotionEffect(PotionEffectType.HASTE)) {
            int level = player.getPotionEffect(PotionEffectType.HASTE).getAmplifier() + 1;
            speed *= (1 + 0.2 * level);
        }

        if (player.hasPotionEffect(PotionEffectType.MINING_FATIGUE)) {
            int level = Math.min(player.getPotionEffect(PotionEffectType.MINING_FATIGUE).getAmplifier(), 4);
            speed *= Math.pow(0.3, level);
        }

        if (WaterMovementUtil.isLegitWaterMovement(player) && !WaterMovementUtil.isPlayerUsingAquaAffinity(player)) {
            speed /= 5;
        }

        if (!player.isOnGround()) {
            speed /= 5;
        }

        double rawTime = (hardness * 1.5) / speed;
        return Math.max(rawTime * allowance, 0.05);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (!enabled || player.getGameMode() == GameMode.CREATIVE || player.hasPermission("anticheat.bypass")) return;

        Material blockType = event.getBlock().getType();
        long lastTime = lastBreak.getOrDefault(playerId, 0L);
        long elapsed = now - lastTime;

        double pingFactor = PingUtil.getPingCompensationFactor(player);
        double tpsFactor = TpsUtil.getTpsCompensationFactor();
        long adjustedTime = (long) (getAdjustedBreakTime(player, blockType) * pingFactor * tpsFactor * 30);

        if (elapsed < adjustedTime && elapsed > minBreakTime) {
            borderlineBreaks.computeIfAbsent(playerId, k -> new ArrayList<>()).add(now);
            pruneOldBreaks(playerId, now, 8000);

            int suspicion = SuspicionHandler.addSuspicionPoints(playerId, suspicionPoints, "FastBreakCheck", plugin);
            CheatReportHandler.handleSuspicionPunishment(player, plugin, "FastBreak (" + blockType + ")", suspicion);

            plugin.getLogger().fine(String.format("[FastBreakCheck] %s broke %s in %dms (min=%dms)",
                    player.getName(), blockType, elapsed, adjustedTime));
        }

        lastBreak.put(playerId, now);
    }

    private void pruneOldBreaks(UUID playerId, long now, long window) {
        List<Long> list = borderlineBreaks.getOrDefault(playerId, new ArrayList<>());
        list.removeIf(t -> (now - t) > window);
    }
}
