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
import org.bukkit.configuration.file.FileConfiguration;
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
        reloadConfig(); // Loads custom config settings
        ensureDefaultConfigStructure(); // Loads missing modules in config


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
        Bukkit.getPluginManager().registerEvents(new AutoClickerCheck(this, teleportHandler), this);
        Bukkit.getPluginManager().registerEvents(new AimbotCheck(this, teleportHandler), this);
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

    private void ensureDefaultConfigStructure() {
        FileConfiguration config = this.getConfig();
        boolean changed = false;

        // === Movement Checks ===
        changed |= addDefault(config, "speedcheck.enabled", true);
        changed |= addDefault(config, "speedcheck.violation_leeway", 1.10);
        changed |= addDefault(config, "speedcheck.suspicion_points", 3);

        changed |= addDefault(config, "flycheck.enabled", true);
        changed |= addDefault(config, "flycheck.max_air_time", 1000);
        changed |= addDefault(config, "flycheck.leeway", 1.0);
        changed |= addDefault(config, "flycheck.suspicion_points", 3);

        changed |= addDefault(config, "nofall.enabled", true);
        changed |= addDefault(config, "nofall.min_fall_distance", 3.5);
        changed |= addDefault(config, "nofall.suspicion_points", 2);

        changed |= addDefault(config, "inventorymove.enabled", true);
        changed |= addDefault(config, "inventorymove.suspicion_points", 3);

        changed |= addDefault(config, "jesuscheck.enabled", true);
        changed |= addDefault(config, "jesuscheck.min_liquid_walk_time", 2500);
        changed |= addDefault(config, "jesuscheck.max_acceleration", 3.5);
        changed |= addDefault(config, "jesuscheck.sprintjump_suspicion_points", 3);
        changed |= addDefault(config, "jesuscheck.liquidwalk_suspicion_points", 2);

        // === Combat Checks ===
        changed |= addDefault(config, "aimbot.enabled", true);
        changed |= addDefault(config, "aimbot.max_snap_angle", 80.0);
        changed |= addDefault(config, "aimbot.suspicion_points", 4);

        changed |= addDefault(config, "killauracheck.enabled", true);
        changed |= addDefault(config, "killauracheck.max_reach", 3.0);
        changed |= addDefault(config, "killauracheck.reach_suspicion_points", 4);
        changed |= addDefault(config, "killauracheck.multi_target_threshold", 3);
        changed |= addDefault(config, "killauracheck.window_duration", 1000L);
        changed |= addDefault(config, "killauracheck.multi_target_suspicion_points", 3);

        changed |= addDefault(config, "autoclicker.enabled", true);
        changed |= addDefault(config, "autoclicker.reset_interval_ms", 1000);
        changed |= addDefault(config, "autoclicker.max_cps_threshold", 20);
        changed |= addDefault(config, "autoclicker.high_cps_suspicion_points", 3);
        changed |= addDefault(config, "autoclicker.consistency_cps_limit", 15);
        changed |= addDefault(config, "autoclicker.consistency_violations", 5);
        changed |= addDefault(config, "autoclicker.consistency_suspicion_points", 4);

        // === World Checks ===
        changed |= addDefault(config, "fastbreak.enabled", true);
        changed |= addDefault(config, "fastbreak.break_allowance", 1.10);
        changed |= addDefault(config, "fastbreak.suspicion_points", 3);
        changed |= addDefault(config, "fastbreak.spam_threshold", 5);
        changed |= addDefault(config, "fastbreak.spam_window_ms", 8000L);
        changed |= addDefault(config, "fastbreak.spam_suspicion_points", 4);

        changed |= addDefault(config, "fastplace.enabled", true);
        changed |= addDefault(config, "fastplace.threshold_ms", 100);
        changed |= addDefault(config, "fastplace.suspicion_points", 2);

        // === Chat/Command Checks ===
        changed |= addDefault(config, "chatspam.enabled", true);
        changed |= addDefault(config, "chatspam.messages_per_interval", 5);
        changed |= addDefault(config, "chatspam.interval_ms", 10000);
        changed |= addDefault(config, "chatspam.suspicion_points", 2);

        changed |= addDefault(config, "commandabuse.enabled", true);
        changed |= addDefault(config, "commandabuse.command_threshold", 10);
        changed |= addDefault(config, "commandabuse.interval_seconds", 15);
        changed |= addDefault(config, "commandabuse.suspicion_points", 3);

        // === Global Logging Settings ===
        changed |= addDefault(config, "logging.max_log_size_mb", 10);
        changed |= addDefault(config, "logging.anonymise_uuids", true);
        changed |= addDefault(config, "logging.debug", false);

        if (changed) {
            this.saveConfig(); // write to disk only if there were changes
            getLogger().info("✅ Default config values initialized or completed.");
        }
    }

    /**
     * Utility: Adds a default if not set.
     */
    private boolean addDefault(FileConfiguration config, String path, Object value) {
        if (!config.contains(path)) {
            config.set(path, value);
            return true;
        }
        return false;
    }

}
