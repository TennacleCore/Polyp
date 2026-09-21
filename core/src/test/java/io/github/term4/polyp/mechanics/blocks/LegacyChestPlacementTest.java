package io.github.term4.polyp.mechanics.blocks;

import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.MechanicsProfile;
import io.github.term4.polyp.presets.vanilla18.Blocks;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.utils.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.github.term4.polyp.mechanics.blocks.LegacyChestParityTest.yawOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A chest lands only where a 1.8 client draws it, and the pair it makes is the one 1.8 makes. */
class LegacyChestPlacementTest extends HeadlessServerTest {

    private static final int Y = 64;
    private static final int Z = 800;
    private static InstanceContainer legacy;

    @BeforeAll
    static void legacyWorld() {
        BlocksSystem.install(polyp);
        legacy = flatInstance(MechanicsProfile.builder().set(MechanicsKeys.BLOCKS, Blocks.config()).build());
        // the rule reads around a cell before anything is set in it; the tie-break reads one chunk north as well
        for (int cx = -1; cx <= 2; cx++) {
            legacy.loadChunk(cx, Z >> 4).join();
            legacy.loadChunk(cx, (Z - 1) >> 4).join();
        }
    }

    private static Block place(int x, int z, Direction look) {
        BlockVec at = new BlockVec(x, Y, z);
        Block landed = LegacyChestPlacement.place(legacy, legacy, at, Block.CHEST, yawOf(look));
        legacy.setBlock(at, landed);
        return landed;
    }

    @Test
    void besideOneNotAPair() {
        try {
            assertTrue(LegacyChestPlacement.canPlace(legacy, new BlockVec(10, Y, Z), Block.CHEST), "alone");
            legacy.setBlock(10, Y, Z, Block.CHEST);
            assertTrue(LegacyChestPlacement.canPlace(legacy, new BlockVec(11, Y, Z), Block.CHEST), "beside one single: a pair");

            legacy.setBlock(1, Y, Z, Block.CHEST);
            legacy.setBlock(2, Y, Z, Block.CHEST); // a pair
            assertFalse(LegacyChestPlacement.canPlace(legacy, new BlockVec(0, Y, Z), Block.CHEST), "three in a row");
            assertFalse(LegacyChestPlacement.canPlace(legacy, new BlockVec(3, Y, Z), Block.CHEST), "the far end of a pair");
            assertFalse(LegacyChestPlacement.canPlace(legacy, new BlockVec(1, Y, Z + 1), Block.CHEST), "beside one half of a pair");
            assertTrue(LegacyChestPlacement.canPlace(legacy, new BlockVec(1, Y, Z + 1), Block.TRAPPED_CHEST), "a trapped chest never pairs with a chest");
        } finally {
            for (int x : new int[]{0, 1, 2, 3, 10, 11}) legacy.setBlock(x, Y, Z, Block.AIR);
        }
    }

    /** The look ran along the join, so postPlace writes nothing at all: checkForSurroundingChests' answer stands
     *  for BOTH halves, and the pair reads across the join - never along it, which is what a chest cannot do. */
    @Test
    void lookAlongTheJoin() {
        try {
            legacy.setBlock(30, Y, Z, Block.CHEST.withProperty("facing", "south"));
            Block mine = place(29, Z, Direction.WEST); // placed from the east, looking west along the join

            assertEquals("south", mine.getProperty("facing"), "the x-join default, not the look");
            assertEquals("south", legacy.getBlock(30, Y, Z).getProperty("facing"), "and the same for the old half");
            assertEquals("right", mine.getProperty("type"), "facing south, its partner sits east: right");
            assertEquals("left", legacy.getBlock(30, Y, Z).getProperty("type"));
        } finally {
            legacy.setBlock(29, Y, Z, Block.AIR);
            legacy.setBlock(30, Y, Z, Block.AIR);
        }
    }

