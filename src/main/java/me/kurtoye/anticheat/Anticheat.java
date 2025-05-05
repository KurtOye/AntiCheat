package me.kurtoye.anticheat;

import me.kurtoye.anticheat.checks.chat.ChatSpamCheck;
import me.kurtoye.anticheat.checks.chat.CommandAbuseCheck;
import me.kurtoye.anticheat.checks.combat.AimbotCheck;
import me.kurtoye.anticheat.checks.combat.AutoClickerCheck;
import me.kurtoye.anticheat.checks.combat.KillAuraCheck;
import me.kurtoye.anticheat.checks.movement.*;
import me.kurtoye.anticheat.checks.world.FastBreakCheck;
import me.kurtoye.anticheat.checks.world.FastPlaceCheck;
import me.kurtoye.anticheat.checks.world.XrayCheck;
import me.kurtoye.anticheat.handlers.PlayerHistoryHandler;
import me.kurtoye.anticheat.handlers.TeleportHandler;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;

/**
 * Main plugin class for the AntiCheat system.
 * Responsible for loading configuration, registering all checks,
 * initializing handlers, and supporting modular future expansion.
 */
public class Anticheat extends JavaPlugin {

    private TeleportHandler teleportHandler;
    private PlayerHistoryHandler historyHandler;
    private static Anticheat instance;

    @Override
    public void onEnable() {
        instance = this;

        saveDefaultConfig();
        ensureDefaultConfigStructure();
        reloadConfig();


        this.teleportHandler = new TeleportHandler(this);
        this.historyHandler = new PlayerHistoryHandler(this);

        registerChecks();

        this.getCommand("anticheat").setExecutor(new AnticheatCommand(this));
        getLogger().info("✅ AntiCheat Plugin Enabled!");
    }

    @Override
    public void onDisable() {
        if (historyHandler != null) historyHandler.saveHistoryData();
        getLogger().info("❌ AntiCheat Plugin Disabled.");
    }

    /**
     * Registers all detection checks from each cheat category.
     */
    private void registerChecks() {
       // Chat
        Bukkit.getPluginManager().registerEvents(new ChatSpamCheck(this), this);
        Bukkit.getPluginManager().registerEvents(new CommandAbuseCheck(this), this);

        // Combat
        Bukkit.getPluginManager().registerEvents(new AutoClickerCheck(this, teleportHandler), this);
        Bukkit.getPluginManager().registerEvents(new AimbotCheck(this, teleportHandler), this);
        Bukkit.getPluginManager().registerEvents(new KillAuraCheck(this, teleportHandler), this);

        // Movement
        Bukkit.getPluginManager().registerEvents(new SpeedCheck(this, teleportHandler), this);
        Bukkit.getPluginManager().registerEvents(new FlyCheck(this, teleportHandler), this);
        Bukkit.getPluginManager().registerEvents(new NoFallCheck(this, teleportHandler), this);
        Bukkit.getPluginManager().registerEvents(new JesusCheck(this, teleportHandler), this);
        Bukkit.getPluginManager().registerEvents(new InventoryMoveCheck(this, teleportHandler), this);

        // World
        Bukkit.getPluginManager().registerEvents(new FastBreakCheck(this), this);
        Bukkit.getPluginManager().registerEvents(new FastPlaceCheck(this), this);
        Bukkit.getPluginManager().registerEvents(new XrayCheck(this), this);
    }

    public static Anticheat getInstance() {
        return instance;
    }

    public PlayerHistoryHandler getHistoryHandler() {
        return historyHandler;
    }

