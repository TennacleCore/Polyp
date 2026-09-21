package io.github.term4.polyp.mechanics.containers;

import io.github.term4.polyp.config.FieldFns;
import io.github.term4.polyp.mechanics.containers.ContainersConfigResolver.ContainerContext;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** What a container holds the first time it is opened or broken; {@code null} or a short array = air from there. */
@FunctionalInterface
public interface ContainerFill {

    ItemStack @Nullable [] of(@NotNull ContainerContext ctx, int slots);

    ContainerFill EMPTY = (ctx, slots) -> null;

    static @NotNull ContainerFill fixed(ItemStack... stacks) {
        return (ctx, slots) -> stacks.clone();
    }

    static void registerFactories() {
        FieldFns.register(ContainerFill.class, "empty", "nothing until someone puts something in", args -> EMPTY);
    }
}
