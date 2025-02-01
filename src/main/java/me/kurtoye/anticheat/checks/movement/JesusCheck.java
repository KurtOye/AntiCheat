package me.kurtoye.anticheat.checks.movement;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.utilities.MovementUtil;
import me.kurtoye.anticheat.utilities.CheatReportUtil;
import me.kurtoye.anticheat.utilities.PingUtil;
import me.kurtoye.anticheat.utilities.TpsUtil;
import me.kurtoye.anticheat.handlers.TeleportHandler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * JesusCheck detects players who walk unnaturally on water or lava.
 *
 * ✅ Uses **shared utilities** (`MovementUtil`, `TeleportHandler`, `CheatReportUtil`)
 * ✅ **Ignores all fundamental movement mechanics** (swimming, boats, bubble columns, Elytra, etc.)
 * ✅ **Flags only real water-walking cheats** after a **2+ second duration**
 * ✅ **Optimized for performance** (reduces unnecessary tracking, prevents lag-based false positives)
 */
public class JesusCheck implements Listener {

    private final Map<UUID, Long> waterWalkStartTime = new HashMap<>();
    private final Map<UUID, Long> lastVelocityChangeTime = new HashMap<>();
    private final Map<UUID, Long> lastTeleport = new HashMap<>();

    private static final long MIN_WATER_WALK_TIME = 2000; // 2 seconds before flagging

    private final TeleportHandler teleportHandler;
    private final Anticheat plugin;

    /**
     * Constructor for JesusCheck.
     *
     * @param plugin The main plugin instance.
     * @param teleportHandler The teleport handler used to track recent teleports.
     */
    public JesusCheck(Anticheat plugin, TeleportHandler teleportHandler) {
        this.plugin = plugin;
        this.teleportHandler = teleportHandler;
    }

    /**
     * Detects water-walking by tracking how long a player remains on water or lava.
     *
     * ✅ **Ignores all fundamental mechanics** (swimming, Elytra, bubble columns, boats, etc.)
     * ✅ **Uses `CheatReportUtil` for modular cheat reporting**
     * ✅ **Avoids lag-based false positives (adjusts for high ping & low TPS)**
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        // ✅ **Ignore movement checks if the player is in a valid state**
        if (MovementUtil.shouldIgnoreMovement(player, teleportHandler, lastVelocityChangeTime, lastTeleport)) {
            return;
        }

        // ✅ **Ignore movement if the player is swimming or using legit water physics**
        if (MovementUtil.isLegitWaterMovement(player)) {
            waterWalkStartTime.remove(playerId);
            return;
        }

        long currentTime = System.currentTimeMillis();

        // ✅ **Start tracking water-walking time**
        if (!waterWalkStartTime.containsKey(playerId)) {
            waterWalkStartTime.put(playerId, currentTime);
            return;
        }

        // ✅ **Prevent lag-based false positives**
        double pingFactor = PingUtil.getPingCompensationFactor(player);
        double tpsFactor = TpsUtil.getTpsCompensationFactor();
        double adjustedTimeThreshold = MIN_WATER_WALK_TIME * pingFactor * tpsFactor;

        // ✅ **Flag the player if they’ve been walking on water for too long**
        if ((currentTime - waterWalkStartTime.get(playerId)) > adjustedTimeThreshold) {
            CheatReportUtil.reportCheat(player, plugin, "Jesus Hack");
        }
    }
}
