package me.kurtoye.anticheat;

import me.kurtoye.anticheat.checks.chat.CommandAbuseCheck;
import me.kurtoye.anticheat.checks.chat.ChatSpamCheck;
import me.kurtoye.anticheat.checks.movement.FlyCheck;
import me.kurtoye.anticheat.checks.movement.InventoryMoveCheck;
import me.kurtoye.anticheat.checks.movement.JesusCheck;
import me.kurtoye.anticheat.checks.movement.NoFallCheck;
import me.kurtoye.anticheat.checks.movement.SpeedCheck;
import me.kurtoye.anticheat.checks.combat.AutoClickerCheck;
import me.kurtoye.anticheat.checks.combat.AimbotCheck;
import me.kurtoye.anticheat.checks.combat.KillAuraCheck;
import me.kurtoye.anticheat.checks.world.FastBreakCheck;
import me.kurtoye.anticheat.checks.world.FastPlaceCheck;
import me.kurtoye.anticheat.checks.world.XrayCheck;
import me.kurtoye.anticheat.handlers.PlayerHistoryHandler;
import me.kurtoye.anticheat.handlers.TeleportHandler;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Main AntiCheat plugin class.
 * - Registers all chat, movement, combat, and world checks.
 * - Initializes utility handlers.
 * - Supports modular expansion.
 */
public class Anticheat extends JavaPlugin {

    private TeleportHandler teleportHandler;
    private PlayerHistoryHandler historyHandler;
    private static Anticheat instance;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        reloadConfig();

        this.teleportHandler = new TeleportHandler();
        this.historyHandler = new PlayerHistoryHandler(this);

        registerChecks();
        getLogger().info("✅ AntiCheat Plugin Enabled!");
    }

    @Override
    public void onDisable() {
        if (historyHandler != null) historyHandler.saveHistoryData();
        getLogger().info("❌ AntiCheat Plugin Disabled!");
    }

    /**
     * Registers all detection checks across chat, movement, combat, and world categories.
     */
    private void registerChecks() {
        // Chat checks
        Bukkit.getPluginManager().registerEvents(new ChatSpamCheck(this), this);
        Bukkit.getPluginManager().registerEvents(new CommandAbuseCheck(this), this);

        // Movement checks
        Bukkit.getPluginManager().registerEvents(new InventoryMoveCheck(this, teleportHandler), this);
        Bukkit.getPluginManager().registerEvents(new JesusCheck(this, teleportHandler), this);
        Bukkit.getPluginManager().registerEvents(new NoFallCheck(this, teleportHandler), this);
        Bukkit.getPluginManager().registerEvents(new SpeedCheck(this, teleportHandler), this);
        Bukkit.getPluginManager().registerEvents(new FlyCheck(this, teleportHandler), this);

        // Combat checks
        Bukkit.getPluginManager().registerEvents(new AutoClickerCheck(this), this);
        Bukkit.getPluginManager().registerEvents(new AimbotCheck(this), this);
        Bukkit.getPluginManager().registerEvents(new KillAuraCheck(this, teleportHandler), this);

        // World checks
        Bukkit.getPluginManager().registerEvents(new FastBreakCheck(this), this);
        Bukkit.getPluginManager().registerEvents(new FastPlaceCheck(this), this);
        Bukkit.getPluginManager().registerEvents(new XrayCheck(this), this);
    }

    public PlayerHistoryHandler getHistoryHandler() {
        return historyHandler;
    }

    public static Anticheat getInstance() {
        return instance;
    }
}
