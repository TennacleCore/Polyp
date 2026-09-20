package io.github.term4.polyp.platform.fixes.client;

import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.play.ClientPlayerBlockPlacementPacket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/** A byte cursor of exactly 0.5 on a side face is the upper half the client aimed at, for the blocks that have halves. */
class LegacyPlacementHalfFixTest {

    private static ClientPlayerBlockPlacementPacket place(BlockFace face, float cursorY) {
        return new ClientPlayerBlockPlacementPacket(PlayerHand.MAIN, new Pos(4, 63, 4), face, 0.5f, cursorY, 0.5f, false, false, 8);
    }

    private static float cursorY(ClientPacket out) {
        return assertInstanceOf(ClientPlayerBlockPlacementPacket.class, out).cursorPositionY();
    }

    @Test
    void halfBlocksNudgeUp() {
        for (Material m : new Material[]{Material.STONE_SLAB, Material.OAK_STAIRS, Material.OAK_TRAPDOOR}) {
            assertEquals(0.5f + 1f / 16f, cursorY(LegacyPlacementHalfFix.rewrite(place(BlockFace.NORTH, 0.5f), ItemStack.of(m))), 1e-7, m.name());
        }
    }

    @Test
    void onlyTheExactMiddle() {
        ClientPlayerBlockPlacementPacket low = place(BlockFace.NORTH, 0.4375f);
        assertSame(low, LegacyPlacementHalfFix.rewrite(low, ItemStack.of(Material.STONE_SLAB)), "7/16 is the bottom half");
        ClientPlayerBlockPlacementPacket high = place(BlockFace.NORTH, 0.5625f);
        assertSame(high, LegacyPlacementHalfFix.rewrite(high, ItemStack.of(Material.STONE_SLAB)), "9/16 already lands top");
    }

    @Test
    void topAndBottomFacesAndOtherBlocksPass() {
        ClientPlayerBlockPlacementPacket top = place(BlockFace.TOP, 0.5f);
        assertSame(top, LegacyPlacementHalfFix.rewrite(top, ItemStack.of(Material.STONE_SLAB)), "the face itself decides there");
        ClientPlayerBlockPlacementPacket bottom = place(BlockFace.BOTTOM, 0.5f);
        assertSame(bottom, LegacyPlacementHalfFix.rewrite(bottom, ItemStack.of(Material.STONE_SLAB)));
        ClientPlayerBlockPlacementPacket stone = place(BlockFace.NORTH, 0.5f);
        assertSame(stone, LegacyPlacementHalfFix.rewrite(stone, ItemStack.of(Material.STONE)), "no halves to pick");
        assertSame(stone, LegacyPlacementHalfFix.rewrite(stone, ItemStack.AIR));
    }
}
