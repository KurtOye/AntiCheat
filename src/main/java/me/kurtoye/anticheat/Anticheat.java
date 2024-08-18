package me.kurtoye.anticheat;

import me.kurtoye.anticheat.movement.SpeedCheck;
import me.kurtoye.anticheat.utilities.TeleportHandler;
import org.bukkit.plugin.java.JavaPlugin;

public class Anticheat extends JavaPlugin {

    private SpeedCheck speedCheck;
    private TeleportHandler teleportHandler;

    @Override
    public void onEnable() {
        // Initialize handlers
        teleportHandler = new TeleportHandler(this);
        speedCheck = new SpeedCheck(this, teleportHandler);


        // Register event listeners
        getServer().getPluginManager().registerEvents(teleportHandler, this);
        getServer().getPluginManager().registerEvents(speedCheck, this);


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