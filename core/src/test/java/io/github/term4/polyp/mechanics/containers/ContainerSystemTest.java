package io.github.term4.polyp.mechanics.containers;

import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.MechanicsProfile;
import io.github.term4.polyp.api.event.item.ItemSpawnEvent;
import io.github.term4.polyp.presets.vanilla18.Containers;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerBlockBreakEvent;
import net.minestom.server.event.player.PlayerBlockInteractEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.inventory.Inventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.play.BlockActionPacket;
import net.minestom.server.network.packet.server.play.OpenWindowPacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A chest opens on its own contents, keeps them between windows, pairs, and spills on break; the rest is kinds. */
class ContainerSystemTest extends HeadlessServerTest {

    private static final int Y = 64, Z = 700;
    private static ContainerSystem containers;
    private static MechanicsWorld world;

    @BeforeAll
    static void install() {
        containers = ContainerSystem.install(polyp, Containers.config());
        world = MechanicsWorld.of(instance);
        for (int cx = 0; cx <= 4; cx++) instance.loadChunk(cx, Z >> 4).join();
    }

    private static PlayerBlockInteractEvent click(FakePlayer p, BlockVec pos) {
        var e = new PlayerBlockInteractEvent(p.player, PlayerHand.MAIN, instance, instance.getBlock(pos), pos,
                new Vec(0.5, 0.5, 0.5), BlockFace.NORTH);
        EventDispatcher.call(e);
        return e;
    }

    private static Inventory window(FakePlayer p) {
        return (Inventory) p.player.getOpenInventory();
    }

    private static String key(BlockVec pos) {
        return "block:" + pos.blockX() + "," + pos.blockY() + "," + pos.blockZ();
    }

    @Test
    void aChestKeepsItsContents() {
        BlockVec pos = new BlockVec(10, Y, Z);
        FakePlayer p = FakePlayer.connect(instance, new Pos(10.5, Y, Z + 2.5), "ChestOpener");
        try {
            instance.setBlock(pos, Block.CHEST.withProperty("facing", "north"));
            assertTrue(click(p, pos).isBlockingItemUse(), "the click is the chest's");
            Inventory first = window(p);
            assertNotNull(first);
            assertEquals(27, first.getSize());
            assertEquals(1, p.sent(OpenWindowPacket.class).size());
            assertEquals(1, p.sent(BlockActionPacket.class).size(), "the lid goes up");

            first.setItemStack(4, ItemStack.of(Material.DIAMOND, 3));
            assertEquals(3, containers.contents(world, key(pos))[4].amount(), "written through to the world's store");

            p.player.closeInventory();
            assertNull(p.player.getOpenInventory());
            click(p, pos);
            Inventory second = window(p);
            assertTrue(second != first, "a closed window is gone; the contents are not");
            assertEquals(Material.DIAMOND, second.getItemStack(4).material());

            second.setItemStack(4, ItemStack.AIR);
            assertNull(containers.contents(world, key(pos)), "an emptied chest holds no entry");
        } finally {
            p.player.closeInventory();
            instance.setBlock(pos, Block.AIR);
            p.player.remove();
        }
    }

    @Test
    void sneakingWithAnItemPlaces() {
        BlockVec pos = new BlockVec(14, Y, Z);
        FakePlayer p = FakePlayer.connect(instance, new Pos(14.5, Y, Z + 2.5), "SneakPlacer");
        try {
            instance.setBlock(pos, Block.CHEST);
            p.player.setItemInMainHand(ItemStack.of(Material.STONE));
            p.player.setSneaking(true);
            assertFalse(click(p, pos).isBlockingItemUse(), "vanilla: a sneaked click with an item places");
            assertNull(p.player.getOpenInventory());
        } finally {
            instance.setBlock(pos, Block.AIR);
            p.player.remove();
        }
    }

