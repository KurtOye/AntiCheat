package me.kurtoye.anticheat.checks.chat;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.handlers.CheatReportHandler;
import me.kurtoye.anticheat.handlers.SuspicionHandler;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Detects player chat spam via two detection modes:
 *
 * 1. Rapid message frequency — messages sent faster than cooldown allows.
 * 2. Message repetition — same message sent repeatedly beyond a configurable limit.
 *
 * This module applies suspicion points accordingly and integrates with the progressive
 * punishment system for handling spammers.
 */
public class ChatSpamCheck implements Listener {

    private final Anticheat plugin;

    // Configurable detection settings
    private final boolean enabled;
    private final long cooldownMs;
    private final int maxRepeats;
    private final int cooldownPoints;
    private final int repeatPoints;

    // Tracks last message time, message content, and repeat counts per player
    private final Map<UUID, Long> lastChat = new HashMap<>();
    private final Map<UUID, String> lastMsg = new HashMap<>();
    private final Map<UUID, Integer> repeatCount = new HashMap<>();

    /**
     * Loads configurable thresholds from config.yml.
     */
    public ChatSpamCheck(Anticheat plugin) {
        this.plugin = plugin;
        FileConfiguration cfg = plugin.getConfig();

        this.enabled = cfg.getBoolean("chatspam.enabled", true);
        this.cooldownMs = cfg.getLong("chatspam.interval_ms", 5);
        this.maxRepeats = cfg.getInt("chatspam.messages_per_interval", 5);
        this.cooldownPoints = cfg.getInt("chatspam.suspicion_points", 2);
        this.repeatPoints = cfg.getInt("chatspam.repeated_suspicion_points", 1);
    }

    /**
     * Monitors player messages and flags violations based on frequency and repetition.
     * Suspicion points are applied progressively, leading to punishments if behavior persists.
     */
    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player p = event.getPlayer();
        UUID id = p.getUniqueId();
        long now = System.currentTimeMillis();
        String msg = event.getMessage();

        if (!enabled) return;

        // --- 1) Cooldown Violation ---
        if (lastChat.containsKey(id) && (now - lastChat.get(id)) < cooldownMs) {
            int sus = SuspicionHandler.addSuspicionPoints(id, cooldownPoints, "ChatSpam(Cooldown)", plugin);
            CheatReportHandler.handleSuspicionPunishment(p, plugin, "Chat Spam (Cooldown)", sus);
            event.setCancelled(true);
            return;
        }

        // --- 2) Repeated Message Violation ---
        if (msg.equalsIgnoreCase(lastMsg.getOrDefault(id, ""))) {
            int count = repeatCount.getOrDefault(id, 0) + 1;
            repeatCount.put(id, count);

            if (count >= maxRepeats) {
                int sus = SuspicionHandler.addSuspicionPoints(id, repeatPoints, "ChatSpam(Repeated)", plugin);
                CheatReportHandler.handleSuspicionPunishment(p, plugin, "Chat Spam (Repeated)", sus);
                event.setCancelled(true);
                repeatCount.put(id, 0); // Reset counter after flag
                return;
            }
        } else {
            repeatCount.put(id, 0); // New message resets repeat counter
        }

        // --- 3) Update Tracking State ---
        lastChat.put(id, now);
        lastMsg.put(id, msg);
    }
}
