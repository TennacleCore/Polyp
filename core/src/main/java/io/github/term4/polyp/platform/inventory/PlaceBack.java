package io.github.term4.polyp.platform.inventory;

import net.minestom.server.entity.Player;
import net.minestom.server.inventory.PlayerInventory;
import net.minestom.server.item.ItemStack;
import net.minestom.server.utils.inventory.PlayerInventoryUtils;
import org.jetbrains.annotations.NotNull;

/** 26.1's {@code Inventory.placeItemBackInInventory}: the held slot, the offhand, any stack with room, then the first empty slot. */
public final class PlaceBack {

    private PlaceBack() {}

    /** Places {@code item} into {@code player}'s inventory and returns what did not fit. */
    public static @NotNull ItemStack into(@NotNull Player player, @NotNull ItemStack item) {
        PlayerInventory inventory = player.getInventory();
        while (!item.isAir()) {
            int slot = roomFor(player, inventory, item);
            if (slot < 0) return item;
            ItemStack there = inventory.getItemStack(slot);
            int put = Math.min(item.amount(), item.maxStackSize() - (there.isAir() ? 0 : there.amount()));
            inventory.setItemStack(slot, there.isAir() ? item.withAmount(put) : there.withAmount(there.amount() + put));
            item = item.amount() == put ? ItemStack.AIR : item.withAmount(item.amount() - put);
        }
        return item;
    }

    private static int roomFor(Player player, PlayerInventory inventory, ItemStack item) {
        int held = player.getHeldSlot();
        if (hasRoom(inventory.getItemStack(held), item)) return held;
        if (hasRoom(inventory.getItemStack(PlayerInventoryUtils.OFFHAND_SLOT), item)) return PlayerInventoryUtils.OFFHAND_SLOT;
        for (int slot = 0; slot < PlayerInventory.INNER_INVENTORY_SIZE; slot++) {
            if (hasRoom(inventory.getItemStack(slot), item)) return slot;
        }
        for (int slot = 0; slot < PlayerInventory.INNER_INVENTORY_SIZE; slot++) {
            if (inventory.getItemStack(slot).isAir()) return slot;
        }
        return -1;
    }

    private static boolean hasRoom(ItemStack there, ItemStack item) {
        return !there.isAir() && there.isSimilar(item) && there.amount() < there.maxStackSize();
    }
}
