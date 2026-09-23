package io.github.term4.polyp.platform.inventory;

import io.github.term4.polyp.platform.inventory.LegacyClicks.Layout;
import io.github.term4.polyp.testsupport.HeadlessServerTest;
import net.minestom.server.item.ItemStack;
import net.minestom.server.item.Material;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Expectations read from MCP-919's Container, ContainerPlayer, ContainerChest and InventoryPlayer. */
class LegacyClicksTest extends HeadlessServerTest {

    private static final int PICKUP = 0, SHIFT = 1, SWAP = 2, CLONE = 3, DRAG = 5, GATHER = 6;
    private static final int CHEST = 27;

    private final LegacyClicks clicks = new LegacyClicks();

    @Test
    void leftPickupTakesAll() {
        ItemStack[] slots = player();
        slots[36] = stone(10);
        assertEquals(stone(10), clicks.click(Layout.PLAYER, slots, ItemStack.AIR, 36, 0, PICKUP, false));
        assertEquals(ItemStack.AIR, slots[36]);
    }

    @Test
    void rightPickupTakesHalf() {
        ItemStack[] slots = player();
        slots[36] = stone(7);
        assertEquals(stone(4), clicks.click(Layout.PLAYER, slots, ItemStack.AIR, 36, 1, PICKUP, false));
        assertEquals(stone(3), slots[36]);
    }

    @Test
    void rightClickPlacesOne() {
        ItemStack[] slots = player();
        assertEquals(stone(4), clicks.click(Layout.PLAYER, slots, stone(5), 36, 1, PICKUP, false));
        assertEquals(stone(1), slots[36]);
    }

    @Test
    void mergeStopsAtMax() {
        ItemStack[] slots = player();
        slots[36] = stone(40);
        assertEquals(stone(16), clicks.click(Layout.PLAYER, slots, stone(40), 36, 0, PICKUP, false));
        assertEquals(stone(64), slots[36]);
    }

    @Test
    void differentItemsSwap() {
        ItemStack[] slots = player();
        slots[36] = stone(3);
        assertEquals(stone(3), clicks.click(Layout.PLAYER, slots, dirt(2), 36, 0, PICKUP, false));
        assertEquals(dirt(2), slots[36]);
    }

    @Test
    void shiftHotbarToMain() {
        ItemStack[] slots = player();
        slots[36] = ItemStack.of(Material.DIAMOND_SWORD);
        clicks.click(Layout.PLAYER, slots, ItemStack.AIR, 36, 0, SHIFT, false);
        assertEquals(Material.DIAMOND_SWORD, slots[9].material());
        assertEquals(ItemStack.AIR, slots[36]);
    }

    @Test
    void shiftTopsUpFirst() {
        ItemStack[] slots = player();
        slots[9] = stone(10);
        slots[38] = stone(60);
        clicks.click(Layout.PLAYER, slots, ItemStack.AIR, 9, 0, SHIFT, false);
        assertEquals(stone(64), slots[38]);
        assertEquals(stone(6), slots[36], "the rest, whole, into the first empty hotbar slot");
        assertEquals(ItemStack.AIR, slots[9]);
    }

    @Test
    void shiftEquipsArmor() {
        ItemStack[] slots = player();
        slots[9] = ItemStack.of(Material.IRON_HELMET);
        clicks.click(Layout.PLAYER, slots, ItemStack.AIR, 9, 0, SHIFT, false);
        assertEquals(Material.IRON_HELMET, slots[5].material());
    }

    @Test
    void chestShiftFillsFromTheEnd() {
        ItemStack[] slots = chest();
        slots[0] = ItemStack.of(Material.DIAMOND_SWORD);
        clicks.click(Layout.chest(CHEST), slots, ItemStack.AIR, 0, 0, SHIFT, false);
        assertEquals(Material.DIAMOND_SWORD, slots[CHEST + 35].material(), "the last hotbar slot");
    }

    @Test
    void numberKeyPushesHotbarAside() {
        ItemStack[] slots = chest();
        slots[0] = ItemStack.of(Material.DIAMOND_SWORD);
        slots[CHEST + 27] = stone(5); // hotbar 0
        clicks.click(Layout.chest(CHEST), slots, ItemStack.AIR, 0, 0, SWAP, false);
        assertEquals(Material.DIAMOND_SWORD, slots[CHEST + 27].material());
        assertEquals(stone(5), slots[CHEST + 28], "the hotbar item goes to the first empty inventory slot");
        assertEquals(ItemStack.AIR, slots[0]);
    }

