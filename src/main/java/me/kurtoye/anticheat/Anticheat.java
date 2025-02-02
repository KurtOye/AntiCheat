package me.kurtoye.anticheat;

import me.kurtoye.anticheat.checks.chat.CommandAbuseCheck;
import me.kurtoye.anticheat.checks.chat.ChatSpamCheck;
import me.kurtoye.anticheat.checks.movement.SpeedCheck;
import me.kurtoye.anticheat.checks.movement.JesusCheck;
import me.kurtoye.anticheat.handlers.TeleportHandler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main AntiCheat plugin class.
 * - Registers **all movement checks**.
 * - Initializes **utility handlers**.
 * - Ensures **future-proofed, modular expansion**.
 */
public class Anticheat extends JavaPlugin {

    private TeleportHandler teleportHandler;

    /**
     * Called when the plugin is enabled.
     * - Registers event listeners.
     * - Initializes teleport handler.
     */
    @Override
    public void onEnable() {
        this.teleportHandler = new TeleportHandler();

        registerChecks();

        getLogger().info("✅ AntiCheat Plugin Enabled!");
    }

    /**
     * Called when the plugin is disabled.
     * - Cleans up plugin resources.
     */
    @Override
    public void onDisable() {
        getLogger().info("❌ AntiCheat Plugin Disabled!");
    }

    /**
     * Registers all movement detection checks.
     * - Ensures all checks **share the same TeleportHandler**.
     */
    private void registerChecks() {
        Bukkit.getPluginManager().registerEvents(new CommandAbuseCheck(this), this);
        Bukkit.getPluginManager().registerEvents(new ChatSpamCheck(this), this);
        Bukkit.getPluginManager().registerEvents(new SpeedCheck(this, teleportHandler), this);
        Bukkit.getPluginManager().registerEvents(new JesusCheck(this, teleportHandler), this);
    }
}
