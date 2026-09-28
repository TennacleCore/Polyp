package io.github.term4.polyp.api.event.item;

import net.minestom.server.entity.ItemEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.event.trait.CancellableEvent;
import net.minestom.server.event.trait.PlayerInstanceEvent;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * {@code player} is about to take {@code item} off the ground: what goes into the inventory is {@link #stack()},
 * changed here at will. Cancelled, the item stays where it lies.
 */
public final class ItemPickupEvent implements PlayerInstanceEvent, CancellableEvent {

    private final Player player;
    private final ItemEntity item;
    private ItemStack stack;
    private boolean cancelled;

    public ItemPickupEvent(@NotNull Player player, @NotNull ItemEntity item) {
        this.player = player;
        this.item = item;
        this.stack = item.getItemStack();
    }

    public @NotNull ItemEntity item() { return item; }
    public @NotNull ItemStack stack() { return stack; }
    public void stack(@NotNull ItemStack stack) { this.stack = stack; }

    @Override
    public @NotNull Player getPlayer() {
        return player;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }
}
