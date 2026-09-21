package io.github.term4.polyp.platform.fixes.client;

import io.github.term4.polyp.platform.PacketShapes;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.ServerPacket;
import net.minestom.server.network.packet.server.play.SetPlayerInventorySlotPacket;
import net.minestom.server.network.packet.server.play.SetSlotPacket;
import net.minestom.server.utils.inventory.PlayerInventoryUtils;

/**
 * Sends a legacy client's player-inventory slot updates the way a 1.8 server does: window 0 with the menu slot,
 * not {@code SET_PLAYER_INVENTORY}. That packet reaches 1.8 as {@code CONTAINER_SET_SLOT} window {@code -2}, which
 * the client has no case for; ViaRewind retargets it, but only as well as its own container tracking - a stale open
 * window routes the slot into a container that isn't there and the item never appears (the kit sword that shows in
 * the hand and in a later full resend, never in the bag). Window-0 hotbar slots 36-44 the 1.8 client always applies.
 */
public final class LegacyInventorySlotFix {

    private static volatile boolean enabled;

    private LegacyInventorySlotFix() {}

    public static void install() {
        enabled = true;
    }

    public static boolean enabled() { return enabled; }

    public static void disable() {
        enabled = false;
    }

    public static SendablePacket rewrite(boolean legacyClient, SendablePacket packet) {
        if (!enabled || !legacyClient) return packet;
        final ServerPacket server = PacketShapes.unwrapStateless(packet);
        if (!(server instanceof SetPlayerInventorySlotPacket slot)) return packet;
        final int minestom = PlayerInventoryUtils.convertPlayerInventorySlotToMinestomSlot(slot.slot());
        if (minestom < 0) return packet;
        return new SetSlotPacket(0, 0, (short) PlayerInventoryUtils.convertMinestomSlotToWindowSlot(minestom),
                slot.itemStack());
    }
}