    @Test
    void aPairIsOneWindow() {
        BlockVec left = new BlockVec(20, Y, Z), right = new BlockVec(21, Y, Z);
        FakePlayer p = FakePlayer.connect(instance, new Pos(20.5, Y, Z + 2.5), "PairOpener");
        try {
            // facing north: the left half's partner sits east of it
            instance.setBlock(left, Block.CHEST.withProperties(Map.of("facing", "north", "type", "left")));
            instance.setBlock(right, Block.CHEST.withProperties(Map.of("facing", "north", "type", "right")));
            click(p, right);
            Inventory window = window(p);
            assertEquals(54, window.getSize());
            assertEquals(2, p.sent(BlockActionPacket.class).size(), "both lids");

            window.setItemStack(3, ItemStack.of(Material.APPLE));  // the upper rows: the west half
            window.setItemStack(30, ItemStack.of(Material.BREAD)); // the lower rows: the east half
            assertEquals(Material.APPLE, containers.contents(world, key(left))[3].material());
            assertEquals(Material.BREAD, containers.contents(world, key(right))[3].material());
        } finally {
            p.player.closeInventory();
            instance.setBlock(left, Block.AIR);
            instance.setBlock(right, Block.AIR);
            p.player.remove();
        }
    }

    /** The modern order: the right half on top, whichever way the pair faces. */
    @Test
    void modernPairPutsTheRightHalfOnTop() {
        InstanceContainer modern = flatInstance(MechanicsProfile.builder()
                .set(MechanicsKeys.CONTAINERS, io.github.term4.polyp.presets.vanilla.Containers.config()).build());
        modern.loadChunk(1, Z >> 4).join();
        BlockVec left = new BlockVec(24, Y, Z), right = new BlockVec(25, Y, Z);
        FakePlayer p = FakePlayer.connect(modern, new Pos(24.5, Y, Z + 2.5), "ModernPair");
        try {
            modern.setBlock(left, Block.CHEST.withProperties(Map.of("facing", "north", "type", "left")));
            modern.setBlock(right, Block.CHEST.withProperties(Map.of("facing", "north", "type", "right")));
            var e = new PlayerBlockInteractEvent(p.player, PlayerHand.MAIN, modern, modern.getBlock(left), left,
                    new Vec(0.5, 0.5, 0.5), BlockFace.NORTH);
            EventDispatcher.call(e);
            Inventory window = window(p);
            window.setItemStack(0, ItemStack.of(Material.APPLE));
            assertEquals(Material.APPLE, containers.contents(MechanicsWorld.of(modern), key(right))[0].material(),
                    "the upper rows are the right half's");
        } finally {
            p.player.closeInventory();
            p.player.remove();
        }
    }

    @Test
    void creativeCloneTakesAFullStack() {
        BlockVec pos = new BlockVec(60, Y, Z);
        FakePlayer p = FakePlayer.connect(instance, new Pos(60.5, Y, Z + 2.5), "Cloner");
        try {
            p.player.setGameMode(GameMode.CREATIVE);
            instance.setBlock(pos, Block.CHEST);
            click(p, pos);
            Inventory window = window(p);
            window.setItemStack(4, ItemStack.of(Material.DIAMOND, 3));
            assertTrue(window.middleClick(p.player, 4));
            assertEquals(64, p.player.getInventory().getCursorItem().amount(), "a full stack, the slot untouched");
            assertEquals(3, window.getItemStack(4).amount());
            assertFalse(window.middleClick(p.player, 4), "a held cursor clones nothing");
        } finally {
            p.player.closeInventory();
            instance.setBlock(pos, Block.AIR);
            p.player.remove();
        }
    }

    @Test
    void cloneDragFillsEachSlot() {
        BlockVec pos = new BlockVec(64, Y, Z);
        FakePlayer p = FakePlayer.connect(instance, new Pos(64.5, Y, Z + 2.5), "CloneDragger");
        try {
            p.player.setGameMode(GameMode.CREATIVE);
            instance.setBlock(pos, Block.CHEST);
            click(p, pos);
            Inventory window = window(p);
            window.setItemStack(2, ItemStack.of(Material.DIAMOND, 10));
            p.player.getInventory().setCursorItem(ItemStack.of(Material.DIAMOND, 1));
            assertTrue(window.dragging(p.player, List.of(1, 2, 3), 10));
            assertEquals(64, window.getItemStack(1).amount());
            assertEquals(64, window.getItemStack(2).amount(), "topped up, not stacked past a stack");
            assertEquals(64, window.getItemStack(3).amount());
            assertTrue(p.player.getInventory().getCursorItem().isAir(), "the clone drag spends the cursor");
        } finally {
            p.player.closeInventory();
            instance.setBlock(pos, Block.AIR);
            p.player.remove();
        }
    }

