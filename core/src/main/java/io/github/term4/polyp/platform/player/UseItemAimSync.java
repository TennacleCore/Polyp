package io.github.term4.polyp.platform.player;

import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerBlockPlacementPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionAndRotationPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerPositionStatusPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerRotationPacket;
import net.minestom.server.network.packet.client.play.ClientUseItemPacket;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Syncs a legacy client's use-item aim to the click (the MineMen behavior). The 1.8 use packet carries no rotation;
 * the click-time aim rides the SAME-TICK flying packet right behind it (the client sends use during input dispatch,
 * look during its entity tick), so Via fills the modern packet's yaw/pitch from the PREVIOUS look - a flick-and-throw
 * launches along the stale aim. Held at queue entry until that tick's flying packet arrives, the use packet is patched
 * from it and the stream reads like a modern client's (whose use packet carries the click aim natively).
 *
 * <p>A click ON a block within reach is a different packet - block placement - and it carries no rotation at all,
 * before or after Via. The server reads the player's stored view when it handles it, so that one is held the same
 * way and released AFTER the flying packet: the look lands first, then the click reads it. A 1.8 right-click at a
 * block can arrive as placement and use together, so the hold is a short ordered list, released in arrival order.
 *
 * <p>Runs on the connection's read thread (single-threaded per player). A flying packet without rotation releases
 * unpatched - the aim didn't change that tick, so the stored rotation is already the click aim.
 */
final class UseItemAimSync {

    /** Stale-hold safety: a vanilla client sends a flying packet every tick; past this, release unpatched. */
    private static final long HOLD_TIMEOUT_NANOS = 100_000_000L;

    private final List<ClientPacket> held = new ArrayList<>(2);
    private long heldAt;
    // re-held re-feeds get a fresh instance per flying packet; an identity-tracking re-feeder (lag sim) then never passes the use
    private final ClientPacket[] emitted = new ClientPacket[6];
    private int emittedIndex;

    private static boolean isClick(ClientPacket packet) {
        return packet instanceof ClientUseItemPacket || packet instanceof ClientPlayerBlockPlacementPacket;
    }

    /** Routes one arriving packet to {@code out}, possibly holding/patching a click. {@code gate} = the knob + legacy check, read at click arrival. */
    synchronized void intercept(ClientPacket packet, BooleanSupplier gate, Consumer<ClientPacket> out) {
        if (isClick(packet) && wasEmitted(packet)) {
            out.accept(packet);
            return;
        }
        if (!held.isEmpty() && System.nanoTime() - heldAt > HOLD_TIMEOUT_NANOS) releaseUnpatched(out);
        if (isClick(packet)) {
            // a vanilla 1.8 client can't send the same click twice in a tick; a repeat means a new tick, don't stack
            if (held.stream().anyMatch(h -> h.getClass() == packet.getClass())) releaseUnpatched(out);
            if (gate.getAsBoolean()) {
                if (held.isEmpty()) heldAt = System.nanoTime();
                held.add(packet);
            } else {
                out.accept(packet);
            }
            return;
        }
        if (held.isEmpty()) {
            out.accept(packet);
            return;
        }
        switch (packet) {
            case ClientPlayerRotationPacket rot -> release(rot.yaw(), rot.pitch(), packet, out);
            case ClientPlayerPositionAndRotationPacket posRot ->
                    release(posRot.position().yaw(), posRot.position().pitch(), packet, out);
            case ClientPlayerPositionPacket ignored -> { releaseUnpatched(out); out.accept(packet); }
            case ClientPlayerPositionStatusPacket ignored -> { releaseUnpatched(out); out.accept(packet); }
            default -> out.accept(packet); // non-flying (swing, chat, ...): pass through, keep holding
        }
    }

    private void release(float yaw, float pitch, ClientPacket flying, Consumer<ClientPacket> out) {
        // a placement reads the stored view, so the look must land before it; a lone use carries its own aim and
        // keeps reading like a modern stream (use, then flying)
        boolean lookFirst = held.stream().anyMatch(h -> h instanceof ClientPlayerBlockPlacementPacket);
        if (lookFirst) out.accept(flying);
        for (ClientPacket h : held) {
            ClientPacket emit = h instanceof ClientUseItemPacket use
                    ? new ClientUseItemPacket(use.hand(), use.sequence(), yaw, pitch) : h;
            remember(emit);
            out.accept(emit);
        }
        held.clear();
        if (!lookFirst) out.accept(flying);
    }

    private void releaseUnpatched(Consumer<ClientPacket> out) {
        for (ClientPacket h : held) {
            remember(h);
            out.accept(h);
        }
        held.clear();
    }

    private void remember(ClientPacket packet) {
        emitted[emittedIndex] = packet;
        emittedIndex = (emittedIndex + 1) % emitted.length;
    }

    private boolean wasEmitted(ClientPacket packet) {
        for (ClientPacket p : emitted) {
            if (p == packet) return true;
        }
        return false;
    }
}
