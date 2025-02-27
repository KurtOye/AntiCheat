package me.kurtoye.anticheat.checks.world;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.handlers.CheatReportHandler;
import me.kurtoye.anticheat.utilities.PingUtil;
import me.kurtoye.anticheat.utilities.TpsUtil;
import me.kurtoye.anticheat.handlers.SuspicionHandler;
import org.bukkit.GameMode;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.*;

/**
 * FastPlaceCheck verifies block placement intervals to detect fast-place hacks.
 * Instead of one-shot flags, it increments suspicion points for borderline events.
 *
 * Config keys:
 *   - fastplace.min_place_time (long): base time in ms between two block placements
 *   - fastplace.suspicion_points (int): how many suspicion points are added per borderline event
 */
public class FastPlaceCheck implements Listener {

    private final Map<UUID, Long> lastPlaceTime = new HashMap<>();
    private final Map<UUID, List<Long>> borderlineEvents = new HashMap<>();
    // Rolling window storing timestamps of borderline events

    private final Anticheat plugin;
    private final long minPlaceTime;
    private final int suspicionPoints;

    public FastPlaceCheck(Anticheat plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        this.minPlaceTime   = config.getLong("fastplace.min_place_time", 10); // default 200ms
        this.suspicionPoints = config.getInt("fastplace.suspicion_points", 3); // number of points to add
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        // Ignore creative mode placements
        if (player.getGameMode() == GameMode.CREATIVE) return;

        long lastTime = lastPlaceTime.getOrDefault(playerId, 0L);
        long timeSinceLastPlace = currentTime - lastTime;

        double pingFactor = PingUtil.getPingCompensationFactor(player);
        double tpsFactor  = TpsUtil.getTpsCompensationFactor();
        long adjustedMinPlaceTime = (long) (minPlaceTime * pingFactor * tpsFactor);

        // If the time since last place is below threshold, treat it as a borderline event
        if (timeSinceLastPlace < adjustedMinPlaceTime) {
            // 1) Add borderline event
            borderlineEvents.computeIfAbsent(playerId, k -> new ArrayList<>()).add(currentTime);

            // 2) Optionally remove old borderline events beyond rolling window (e.g., 6 seconds)
            pruneOldEvents(playerId, currentTime, 6000);

            // 3) If borderline event recurs, increment suspicion instead of immediate flag
            int newSuspicion = SuspicionHandler.addSuspicionPoints(playerId, suspicionPoints, "FastPlace");
            // 4) Let CheatReportUtil decide if punishment is triggered
            CheatReportHandler.handleSuspicionPunishment(player, plugin, "FastPlace", newSuspicion);

            // (Optional) Log data for offline analysis:
            plugin.getLogger().info("[DATA-LOG] " + player.getName() + " borderline fast place at " + currentTime);
        }

        // Update last place time
        lastPlaceTime.put(playerId, currentTime);
    }

    /**
     * Removes borderline events older than 'windowMs' from the player's event list.
     */
    private void pruneOldEvents(UUID playerId, long currentTime, long windowMs) {
        List<Long> events = borderlineEvents.getOrDefault(playerId, Collections.emptyList());
        events.removeIf(timestamp -> (currentTime - timestamp) > windowMs);
    }
}