    @Test
    void armorSlotRefusesStone() {
        ItemStack[] slots = player();
        slots[36] = stone(5);
        clicks.click(Layout.PLAYER, slots, ItemStack.AIR, 5, 0, SWAP, false);
        assertEquals(ItemStack.AIR, slots[5]);
        assertEquals(stone(5), slots[36]);
    }

    @Test
    void leftDragSplitsEvenly() {
        ItemStack[] slots = player();
        ItemStack cursor = stone(9);
        cursor = clicks.click(Layout.PLAYER, slots, cursor, -999, 0, DRAG, false);
        for (int slot : new int[]{9, 10, 11}) cursor = clicks.click(Layout.PLAYER, slots, cursor, slot, 1, DRAG, false);
        cursor = clicks.click(Layout.PLAYER, slots, cursor, -999, 2, DRAG, false);
        assertEquals(ItemStack.AIR, cursor);
        assertEquals(stone(3), slots[9]);
        assertEquals(stone(3), slots[11]);
    }

    @Test
    void rightDragPlacesOneEach() {
        ItemStack[] slots = player();
        ItemStack cursor = stone(5);
        cursor = clicks.click(Layout.PLAYER, slots, cursor, -999, 4, DRAG, false);
        for (int slot : new int[]{9, 10}) cursor = clicks.click(Layout.PLAYER, slots, cursor, slot, 5, DRAG, false);
        cursor = clicks.click(Layout.PLAYER, slots, cursor, -999, 6, DRAG, false);
        assertEquals(stone(3), cursor);
        assertEquals(stone(1), slots[10]);
    }

    @Test
    void clickCancelsADrag() {
        ItemStack[] slots = player();
        slots[20] = stone(2);
        ItemStack cursor = clicks.click(Layout.PLAYER, slots, stone(9), -999, 0, DRAG, false);
        cursor = clicks.click(Layout.PLAYER, slots, cursor, 20, 0, PICKUP, false);
        assertEquals(stone(9), cursor, "the pickup only ended the drag");
        assertEquals(stone(2), slots[20]);
    }

    @Test
    void doubleClickTakesPartialsFirst() {
        ItemStack[] slots = player();
        slots[9] = stone(64);
        slots[10] = stone(30);
        assertEquals(stone(64), clicks.click(Layout.PLAYER, slots, stone(1), 20, 0, GATHER, false));
        assertEquals(ItemStack.AIR, slots[10]);
        assertEquals(stone(31), slots[9]);
    }

    @Test
    void cloneNeedsCreative() {
        ItemStack[] slots = player();
        slots[36] = stone(5);
        assertEquals(ItemStack.AIR, clicks.click(Layout.PLAYER, slots, ItemStack.AIR, 36, 2, CLONE, false));
        assertEquals(stone(64), clicks.click(Layout.PLAYER, slots, ItemStack.AIR, 36, 2, CLONE, true));
    }

    @Test
    void outsideDropsTheCursor() {
        assertEquals(ItemStack.AIR, clicks.click(Layout.PLAYER, player(), stone(5), -999, 0, PICKUP, false));
        assertEquals(stone(4), clicks.click(Layout.PLAYER, player(), stone(5), -999, 1, PICKUP, false));
    }

    @Test
    void craftingGridIsNotModelled() {
        assertNull(clicks.click(Layout.PLAYER, player(), stone(5), 1, 0, PICKUP, false));
    }

    private static ItemStack[] player() {
        ItemStack[] slots = new ItemStack[Layout.PLAYER.size()];
        Arrays.fill(slots, ItemStack.AIR);
        return slots;
    }

    private static ItemStack[] chest() {
        ItemStack[] slots = new ItemStack[Layout.chest(CHEST).size()];
        Arrays.fill(slots, ItemStack.AIR);
        return slots;
    }

    private static ItemStack stone(int amount) {
        return ItemStack.of(Material.STONE, amount);
    }

    private static ItemStack dirt(int amount) {
        return ItemStack.of(Material.DIRT, amount);
    }
}
