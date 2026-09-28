package io.github.term4.polyp.mechanics.fluids;

import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.MechanicsProfile;
import io.github.term4.polyp.entity.DroppedItemEntity;
import io.github.term4.polyp.presets.vanilla18.Fluids;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.instance.InstanceTickEvent;
import net.minestom.server.event.player.PlayerUseItemEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.utils.Direction;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Water and lava on a flat floor: the spread, the infinite pool, the mix, the knobs that shut a side or freeze it,
 * and the same scenes under both eras' presets.
 */
class FluidsTest extends HeadlessServerTest {

    private static final int Y = 64, Z = 1100; // the harness floor tops at 63
    private static FluidSystem fluids;
    private static MechanicsWorld world;

    @BeforeAll
    static void install() {
        fluids = FluidSystem.install(polyp, Fluids.config());
        world = MechanicsWorld.of(instance);
        for (int cx = -2; cx <= 12; cx++) for (int cz = (Z - 16) >> 4; cz <= (Z + 16) >> 4; cz++) instance.loadChunk(cx, cz).join();
    }

    private static void ticks(InstanceContainer inst, int n) {
        for (int i = 0; i < n; i++) EventDispatcher.call(new InstanceTickEvent(inst, 0, 0));
    }

    private static int level(InstanceContainer inst, int x, int y, int z) {
        Block b = inst.getBlock(x, y, z);
        return b.liquid() ? Spread.level(b) : -1;
    }

    @Test
    void waterSpreadsSevenWide() {
        fluids.place(world, new BlockVec(0, Y, Z), Block.WATER);
        ticks(instance, 60);
        assertEquals(0, level(instance, 0, Y, Z), "the source stays");
        assertEquals(1, level(instance, 1, Y, Z));
        assertEquals(4, level(instance, 4, Y, Z));
        assertEquals(7, level(instance, 7, Y, Z), "a level a block");
        assertEquals(-1, level(instance, 8, Y, Z), "eight blocks out is dry");
        assertEquals(2, level(instance, -1, Y, Z + 1), "the diagonal costs two");
    }

    @Test
    void twoSourcesMakeAThirdOfWaterNotLava() {
        fluids.place(world, new BlockVec(40, Y, Z), Block.WATER);
        fluids.place(world, new BlockVec(42, Y, Z), Block.WATER);
        ticks(instance, 20);
        assertEquals(0, level(instance, 41, Y, Z), "between two water sources a source");
        fluids.place(world, new BlockVec(60, Y, Z), Block.LAVA);
        fluids.place(world, new BlockVec(62, Y, Z), Block.LAVA);
        ticks(instance, 150);
        assertEquals(2, level(instance, 61, Y, Z), "lava only flows there, two levels down");
        assertTrue(instance.getBlock(61, Y, Z).compare(Block.LAVA));
    }

    @Test
    void lavaMeetsWater() {
        fluids.place(world, new BlockVec(80, Y, Z), Block.LAVA);
        ticks(instance, 40);
        fluids.place(world, new BlockVec(84, Y, Z), Block.WATER);
        ticks(instance, 60);
        boolean cobbled = false;
        for (int x = 81; x <= 83; x++) cobbled |= instance.getBlock(x, Y, Z).compare(Block.COBBLESTONE);
        assertTrue(cobbled, "a flow the water touches cobbles");
        fluids.place(world, new BlockVec(100, Y, Z), Block.LAVA);
        fluids.place(world, new BlockVec(101, Y, Z), Block.WATER);
        ticks(instance, 2);
        assertTrue(instance.getBlock(100, Y, Z).compare(Block.OBSIDIAN), "a source beside water is obsidian");
        instance.setBlock(120, Y + 3, Z, Block.LAVA);
        fluids.placed(world, new BlockVec(120, Y + 3, Z));
        instance.setBlock(120, Y, Z, Block.WATER);
        ticks(instance, 200);
        assertTrue(instance.getBlock(120, Y, Z).compare(Block.STONE), "lava falling onto water leaves stone: " + instance.getBlock(120, Y, Z).name());
    }

