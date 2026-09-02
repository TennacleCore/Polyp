package io.github.term4.polyp.platform.compatibility;

import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;
import java.util.Set;
import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.platform.player.OptimizedPlayer;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Player;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerPacketOutEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.network.NetworkBuffer;
import net.minestom.server.network.packet.server.play.SetSlotPacket;
import net.minestom.server.network.packet.server.play.WindowItemsPacket;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * A stack of zero on the 1.8 wire. The client draws a "0" over the icon; the modern wire cannot carry it (a count of
 * 0 is an empty slot), so an item marked {@link #zero} goes out as count 1 and, for a legacy client whose bridge
 * answers, every slot packet carrying it is followed by the same slot byte-exact at 1.8 with the count patched to
 * 0: Via translates the item ({@code TRANSFORM}), the bridge sends the patched bytes raw. A modern client keeps the
 * lone icon.
 */
public final class LegacyZeroCountBridge {

    private static final Logger LOGGER = LoggerFactory.getLogger(LegacyZeroCountBridge.class);
    private static final Set<UUID> WARNED = ConcurrentHashMap.newKeySet();
    private static final Tag<Boolean> ZERO = Tag.Boolean("polyp:zero-count");
    /** 26.2 reuses 26.1's clientbound enum in Via. */
    private static final String BACKEND_PACKETS = "ClientboundPackets26_1";
    private static final String SET_SLOT = "CONTAINER_SET_SLOT";
    /** 1.8 {@code SET_SLOT} fields: byte window, short slot, short item id, byte count. */
    private static final int ITEM_ID_OFFSET = 3;
    private static final int COUNT_OFFSET = 5;

    private LegacyZeroCountBridge() {}

    public static void install(@NotNull Polyp polyp) {
        EventNode<@NotNull PlayerEvent> node = EventNode.type("polyp:legacy-zero-count-bridge", EventFilter.PLAYER);
        node.addListener(PlayerPacketOutEvent.class, LegacyZeroCountBridge::onPacketOut);
        polyp.install(node);
    }

    /** {@code item} displayed as a stack of zero (1.8 clients over the bridge; a lone icon elsewhere). */
    public static @NotNull ItemStack zero(@NotNull ItemStack item) {
        return item.withAmount(1).withTag(ZERO, true);
    }

    public static boolean isZero(@NotNull ItemStack item) {
        return !item.isAir() && Boolean.TRUE.equals(item.getTag(ZERO));
    }

    private static void onPacketOut(PlayerPacketOutEvent e) {
        if (!(e.getPlayer() instanceof OptimizedPlayer player) || !player.compat().legacyClient()
                || !ViaBridgeRpc.available(player)) return;
        switch (e.getPacket()) {
            case SetSlotPacket p when isZero(p.itemStack()) -> resend(player, p);
            case WindowItemsPacket p -> {
                List<ItemStack> items = p.items();
                for (int i = 0; i < items.size(); i++) {
                    if (isZero(items.get(i))) resend(player, new SetSlotPacket(p.windowId(), p.stateId(), (short) i, items.get(i)));
                }
            }
            default -> { }
        }
    }

    // lands after the packet it follows: the RPC frames queue behind it on the same connection
    private static void resend(Player player, SetSlotPacket slot) {
        NetworkBuffer buffer = NetworkBuffer.resizableBuffer(256, MinecraftServer.process());
        buffer.write(SetSlotPacket.SERIALIZER, slot);
        byte[] modern = buffer.read(NetworkBuffer.RAW_BYTES);
        ViaBridgeRpc rpc = ViaBridgeRpc.get();
        rpc.transformClientbound(player, MinecraftServer.PROTOCOL_VERSION, BACKEND_PACKETS, SET_SLOT, modern)
                .thenCompose(legacy -> {
                    byte[] body = zeroed(legacy);
                    if (body == null) return CompletableFuture.<Void>completedFuture(null);
                    return rpc.sendClientbound(player, ViaBridgeRpc.PROTOCOL_1_8, ViaBridgeRpc.PACKETS_1_8, SET_SLOT, body);
                })
                .exceptionally(err -> {
                    if (WARNED.add(player.getUuid())) LOGGER.warn("zero-count resend for {} failed: {}", player.getUsername(), err.getMessage());
                    return null;
                });
    }

    /** The transformed packet's fields (its id dropped) with the count patched to 0; {@code null} = an empty slot, nothing to patch. */
    static byte @Nullable [] zeroed(byte[] transformed) {
        int at = 0;
        while (at < transformed.length && (transformed[at] & 0x80) != 0) at++; // the packet id varint
        at++;
        if (transformed.length - at <= COUNT_OFFSET) return null;
        byte[] body = new byte[transformed.length - at];
        System.arraycopy(transformed, at, body, 0, body.length);
        short id = (short) ((body[ITEM_ID_OFFSET] << 8) | (body[ITEM_ID_OFFSET + 1] & 0xFF));
        if (id < 0) return null;
        body[COUNT_OFFSET] = 0;
        return body;
    }
}
