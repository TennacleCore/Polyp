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
 *
 * <p><b>The one reflection in the stack, and why it stays.</b> The public way to move the seed is
 * {@code synchronizeNextTick()} plus a far interval, which reseeds on the entity's first tick - but the tick
 * that reseeds also SENDS a position sync and a velocity packet, and every caller here is an entity whose wire
 * is captured parity (a 1.8 TNT spawn, a replay twin's track). Paying two packets on the first tick to avoid a
 * VarHandle is the wrong trade. The real fix is upstream: {@code Entity.setSynchronizationTicks} should move
 * {@code nextSynchronizationTick} with the interval it sets, which is one line and makes this class unnecessary.
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
