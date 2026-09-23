package io.github.term4.polyp.platform.inventory;

import net.minestom.server.inventory.AbstractInventory;
import net.minestom.server.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** One window as its client shows it, slot by slot in window order, and the state id it last received. */
final class ClientWindow {

    final int windowId;
    /** Slots {@code 0..size-36} of a container window; {@code null} for window 0, or until the inventory is known. */
    @Nullable AbstractInventory inventory;
    private final Remote[] slots;
    private int stateId;
    /** The next broadcast sends every slot. */
    boolean resyncAll;

    ClientWindow(int windowId, int size, @Nullable AbstractInventory inventory) {
        this.windowId = windowId;
        this.inventory = inventory;
        this.slots = new Remote[size];
        for (int i = 0; i < size; i++) slots[i] = new Remote();
    }

    /** A container window: its own slots, then the viewer's 27 main and 9 hotbar slots. */
    static @NotNull ClientWindow container(@NotNull AbstractInventory inventory) {
        return new ClientWindow(inventory.getWindowId(), inventory.getSize() + PlayerInventory.INNER_INVENTORY_SIZE,
                inventory);
    }

    int size() {
        return slots.length;
    }

    /** The container's own slot count; the player's slots follow it. */
    int containerSize() {
        return slots.length - PlayerInventory.INNER_INVENTORY_SIZE;
    }

    @NotNull Remote slot(int slot) {
        return slots[slot];
    }

    boolean has(int slot) {
        return slot >= 0 && slot < slots.length;
    }

    int stateId() {
        return stateId;
    }

    int nextStateId() {
        stateId = stateId + 1 & 32767;
        return stateId;
    }

    void forgetAll() {
        for (Remote slot : slots) slot.forget();
    }
}
