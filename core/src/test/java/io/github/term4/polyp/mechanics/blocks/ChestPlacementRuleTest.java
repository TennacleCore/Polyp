package io.github.term4.polyp.mechanics.blocks;

import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.MechanicsProfile;
import io.github.term4.polyp.presets.vanilla.Blocks;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.player.PlayerBlockPlaceEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.instance.block.rule.BlockPlacementRule;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;

/** The chest slot dispatches by the WORLD's era: the app's rule for a modern world, 1.8's for a legacy one. */
class ChestPlacementRuleTest extends HeadlessServerTest {

    private static final int Y = 64, Z = 900;
    private static final BlockPlacementRule APP_RULE = new BlockPlacementRule(Block.CHEST) {
        @Override public Block blockPlace(PlacementState state) {
            return state.block().withProperty("facing", "east");
        }
    };
    private static InstanceContainer modern, legacy;

    @BeforeAll
    static void twoWorlds() {
        MinecraftServer.getBlockManager().registerBlockPlacementRule(APP_RULE);
        BlocksSystem.install(polyp);
        polyp.unregisterAll();
        BlocksSystem.install(polyp); // a re-install wraps the app's rule again, not the previous wrapper
        modern = flatInstance(MechanicsProfile.builder().set(MechanicsKeys.BLOCKS, Blocks.config()).build());
        legacy = flatInstance(MechanicsProfile.builder()
                .set(MechanicsKeys.BLOCKS, io.github.term4.polyp.presets.vanilla18.Blocks.config()).build());
        for (int cx = 0; cx <= 2; cx++) {
            modern.loadChunk(cx, Z >> 4).join();
            legacy.loadChunk(cx, Z >> 4).join();
        }
    }

    private static Block place(InstanceContainer world, int x, float yaw) {
        BlockPlacementRule rule = MinecraftServer.getBlockManager().getBlockPlacementRule(Block.CHEST);
        return rule.blockPlace(new BlockPlacementRule.PlacementState(world, Block.CHEST, BlockFace.TOP,
                new BlockVec(x, Y, Z), new Vec(0.5, 1, 0.5), new Pos(x + 0.5, Y, Z + 3.5, yaw, 0), null, false));
    }

    @Test
    void modernRunsAppRule() {
        assertSame(APP_RULE, ((ChestPlacementRule) MinecraftServer.getBlockManager().getBlockPlacementRule(Block.CHEST)).modern);
        assertEquals("east", place(modern, 10, 180f).getProperty("facing"), "the app's rule, whatever the look");
    }

    @Test
    void legacyRunsBlockChest() {
        try {
            Block landed = place(legacy, 20, 180f); // looking north: the chest faces the placer, south
            assertEquals("south", landed.getProperty("facing"));
            assertEquals("false", landed.getProperty("waterlogged"));

            legacy.setBlock(21, Y, Z, Block.CHEST.withProperty("facing", "south"));
            landed = place(legacy, 20, 90f); // looking west, along the join: e()'s default, for both
            assertEquals("south", landed.getProperty("facing"));
            assertEquals("right", landed.getProperty("type"));
            assertEquals("left", legacy.getBlock(21, Y, Z).getProperty("type"), "the partner is written through the world");
        } finally {
            legacy.setBlock(21, Y, Z, Block.AIR);
        }
    }

    @Test
    void modernRefusesNothing() {
        FakePlayer placer = FakePlayer.connect(modern, new Pos(0.5, Y, Z + 5.5), "ModernPlacer");
        try {
            modern.setBlock(31, Y, Z, Block.CHEST);
            modern.setBlock(32, Y, Z, Block.CHEST);
            var third = new PlayerBlockPlaceEvent(placer.player, modern, Block.CHEST, BlockFace.TOP,
                    new BlockVec(33, Y, Z), new Pos(0.5, 1, 0.5), PlayerHand.MAIN);
            EventDispatcher.call(third);
            assertFalse(third.isCancelled(), "1.8's refusal is 1.8's");
        } finally {
            modern.setBlock(31, Y, Z, Block.AIR);
            modern.setBlock(32, Y, Z, Block.AIR);
            placer.player.remove();
        }
    }
}
