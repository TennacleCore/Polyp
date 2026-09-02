package io.github.term4.polyp.platform.fixes.client;

import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.network.packet.server.SendablePacket;
import net.minestom.server.network.packet.server.play.SetPlayerInventorySlotPacket;
import net.minestom.server.network.packet.server.play.SetSlotPacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/** The 1.8 slot numbering the rewrite has to land on: hotbar 36-44, main 9-35, armor 5-8. */
class LegacyInventorySlotFixTest {

    private static final ItemStack SWORD = ItemStack.of(Material.DIAMOND_SWORD);

    @BeforeAll
    static void setUp() {
        LegacyInventorySlotFix.install();
    }

    private static short windowSlot(int playerInventorySlot) {
        SendablePacket out = LegacyInventorySlotFix.rewrite(true,
                new SetPlayerInventorySlotPacket(playerInventorySlot, SWORD));
        SetSlotPacket slot = assertInstanceOf(SetSlotPacket.class, out);
        assertEquals(0, slot.windowId(), "window 0 is the one a 1.8 client always applies");
        assertEquals(SWORD, slot.itemStack());
        return slot.slot();
    }

    @Test
    void playerInventorySlotsBecomeWindowZeroSlots() {
        assertEquals((short) 36, windowSlot(0), "first hotbar slot");
        assertEquals((short) 44, windowSlot(8), "last hotbar slot");
        assertEquals((short) 9, windowSlot(9), "main inventory is unmoved");
        assertEquals((short) 35, windowSlot(35));
        assertEquals((short) 5, windowSlot(39), "helmet");
        assertEquals((short) 8, windowSlot(36), "boots");
    }

    @Test
    void modernClientsKeepTheirOwnPacket() {
        SetPlayerInventorySlotPacket packet = new SetPlayerInventorySlotPacket(0, SWORD);
        assertSame(packet, LegacyInventorySlotFix.rewrite(false, packet));
    }
}
