package me.kurtoye.anticheat;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.ChatColor;

public class AnticheatCommand implements CommandExecutor {

    private final Anticheat plugin;

    public AnticheatCommand(Anticheat plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            plugin.reloadPlugin();
            sender.sendMessage(ChatColor.GREEN + "[AntiCheat] Reloaded plugin and config!");
            return true;
        }

        sender.sendMessage(ChatColor.YELLOW + "Usage: /anticheat reload");
        return true;
    }
}
