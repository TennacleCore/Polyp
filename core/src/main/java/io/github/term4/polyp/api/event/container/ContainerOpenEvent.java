package io.github.term4.polyp.api.event.container;

import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.entity.Player;
import net.minestom.server.event.trait.CancellableEvent;
import net.minestom.server.event.trait.PlayerInstanceEvent;
import org.jetbrains.annotations.NotNull;

/** {@code player} is opening the contents under {@code key}; cancelled, nothing opens and the click is still spent. */
public final class ContainerOpenEvent implements PlayerInstanceEvent, CancellableEvent {

    private final MechanicsWorld world;
    private final BlockVec pos;
    private final String key;
    private final Player player;
    private boolean cancelled;

    public ContainerOpenEvent(@NotNull MechanicsWorld world, @NotNull BlockVec pos, @NotNull String key, @NotNull Player player) {
        this.world = world;
        this.pos = pos;
        this.key = key;
        this.player = player;
    }

    public @NotNull MechanicsWorld world() { return world; }
    public @NotNull BlockVec pos() { return pos; }
    public @NotNull String key() { return key; }

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
