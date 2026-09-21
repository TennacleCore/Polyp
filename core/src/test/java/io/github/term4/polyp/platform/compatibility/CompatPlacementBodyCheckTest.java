package io.github.term4.polyp.platform.compatibility;

import io.github.term4.polyp.platform.player.OptimizedPlayer;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.util.BlockContact;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.instance.block.Block;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 1.8's own placement check for a legacy placer: nobody excluded, the block's 1.8 box - none for a passable block,
 * the raytrace octant for stairs. Modern placers stay on the precise shape their client predicts.
 */
class CompatPlacementBodyCheckTest extends HeadlessServerTest {

    private static final BoundingBox PLAYER = new BoundingBox(0.6, 1.8, 0.6);
    private static final Vec CENTERED = new Vec(0.5, 0, 0.5);
    private static final Vec WEST_EDGE = new Vec(0.15, 0, 0.5);

    private static boolean blocks(OptimizedPlayer placer, Entity body, Block placing, Vec at) {
        return CompatPlacement.placementBodyCheck(placer, body, placing, at, PLAYER);
    }

    @Test
    void legacyChecksItsOwnBody() {
        FakePlayer fp = FakePlayer.connect(instance, new Pos(0.5, 65, 864.5), "BodyGate");
        OptimizedPlayer op = (OptimizedPlayer) fp.player;
        LivingEntity other = looseZombie();
        try {
            assertTrue(blocks(op, op, Block.OAK_STAIRS, CENTERED), "modern: the stair shape, like the client predicts");
            assertTrue(blocks(op, op, Block.STONE, CENTERED));

            op.compat().setLegacyClient(true);
            assertTrue(blocks(op, op, Block.STONE, CENTERED), "1.8 checks the placer too");
            assertTrue(blocks(op, op, Block.CHEST, CENTERED), "a chest never lands in your own cell");
            assertTrue(blocks(op, op, Block.OAK_STAIRS, CENTERED), "the octant (0.5,0.5,0.5)-(1,1,1) meets a centered body");
            assertFalse(blocks(op, op, Block.OAK_STAIRS, WEST_EDGE), "from the west edge it clears: stairs into your own face");
            assertFalse(blocks(op, other, Block.LADDER, CENTERED), "no collision box: no check for anyone");
            assertTrue(blocks(op, other, Block.STONE, CENTERED));
            assertFalse(blocks(op, other, Block.OAK_STAIRS, WEST_EDGE), "one box for every body");

            // the app-side veto condition (a Hypixel-style PlayerBlockPlaceEvent cancel), by fill level
            Vec feet = new Vec(fp.player.getPosition().blockX(), 65, fp.player.getPosition().blockZ());
            assertTrue(BlockContact.overlapsBody(Block.OAK_STAIRS, feet, fp.player), "a stair in the placer's feet cell overlaps them");
            assertFalse(BlockContact.overlapsBody(Block.LADDER, feet, fp.player), "no collision shape, no overlap");
            assertFalse(BlockContact.overlapsBody(Block.OAK_STAIRS, feet.add(3, 0, 0), fp.player));
            assertFalse(BlockContact.isFullCube(Block.OAK_STAIRS) && BlockContact.overlapsBody(Block.OAK_STAIRS, feet, fp.player),
                    "a full-cube-only policy lets partial blocks through");
            assertTrue(BlockContact.isFullCube(Block.STONE) && BlockContact.overlapsBody(Block.STONE, feet, fp.player));
        } finally {
            fp.player.remove();
        }
    }

    /** ItemSlab's merge and ItemBlock's single are one rule: the placer counts on both. */
    @Test
    void aSlabBlocksItsPlacer() {
        FakePlayer fp = FakePlayer.connect(instance, new Pos(0.5, 64.5, 880.5), "SlabGate");
        OptimizedPlayer op = (OptimizedPlayer) fp.player;
        Vec standing = new Vec(0.5, 0.5, 0.5); // feet on the top face of the half they clicked
        try {
            op.compat().setLegacyClient(true);
            assertTrue(blocks(op, op, Block.OAK_SLAB.withProperty("type", "double"), standing), "the double would engulf the placer");
            assertTrue(blocks(op, op, Block.OAK_SLAB.withProperty("type", "top"), standing), "a single slab is a real box too");
            assertFalse(blocks(op, op, Block.OAK_SLAB.withProperty("type", "double"), standing.add(3, 0, 0)),
                    "a cell clear of the body still merges");
        } finally {
            fp.player.remove();
        }
    }

    /** legacySelfPlace(false) - the hypixel bounce: the real stair shape, ladders still clutch. */
    @Test
    void hypixelBounceIsTheRealShape() {
        FakePlayer fp = FakePlayer.connect(instance, new Pos(0.5, 65, 872.5), "StrictGate");
        OptimizedPlayer op = (OptimizedPlayer) fp.player;
        try {
            op.compat().setLegacyClient(true);
            op.compat().apply(CompatConfig.builder().legacySelfPlace(false).build(), op);

            assertTrue(blocks(op, op, Block.OAK_STAIRS, WEST_EDGE), "no octant: the stair's base meets a body at the edge");
            assertTrue(blocks(op, op, Block.STONE, CENTERED));
            assertFalse(blocks(op, op, Block.LADDER, CENTERED), "passable: the ladder clutch survives the bounce");
            assertFalse(blocks(op, op, Block.OAK_STAIRS, CENTERED.add(3, 0, 0)), "a cell clear of the body still places");
        } finally {
            fp.player.remove();
        }
    }
}
