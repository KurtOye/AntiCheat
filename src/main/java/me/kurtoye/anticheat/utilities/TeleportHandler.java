package me.kurtoye.anticheat.utilities;

import me.kurtoye.anticheat.Anticheat;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class TeleportHandler implements Listener {

    private final Map<UUID, Long> lastTeleportTime = new HashMap<>();
    private final Anticheat plugin;

    public TeleportHandler(Anticheat plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        lastTeleportTime.put(playerId, System.currentTimeMillis());
    }

    public boolean isRecentTeleport(UUID playerId, long currentTime) {
        if (lastTeleportTime.containsKey(playerId)) {
            long teleportTime = lastTeleportTime.get(playerId);
            return currentTime - teleportTime < 2000; // Ignore speed check for 2 seconds after teleport
        }
        return false;
    }
}