    /**
     * Ensures all config keys exist with default values on first run or update.
     */
    private void ensureDefaultConfigStructure() {
        FileConfiguration cfg = getConfig();
        boolean changed = false;

        // === Chat ===
        changed |= addDefault(cfg, "chatspam.enabled", true);
        changed |= addDefault(cfg, "chatspam.interval_ms", 1000);
        changed |= addDefault(cfg, "chatspam.messages_per_interval", 5);
        changed |= addDefault(cfg, "chatspam.suspicion_points", 2);
        changed |= addDefault(cfg, "chatspam.repeated_suspicion_points", 2);

        changed |= addDefault(cfg, "commandabuse.enabled", true);
        changed |= addDefault(cfg, "commandabuse.cooldown", 500);
        changed |= addDefault(cfg, "commandabuse.max_repeats", 4);
        changed |= addDefault(cfg, "commandabuse.restricted_commands", Arrays.asList("/pl", "/plugins", "/ver", "/version"));
        changed |= addDefault(cfg, "commandabuse.cooldown_suspicion_points", 2);
        changed |= addDefault(cfg, "commandabuse.restricted_suspicion_points", 3);

        // === Combat ===
        changed |= addDefault(cfg, "autoclicker.enabled", true);
        changed |= addDefault(cfg, "autoclicker.reset_interval_ms", 1000);
        changed |= addDefault(cfg, "autoclicker.max_cps_threshold", 20);
        changed |= addDefault(cfg, "autoclicker.consistency_cps_limit", 15);
        changed |= addDefault(cfg, "autoclicker.consistency_violations", 5);
        changed |= addDefault(cfg, "autoclicker.high_cps_suspicion_points", 3);
        changed |= addDefault(cfg, "autoclicker.consistency_suspicion_points", 4);

        changed |= addDefault(cfg, "aimbot.enabled", true);
        changed |= addDefault(cfg, "aimbot.max_snap_angle", 80.0);
        changed |= addDefault(cfg, "aimbot.suspicion_points", 4);

        changed |= addDefault(cfg, "killauracheck.enabled", true);
        changed |= addDefault(cfg, "killauracheck.max_reach", 3.0);
        changed |= addDefault(cfg, "killauracheck.reach_suspicion_points", 4);
        changed |= addDefault(cfg, "killauracheck.multi_target_threshold", 3);
        changed |= addDefault(cfg, "killauracheck.window_duration", 1000L);
        changed |= addDefault(cfg, "killauracheck.multi_target_suspicion_points", 3);

        // === Movement ===
        changed |= addDefault(cfg, "speedcheck.enabled", true);
        changed |= addDefault(cfg, "speedcheck.violation_leeway", 1.3);
        changed |= addDefault(cfg, "speedcheck.suspicion_points", 3);

        changed |= addDefault(cfg, "flycheck.enabled", true);
        changed |= addDefault(cfg, "flycheck.max_air_time", 1000);
        changed |= addDefault(cfg, "flycheck.leeway", 1.0);
        changed |= addDefault(cfg, "flycheck.suspicion_points", 3);

        changed |= addDefault(cfg, "nofall.enabled", true);
        changed |= addDefault(cfg, "nofall.min_fall_distance", 3.5);
        changed |= addDefault(cfg, "nofall.suspicion_points", 2);

        changed |= addDefault(cfg, "inventorymove.enabled", true);
        changed |= addDefault(cfg, "inventorymove.min_movement_threshold", 0.2);
        changed |= addDefault(cfg, "inventorymove.distance_leeway", 1.0);
        changed |= addDefault(cfg, "inventorymove.suspicion_points", 1);

        changed |= addDefault(cfg, "jesus.enabled", true);
        changed |= addDefault(cfg, "jesus.max_water_stand_time", 1500);
        changed |= addDefault(cfg, "jesus.suspicious_points", 2);


        // === World ===
        changed |= addDefault(cfg, "fastbreak.enabled", true);
        changed |= addDefault(cfg, "fastbreak.min_break_time", 30);
        changed |= addDefault(cfg, "fastbreak.break_allowance", 1.10);
        changed |= addDefault(cfg, "fastbreak.suspicion_points", 3);
        changed |= addDefault(cfg, "fastbreak.spam_threshold", 5);
        changed |= addDefault(cfg, "fastbreak.spam_window_ms", 8000L);
        changed |= addDefault(cfg, "fastbreak.spam_suspicion_points", 4);

        changed |= addDefault(cfg, "fastplace.enabled", true);
        changed |= addDefault(cfg, "fastplace.min_place_time", 200);
        changed |= addDefault(cfg, "fastplace.suspicion_points", 2);

        changed |= addDefault(cfg, "xray.enabled", true);
        changed |= addDefault(cfg, "xray.block_threshold", 30);
        changed |= addDefault(cfg, "xray.time_window", 30000L);
        changed |= addDefault(cfg, "xray.suspicion_points", 5);
        changed |= addDefault(cfg, "xray.lag_compensation", 1.0);

        // === Handler Options ===
        changed |= addDefault(cfg, "suspicion.decay_interval_ms", 5000);
        changed |= addDefault(cfg, "suspicion.decay_amount", 1);

        changed |= addDefault(cfg, "cheatreport.stage1_threshold", 10);
        changed |= addDefault(cfg, "cheatreport.stage2_threshold", 20);
        changed |= addDefault(cfg, "cheatreport.stage3_threshold", 30);

        changed |= addDefault(cfg, "history.one_month_ms", 2592000000L);
        changed |= addDefault(cfg, "history.two_months_ms", 5184000000L);

        changed |= addDefault(cfg, "velocity.grace_period_ms", 200L);

        changed |= addDefault(cfg, "teleport.grace_period_ms", 3000L);

        if (changed) {
            cfg.options().copyDefaults(true);
            saveConfig();
            getLogger().info("✅ Config updated with new default values.");
        }
    }

    /**
     * Adds a default config value only if the key is missing.
     */
    private boolean addDefault(FileConfiguration config, String path, Object value) {
        if (!config.contains(path)) {
            config.set(path, value);
            return true;
        }
        return false;
    }

    public void reloadPlugin() {
        reloadConfig();              // Reload config
        ensureDefaultConfigStructure();  // Reload defaults

        // Reinit handlers
        this.teleportHandler = new TeleportHandler(this);
        this.historyHandler = new PlayerHistoryHandler(this);

        // Unregister all previous listeners
        HandlerList.unregisterAll(this);

        // Re-register checks
        registerChecks();

        getLogger().info("✅ AntiCheat reloaded successfully.");
    }

}
