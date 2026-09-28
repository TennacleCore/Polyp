package io.github.term4.polyp.api.event.container;

import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.entity.Player;
import net.minestom.server.event.trait.CancellableEvent;
import net.minestom.server.event.trait.PlayerInstanceEvent;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * A click is moving {@code item} into the contents under {@code key}; cancelled, the click is refused. Only clicks:
 * a write through {@code ContainerSystem} fires nothing.
 */
public final class ContainerInsertEvent implements PlayerInstanceEvent, CancellableEvent {

    private final MechanicsWorld world;
    private final String key;
    private final ItemStack item;
    private final Player player;
    private boolean cancelled;

    public ContainerInsertEvent(@NotNull MechanicsWorld world, @NotNull String key, @NotNull ItemStack item,
                                @NotNull Player player) {
        this.world = world;
        this.key = key;
        this.item = item;
        this.player = player;
    }

    public @NotNull MechanicsWorld world() { return world; }
    public @NotNull String key() { return key; }
    public @NotNull ItemStack item() { return item; }

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
