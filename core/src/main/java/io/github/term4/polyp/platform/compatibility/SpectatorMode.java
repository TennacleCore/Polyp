package io.github.term4.polyp.platform.compatibility;

import net.minestom.server.entity.LivingEntity;
import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.tracking.ClientInfoTracker;
import io.github.term4.polyp.world.SpectatorCamera;
import io.github.term4.polyp.world.WorldPolicy;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerGameModeChangeEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.tag.Tag;
import net.minestom.server.timer.Task;
import net.minestom.server.timer.TaskSchedule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The body a spectator wears, and the switch between the two. {@link Mode#GHOST}: adventure, hovering, the stat
 * bars off a 1.8 screen ({@link SpectatorHud}) - the scrims spectator, still drawn their own body. {@link Mode#TRUE}:
 * the client's own spectator mode - noclip, and the camera inside whoever they click ({@link #camera}). While it
 * rides, the body follows the target every tick and sneaking brings the camera home, as {@code ServerPlayer.tick}
 * does (1.8: {@code EntityPlayerMP.onUpdate}). A spectator wears the invisible flag, as vanilla's does: it is what
 * keeps a 1.8 client from drawing its own shadow while the camera is elsewhere, and what a co-spectator's client
 * draws as the translucent head; who sees the body at all is {@link WorldPolicy#canSee}'s law. 1.7 has no
 * spectator mode, so TRUE is refused there ({@link #supports}) and the app falls back to GHOST. The first
 * {@link #enter} keeps the body the player walked in with; {@link #exit} hands it back.
 */
public final class SpectatorMode {

    public enum Mode { GHOST, TRUE }

    private record Worn(GameMode gamemode, boolean allowFlying, boolean flying) {}

    private static final Tag<String> MODE = Tag.Transient("polyp:spectator-mode");
    private static final Tag<Worn> WORN = Tag.Transient("polyp:spectator-worn");
    private static final Tag<Entity> CAMERA = SpectatorCamera.RIDING;
    private static final Tag<Task> RIDE = Tag.Transient("polyp:spectator-ride");
    private static final int FIRST_SPECTATOR_PROTOCOL = 47; // 1.8

    private SpectatorMode() {}

    /** Every game-mode change, this API's or a bare one: the flag, then the sight rules once the mode has landed. */
    public static void install(Polyp polyp) {
        EventNode<@NotNull PlayerEvent> node = EventNode.type("polyp:spectator-mode", EventFilter.PLAYER);
        node.addListener(PlayerGameModeChangeEvent.class, e -> {
            Player player = e.getPlayer();
            boolean spectator = e.getNewGameMode() == GameMode.SPECTATOR;
            if (spectator != (player.getGameMode() == GameMode.SPECTATOR)) player.setInvisible(spectator);
            MinecraftServer.getSchedulerManager().scheduleNextTick(() -> {
                if (player.isOnline()) WorldPolicy.refreshSight(player);
            });
        });
        polyp.install(node);
    }

    /** Null when not spectating. */
    public static @Nullable Mode mode(@NotNull Player player) {
        String mode = player.getTag(MODE);
        return mode == null ? null : Mode.valueOf(mode);
    }

    /** An unknown protocol reads as {@link ClientInfoTracker#protocolWhenUnknown} (modern by default). */
    public static boolean supports(@NotNull Player player, @NotNull Mode mode) {
        if (mode != Mode.TRUE) return true;
        ClientInfoTracker info = Polyp.getInstance().clientInfo();
        return info == null || info.protocolOrAssumed(player) >= FIRST_SPECTATOR_PROTOCOL;
    }

    /** {@code false} = the client can't wear {@code mode}; nothing changes. */
    public static boolean enter(@NotNull Player player, @NotNull Mode mode) {
        if (!supports(player, mode)) return false;
        if (player.getTag(WORN) == null) {
            player.setTag(WORN, new Worn(player.getGameMode(), player.isAllowFlying(), player.isFlying()));
        }
        if (mode != Mode.TRUE) cameraHome(player);
        player.setTag(MODE, mode.name());
        switch (mode) {
            case GHOST -> {
                player.setGameMode(GameMode.ADVENTURE);
                SpectatorHud.hide(player);
                player.setAllowFlying(true);
                player.setFlying(true);
            }
            case TRUE -> {
                SpectatorHud.show(player); // the spoof would swallow the change
                player.setGameMode(GameMode.SPECTATOR);
            }
        }
        WorldPolicy.refreshSight(player);
        return true;
    }

    /** Gives the body back; {@code false} = was not spectating. */
    public static boolean exit(@NotNull Player player) {
        Worn was = player.getTag(WORN);
        if (mode(player) == null && was == null) return false;
        cameraHome(player);
        player.removeTag(MODE);
        player.removeTag(WORN);
        SpectatorHud.show(player);
        if (was != null) {
            player.setGameMode(was.gamemode());
            player.setAllowFlying(was.allowFlying());
            player.setFlying(was.flying() && was.allowFlying());
        }
        WorldPolicy.refreshSight(player);
        return true;
    }

    /** The camera rides {@code target}; TRUE mode only. */
    public static boolean camera(@NotNull Player player, @NotNull Entity target) {
        if (mode(player) != Mode.TRUE || target == player || target.isRemoved()) return false;
        player.setTag(CAMERA, target);
        WorldPolicy.refreshSight(player); // a rider's body reaches no one
        if (target.getInstance() == player.getInstance()) player.teleport(target.getPosition());
        player.spectate(target);
        if (player.getTag(RIDE) == null) {
            player.setTag(RIDE, MinecraftServer.getSchedulerManager().scheduleTask(() -> ride(player), TaskSchedule.tick(1), TaskSchedule.tick(1)));
        }
        return true;
    }

    private static void ride(Player player) {
        Entity riding = player.getTag(CAMERA);
        if (riding == null || !player.isOnline()) {
            cameraHome(player);
            return;
        }
        boolean alive = !riding.isRemoved() && !(riding instanceof LivingEntity living && living.isDead());
        if (!alive || riding.getInstance() != player.getInstance() || player.isSneaking()) {
            cameraHome(player);
            return;
        }
        player.refreshPosition(riding.getPosition()); // no packet: the client rides the camera, not its body
    }

    public static @Nullable Entity camera(@NotNull Player player) {
        return player.getTag(CAMERA);
    }

    /** The camera back in the player's own eyes, where the ride left them. */
    public static void cameraHome(@NotNull Player player) {
        Task task = player.getTag(RIDE);
        if (task != null) {
            task.cancel();
            player.removeTag(RIDE);
        }
        Entity riding = player.getTag(CAMERA);
        if (riding == null) return;
        player.removeTag(CAMERA);
        if (!player.isOnline()) return;
        WorldPolicy.refreshSight(player);
        if (!riding.isRemoved() && riding.getInstance() == player.getInstance()) player.teleport(riding.getPosition());
        player.stopSpectating();
    }
}
