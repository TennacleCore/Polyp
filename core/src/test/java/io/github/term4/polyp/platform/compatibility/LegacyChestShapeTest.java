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
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
            assertTrue(CompatPlacement.legacyChestShape(world, new BlockVec(10, Y, Z), Block.CHEST), "alone");
            instance.setBlock(10, Y, Z, Block.CHEST);
            assertTrue(CompatPlacement.legacyChestShape(world, new BlockVec(11, Y, Z), Block.CHEST), "beside one single: a pair");

            instance.setBlock(1, Y, Z, Block.CHEST);
            instance.setBlock(2, Y, Z, Block.CHEST); // a pair
            assertFalse(CompatPlacement.legacyChestShape(world, new BlockVec(0, Y, Z), Block.CHEST), "three in a row");
            assertFalse(CompatPlacement.legacyChestShape(world, new BlockVec(3, Y, Z), Block.CHEST), "the far end of a pair");
            assertFalse(CompatPlacement.legacyChestShape(world, new BlockVec(1, Y, Z + 1), Block.CHEST), "beside one half of a pair");
            assertTrue(CompatPlacement.legacyChestShape(world, new BlockVec(1, Y, Z + 1), Block.TRAPPED_CHEST), "a trapped chest never pairs with a chest");
        } finally {
            for (int x : new int[]{0, 1, 2, 3, 10, 11}) instance.setBlock(x, Y, Z, Block.AIR);
        }
    }

    /** A chest placed in front of a north-facing single: 1.8 turns both to face across the join and pairs them. */
    @Test
    void aNeighborTurnsThePairAcrossTheJoin() {
        MechanicsWorld world = MechanicsWorld.of(instance);
        try {
            instance.setBlock(30, Y, Z, Block.CHEST); // facing north, single
            instance.setBlock(30, Y, Z + 1, Block.CHEST.withProperty("facing", "south")); // faces the placer, single
            CompatPlacement.pairLike18(world, new BlockVec(30, Y, Z + 1), Block.CHEST);
            Block mine = instance.getBlock(30, Y, Z + 1);
            Block theirs = instance.getBlock(30, Y, Z);
            assertEquals("east", mine.getProperty("facing"), "across a north-south join: east");
            assertEquals("east", theirs.getProperty("facing"));
            assertEquals("right", mine.getProperty("type"), "the south half, facing east, is the right one");
            assertEquals("left", theirs.getProperty("type"));

            // both halves face ALONG the join, so postPlace passes and the surroundings decide: away from the stone
            instance.setBlock(34, Y, Z, Block.CHEST.withProperty("facing", "west"));
            instance.setBlock(35, Y, Z, Block.CHEST.withProperty("facing", "east"));
            instance.setBlock(35, Y, Z + 1, Block.STONE);
            CompatPlacement.pairLike18(world, new BlockVec(35, Y, Z), Block.CHEST);
            assertEquals("north", instance.getBlock(35, Y, Z).getProperty("facing"), "south is blocked: north");
            assertEquals("north", instance.getBlock(34, Y, Z).getProperty("facing"), "and the neighbor follows");
        } finally {
            for (int x : new int[]{30, 34, 35}) instance.setBlock(x, Y, Z, Block.AIR);
            instance.setBlock(30, Y, Z + 1, Block.AIR);
            instance.setBlock(35, Y, Z + 1, Block.AIR);
        }
    }

    /** 1.8's postPlace is the last word: the chest you just set turns the one already there, not the other way. */
    @Test
    void theNewChestTurnsTheOldOne() {
        MechanicsWorld world = MechanicsWorld.of(instance);
        try {
            instance.setBlock(40, Y, Z, Block.CHEST.withProperty("facing", "east"));     // stood here facing east
            instance.setBlock(40, Y, Z + 1, Block.CHEST.withProperty("facing", "west")); // placed from the other side
            CompatPlacement.pairLike18(world, new BlockVec(40, Y, Z + 1), Block.CHEST);

            assertEquals("west", instance.getBlock(40, Y, Z + 1).getProperty("facing"), "the placer's facing wins");
            assertEquals("west", instance.getBlock(40, Y, Z).getProperty("facing"), "and the old chest turns to it");
            assertEquals("left", instance.getBlock(40, Y, Z + 1).getProperty("type"), "north of a west-facing pair is left");
            assertEquals("right", instance.getBlock(40, Y, Z).getProperty("type"));
        } finally {
            instance.setBlock(40, Y, Z, Block.AIR);
            instance.setBlock(40, Y, Z + 1, Block.AIR);
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
            assertFalse(front.isCancelled(), "in front of one lands, as in 1.8");
        } finally {
            for (int x : new int[]{21, 22, 40}) instance.setBlock(x, Y, Z, Block.AIR);
            placer.player.remove();
        }
    }
}
