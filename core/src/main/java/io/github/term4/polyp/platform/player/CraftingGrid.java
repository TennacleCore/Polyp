package io.github.term4.polyp.platform.player;

import io.github.term4.polyp.Polyp;
import net.minestom.server.event.EventFilter;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.inventory.InventoryCloseEvent;
import net.minestom.server.event.inventory.InventoryPreClickEvent;
import net.minestom.server.event.trait.PlayerEvent;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.inventory.click.Click;
import net.minestom.server.item.ItemStack;
import net.minestom.server.utils.inventory.PlayerInventoryUtils;
import org.jetbrains.annotations.NotNull;

/**
 * Minestom crafts nothing, so the player inventory's 2x2 grid is a trap: a 1.8 client predicts a result the server
 * never holds, and an item left in the grid outlives the close on the server while the client forgets it, then
 * reappears with the next full resync. Every click that touches the grid or its result is refused (the cancel
 * resyncs the client), and whatever reaches the grid anyway goes back to the inventory on close, as vanilla does.
 */
public final class CraftingGrid {

    private CraftingGrid() {}

    public static void install(@NotNull Polyp polyp) {
        EventNode<@NotNull PlayerEvent> node = EventNode.type("polyp:crafting-grid", EventFilter.PLAYER);
        node.addListener(InventoryPreClickEvent.class, e -> {
            if (e.getInventory() instanceof PlayerInventory && touchesGrid(e.getClick())) e.setCancelled(true);
        });
        node.addListener(InventoryCloseEvent.class, e -> {
            if (e.getInventory() instanceof PlayerInventory inventory) {
                for (ItemStack left : empty(inventory)) e.getPlayer().dropItem(left);
            }
        });
        polyp.install(node);
    }

    static boolean touchesGrid(@NotNull Click click) {
        if (click instanceof Click.Drag drag) return drag.slots().stream().anyMatch(CraftingGrid::inGrid);
        return inGrid(click.slot());
    }

    private static boolean inGrid(int slot) {
        return slot >= PlayerInventoryUtils.CRAFT_RESULT && slot <= PlayerInventoryUtils.CRAFT_SLOT_4;
    }

    /** Clears the grid into the inventory; what does not fit comes back for the caller to drop. */
    static @NotNull java.util.List<ItemStack> empty(@NotNull PlayerInventory inventory) {
        java.util.List<ItemStack> homeless = new java.util.ArrayList<>();
        for (int slot = PlayerInventoryUtils.CRAFT_SLOT_1; slot <= PlayerInventoryUtils.CRAFT_SLOT_4; slot++) {
            ItemStack held = inventory.getItemStack(slot);
            if (held.isAir()) continue;
            inventory.setItemStack(slot, ItemStack.AIR);
            if (!inventory.addItemStack(held)) homeless.add(held);
        }
        inventory.setItemStack(PlayerInventoryUtils.CRAFT_RESULT, ItemStack.AIR);
        return homeless;
    }
}
