package me.kurtoye.anticheat.checks.chat;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.utilities.CheatReportUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * ChatSpammingCheck detects **excessive or repeated chat messages**.
 * ✅ Uses `CheatReportUtil` for **spam warnings & cooldown tracking**.
 * ✅ Prevents **false positives by allowing natural typing variations**.
 */
public class ChatSpamCheck implements Listener {

    private final Map<UUID, Long> lastChatTime = new HashMap<>();
    private final Map<UUID, String> lastMessage = new HashMap<>();
    private final Map<UUID, Integer> spamCount = new HashMap<>();

    private static final long CHAT_COOLDOWN = 500; // 0.5 seconds
    private static final int MAX_SPAM_COUNT = 5; // Max repeated messages before flagging

    private final Anticheat plugin;

    /**
     * Constructor for ChatSpamCheck.
     *
     * @param plugin The main plugin instance.
     */
    public ChatSpamCheck(Anticheat plugin) {
        this.plugin = plugin;
    }

    /**
     * Listens for player chat messages and detects spam patterns.
     */
    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        String message = event.getMessage();

        // ✅ **Check for cooldown spam**
        if (lastChatTime.containsKey(playerId)) {
            long timeSinceLastMessage = currentTime - lastChatTime.get(playerId);
            if (timeSinceLastMessage < CHAT_COOLDOWN) {
                event.setCancelled(true); // Cancel excessive chat
                CheatReportUtil.reportCheat(player, plugin, "Chat Spam (Cooldown Violation)");
                return;
            }
        }

        // ✅ **Check for repeated message spam**
        if (lastMessage.containsKey(playerId) && lastMessage.get(playerId).equalsIgnoreCase(message)) {
            int count = spamCount.getOrDefault(playerId, 0) + 1;
            spamCount.put(playerId, count);

            if (count >= MAX_SPAM_COUNT) {
                event.setCancelled(true);
                CheatReportUtil.reportCheat(player, plugin, "Chat Spam (Repeated Message)");
                spamCount.put(playerId, 0); // Reset after flagging
                return;
            }
        } else {
            spamCount.put(playerId, 0); // Reset if the message is different
        }

        // ✅ **Update last message tracking**
        lastChatTime.put(playerId, currentTime);
        lastMessage.put(playerId, message);
    }
}
