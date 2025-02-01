package me.kurtoye.anticheat;

import me.kurtoye.anticheat.checks.movement.FlyCheck;
import me.kurtoye.anticheat.checks.movement.JesusCheck;
import me.kurtoye.anticheat.checks.movement.NoFallCheck;
import me.kurtoye.anticheat.checks.movement.SpeedCheck;
import me.kurtoye.anticheat.handlers.TeleportHandler;
import org.bukkit.plugin.java.JavaPlugin;

public class Anticheat extends JavaPlugin {

    private SpeedCheck speedCheck;
    private FlyCheck flyCheck;
    private JesusCheck jesusCheck;
    private NoFallCheck noFallCheck;
    private TeleportHandler teleportHandler;

    @Override
    public void onEnable() {
        // Initialize handlers
        teleportHandler = new TeleportHandler();

        // Initialize movement checks
        speedCheck = new SpeedCheck(this, teleportHandler);
       // flyCheck = new FlyCheck(this);
        jesusCheck = new JesusCheck(this);
        noFallCheck = new NoFallCheck(this);

        // Register event listeners
        getServer().getPluginManager().registerEvents(teleportHandler, this);
        getServer().getPluginManager().registerEvents(speedCheck, this);
        //getServer().getPluginManager().registerEvents(flyCheck, this);
        getServer().getPluginManager().registerEvents(jesusCheck, this);
        getServer().getPluginManager().registerEvents(noFallCheck, this);

        // Start background tasks if needed
        startPeriodicTasks();

        getLogger().info("✅ AntiCheat Plugin Enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("❌ AntiCheat Plugin Disabled!");
    }

    private void startPeriodicTasks() {
        // Example of adding periodic tasks if needed
    }
}
