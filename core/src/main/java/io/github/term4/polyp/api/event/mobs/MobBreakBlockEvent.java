package io.github.term4.polyp.api.event.mobs;

import io.github.term4.polyp.mechanics.mobs.MobEntity;
import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.coordinate.Point;
import net.minestom.server.event.trait.CancellableEvent;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.NotNull;

/**
 * Fired for each block a mob is about to take out of the world (a dragon's flight through it), after the kind's
 * own {@code breaks} test let it. Cancel to keep the block; the mob treats it as it does bedrock.
 */
public final class MobBreakBlockEvent implements CancellableEvent {

    private final MechanicsWorld world;
    private final MobEntity mob;
    private final Point position;
    private final Block block;
    private boolean cancelled;

    public MobBreakBlockEvent(@NotNull MechanicsWorld world, @NotNull MobEntity mob, @NotNull Point position, @NotNull Block block) {
        this.world = world;
        this.mob = mob;
        this.position = position;
        this.block = block;
    }

    public @NotNull MechanicsWorld world() { return world; }
    public @NotNull MobEntity mob() { return mob; }
    public @NotNull Point position() { return position; }
    public @NotNull Block block() { return block; }

    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancel) { this.cancelled = cancel; }
}
