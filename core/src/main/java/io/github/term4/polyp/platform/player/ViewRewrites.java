package io.github.term4.polyp.platform.player;

import net.minestom.server.network.packet.server.SendablePacket;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;

/**
 * An app's per-viewer packet rewrites, applied to every packet an {@link OptimizedPlayer} is sent after the
 * platform's own: what one client sees of an entity that another must not (a ghost's body to its fellow dead,
 * an invisible player's armor to an enemy). A rewrite returns the packet it was given to pass it on unchanged.
 */
public final class ViewRewrites {

    private static final List<BiFunction<OptimizedPlayer, SendablePacket, SendablePacket>> REWRITES = new CopyOnWriteArrayList<>();
    private static final List<Class<?>> BARE = new CopyOnWriteArrayList<>();

    private ViewRewrites() {}

    /**
     * Installs {@code rewrite}; the returned runnable removes it. A broadcast of a packet of one of the {@code bare}
     * classes goes to each viewer on its own instead of grouped, so the rewrite sees it at all.
     */
    public static @NotNull Runnable register(@NotNull BiFunction<OptimizedPlayer, SendablePacket, SendablePacket> rewrite,
                                             @NotNull Class<?>... bare) {
        REWRITES.add(rewrite);
        List<Class<?>> mine = List.of(bare);
        BARE.addAll(mine);
        return () -> {
            REWRITES.remove(rewrite);
            for (Class<?> type : mine) BARE.remove(type);
        };
    }

    static boolean bare(@NotNull SendablePacket packet) {
        for (Class<?> type : BARE) if (type.isInstance(packet)) return true;
        return false;
    }

    static @NotNull SendablePacket apply(@NotNull OptimizedPlayer viewer, @NotNull SendablePacket packet) {
        SendablePacket p = packet;
        for (BiFunction<OptimizedPlayer, SendablePacket, SendablePacket> rewrite : REWRITES) p = rewrite.apply(viewer, p);
        return p;
    }
}
