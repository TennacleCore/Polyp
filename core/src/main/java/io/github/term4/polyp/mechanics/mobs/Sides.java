package io.github.term4.polyp.mechanics.mobs;

import net.minestom.server.entity.Entity;
import net.minestom.server.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Whose side an entity is on, for the "never target a teammate" rule. */
@FunctionalInterface
public interface Sides {

    @Nullable Object of(@NotNull Entity entity);

    /** Vanilla: the scoreboard team. */
    Sides SCOREBOARD = e -> e instanceof LivingEntity le ? le.getTeam() : null;

    default boolean same(@NotNull Entity a, @NotNull Entity b) {
        Object side = of(a);
        return side != null && side.equals(of(b));
    }
}