    @Test
    void aSideCanBeShutAndTheWholeFrozen() {
        InstanceContainer inst = flatInstance(MechanicsProfile.builder()
                .set(MechanicsKeys.FLUIDS, Fluids.config().toBuilder()
                        .fluid(Block.WATER, Fluids.water().toBuilder().flows(Flow.except(Direction.EAST)).build())
                        .build())
                .build());
        MechanicsWorld other = MechanicsWorld.of(inst);
        for (int cx = -1; cx <= 1; cx++) for (int cz = -1; cz <= 1; cz++) inst.loadChunk(cx, cz).join();
        fluids.place(other, new BlockVec(0, Y, 0), Block.WATER);
        ticks(inst, 30);
        assertEquals(-1, level(inst, 1, Y, 0), "nothing east");
        assertEquals(1, level(inst, -1, Y, 0), "west as ever");
        assertEquals(1, level(inst, 0, Y, 1), "south too");
        InstanceContainer still = flatInstance(MechanicsProfile.builder()
                .set(MechanicsKeys.FLUIDS, Fluids.config().toBuilder()
                        .fluid(Block.WATER, Fluids.water().toBuilder().updates(false).build())
                        .build())
                .build());
        MechanicsWorld stillWorld = MechanicsWorld.of(still);
        fluids.place(stillWorld, new BlockVec(0, Y, 0), Block.WATER);
        ticks(still, 30);
        assertEquals(0, level(still, 0, Y, 0));
        assertEquals(-1, level(still, 1, Y, 0), "a fluid that never ticks never moves");
    }

    private static MechanicsWorld worldOf(FluidsConfig cfg) {
        InstanceContainer inst = flatInstance(MechanicsProfile.builder().set(MechanicsKeys.FLUIDS, cfg).build());
        for (int cx = -1; cx <= 1; cx++) for (int cz = -1; cz <= 1; cz++) inst.loadChunk(cx, cz).join();
        return MechanicsWorld.of(inst);
    }

    private static FluidsConfig modern() {
        return io.github.term4.polyp.presets.vanilla.Fluids.config();
    }

    private static InstanceContainer instanceOf(MechanicsWorld w) {
        return (InstanceContainer) w.instance();
    }

    private static long drops(MechanicsWorld w) {
        return w.instance().getEntities().stream().filter(e -> e instanceof DroppedItemEntity).count();
    }

    private static boolean waterlogged(Block block) {
        return io.github.term4.polyp.mechanics.fluids.Fluids.waterlogged(block);
    }

    @Test
    void waterFillsASlabOnlyModern() {
        for (boolean modern : new boolean[]{false, true}) {
            MechanicsWorld w = worldOf(modern ? modern() : Fluids.config());
            w.setBlock(new BlockVec(1, Y, 0), Block.OAK_SLAB);
            fluids.place(w, new BlockVec(0, Y, 0), Block.WATER);
            ticks(instanceOf(w), 30);
            Block slab = w.getBlock(1, Y, 0);
            assertTrue(slab.compare(Block.OAK_SLAB), "the slab stays a slab");
            if (modern) assertTrue(waterlogged(slab), "26.1 water fills the slab");
            else assertFalse(waterlogged(slab), "1.8 has no water inside a block");
        }
    }

    @Test
    void aFallBesideThreeSourcesSpreadsOnlyModern() {
        for (boolean modern : new boolean[]{false, true}) {
            MechanicsWorld w = worldOf(modern ? modern() : Fluids.config());
            int x = 4;
            w.setBlock(new BlockVec(x, Y - 1, 0), Block.AIR); // the hole under the middle source
            w.setBlock(new BlockVec(x - 1, Y, -1), Block.STONE);
            w.setBlock(new BlockVec(x - 1, Y, 1), Block.STONE);
            // three sources around the hole; the cell over it fills from them and stays a flow, the ground being air
            for (BlockVec at : List.of(new BlockVec(x, Y, -1), new BlockVec(x, Y, 1), new BlockVec(x + 1, Y, 0))) {
                fluids.place(w, at, Block.WATER);
            }
            ticks(instanceOf(w), 40);
            assertEquals(1, level(instanceOf(w), x, Y, 0), "over the hole, a flow");
            assertTrue(w.getBlock(x, Y - 1, 0).compare(Block.WATER), "and it falls");
            int west = level(instanceOf(w), x - 1, Y, 0);
            if (modern) assertEquals(2, west, "26.1: three sources beside a fall push it sideways too");
            else assertEquals(-1, west, "1.8: a flow that falls never spreads sideways");
        }
    }

