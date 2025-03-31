package me.kurtoye.anticheat;

import me.kurtoye.anticheat.checks.chat.CommandAbuseCheck;
import me.kurtoye.anticheat.checks.chat.ChatSpamCheck;
import me.kurtoye.anticheat.checks.movement.InventoryMoveCheck;
import me.kurtoye.anticheat.checks.movement.NoFallCheck;
import me.kurtoye.anticheat.checks.movement.SpeedCheck;
import me.kurtoye.anticheat.checks.movement.JesusCheck;
import me.kurtoye.anticheat.checks.combat.AutoClickerCheck;
import me.kurtoye.anticheat.checks.world.FastBreakCheck;
import me.kurtoye.anticheat.checks.world.FastPlaceCheck;
import me.kurtoye.anticheat.handlers.PlayerHistoryHandler;
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
    private PlayerHistoryHandler historyHandler;
    private static Anticheat instance;

    /**
     * Called when the plugin is enabled.
     * - Registers event listeners.
     * - Initializes teleport handler.
     */
    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadConfig();

        this.teleportHandler = new TeleportHandler();
        this.historyHandler = new PlayerHistoryHandler(this);

        registerChecks();
        getLogger().info("✅ AntiCheat Plugin Enabled!");
    }

    /**
     * Called when the plugin is disabled.
     * - Cleans up plugin resources.
     */
    @Override
    public void onDisable() {

        if (historyHandler != null) historyHandler.saveHistoryData();
        getLogger().info("❌ AntiCheat Plugin Disabled!");
    }

    /**
     * Registers all movement detection checks.
     * - Ensures all checks **share the same TeleportHandler**.
     */
    private void registerChecks() {

        // Chat
        Bukkit.getPluginManager().registerEvents(new ChatSpamCheck(this), this);
        Bukkit.getPluginManager().registerEvents(new CommandAbuseCheck(this), this);

        // Combat

        //Movement

        Bukkit.getPluginManager().registerEvents(new InventoryMoveCheck(this, teleportHandler), this);
        Bukkit.getPluginManager().registerEvents(new JesusCheck(this, teleportHandler), this);
        Bukkit.getPluginManager().registerEvents(new NoFallCheck(this), this);
        Bukkit.getPluginManager().registerEvents(new SpeedCheck(this, teleportHandler), this);

        //Player
        Bukkit.getPluginManager().registerEvents(new AutoClickerCheck(this), this);

        //World
        Bukkit.getPluginManager().registerEvents(new FastBreakCheck(this), this);
        Bukkit.getPluginManager().registerEvents(new FastPlaceCheck(this), this);

    }

    public PlayerHistoryHandler getHistoryHandler(){
        return historyHandler;
    }
    public static Anticheat getInstance(){
        return instance;
    }

}
