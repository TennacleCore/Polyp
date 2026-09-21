package io.github.term4.polyp.mechanics.containers;

import io.github.term4.polyp.config.FieldFns;
import io.github.term4.polyp.mechanics.containers.ContainersConfigResolver.ContainerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** What a container's contents are filed under in its world's store; {@code null} = nothing to open here. */
@FunctionalInterface
public interface ContainerKey {

    @Nullable String of(@NotNull ContainerContext ctx);

    /** The block's own cell. */
    ContainerKey BLOCK = ctx -> "block:" + ctx.pos().blockX() + "," + ctx.pos().blockY() + "," + ctx.pos().blockZ();

    /** The opener's own store, the vanilla ender chest; nothing without an opener. */
    ContainerKey VIEWER = ctx -> ctx.viewer() == null ? null : "player:" + ctx.viewer().getUsername();

    static void registerFactories() {
        FieldFns.register(ContainerKey.class, "block", "the block's own cell", args -> BLOCK);
        FieldFns.register(ContainerKey.class, "viewer", "the opener's own store (the ender chest)", args -> VIEWER);
    }
}