    @Test
    void breakingScattersAndForgets() {
        BlockVec pos = new BlockVec(30, Y, Z);
        FakePlayer p = FakePlayer.connect(instance, new Pos(30.5, Y, Z + 2.5), "ChestBreaker");
        List<Entity> spawned = new java.util.concurrent.CopyOnWriteArrayList<>();
        EventNode<Event> count = EventNode.all("count-drops");
        count.addListener(ItemSpawnEvent.class, e -> spawned.add(e.item()));
        MinecraftServer.getGlobalEventHandler().addChild(count);
        try {
            instance.setBlock(pos, Block.CHEST);
            click(p, pos);
            window(p).setItemStack(0, ItemStack.of(Material.IRON_INGOT));
            window(p).setItemStack(26, ItemStack.of(Material.GOLD_INGOT));
            p.player.closeInventory();

            EventDispatcher.call(new PlayerBlockBreakEvent(p.player, instance, Block.CHEST, Block.AIR, pos, BlockFace.TOP));
            MinecraftServer.getSchedulerManager().processTickEnd();
            assertEquals(2, spawned.size(), "each stack scatters");
            assertNull(containers.contents(world, key(pos)));
        } finally {
            MinecraftServer.getGlobalEventHandler().removeChild(count);
            spawned.forEach(Entity::remove); // other classes count the shared instance's items
            instance.setBlock(pos, Block.AIR);
            p.player.remove();
        }
    }

    @Test
    void anEnderChestIsTheOpeners() {
        BlockVec pos = new BlockVec(40, Y, Z);
        FakePlayer p = FakePlayer.connect(instance, new Pos(40.5, Y, Z + 2.5), "EnderOwner");
        try {
            instance.setBlock(pos, Block.ENDER_CHEST);
            click(p, pos);
            window(p).setItemStack(0, ItemStack.of(Material.EMERALD));
            p.player.closeInventory();
            assertEquals(Material.EMERALD, containers.contents(world, "player:EnderOwner")[0].material());

            EventDispatcher.call(new PlayerBlockBreakEvent(p.player, instance, Block.ENDER_CHEST, Block.AIR, pos, BlockFace.TOP));
            MinecraftServer.getSchedulerManager().processTickEnd();
            assertNotNull(containers.contents(world, "player:EnderOwner"), "KEEP: the contents outlive the block");
        } finally {
            instance.setBlock(pos, Block.AIR);
            p.player.remove();
        }
    }

    @Test
    void aDeclaredBlockFillsOnce() {
        BlockVec pos = new BlockVec(50, Y, Z);
        FakePlayer p = FakePlayer.connect(instance, new Pos(50.5, Y, Z + 2.5), "DirtOpener");
        AtomicInteger fills = new AtomicInteger();
        try {
            instance.setBlock(pos, Block.DIRT);
            containers.declare(world, pos, ContainerTypeConfig.builder()
                    .fill((ctx, slots) -> {
                        fills.incrementAndGet();
                        return new ItemStack[]{ItemStack.of(Material.COOKIE, 8)};
                    })
                    .build());
            assertTrue(click(p, pos).isBlockingItemUse(), "a declared dirt block is a chest");
            assertEquals(8, window(p).getItemStack(0).amount(), "filled on first open");
            p.player.closeInventory();
            click(p, pos);
            assertEquals(1, fills.get(), "and only then");
            window(p).setItemStack(0, ItemStack.AIR);
            p.player.closeInventory();
            click(p, pos);
            assertTrue(window(p).getItemStack(0).isAir(), "an emptied container stays empty");
            assertEquals(1, fills.get());
        } finally {
            p.player.closeInventory();
            containers.undeclare(world, pos);
            instance.setBlock(pos, Block.AIR);
            p.player.remove();
        }
    }
}
