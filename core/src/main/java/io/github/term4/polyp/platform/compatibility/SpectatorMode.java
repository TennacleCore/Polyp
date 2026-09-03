package io.github.term4.polyp.platform.compatibility;

import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.tracking.ClientInfoTracker;
import io.github.term4.polyp.tracking.ClientVersion;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The body a spectator wears, and the switch between the two. {@link Mode#GHOST}: adventure, hovering, the stat
 * bars off a 1.8 screen ({@link SpectatorHud}) - the scrims spectator, still drawn their own body. {@link Mode#TRUE}:
 * the client's own spectator mode - noclip, and the camera inside whoever they attack-click ({@link #camera});
 * shift brings it home, the vanilla way. 1.7 has no spectator mode, so TRUE is refused there ({@link #supports})
 * and the app falls back to GHOST. The first {@link #enter} keeps the body the player walked in with; {@link #exit}
 * hands it back.
 */
public final class SpectatorMode {

    public enum Mode { GHOST, TRUE }

    private record Worn(GameMode gamemode, boolean allowFlying, boolean flying) {}

    private static final Tag<String> MODE = Tag.Transient("polyp:spectator-mode");
    private static final Tag<Worn> WORN = Tag.Transient("polyp:spectator-worn");
    private static final Tag<Entity> CAMERA = Tag.Transient("polyp:spectator-camera");
    private static final int FIRST_SPECTATOR_PROTOCOL = 47; // 1.8

    private SpectatorMode() {}

    /** Null when not spectating. */
    public static @Nullable Mode mode(@NotNull Player player) {
        String mode = player.getTag(MODE);
        return mode == null ? null : Mode.valueOf(mode);
    }

    /** An unknown protocol counts as modern. */
    public static boolean supports(@NotNull Player player, @NotNull Mode mode) {
        if (mode != Mode.TRUE) return true;
        ClientInfoTracker info = Polyp.getInstance().clientInfo();
        int protocol = info == null ? ClientVersion.UNKNOWN_PROTOCOL : info.getProtocol(player);
        return protocol == ClientVersion.UNKNOWN_PROTOCOL || protocol >= FIRST_SPECTATOR_PROTOCOL;
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
        return true;
    }

    /** The camera rides {@code target}; TRUE mode only. The body follows, as vanilla moves it. */
    public static boolean camera(@NotNull Player player, @NotNull Entity target) {
        if (mode(player) != Mode.TRUE || target == player) return false;
        player.setTag(CAMERA, target);
        if (target.getInstance() == player.getInstance()) player.teleport(target.getPosition());
        player.spectate(target);
        return true;
    }

    public static @Nullable Entity camera(@NotNull Player player) {
        return player.getTag(CAMERA);
    }

    /** The camera back in the player's own eyes, where the ride left them. */
    public static void cameraHome(@NotNull Player player) {
        Entity riding = player.getTag(CAMERA);
        if (riding == null) return;
        player.removeTag(CAMERA);
        if (!riding.isRemoved() && riding.getInstance() == player.getInstance()) player.teleport(riding.getPosition());
        player.stopSpectating();
    }
}
