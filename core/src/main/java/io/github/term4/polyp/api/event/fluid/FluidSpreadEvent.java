package io.github.term4.polyp.api.event.fluid;

import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.event.trait.CancellableEvent;
import net.minestom.server.instance.block.Block;
import net.minestom.server.utils.Direction;
import org.jetbrains.annotations.NotNull;

/** A fluid about to enter {@code to} from {@code from}; cancel and it stays out this tick. */
public final class FluidSpreadEvent implements CancellableEvent {

    private final MechanicsWorld world;
    private final BlockVec from;
    private final BlockVec to;
    private final Direction direction;
    private final Block fluid;
    private final Block into;
    private boolean cancelled;

    public FluidSpreadEvent(@NotNull MechanicsWorld world, @NotNull BlockVec from, @NotNull BlockVec to,
                            @NotNull Direction direction, @NotNull Block fluid, @NotNull Block into) {
        this.world = world;
        this.from = from;
        this.to = to;
        this.direction = direction;
        this.fluid = fluid;
        this.into = into;
    }

    public @NotNull MechanicsWorld world() { return world; }
    public @NotNull BlockVec from() { return from; }
    public @NotNull BlockVec to() { return to; }
    public @NotNull Direction direction() { return direction; }
    /** The fluid block that would land, level set. */
    public @NotNull Block fluid() { return fluid; }
    /** What stands at {@code to} now. */
    public @NotNull Block into() { return into; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
}
