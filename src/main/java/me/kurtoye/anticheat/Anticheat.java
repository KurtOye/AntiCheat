package me.kurtoye.anticheat;

import me.kurtoye.anticheat.utilities.TeleportHandler;
import org.bukkit.plugin.java.JavaPlugin;

public class Anticheat extends JavaPlugin {

    private SpeedCheck speedCheck;
    private TeleportHandler teleportHandler;

    @Override
    public void onEnable() {
        // Initialize handlers
        speedCheck = new SpeedCheck(this, teleportHandler);
        teleportHandler = new TeleportHandler(this);

        // Register event listeners
        getServer().getPluginManager().registerEvents(speedCheck, this);
        getServer().getPluginManager().registerEvents(teleportHandler, this);

        // Optionally start any periodic tasks or additional initialization here
        startPeriodicTasks();
    }

    @Override
    public void onDisable() {
        // Clean up tasks if necessary
    }

    private void startPeriodicTasks() {
        // Example of adding periodic tasks if needed
    }

    public TeleportHandler getTeleportHandler(){
        return teleportHandler;
    }
}