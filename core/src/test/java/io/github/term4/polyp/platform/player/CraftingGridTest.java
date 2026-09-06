package io.github.term4.polyp.platform.player;

import io.github.term4.polyp.testsupport.FakePlayer;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.inventory.click.Click;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import net.minestom.server.utils.inventory.PlayerInventoryUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The 2x2 grid refuses every click and empties into the inventory on close. */
class CraftingGridTest extends HeadlessServerTest {

    private static boolean refused(FakePlayer p, Click click) {
        var event = new InventoryPreClickEvent(p.player.getInventory(), p.player, click);
        EventDispatcher.call(event);
        return event.isCancelled();
    }

    @Test
    void gridClicksAreRefused() {
        FakePlayer p = FakePlayer.connect(instance, new Pos(0.5, 41, 0.5), "Crafter");
        try {
            assertTrue(refused(p, new Click.Left(PlayerInventoryUtils.CRAFT_SLOT_1)), "into the grid");
            assertTrue(refused(p, new Click.LeftShift(PlayerInventoryUtils.CRAFT_RESULT)), "off the result");
            assertTrue(refused(p, new Click.LeftDrag(List.of(9, PlayerInventoryUtils.CRAFT_SLOT_4))), "a drag across it");
            assertTrue(refused(p, new Click.HotbarSwap(0, PlayerInventoryUtils.CRAFT_SLOT_2)), "a hotbar swap into it");
            assertFalse(refused(p, new Click.Left(9)), "the inventory itself is untouched");
            assertFalse(refused(p, new Click.LeftDropCursor()), "so is a cursor drop");
        } finally {
            p.player.remove();
        }
    }

    @Test
    void theCloseEmptiesTheGrid() {
        FakePlayer p = FakePlayer.connect(instance, new Pos(0.5, 41, 0.5), "Closer");
        try {
            PlayerInventory inventory = p.player.getInventory();
            inventory.setItemStack(PlayerInventoryUtils.CRAFT_SLOT_1, ItemStack.of(Material.STONE, 3));
            EventDispatcher.call(new InventoryCloseEvent(inventory, p.player, true));
            assertTrue(inventory.getItemStack(PlayerInventoryUtils.CRAFT_SLOT_1).isAir(), "the grid is clear");
            int stone = 0;
            for (int slot = 0; slot < 36; slot++) {
                if (inventory.getItemStack(slot).material() == Material.STONE) stone += inventory.getItemStack(slot).amount();
            }
            assertEquals(3, stone, "and the stone is back in the inventory");
        } finally {
            p.player.remove();
        }
    }
}
