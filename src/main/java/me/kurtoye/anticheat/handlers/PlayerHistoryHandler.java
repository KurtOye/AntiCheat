package me.kurtoye.anticheat.handlers;

import me.kurtoye.anticheat.Anticheat;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * PlayerHistoryManager handles saving/loading long-term suspicion data to a file.
 */
public class PlayerHistoryHandler {

    private final Anticheat plugin;
    private File historyFile;
    private FileConfiguration historyConfig;

    // We store lifetime or “rolling” suspicion totals here
    private final Map<UUID, Integer> totalLifetimeSuspicion = new HashMap<>();
    // Could also store daily or weekly suspicion. Up to you.

    public PlayerHistoryHandler(Anticheat plugin) {
        this.plugin = plugin;
        createHistoryFile();
        loadHistoryData();
        startAutoSaveTask();
    }

    /**
     * Increments the player’s lifetime suspicion in memory.
     */
    public void addLifetimeSuspicion(UUID playerId, int points) {
        int oldValue = totalLifetimeSuspicion.getOrDefault(playerId, 0);
        totalLifetimeSuspicion.put(playerId, oldValue + points);
    }

    /**
     * Retrieves the total suspicion recorded for a player.
     */
    public int getLifetimeSuspicion(UUID playerId) {
        return totalLifetimeSuspicion.getOrDefault(playerId, 0);
    }

    /**
     * Saves the data to the file (called periodically or onDisable).
     */
    public void saveHistoryData() {
        try {
            for (Map.Entry<UUID, Integer> entry : totalLifetimeSuspicion.entrySet()) {
                historyConfig.set("players." + entry.getKey() + ".lifetimeSuspicion", entry.getValue());
            }
            historyConfig.save(historyFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save AntiCheat history data!");
            e.printStackTrace();
        }
    }

    // ------------------ Private Helpers ------------------ //

    private void createHistoryFile() {
        historyFile = new File(plugin.getDataFolder(), "player_history.yml");
        if (!historyFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                historyFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("Could not create player_history.yml!");
                e.printStackTrace();
            }
        }
        historyConfig = YamlConfiguration.loadConfiguration(historyFile);
    }

    private void loadHistoryData() {
        if (historyConfig.contains("players")) {
            for (String uuidStr : historyConfig.getConfigurationSection("players").getKeys(false)) {
                UUID uuid = UUID.fromString(uuidStr);
                int suspicion = historyConfig.getInt("players." + uuidStr + ".lifetimeSuspicion", 0);
                totalLifetimeSuspicion.put(uuid, suspicion);
            }
        }
    }

    private void startAutoSaveTask() {
        // Saves every 5 minutes
        new BukkitRunnable() {
            @Override
            public void run() {
                saveHistoryData();
            }
        }.runTaskTimerAsynchronously(plugin, 20 * 300, 20 * 300);
    }
}
