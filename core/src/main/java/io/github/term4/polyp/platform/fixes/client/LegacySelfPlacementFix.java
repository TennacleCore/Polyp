package io.github.term4.polyp.platform.fixes.client;

import io.github.term4.polyp.platform.player.OptimizedPlayer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.listener.BlockPlacementListener;
import net.minestom.server.network.packet.client.play.ClientPlayerBlockPlacementPacket;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

/**
 * Paper 1.8.8's placer exclusion for a plain-Minestom app: the placer's own body never blocks their placement.
 * A vanilla 1.8 client gates on its own body before sending, so only an ungated client sees the difference;
 * an Archipelago app runs 1.8's own check through {@code Shard.placementBodyCheck} instead. Arms {@link OptimizedPlayer#setSelfPlacing} per placement for
 * legacy placers (a modern client refuses these itself; server leniency would let it bury blocks in its own
 * body). An app refusing self-overlap as POLICY (Hypixel-style) cancels {@code PlayerBlockPlaceEvent} with
 * {@code BlockContact.overlapsBody} as the condition. Wraps the stock listener; shard worlds use
 * {@code Shard.placementBodyCheck} instead, which adds the no-collision-box skip for other bodies too.
 */
public final class LegacySelfPlacementFix {

    private LegacySelfPlacementFix() {}

    /** Installs the stock placement listener wrapped with the 1.8 self-placement exclusion. */
    public static void install() {
        install(BlockPlacementListener::listener);
    }

    /** Hands the slot back to Minestom's own listener. */
    public static void uninstall() {
        MinecraftServer.getPacketListenerManager().setPlayListener(ClientPlayerBlockPlacementPacket.class,
                BlockPlacementListener::listener);
    }

    /** Installs {@code delegate} wrapped with the exclusion - the composition seam for replaced placement listeners. */
    public static void install(@NotNull BiConsumer<ClientPlayerBlockPlacementPacket, Player> delegate) {
        MinecraftServer.getPacketListenerManager().setPlayListener(ClientPlayerBlockPlacementPacket.class,
                wrap(delegate)::accept);
    }

    /** {@code delegate} wrapped with the exclusion but NOT installed - for hosts that own the listener slot, where
     *  registration order must not matter. */
    public static @NotNull BiConsumer<ClientPlayerBlockPlacementPacket, Player> wrap(
            @NotNull BiConsumer<ClientPlayerBlockPlacementPacket, Player> delegate) {
        return (packet, player) -> wrapped(delegate, packet, player);
    }

    private static void wrapped(BiConsumer<ClientPlayerBlockPlacementPacket, Player> delegate,
                                ClientPlayerBlockPlacementPacket packet, Player player) {
        OptimizedPlayer op = player instanceof OptimizedPlayer o && excludesPlacer(packet, o) ? o : null;
        if (op != null) op.setSelfPlacing(true);
        try {
            delegate.accept(packet, player);
        } finally {
            if (op != null) op.setSelfPlacing(false);
        }
    }

    /** The null guard scopes the arming to actual block placements (a non-block item has a {@code null} {@code Material#block()}). */
    private static boolean excludesPlacer(ClientPlayerBlockPlacementPacket packet, OptimizedPlayer player) {
        return player.compat().legacyClient() && player.getItemInHand(packet.hand()).material().block() != null;
    }
}
