package io.github.term4.polyp.mechanics.mobs;

import io.github.term4.polyp.Services;
import io.github.term4.polyp.config.SubjectContext;
import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

public final class MobsConfigResolver {

    private MobsConfigResolver() {}

    /** {@code mob} is null for a read with no mob in hand (a difficulty scale on a victim). */
    public record MobContext(@Nullable MobEntity mob, MechanicsWorld world, Services services) implements SubjectContext {
        @Override public @Nullable Entity subject() { return mob; }

        public Random random() {
            return mob != null ? mob.random() : ThreadLocalRandom.current();
        }
    }
}
