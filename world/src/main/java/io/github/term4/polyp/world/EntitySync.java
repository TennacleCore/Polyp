package io.github.term4.polyp.world;

import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

/**
 * Minestom seeds a first scheduled position sync around tick 20 that {@code setSynchronizationTicks} does NOT reset.
 * On a hand-driven entity (a TNT, a replay twin) that absolute lands mid-flight as a catch-up snap.
 */
public final class EntitySync {

    private static final Logger LOG = LoggerFactory.getLogger(EntitySync.class);
    private static final VarHandle NEXT_SYNC = resolve();

    private EntitySync() {}

    private static VarHandle resolve() {
        try {
            return MethodHandles.privateLookupIn(Entity.class, MethodHandles.lookup())
                    .findVarHandle(Entity.class, "nextSynchronizationTick", long.class);
        } catch (ReflectiveOperationException e) {
            LOG.warn("Entity.nextSynchronizationTick is gone: a hand-driven entity keeps one seeded position sync", e);
            return null;
        }
    }

    /** Pushes the seeded sync out of reach. A no-op when the field is gone: one early snap, not a crash. */
    public static void clearSeeded(@NotNull Entity entity) {
        if (NEXT_SYNC != null) NEXT_SYNC.set(entity, Long.MAX_VALUE);
    }
}
