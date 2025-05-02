package me.kurtoye.anticheat.checks.world;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.handlers.CheatReportHandler;
import me.kurtoye.anticheat.handlers.SuspicionHandler;
import me.kurtoye.anticheat.utilities.PingUtil;
import me.kurtoye.anticheat.utilities.TpsUtil;

import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.*;

/**
 * Detects players placing blocks too quickly in succession,
 * adjusted for ping and TPS to prevent false positives.
 */
public class FastPlaceCheck implements Listener {

    private final Anticheat plugin;

    private final Map<UUID, Long> lastPlaceTime = new HashMap<>();
    private final Map<UUID, List<Long>> borderlineEvents = new HashMap<>();

    private final boolean enabled;
    private final long minPlaceTime;
    private final int suspicionPoints;

    public FastPlaceCheck(Anticheat plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();

        this.enabled = config.getBoolean("fastplace.enabled", true);
        this.minPlaceTime = config.getLong("fastplace.min_place_time", 5);
        this.suspicionPoints = config.getInt("fastplace.suspicion_points", 1);
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (!enabled || player.getGameMode() != GameMode.SURVIVAL || player.hasPermission("anticheat.bypass")) return;

        long last = lastPlaceTime.getOrDefault(playerId, 0L);
        long elapsed = now - last;

        double adjusted = minPlaceTime
                * PingUtil.getPingCompensationFactor(player)
                * TpsUtil.getTpsCompensationFactor();

        if (elapsed < adjusted) {
            borderlineEvents.computeIfAbsent(playerId, k -> new ArrayList<>()).add(now);
            pruneOldEvents(playerId, now, 6000);

            int suspicion = SuspicionHandler.addSuspicionPoints(playerId, suspicionPoints, "FastPlaceCheck", plugin);
            CheatReportHandler.handleSuspicionPunishment(player, plugin, "FastPlace", suspicion);

            plugin.getLogger().fine(String.format("[FastPlaceCheck] %s placed block in %dms < %dms",
                    player.getName(), elapsed, (long) adjusted));
        }

        lastPlaceTime.put(playerId, now);
    }

    private void pruneOldEvents(UUID playerId, long now, long window) {
        List<Long> list = borderlineEvents.getOrDefault(playerId, new ArrayList<>());
        list.removeIf(t -> (now - t) > window);
    }
}
