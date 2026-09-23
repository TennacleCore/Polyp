package io.github.term4.polyp.vri;

import io.github.term4.polyp.platform.inventory.InventorySync;
import io.github.term4.polyp.platform.player.OptimizedPlayer;
import io.github.term4.polyp.platform.player.PlayerConfig;
import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.event.EventListener;
import net.minestom.server.event.item.ItemDropEvent;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.client.play.ClientCloseWindowPacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** VRI drops a cursor left on the player's own screen; a profile's cursor rule takes that close over. */
class ItemDropCursorRuleTest extends HeadlessServerTest {

    @BeforeAll
    static void install() {
        Vri.install(polyp, VriConfig.all());
    }

    @Test
    void cursorRuleOwnsTheClose() {
        FakePlayer p = FakePlayer.connect(instance, new Pos(30.5, 64, 30.5), "VriReturn");
        List<ItemStack> dropped = new ArrayList<>();
        EventListener<ItemDropEvent> drops = EventListener.of(ItemDropEvent.class, e -> {
            if (e.getPlayer() == p.player) dropped.add(e.getItemStack());
        });
        MinecraftServer.getGlobalEventHandler().addListener(drops);
        try {
            InventorySync sync = ((OptimizedPlayer) p.player).inventorySync();
            sync.cursorOnClose(PlayerConfig.CursorOnClose.RETURN);
            ItemStack carried = ItemStack.of(Material.STONE, 3);
            p.player.getInventory().setCursorItem(carried);
            sync.broadcast();
            MinecraftServer.getPacketListenerManager().processClientPacket(new ClientCloseWindowPacket((byte) 0),
                    p.player.getPlayerConnection());
            MinecraftServer.getSchedulerManager().processTickEnd();
            assertTrue(dropped.isEmpty(), "returned, not dropped");
            assertEquals(carried, p.player.getInventory().getItemStack(0));
        } finally {
            MinecraftServer.getGlobalEventHandler().removeListener(drops);
            p.player.remove();
        }
    }
}
