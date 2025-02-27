package me.kurtoye.anticheat.checks.chat;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.utilities.CheatReportUtil;
import me.kurtoye.anticheat.utilities.SuspicionManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 🚀 Refined ChatSpamCheck:
 * - Integrates incremental suspicion scoring (vs. instant flags).
 * - Uses config-based tuning for spam thresholds & suspicion points.
 * - Minimizes false positives by checking repeated messages and fast chat.
 */
public class ChatSpamCheck implements Listener {

    // COOL-DOWN related
    private final long chatCooldown;
    private final int maxSpamCount;

    // SUSPICION
    private final int chatCooldownSuspicionPoints;  // suspicion increment for cooldown violations
    private final int repeatedMsgSuspicionPoints;   // suspicion increment for repeated message spam

    // TRACKING
    private final Map<UUID, Long> lastChatTime = new HashMap<>();
    private final Map<UUID, String> lastMessage = new HashMap<>();
    private final Map<UUID, Integer> spamCount = new HashMap<>();

    private final Anticheat plugin;

    public ChatSpamCheck(Anticheat plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();

        // Cooldown config
        this.chatCooldown = config.getLong("chatspam.cooldown", 500);       // Default: 2 seconds
        this.maxSpamCount = config.getInt("chatspam.max_repeats", 5);        // Default: 3 repeated messages

        // Suspicion increments (config-based)
        this.chatCooldownSuspicionPoints = config.getInt("chatspam.cooldown_suspicion_points", 2);
        this.repeatedMsgSuspicionPoints  = config.getInt("chatspam.repeated_suspicion_points", 2);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        String message = event.getMessage();

        // 1) Check for chat cooldown spam
        if (lastChatTime.containsKey(playerId)) {
            long timeSinceLastMessage = currentTime - lastChatTime.get(playerId);
            if (timeSinceLastMessage < chatCooldown) {
                // Instead of immediate punish, add suspicion
                int suspicion = SuspicionManager.addSuspicionPoints(
                        playerId,
                        chatCooldownSuspicionPoints,
                        "ChatSpam (Cooldown Violation)"
                );
                // Let the suspicion manager escalate if needed
                CheatReportUtil.handleSuspicionPunishment(player, plugin, "Chat Spam (Cooldown)", suspicion);

                // Cancel the chat event
                event.setCancelled(true);
                return;
            }
        }

        // 2) Check repeated message spam
        if (lastMessage.containsKey(playerId) && lastMessage.get(playerId).equalsIgnoreCase(message)) {
            int newCount = spamCount.getOrDefault(playerId, 0) + 1;
            spamCount.put(playerId, newCount);

            if (newCount >= maxSpamCount) {
                // Instead of direct flagging, add suspicion
                int suspicion = SuspicionManager.addSuspicionPoints(
                        playerId,
                        repeatedMsgSuspicionPoints,
                        "ChatSpam (Repeated Message)"
                );
                CheatReportUtil.handleSuspicionPunishment(player, plugin, "Chat Spam (Repeated)", suspicion);

                // Cancel this chat event
                event.setCancelled(true);
                // Reset the spam counter
                spamCount.put(playerId, 0);
                return;
            }
        } else {
            // If new message is different, reset spam count
            spamCount.put(playerId, 0);
        }

        // 3) Update last chat data
        lastChatTime.put(playerId, currentTime);
        lastMessage.put(playerId, message);
    }
}