    /** checkForSurroundingChests' tie-break: a FULL block against one side of either half turns the pair away -
     *  1.8 reads isFullBlock (isOpaqueCube, taken once in the constructor), so a slab or a pane is none. */
    @Test
    void blockedSideTurnsPair() {
        try {
            legacy.setBlock(34, Y, Z, Block.CHEST.withProperty("facing", "west"));
            legacy.setBlock(35, Y, Z + 1, Block.STONE);
            Block mine = place(35, Z, Direction.WEST);
            assertEquals("north", mine.getProperty("facing"), "south is blocked: north");
            assertEquals("north", legacy.getBlock(34, Y, Z).getProperty("facing"), "both halves, one answer");

            // a bottom slab occludes but is no full cube: 1.8 does not turn away from it
            legacy.setBlock(35, Y, Z + 1, Block.STONE_SLAB);
            legacy.setBlock(34, Y, Z, Block.CHEST.withProperty("facing", "west"));
            place(35, Z, Direction.WEST);
            assertEquals("south", legacy.getBlock(34, Y, Z).getProperty("facing"), "nothing full either side: the x-join default");
        } finally {
            legacy.setBlock(34, Y, Z, Block.AIR);
            legacy.setBlock(35, Y, Z, Block.AIR);
            legacy.setBlock(35, Y, Z + 1, Block.AIR);
        }
    }

    /** 1.8's postPlace is the last word: the chest you just set turns the one already there, not the other way. */
    @Test
    void newChestTurnsOld() {
        try {
            legacy.setBlock(40, Y, Z, Block.CHEST.withProperty("facing", "east")); // stood here facing east
            Block mine = place(40, Z + 1, Direction.EAST);                        // placed from the other side

            assertEquals("west", mine.getProperty("facing"), "the placer's facing wins");
            assertEquals("west", legacy.getBlock(40, Y, Z).getProperty("facing"), "and the old chest turns to it");
            assertEquals("left", mine.getProperty("type"), "north of a west-facing pair is left");
            assertEquals("right", legacy.getBlock(40, Y, Z).getProperty("type"));
        } finally {
            legacy.setBlock(40, Y, Z, Block.AIR);
            legacy.setBlock(40, Y, Z + 1, Block.AIR);
        }
    }

    @Test
    void refusedShapes() {
        FakePlayer placer = FakePlayer.connect(legacy, new Pos(0.5, Y, Z + 5.5), "ChestPlacer");
        try {
            legacy.setBlock(21, Y, Z, Block.CHEST);
            legacy.setBlock(22, Y, Z, Block.CHEST);
            var third = new PlayerBlockPlaceEvent(placer.player, legacy, Block.CHEST, BlockFace.TOP,
                    new BlockVec(23, Y, Z), new Pos(0.5, 1, 0.5), PlayerHand.MAIN);
            EventDispatcher.call(third);
            assertTrue(third.isCancelled(), "a third in a row is refused");

            var apart = new PlayerBlockPlaceEvent(placer.player, legacy, Block.CHEST, BlockFace.TOP,
                    new BlockVec(25, Y, Z), new Pos(0.5, 1, 0.5), PlayerHand.MAIN);
            EventDispatcher.call(apart);
            assertFalse(apart.isCancelled(), "one apart lands");

            legacy.setBlock(40, Y, Z, Block.CHEST); // a north-facing single; the placer looks south, along it
            var front = new PlayerBlockPlaceEvent(placer.player, legacy, Block.CHEST, BlockFace.TOP,
                    new BlockVec(40, Y, Z + 1), new Pos(0.5, 1, 0.5), PlayerHand.MAIN);
            EventDispatcher.call(front);
            assertFalse(front.isCancelled(), "in front of one lands, as in 1.8");
        } finally {
            for (int x : new int[]{21, 22, 40}) legacy.setBlock(x, Y, Z, Block.AIR);
            placer.player.remove();
        }
    }
}
