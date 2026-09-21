package io.github.term4.polyp.mechanics.containers;

import io.github.term4.polyp.Services;
import io.github.term4.polyp.codegen.CheckResolveOrder;
import io.github.term4.polyp.config.Config;
import io.github.term4.polyp.config.FieldValue;
import io.github.term4.polyp.config.SubjectContext;
import io.github.term4.polyp.world.MechanicsWorld;
import net.kyori.adventure.text.Component;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.block.Block;
import org.jetbrains.annotations.Nullable;

@CheckResolveOrder
public final class ContainersConfigResolver {

    private ContainersConfigResolver() {}

    /** One container's cell and, when there is one, the player opening or breaking it. */
    public record ContainerContext(MechanicsWorld world, BlockVec pos, Block block, @Nullable Player viewer,
                                   Services services) implements SubjectContext {
        @Override public @Nullable Entity subject() { return viewer(); }
    }

    public record ResolvedContainer(int rows, Component title, ContainerKey key, ContainerFill fill, Spill spill,
                                    ContainerTypeConfig.Pairing pairing) {}

    /** {@code declared} (a position's own kind) over the config's entry for the block; {@code null} = not a container. */
    public static @Nullable ResolvedContainer resolve(@Nullable ContainersConfig cfg, @Nullable ContainerTypeConfig declared,
                                                      ContainerContext ctx) {
        ContainerTypeConfig entry = declared != null ? declared : cfg != null ? cfg.typeConfig(ctx.block()) : null;
        if (entry == null) return null;
        ContainerTypeConfig tc = Config.layer(ContainerTypeConfig.builder().build(),
                cfg != null ? cfg.defaults() : null, entry, ctx);
        return new ResolvedContainer(
                FieldValue.resolve(tc.rows, ctx, 3),
                FieldValue.resolve(tc.title, ctx, Component.translatable("container.chest")),
                FieldValue.resolve(tc.key, ctx, ContainerKey.BLOCK),
                FieldValue.resolve(tc.fill, ctx, ContainerFill.EMPTY),
                FieldValue.resolve(tc.spill, ctx, Spill.DROP),
                FieldValue.resolve(tc.pairing, ctx, ContainerTypeConfig.Pairing.NONE));
    }
}
