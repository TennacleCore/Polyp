package io.github.term4.polyp.platform.fixes.client;

import net.minestom.server.entity.Player;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.item.ItemStack;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerBlockPlacementPacket;
import org.jetbrains.annotations.Nullable;

import java.util.function.BooleanSupplier;

/**
 * A 1.8 client sends the placement cursor in sixteenths, so a side-face hit in {@code [0.5, 0.5625)} arrives as
 * exactly 0.5 and every slab/stair/trapdoor rule (strict {@code > 0.5}) lands the BOTTOM half. One sixteenth up,
 * side faces only. A modern client's 0.5 is a real bottom-half hit.
 */
public final class LegacyPlacementHalfFix {

    private static final float SIXTEENTH = 1f / 16f;

    private LegacyPlacementHalfFix() {}

    /** The same instance when nothing applies; {@code gate} (legacy client + toggle) is read only for a 0.5 placement. */
    public static ClientPacket rewrite(ClientPacket packet, Player player, BooleanSupplier gate) {
        if (!(packet instanceof ClientPlayerBlockPlacementPacket p) || p.cursorPositionY() != 0.5f) return packet;
        if (!gate.getAsBoolean()) return packet;
        return rewrite(p, player.getItemInHand(p.hand()));
    }

    public static ClientPacket rewrite(ClientPlayerBlockPlacementPacket p, @Nullable ItemStack held) {
        if (p.cursorPositionY() != 0.5f) return p;
        BlockFace face = p.blockFace();
        if (face == BlockFace.TOP || face == BlockFace.BOTTOM) return p;
        if (held == null || held.isAir() || !hasHalves(held)) return p;
        return new ClientPlayerBlockPlacementPacket(p.hand(), p.blockPosition(), face,
                p.cursorPositionX(), 0.5f + SIXTEENTH, p.cursorPositionZ(),
                p.insideBlock(), p.hitWorldBorder(), p.sequence());
    }

    private static boolean hasHalves(ItemStack held) {
        String key = held.material().key().value();
        return key.endsWith("_slab") || key.endsWith("_stairs") || key.endsWith("_trapdoor");
    }
}
