package io.github.term4.polyp.platform.compatibility;

import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.utils.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A chest lands only where a 1.8 client draws it: beside at most one chest, never beside a pair. */
class LegacyChestShapeTest extends HeadlessServerTest {

    private static final int Y = 64;
    private static final int Z = 800;

    @Test
    void besideOneNeverBesideAPair() {
        MechanicsWorld world = MechanicsWorld.of(instance);
        try {
            assertTrue(CompatPlacement.legacyChestShape(world, new BlockVec(10, Y, Z), Block.CHEST, Direction.NORTH), "alone");
            instance.setBlock(10, Y, Z, Block.CHEST);
            assertTrue(CompatPlacement.legacyChestShape(world, new BlockVec(11, Y, Z), Block.CHEST, Direction.NORTH), "beside one single: a pair");

            instance.setBlock(1, Y, Z, Block.CHEST);
            instance.setBlock(2, Y, Z, Block.CHEST); // a pair
            assertFalse(CompatPlacement.legacyChestShape(world, new BlockVec(0, Y, Z), Block.CHEST, Direction.NORTH), "three in a row");
            assertFalse(CompatPlacement.legacyChestShape(world, new BlockVec(3, Y, Z), Block.CHEST, Direction.NORTH), "the far end of a pair");
            assertFalse(CompatPlacement.legacyChestShape(world, new BlockVec(1, Y, Z + 1), Block.CHEST, Direction.EAST), "beside one half of a pair");
            assertTrue(CompatPlacement.legacyChestShape(world, new BlockVec(1, Y, Z + 1), Block.TRAPPED_CHEST, Direction.EAST), "a trapped chest never pairs with a chest");
        } finally {
            for (int x : new int[]{0, 1, 2, 3, 10, 11}) instance.setBlock(x, Y, Z, Block.AIR);
        }
    }

    /** A north-facing single: a chest west of it pairs in both worlds; one north of it sits along its facing. */
    @Test
    void alongAFacingAxisRefuses() {
        MechanicsWorld world = MechanicsWorld.of(instance);
        try {
            instance.setBlock(30, Y, Z, Block.CHEST); // facing north, single
            assertTrue(CompatPlacement.legacyChestShape(world, new BlockVec(29, Y, Z), Block.CHEST, Direction.NORTH), "beside it, both facing north");
            assertFalse(CompatPlacement.legacyChestShape(world, new BlockVec(29, Y, Z), Block.CHEST, Direction.EAST), "beside it, but facing along the join");
            assertFalse(CompatPlacement.legacyChestShape(world, new BlockVec(30, Y, Z + 1), Block.CHEST, Direction.NORTH), "in front of it");
            assertFalse(CompatPlacement.legacyChestShape(world, new BlockVec(30, Y, Z + 1), Block.CHEST, Direction.EAST), "in front of it, whichever way this one faces");
        } finally {
            instance.setBlock(30, Y, Z, Block.AIR);
        }
    }

    @Test
    void thePlacementIsRefused() {
        FakePlayer placer = FakePlayer.connect(instance, new Pos(0.5, Y, Z + 5.5), "ChestPlacer");
        try {
            instance.setBlock(21, Y, Z, Block.CHEST);
            instance.setBlock(22, Y, Z, Block.CHEST);
            var third = new PlayerBlockPlaceEvent(placer.player, instance, Block.CHEST, BlockFace.TOP,
                    new BlockVec(23, Y, Z), new Pos(0.5, 1, 0.5), PlayerHand.MAIN);
            EventDispatcher.call(third);
            assertTrue(third.isCancelled(), "a third in a row is refused");

            var apart = new PlayerBlockPlaceEvent(placer.player, instance, Block.CHEST, BlockFace.TOP,
                    new BlockVec(25, Y, Z), new Pos(0.5, 1, 0.5), PlayerHand.MAIN);
            EventDispatcher.call(apart);
            assertFalse(apart.isCancelled(), "one apart lands");

            instance.setBlock(40, Y, Z, Block.CHEST); // a north-facing single; the placer looks south, along it
            var front = new PlayerBlockPlaceEvent(placer.player, instance, Block.CHEST, BlockFace.TOP,
                    new BlockVec(40, Y, Z + 1), new Pos(0.5, 1, 0.5), PlayerHand.MAIN);
            EventDispatcher.call(front);
            assertTrue(front.isCancelled(), "in front of one is refused");
        } finally {
            for (int x : new int[]{21, 22, 40}) instance.setBlock(x, Y, Z, Block.AIR);
            placer.player.remove();
        }
    }
}
