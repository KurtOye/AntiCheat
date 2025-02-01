package me.kurtoye.anticheat.checks.movement;

import me.kurtoye.anticheat.Anticheat;
import me.kurtoye.anticheat.utilities.MovementUtil;
import me.kurtoye.anticheat.utilities.WaterMovementUtil;
import me.kurtoye.anticheat.utilities.VelocityUtil;
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
 * JesusCheck detects **unnatural water-walking**, including sprint-jumping and bouncing.
 * ✅ Uses `WaterMovementUtil` for **all water-based logic**.
 * ✅ Uses `VelocityUtil` to **avoid false positives from acceleration & vertical movement**.
 * ✅ Allows **bouncing on water within a realistic range**.
 * ✅ Ensures **proper tracking reset when leaving water, teleporting, or using ladders/vines**.
 * ✅ Accounts for **Frost Walker, Depth Strider, boats, and Dolphin’s Grace**.
 */
public class JesusCheck implements Listener {

    private final Map<UUID, Long> waterWalkStartTime = new HashMap<>();
    private final Map<UUID, Long> lastVelocityChangeTime = new HashMap<>();
    private final Map<UUID, Long> lastTeleport = new HashMap<>();

    private static final long MIN_WATER_WALK_TIME = 2500; // 2.5 seconds before flagging
    private static final double MAX_ALLOWED_ACCELERATION = 3.5; // Prevents acceleration false positives
    private static final double MIN_BOUNCE_VELOCITY = -0.12; // Minimum velocity to ignore natural bouncing
    private static final double MAX_BOUNCE_VELOCITY = 0.12; // Maximum velocity to ignore natural bouncing

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
     * Detects **unnatural water-walking, sprint-jumping, and bouncing** while allowing normal physics.
     * ✅ Uses `WaterMovementUtil` to correctly detect **water-based movement**.
     * ✅ Uses `VelocityUtil` to ensure **knockback and acceleration aren't interfering**.
     * ✅ Resets movement tracking properly **when leaving water or teleporting**.
     * ✅ Handles **Depth Strider, Dolphin’s Grace, Frost Walker, and boats** to prevent false positives.
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        double verticalVelocity = player.getVelocity().getY();

        // ✅ **Ignore movement checks if the player is in a valid state**
        if (MovementUtil.shouldIgnoreMovement(player, teleportHandler, lastVelocityChangeTime, lastTeleport)) {
            return;
        }

        // ✅ **Ignore natural bouncing & floating**
        if (verticalVelocity >= MIN_BOUNCE_VELOCITY && verticalVelocity <= MAX_BOUNCE_VELOCITY) {
            return;
        }

        // ✅ **Ignore Frost Walker users, boats, and depth strider movement boosts**
        if (WaterMovementUtil.isPlayerUsingFrostWalker(player) ||
                WaterMovementUtil.isPlayerInBoat(player) ||
                WaterMovementUtil.isPlayerUsingDepthStrider(player)) {
            return;
        }

        // ✅ **Reset tracking when player leaves water or uses ladders/vines**
        if (!WaterMovementUtil.isPlayerRunningOnWater(player) &&
                !WaterMovementUtil.isPlayerSprintJumpingOnWater(player)) {
            waterWalkStartTime.remove(playerId);
            return;
        }

        // ✅ **Start tracking water-walking time**
        if (!waterWalkStartTime.containsKey(playerId)) {
            waterWalkStartTime.put(playerId, currentTime);
            return;
        }

        // ✅ **Prevent lag-based false positives**
        double pingFactor = PingUtil.getPingCompensationFactor(player);
        double tpsFactor = TpsUtil.getTpsCompensationFactor();
        double adjustedTimeThreshold = MIN_WATER_WALK_TIME * pingFactor * tpsFactor;

        // ✅ **Prevent acceleration & vertical movement false positives**
        double acceleration = VelocityUtil.getAcceleration(player);
        if (acceleration > MAX_ALLOWED_ACCELERATION) {
            return;
        }

        // ✅ **Flag the player if they’ve been running on water too long**
        if ((currentTime - waterWalkStartTime.get(playerId)) > adjustedTimeThreshold) {
            CheatReportUtil.reportCheat(player, plugin, "Jesus Hack");
        }
    }
}
