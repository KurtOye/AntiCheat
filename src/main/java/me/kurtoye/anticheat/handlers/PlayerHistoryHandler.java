package me.kurtoye.anticheat.handlers;

import me.kurtoye.anticheat.Anticheat;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Tracks long-term player behavior by storing per-player, per-cheat suspicion events.
 * Supports exponential decay for older offenses and persists data via YML storage.
 */
public class PlayerHistoryHandler {

    private final Anticheat plugin;
    private File historyFile;
    private FileConfiguration historyConfig;

    private final Map<UUID, Map<String, List<SuspiciousEvent>>> history = new HashMap<>();

    private final long ONE_MONTH_MS;
    private final long TWO_MONTHS_MS;

    public PlayerHistoryHandler(Anticheat plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();

        this.ONE_MONTH_MS  = config.getLong("history.one_month_ms", 30L * 24 * 60 * 60 * 1000);
        this.TWO_MONTHS_MS = config.getLong("history.two_months_ms", 2 * ONE_MONTH_MS);

        createHistoryFile();
        loadHistoryData();
        startAutoSaveTask();
    }

    /**
     * Adds a new suspicion event to the player's lifetime history.
     * Events are grouped by cheat type.
     */
    public void addLifetimeSuspicionForCheat(UUID playerId, String cheatType, int points) {
        Map<String, List<SuspiciousEvent>> playerHistory = history.computeIfAbsent(playerId, k -> new HashMap<>());
        List<SuspiciousEvent> events = playerHistory.computeIfAbsent(cheatType, k -> new ArrayList<>());
        events.add(new SuspiciousEvent(points, System.currentTimeMillis()));
    }

    /**
     * Retrieves the player's lifetime suspicion score for a specific cheat type.
     * Applies time decay: full weight for 0–1 month, reduced weight from 1–2 months.
     */
    public int getLifetimeSuspicionForCheat(UUID playerId, String cheatType) {
        int total = 0;
        long now = System.currentTimeMillis();

        Map<String, List<SuspiciousEvent>> playerHistory = history.get(playerId);
        if (playerHistory == null) return 0;

        List<SuspiciousEvent> events = playerHistory.get(cheatType);
        if (events == null) return 0;

        for (SuspiciousEvent event : events) {
            long age = now - event.getTimestamp();
            if (age <= ONE_MONTH_MS) {
                total += event.getPoints();
            } else if (age < TWO_MONTHS_MS) {
                double decay = 1.0 - ((double) (age - ONE_MONTH_MS) / ONE_MONTH_MS);
                total += (int) (event.getPoints() * decay);
            }
        }
        return total;
    }

    /**
     * Wipes all stored suspicion events for the given player and cheat.
     */
    public void resetLifetimeSuspicionForCheat(UUID playerId, String cheatType) {
        Map<String, List<SuspiciousEvent>> playerHistory = history.get(playerId);
        if (playerHistory != null) {
            playerHistory.put(cheatType, new ArrayList<>());
        }
    }

    /**
     * Saves all stored suspicion history to `player_history.yml`.
     */
    public void saveHistoryData() {
        try {
            for (UUID playerId : history.keySet()) {
                String uuid = playerId.toString();
                for (String cheat : history.get(playerId).keySet()) {
                    String path = "players." + uuid + "." + cheat;
                    List<Map<String, Object>> eventList = new ArrayList<>();
                    for (SuspiciousEvent event : history.get(playerId).get(cheat)) {
                        Map<String, Object> entry = new HashMap<>();
                        entry.put("points", event.getPoints());
                        entry.put("timestamp", event.getTimestamp());
                        eventList.add(entry);
                    }
                    historyConfig.set(path, eventList);
                }
            }
            historyConfig.save(historyFile);
        } catch (IOException e) {
            plugin.getLogger().severe("❌ Could not save player history!");
            e.printStackTrace();
        }
    }

    /**
     * Creates the YAML file used to store long-term player behavior.
     */
    private void createHistoryFile() {
        historyFile = new File(plugin.getDataFolder(), "player_history.yml");
        if (!historyFile.exists()) {
            try {
                plugin.getDataFolder().mkdirs();
                historyFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().severe("❌ Could not create player_history.yml!");
                e.printStackTrace();
            }
        }
        historyConfig = YamlConfiguration.loadConfiguration(historyFile);
    }

    /**
     * Loads history entries from disk on plugin startup.
     */
    private void loadHistoryData() {
        if (!historyConfig.contains("players")) return;

        for (String uuidStr : historyConfig.getConfigurationSection("players").getKeys(false)) {
            UUID playerId = UUID.fromString(uuidStr);
            Map<String, List<SuspiciousEvent>> playerMap = new HashMap<>();

            for (String cheatType : historyConfig.getConfigurationSection("players." + uuidStr).getKeys(false)) {
                List<SuspiciousEvent> events = new ArrayList<>();
                List<?> rawList = historyConfig.getList("players." + uuidStr + "." + cheatType);
                if (rawList != null) {
                    for (Object obj : rawList) {
                        if (obj instanceof Map<?, ?> map) {
                            int points = (int) map.get("points");
                            long timestamp = ((Number) map.get("timestamp")).longValue();
                            events.add(new SuspiciousEvent(points, timestamp));
                        }
                    }
                }
                playerMap.put(cheatType, events);
            }
            history.put(playerId, playerMap);
        }
    }

    /**
     * Schedules autosaving of all data every 5 minutes.
     */
    private void startAutoSaveTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                saveHistoryData();
            }
        }.runTaskTimerAsynchronously(plugin, 20 * 300, 20 * 300); // every 5 minutes
    }
}
