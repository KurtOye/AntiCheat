package me.kurtoye.anticheat.checks.player;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.utilities.CheatReportUtil;
import me.kurtoye.anticheat.utilities.ClickUtil;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.UUID;

/**
 * AutoClickerCheck detects **unnatural clicking speeds and perfectly consistent clicking patterns**.
 * ✅ Uses `CheatReportUtil` for **warnings & logging**.
 * ✅ Uses `ClickUtil` for **CPS tracking & click pattern analysis**.
 * ✅ Supports **config.yml settings for adjustable thresholds**.
 */
public class AutoClickerCheck implements Listener {

    private final Anticheat plugin;
    private final int maxCps;
    private final int perfectCpsThreshold;

    public AutoClickerCheck(Anticheat plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();

        // ✅ Load configurable thresholds from config.yml
        this.maxCps = config.getInt("autoclicker.max_cps", 20); // Default: 20 CPS
        this.perfectCpsThreshold = config.getInt("autoclicker.perfect_cps_threshold", 3); // Default: 3 times
    }

    @EventHandler
    public void onPlayerClick(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // ✅ **Record click and get CPS from ClickUtil**
        double cps = ClickUtil.recordClick(playerId);

        // ✅ **Detect if player is exceeding normal clicking speed**
        if (ClickUtil.isExceedingCPS(playerId, maxCps)) {
            CheatReportUtil.flagPlayer(player, plugin, "AutoClicker (CPS: " + cps + ")");
        }

        // ✅ **Detect perfectly consistent CPS over multiple checks**
        if (ClickUtil.isPerfectlyConsistentCPS(playerId, perfectCpsThreshold)) {
            CheatReportUtil.flagPlayer(player, plugin, "AutoClicker (Consistent CPS: " + cps + ")");
        }
    }
}
