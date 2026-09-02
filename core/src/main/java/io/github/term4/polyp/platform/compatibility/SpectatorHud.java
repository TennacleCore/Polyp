package io.github.term4.polyp.platform.compatibility;

import io.github.term4.polyp.Polyp;
import net.minestom.server.entity.Player;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.play.ChangeGameStatePacket;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;

/**
 * The health, hunger and armor bars off a 1.8 client's screen while the server keeps the player in survival
 * or adventure - the scrims spectator: the client is told game mode {@code -1}, which a 1.8 client reads as
 * NOT_SET and draws no stat bars, keeps the hotbar, and opens the ordinary inventory on E (captured on
 * na.scrims.network). A MODERN client has no such lever - its HUD hides the bars only in creative or
 * spectator, both of which change the inventory - so it is left alone (bars visible). The server never
 * changes its own mind: every game-mode change and every respawn the 1.8 client is sent is re-told the
 * spoof until {@link #show}.
 */
public final class SpectatorHud {

    private static final Tag<Boolean> HIDDEN = Tag.Transient("polyp:hud-hidden");
    private static final float LEGACY_NOT_SET = -1f;

    private SpectatorHud() {}

    public static void hide(@NotNull Player player) {
        if (!legacy(player)) return; // no modern lever keeps the survival inventory AND drops the bars
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

    /** A 1.8 client is told NOT_SET; only a hidden (hence legacy) player is ever sent this. */
    public static @NotNull ChangeGameStatePacket spoof(@NotNull Player player) {
        return new ChangeGameStatePacket(ChangeGameStatePacket.Reason.CHANGE_GAMEMODE, LEGACY_NOT_SET);
    }

    private static boolean legacy(Player player) {
        return Polyp.getInstance().clientInfo() != null && Polyp.getInstance().clientInfo().isLegacy(player);
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
