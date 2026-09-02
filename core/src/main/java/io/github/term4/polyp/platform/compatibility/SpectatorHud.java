package io.github.term4.polyp.platform.compatibility;

import io.github.term4.polyp.Polyp;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.play.ChangeGameStatePacket;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;

/**
 * The health, hunger and armor bars off a player's screen while the server keeps them in survival or
 * adventure - the scrims spectator: the client is told a game mode it has no case for. A 1.8 client reads
 * {@code -1} as NOT_SET, which draws no stat bars, keeps the hotbar, and opens the ordinary inventory on E
 * (captured on na.scrims.network). A modern client clamps unknown ids to survival, so it is told spectator
 * instead: bars and hotbar off, the ordinary inventory on E. The server never changes its own mind: every
 * game-mode change and every respawn the client is sent is re-told the spoof until {@link #show}.
 */
public final class SpectatorHud {

    private static final Tag<Boolean> HIDDEN = Tag.Transient("polyp:hud-hidden");
    private static final float LEGACY_NOT_SET = -1f;

    private SpectatorHud() {}

    public static void hide(@NotNull Player player) {
        player.setTag(HIDDEN, true);
        player.sendPacket(spoof(player));
    }

    public static void show(@NotNull Player player) {
        if (!hidden(player)) return;
        player.removeTag(HIDDEN);
        player.sendPacket(new ChangeGameStatePacket(ChangeGameStatePacket.Reason.CHANGE_GAMEMODE, player.getGameMode().ordinal()));
    }

    public static boolean hidden(@NotNull Player player) {
        return Boolean.TRUE.equals(player.getTag(HIDDEN));
    }

    /** What the client is told its game mode is. */
    public static @NotNull ChangeGameStatePacket spoof(@NotNull Player player) {
        boolean legacy = Polyp.getInstance().clientInfo() != null && Polyp.getInstance().clientInfo().isLegacy(player);
        return new ChangeGameStatePacket(ChangeGameStatePacket.Reason.CHANGE_GAMEMODE,
                legacy ? LEGACY_NOT_SET : GameMode.SPECTATOR.ordinal());
    }

    /** The outgoing game-mode change, re-told as the spoof while hidden. */
    public static @NotNull SendablePacket rewrite(@NotNull Player player, @NotNull SendablePacket packet) {
        if (packet instanceof ChangeGameStatePacket change
                && change.reason() == ChangeGameStatePacket.Reason.CHANGE_GAMEMODE && hidden(player)) {
            return spoof(player);
        }
        return packet;
    }
}
