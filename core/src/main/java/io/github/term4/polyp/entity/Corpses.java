package io.github.term4.polyp.entity;

import io.github.term4.polyp.util.tick.TickSystem;
import net.minestom.server.MinecraftServer;
import net.minestom.server.entity.Entity;
import net.minestom.server.event.entity.EntityDeathEvent;
import net.minestom.server.tag.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/** The tick a body last died on its world's clock, for rules that wait out the corpse as 1.8 does. */
public final class Corpses {

    private static final Tag<Long> DIED_AT = Tag.Long("polyp:died-at");
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    private Corpses() {}

    public static void install() {
        if (!INSTALLED.compareAndSet(false, true)) return;
        MinecraftServer.getGlobalEventHandler().addListener(EntityDeathEvent.class, e -> died(e.getEntity()));
        TickSystem.onClockChange(e -> e.removeTag(DIED_AT));
    }

    /** For a death a game decides itself, its fatal hit cancelled: {@link #diedAt} reads it like any other. */
    public static void died(@NotNull Entity body) {
        body.setTag(DIED_AT, TickSystem.tick(body));
    }

    /** Null until it dies, and again after its world's clock changes. */
    public static @Nullable Long diedAt(@NotNull Entity body) {
        return body.getTag(DIED_AT);
    }
}
