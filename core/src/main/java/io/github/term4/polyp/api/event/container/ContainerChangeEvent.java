package io.github.term4.polyp.api.event.container;

import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.event.Event;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/** One slot of a stored container changed; {@code key} is the store key, {@code slot} the index within it. */
public final class ContainerChangeEvent implements Event {

    private final MechanicsWorld world;
    private final String key;
    private final int slot;
    private final ItemStack before;
    private final ItemStack after;

    public ContainerChangeEvent(@NotNull MechanicsWorld world, @NotNull String key, int slot,
                                @NotNull ItemStack before, @NotNull ItemStack after) {
        this.world = world;
        this.key = key;
        this.slot = slot;
        this.before = before;
        this.after = after;
    }

    public @NotNull MechanicsWorld world() { return world; }
    public @NotNull String key() { return key; }
    public int slot() { return slot; }
    public @NotNull ItemStack before() { return before; }
    public @NotNull ItemStack after() { return after; }
}
