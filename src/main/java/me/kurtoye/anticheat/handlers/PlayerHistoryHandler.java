package me.kurtoye.anticheat.handlers;

import me.kurtoye.anticheat.Anticheat;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * Handles the persistent tracking of lifetime suspicion events on a per-cheat basis.
 * Each event counts at full value for 30 days and then decays linearly over the next 30 days.
 */
public class PlayerHistoryHandler {

    private final Anticheat plugin;
    private File historyFile;
    private FileConfiguration historyConfig;

    // Map: player UUID -> (cheat type -> list of suspicion events)
    private Map<UUID, Map<String, List<SuspiciousEvent>>> history = new HashMap<>();

    private final long ONE_MONTH_MS;
    private final long TWO_MONTHS_MS;

    public PlayerHistoryHandler(Anticheat plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        ONE_MONTH_MS = config.getLong("history.one_month_ms", 30L * 24 * 60 * 60 * 1000);
        TWO_MONTHS_MS = config.getLong("history.two_months_ms", 2 * ONE_MONTH_MS);
        createHistoryFile();
        loadHistoryData();
        startAutoSaveTask();
    }

    public void addLifetimeSuspicionForCheat(UUID playerId, String cheatType, int points) {
        Map<String, List<SuspiciousEvent>> playerHistory = history.getOrDefault(playerId, new HashMap<>());
        List<SuspiciousEvent> events = playerHistory.getOrDefault(cheatType, new ArrayList<>());
        events.add(new SuspiciousEvent(points, System.currentTimeMillis()));
        playerHistory.put(cheatType, events);
        history.put(playerId, playerHistory);
    }

    /**
     * Calculates the total lifetime suspicion for a given cheat type.
     * Full points count for events < 1 month old.
     * For events between 1 and 2 months old, the contribution decays linearly.
     * Events older than 2 months contribute nothing.
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
                double decayFactor = 1.0 - ((double)(age - ONE_MONTH_MS) / ONE_MONTH_MS);
                total += (int)(event.getPoints() * decayFactor);
            }
        }
        return total;
    }

    public void resetLifetimeSuspicionForCheat(UUID playerId, String cheatType) {
        Map<String, List<SuspiciousEvent>> playerHistory = history.get(playerId);
        if (playerHistory != null) {
            playerHistory.put(cheatType, new ArrayList<>());
        }
    }

    public void saveHistoryData() {
        try {
            for (Map.Entry<UUID, Map<String, List<SuspiciousEvent>>> playerEntry : history.entrySet()) {
                String uuidStr = playerEntry.getKey().toString();
                for (Map.Entry<String, List<SuspiciousEvent>> cheatEntry : playerEntry.getValue().entrySet()) {
                    String path = "players." + uuidStr + "." + cheatEntry.getKey();
                    List<Map<String, Object>> eventList = new ArrayList<>();
                    for (SuspiciousEvent event : cheatEntry.getValue()) {
                        Map<String, Object> eventData = new HashMap<>();
                        eventData.put("points", event.getPoints());
                        eventData.put("timestamp", event.getTimestamp());
                        eventList.add(eventData);
                    }
                    historyConfig.set(path, eventList);
                }
            }
            historyConfig.save(historyFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save player history data!");
            e.printStackTrace();
        }
    }

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
                UUID playerId = UUID.fromString(uuidStr);
                Map<String, List<SuspiciousEvent>> cheatMap = new HashMap<>();
                for (String cheatType : historyConfig.getConfigurationSection("players." + uuidStr).getKeys(false)) {
                    List<?> eventList = historyConfig.getList("players." + uuidStr + "." + cheatType);
                    List<SuspiciousEvent> events = new ArrayList<>();
                    if (eventList != null) {
                        for (Object obj : eventList) {
                            if (obj instanceof Map) {
                                Map<?, ?> eventData = (Map<?, ?>) obj;
                                int points = (int) eventData.get("points");
                                long timestamp = ((Number) eventData.get("timestamp")).longValue();
                                events.add(new SuspiciousEvent(points, timestamp));
                            }
                        }
                    }
                    cheatMap.put(cheatType, events);
                }
                history.put(playerId, cheatMap);
            }
        }
    }

    private void startAutoSaveTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                saveHistoryData();
            }
        }.runTaskTimerAsynchronously(plugin, 20 * 300, 20 * 300);
    }
}