    @Test
    void aFaceShutsOnlyModern() {
        Block bottom = Block.OAK_SLAB, top = Block.OAK_SLAB.withProperty("type", "top");
        assertFalse(Passage.MODERN.through(null, bottom, Direction.DOWN, Block.AIR), "a bottom slab's floor is whole");
        assertTrue(Passage.MODERN.through(null, bottom, Direction.EAST, Block.AIR), "its side is half open");
        assertTrue(Passage.MODERN.through(null, Block.AIR, Direction.DOWN, bottom), "water drops into a bottom slab");
        assertFalse(Passage.MODERN.through(null, Block.AIR, Direction.DOWN, top), "not into a top one");
        assertFalse(Passage.MODERN.through(null, Block.STONE, Direction.EAST, Block.AIR));
        assertTrue(Passage.LEGACY.through(null, bottom, Direction.DOWN, Block.AIR), "1.8 knows no faces");
    }

    @Test
    void washedLootFollowsTheEra() {
        MechanicsWorld legacy = worldOf(Fluids.config());
        legacy.setBlock(new BlockVec(1, Y, 0), Block.DEAD_BUSH);
        fluids.place(legacy, new BlockVec(0, Y, 0), Block.WATER);
        ticks(instanceOf(legacy), 20);
        assertTrue(legacy.getBlock(1, Y, 0).compare(Block.WATER), "the bush is washed out");
        assertEquals(0, drops(legacy), "1.8: a bare hand gets nothing from a dead bush");
        MechanicsWorld modern = worldOf(modern());
        for (int i = 0; i < 12 && drops(modern) == 0; i++) {
            modern.setBlock(new BlockVec(1, Y, i * 3), Block.DEAD_BUSH);
            fluids.place(modern, new BlockVec(0, Y, i * 3), Block.WATER);
            ticks(instanceOf(modern), 20);
        }
        assertTrue(drops(modern) > 0, "26.1: the loot table's sticks");
    }

    @Test
    void aBucketFillsASlabOnlyModern() {
        for (boolean modern : new boolean[]{false, true}) {
            MechanicsWorld w = worldOf(modern ? modern() : Fluids.config());
            InstanceContainer inst = instanceOf(w);
            inst.setBlock(6, Y, 0, Block.OAK_SLAB);
            FakePlayer fp = FakePlayer.connect(inst, new Pos(6.5, Y + 0.5, 0.5, 0, 90), modern ? "Slabber" : "Topper");
            try {
                fp.player.setItemInMainHand(ItemStack.of(Material.WATER_BUCKET));
                EventDispatcher.call(new PlayerUseItemEvent(fp.player, PlayerHand.MAIN, fp.player.getItemInMainHand(), 0));
                assertEquals(Material.BUCKET, fp.player.getItemInMainHand().material());
                if (modern) {
                    assertTrue(waterlogged(inst.getBlock(6, Y, 0)), "poured into the slab");
                    assertTrue(inst.getBlock(6, Y + 1, 0).air());
                    EventDispatcher.call(new PlayerUseItemEvent(fp.player, PlayerHand.MAIN, fp.player.getItemInMainHand(), 0));
                    assertFalse(waterlogged(inst.getBlock(6, Y, 0)), "scooped back out");
                    assertEquals(Material.WATER_BUCKET, fp.player.getItemInMainHand().material());
                } else {
                    assertTrue(inst.getBlock(6, Y + 1, 0).compare(Block.WATER), "poured onto the slab");
                    assertFalse(waterlogged(inst.getBlock(6, Y, 0)));
                }
            } finally {
                fp.player.remove();
            }
        }
    }

    @Test
    void aBucketPoursAndScoops() {
        FakePlayer fp = FakePlayer.connect(instance, new Pos(140.5, Y, Z + 0.5, 0, 90), "Pourer");
        try {
            fp.player.setItemInMainHand(ItemStack.of(Material.WATER_BUCKET));
            EventDispatcher.call(new PlayerUseItemEvent(fp.player, PlayerHand.MAIN, fp.player.getItemInMainHand(), 0));
            assertTrue(instance.getBlock(140, Y, Z).compare(Block.WATER), "poured at the feet, off the floor's top face");
            assertEquals(Material.BUCKET, fp.player.getItemInMainHand().material(), "the bucket empties");
            EventDispatcher.call(new PlayerUseItemEvent(fp.player, PlayerHand.MAIN, fp.player.getItemInMainHand(), 0));
            assertTrue(instance.getBlock(140, Y, Z).air(), "scooped back up");
            assertEquals(Material.WATER_BUCKET, fp.player.getItemInMainHand().material());
        } finally {
            fp.player.remove();
        }
    }
}
