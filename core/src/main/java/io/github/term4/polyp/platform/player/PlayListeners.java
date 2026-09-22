package io.github.term4.polyp.platform.player;

import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.listener.manager.PacketPlayListenerConsumer;
import net.minestom.server.network.packet.client.ClientPacket;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Ownership of Minestom's one-listener-per-packet play slots. Minestom keeps a single listener per type and
 * exposes no getter, so whoever calls {@code setPlayListener} last silently drops everyone before them - Polyp's
 * own fixes did this to each other and to any app listener installed before init.
 *
 * <p>A slot claimed here holds the stock listener as its base and composes wrappers over it, each with a removal
 * handle. Apps register the same way, in any order. The chain is rebuilt on registration, never per packet.
 */
public final class PlayListeners {

    /** A link in a slot's chain: do the work, then hand the packet to {@code next} (or drop it by not calling it). */
    @FunctionalInterface
    public interface Wrapper<T extends ClientPacket> {
        void handle(@NotNull T packet, @NotNull Player player, @NotNull PacketPlayListenerConsumer<T> next);
    }

    private static final Map<Class<?>, Slot<?>> SLOTS = new ConcurrentHashMap<>();

    private PlayListeners() {}

    /**
     * The slot for {@code type}, claiming it on first ask with {@code stock} as the innermost listener - pass
     * Minestom's own (e.g. {@code BlockPlacementListener::listener}). A later call with a different stock keeps
     * the first: the base is whatever the slot was claimed with.
     */
    @SuppressWarnings("unchecked")
    public static <T extends ClientPacket> @NotNull Slot<T> slot(@NotNull Class<T> type,
                                                                 @NotNull PacketPlayListenerConsumer<T> stock) {
        return (Slot<T>) SLOTS.computeIfAbsent(type, ignored -> new Slot<>(type, stock));
    }

    /** {@link #slot} plus one wrapper, for the common single-wrapper case. Returns the removal handle. */
    public static <T extends ClientPacket> @NotNull Runnable wrap(@NotNull Class<T> type,
                                                                  @NotNull PacketPlayListenerConsumer<T> stock,
                                                                  @NotNull Wrapper<T> wrapper) {
        return slot(type, stock).wrap(wrapper);
    }

    public static final class Slot<T extends ClientPacket> {

        private final PacketPlayListenerConsumer<T> stock;
        private final List<Wrapper<T>> wrappers = new CopyOnWriteArrayList<>();
        private volatile PacketPlayListenerConsumer<T> chain;

        private Slot(Class<T> type, PacketPlayListenerConsumer<T> stock) {
            this.stock = stock;
            this.chain = stock;
            MinecraftServer.getPacketListenerManager().setPlayListener(type, this::dispatch);
        }

        private void dispatch(T packet, Player player) {
            chain.accept(packet, player);
        }

        /** Adds {@code wrapper} outside every wrapper already here; the handle removes it. */
        public @NotNull Runnable wrap(@NotNull Wrapper<T> wrapper) {
            wrappers.add(wrapper);
            rebuild();
            return () -> {
                if (wrappers.remove(wrapper)) rebuild();
            };
        }

        // folded once per registration, so a packet walks plain accepts with nothing allocated
        private void rebuild() {
            PacketPlayListenerConsumer<T> next = stock;
            List<Wrapper<T>> current = List.copyOf(wrappers);
            for (int i = current.size() - 1; i >= 0; i--) {
                Wrapper<T> wrapper = current.get(i);
                PacketPlayListenerConsumer<T> inner = next;
                next = (packet, player) -> wrapper.handle(packet, player, inner);
            }
            this.chain = next;
        }
    }
}
