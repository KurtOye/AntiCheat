package me.kurtoye.anticheat.checks.movement;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.handlers.CheatReportHandler;
import me.kurtoye.anticheat.handlers.SuspicionHandler;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;

import java.util.UUID;

/**
 *  Fully Optimized NoFallCheck (Performance, Accuracy & Configurable Settings)
 *  Uses incremental suspicion logic instead of instant flags.
 *  Avoids simple false positives by checking fall distance & potential edge cases.
 */
public class NoFallCheck implements Listener {

    private final Anticheat plugin;

    // Suspicion increments
    private final int noFallSuspicionPoints;         // Points to add when zero fall damage is suspicious
    private final double minFallDistance;            // If they fell beyond this distance but took 0 damage => suspicious

    public NoFallCheck(Anticheat plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();

        // Load settings from config, with fallbacks
        this.noFallSuspicionPoints = config.getInt("nofall.suspicion_points", 2);
        // Minimum "suspect" distance: e.g. if the player fell from >3.5 blocks but took 0 damage => likely NoFall hack
        this.minFallDistance      = config.getDouble("nofall.min_fall_distance", 3.5);
    }

    /**
     * Handles detection of zero fall damage from a potentially lethal or high fall,
     * integrated with suspicion-based approach.
     */
    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }

        // If final damage is 0, let's see if they likely should have taken damage.
        // E.g. if they fell from a decent height (like >3.5 blocks or your threshold).
        // The built-in "event.getDamage()" may or may not always reflect final distance.
        // For simpler cases, we check if the final damage is 0 => suspicious, but optional to store lastFallDistance as well
        if (event.getFinalDamage() <= 0) {

            // We'll do a minimal approach here:

            float actualFallDistance = player.getFallDistance(); // By default, might be reset if they used NoFall
            if (actualFallDistance >= minFallDistance) {
                // Add suspicion points
                UUID playerId = player.getUniqueId();
                int newSuspicion = SuspicionHandler.addSuspicionPoints(
                        playerId,
                        noFallSuspicionPoints,
                        "NoFallCheck"
                );

                // Now we handle or escalate punishment
                CheatReportHandler.handleSuspicionPunishment(
                        player,
                        plugin,
                        "No-Fall hack suspected",
                        newSuspicion
                );

                // Optional debug log
                plugin.getLogger().info("[DATA-LOG] " + player.getName()
                        + " possibly using NoFall, fallDist=" + actualFallDistance);
            }
        }
    }
}
