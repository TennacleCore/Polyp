package io.github.term4.polyp.mechanics.fluids;

import io.github.term4.polyp.Services;
import io.github.term4.polyp.config.SubjectContext;
import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.entity.Entity;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;

public final class FluidsConfigResolver {

    private FluidsConfigResolver() {}

    /** {@code pos}/{@code block} are the fluid cell being decided; null for a world-wide read (the bucket knobs). */
    public record FluidContext(MechanicsWorld world, @Nullable BlockVec pos, @Nullable Block block, @Nullable Entity actor,
                               Services services) implements SubjectContext {
        @Override public @Nullable Entity subject() { return actor; }
    }
}
