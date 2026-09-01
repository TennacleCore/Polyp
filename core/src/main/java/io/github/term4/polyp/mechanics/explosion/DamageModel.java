package io.github.term4.polyp.mechanics.explosion;

import io.github.term4.polyp.config.FieldFns;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.NotNull;

/**
 * <em>How</em> a blast decides one living target's damage - the explosion system's damage abstraction (the
 * {@code BlockingBehavior} / {@code ConsumableBehavior} idiom). The vanilla curve and Hypixel's flat 2.0 are two
 * behaviors, not two config modes: a preset picks one, and a server can supply its own without touching polyp.
 *
 * <p>Set on {@link ExplosionConfig#damageModel}; {@link ExplosionConfig#damageScale} still scales whatever it returns.
 */
@FunctionalInterface
public interface DamageModel {

    /** One target of a blast, with the falloff amount it would take under {@link #CURVE}. */
    record Hit(@NotNull Entity target, double distance, float exposure, float curve) {}

    float amount(@NotNull Hit hit);

    /** Vanilla: distance + exposure falloff. */
    DamageModel CURVE = Hit::curve;

    /** The same {@code amount} to every target in range, whatever the distance (Hypixel/BedWars = 2.0). */
    static @NotNull DamageModel flat(double amount) {
        return hit -> (float) amount;
    }

    /** Names these for data paths ({@code explosion/damageModel = flat(2.0)}); a server registers its own too. */
    static void registerFactories() {
        FieldFns.register(DamageModel.class, "curve", "vanilla distance + exposure falloff", args -> CURVE);
        FieldFns.register(DamageModel.class, "flat(amount)", "the same damage to everything in range",
                args -> flat(args.arity(1).dbl(0)));
    }
}
