package io.github.term4.polyp.platform.fixes.client;

import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.platform.fixes.FixToggleConfig;
import io.github.term4.polyp.platform.fixes.FixesConfig;
import io.github.term4.polyp.platform.fixes.FixesSystem;
import io.github.term4.polyp.platform.inventory.InventorySync;
import io.github.term4.polyp.platform.player.PolypPlayer;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.PlayerHand;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.item.PlayerCancelItemUseEvent;
import net.minestom.server.instance.block.BlockFace;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.client.ClientPacket;
import net.minestom.server.network.packet.client.play.ClientHeldItemChangePacket;
import net.minestom.server.network.packet.client.play.ClientPlayerActionPacket;
import net.minestom.server.network.packet.client.play.ClientUseItemPacket;
import net.minestom.server.network.packet.server.play.SetSlotPacket;
import net.minestom.server.network.packet.server.play.WindowItemsPacket;
import net.minestom.server.utils.inventory.PlayerInventoryUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A client before 1.21 spends its own arrow on a bow release: its arrows are re-sent, never its bow. */
class LegacyUseResyncFixTest extends HeadlessServerTest {

    @BeforeAll
    static void setUp() {
        FixesSystem.install(polyp, FixesConfig.builder().legacyUseResync(FixToggleConfig.on()).build());
    }

    @Test
    void bowReleaseResendsArrows() {
        List<SetSlotPacket> slots = release("DrawBow", 47, Material.BOW);
        assertEquals(1, slots.size(), "the arrows, not the bow");
        assertEquals(Material.ARROW, slots.getFirst().itemStack().material());
    }

    @Test
    void swordReleaseSendsNothing() {
        assertTrue(release("DrawSword", 47, Material.DIAMOND_SWORD).isEmpty());
    }

    @Test
    void aModernClientIsLeftAlone() {
        assertTrue(release("DrawModern", MinecraftServer.PROTOCOL_VERSION, Material.BOW).isEmpty());
    }

    @Test
    void a120ClientGetsItsArrows() {
        FakePlayer drawer = FakePlayer.connect(instance, new Pos(40.5, 64, 40.5), "Draw120");
        try {
            Polyp.getInstance().clientInfo().setProtocol(drawer.player, 763); // 1.20.1
            drawer.player.getInventory().setItemStack(0, ItemStack.of(Material.BOW));
            drawer.player.getInventory().setItemStack(9, ItemStack.of(Material.ARROW, 16));
            drawer.player.getInventory().setItemStack(PlayerInventoryUtils.OFFHAND_SLOT, ItemStack.of(Material.ARROW, 8));
            InventorySync sync = ((PolypPlayer) drawer.player).inventorySync();
            sync.broadcast();
            drawer.sent.clear();
            EventDispatcher.call(new PlayerCancelItemUseEvent(drawer.player, PlayerHand.MAIN, ItemStack.of(Material.BOW), 3));
            sync.broadcast();
            List<SetSlotPacket> slots = drawer.sent(SetSlotPacket.class);
            assertEquals(2, slots.size(), "the inventory's arrows and the offhand's: " + slots);
            assertTrue(slots.stream().allMatch(s -> s.itemStack().material() == Material.ARROW));
        } finally {
            drawer.player.remove();
        }
    }

    @Test
    void tapThenHoldKeepsTheUse() {
        FakePlayer p = FakePlayer.connect(instance, new Pos(40.5, 64, 40.5), "TapHold");
        try {
            Polyp.getInstance().clientInfo().setProtocol(p.player, 47);
            p.player.getInventory().setItemStack(0, ItemStack.of(Material.BOW));
            p.player.getInventory().setItemStack(9, ItemStack.of(Material.ARROW, 16));
            InventorySync sync = ((PolypPlayer) p.player).inventorySync();
            sync.broadcast();
            p.sent.clear();
            // a tap and a fresh press: 1.8 starts the new use the tick after the release
            feed(p, new ClientUseItemPacket(PlayerHand.MAIN, 1, 0f, 0f));
            feed(p, new ClientPlayerActionPacket(ClientPlayerActionPacket.Status.UPDATE_ITEM_STATE, Vec.ZERO, BlockFace.BOTTOM, 0));
            feed(p, new ClientUseItemPacket(PlayerHand.MAIN, 2, 0f, 0f));
            MinecraftServer.getSchedulerManager().processTickEnd();
            sync.broadcast();
            assertTrue(p.player.isUsingItem(), "the second use runs on");
            assertTrue(p.sent(WindowItemsPacket.class).isEmpty());
            assertTrue(p.sent(SetSlotPacket.class).stream().noneMatch(s -> s.slot() == 36), "the bow is left alone");
            assertTrue(p.sent(SetSlotPacket.class).stream().anyMatch(s -> s.slot() == 9), "the tap's arrow goes back");
        } finally {
            p.player.remove();
        }
    }

    /** 1.8 sends the swap ahead of the release when both land in one tick, and still releases the drawn bow. */
    @Test
    void aSwapThenReleaseGetsItsArrow() {
        FakePlayer p = FakePlayer.connect(instance, new Pos(40.5, 64, 40.5), "SwapDraw");
        try {
            Polyp.getInstance().clientInfo().setProtocol(p.player, 47);
            p.player.getInventory().setItemStack(0, ItemStack.of(Material.BOW));
            p.player.getInventory().setItemStack(1, ItemStack.of(Material.STONE_SWORD));
            p.player.getInventory().setItemStack(9, ItemStack.of(Material.ARROW, 16));
            InventorySync sync = ((PolypPlayer) p.player).inventorySync();
            sync.broadcast();
            feed(p, new ClientUseItemPacket(PlayerHand.MAIN, 1, 0f, 0f));
            p.sent.clear();
            feed(p, new ClientHeldItemChangePacket((short) 1));
            feed(p, new ClientPlayerActionPacket(ClientPlayerActionPacket.Status.UPDATE_ITEM_STATE, Vec.ZERO, BlockFace.BOTTOM, 0));
            sync.broadcast();
            assertTrue(p.sent(SetSlotPacket.class).stream().anyMatch(s -> s.slot() == 9), "the arrow goes back");
        } finally {
            p.player.remove();
        }
    }

    private static void feed(FakePlayer p, ClientPacket packet) {
        MinecraftServer.getPacketListenerManager().processClientPacket(packet, p.player.getPlayerConnection());
    }

    private static List<SetSlotPacket> release(String name, int protocol, Material held) {
        FakePlayer drawer = FakePlayer.connect(instance, new Pos(40.5, 64, 40.5), name);
        try {
            Polyp.getInstance().clientInfo().setProtocol(drawer.player, protocol);
            drawer.player.getInventory().setItemStack(0, ItemStack.of(held));
            drawer.player.getInventory().setItemStack(9, ItemStack.of(Material.ARROW, 16));
            InventorySync sync = ((PolypPlayer) drawer.player).inventorySync();
            sync.broadcast();
            drawer.sent.clear();
            EventDispatcher.call(new PlayerCancelItemUseEvent(drawer.player, PlayerHand.MAIN, ItemStack.of(held), 3));
            sync.broadcast();
            assertTrue(drawer.sent(WindowItemsPacket.class).isEmpty(), "a whole-window resend ends a re-draw");
            return drawer.sent(SetSlotPacket.class);
        } finally {
            drawer.player.remove();
        }
    }
}
