package io.github.term4.polyp.platform.fixes.client;

import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.platform.fixes.FixToggleConfig;
import io.github.term4.polyp.platform.fixes.FixesConfig;
import io.github.term4.polyp.platform.fixes.FixesSystem;
import io.github.term4.polyp.platform.inventory.InventorySync;
import io.github.term4.polyp.platform.player.OptimizedPlayer;
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
import net.minestom.server.network.packet.client.play.ClientPlayerActionPacket;
import net.minestom.server.network.packet.client.play.ClientUseItemPacket;
import net.minestom.server.network.packet.server.play.SetSlotPacket;
import net.minestom.server.network.packet.server.play.WindowItemsPacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A 1.8 client spends its own arrow on a bow release: its arrows are re-sent, never its bow. */
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
    void tapThenHoldKeepsTheUse() {
        FakePlayer p = FakePlayer.connect(instance, new Pos(40.5, 64, 40.5), "TapHold");
        try {
            Polyp.getInstance().clientInfo().setProtocol(p.player, 47);
            p.player.getInventory().setItemStack(0, ItemStack.of(Material.BOW));
            p.player.getInventory().setItemStack(9, ItemStack.of(Material.ARROW, 16));
            InventorySync sync = ((OptimizedPlayer) p.player).inventorySync();
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
            InventorySync sync = ((OptimizedPlayer) drawer.player).inventorySync();
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
