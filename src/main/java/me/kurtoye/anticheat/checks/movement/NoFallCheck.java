package me.kurtoye.anticheat.checks.movement;

import me.kurtoye.anticheat.Anticheat;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.entity.Player;

public class NoFallCheck implements Listener {
    private final Anticheat plugin;

    public NoFallCheck(Anticheat plugin) { // Not completed
        this.plugin = plugin;
    }

    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            if (event.getDamage() == 0) {
                player.sendMessage("§c[AntiCheat] No-Fall hack detected!");
                plugin.getLogger().warning("⚠️ Player " + player.getName() + " may be using No-Fall hack.");
            }
        }
    }
}
