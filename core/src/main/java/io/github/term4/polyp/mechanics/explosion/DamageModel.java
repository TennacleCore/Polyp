package io.github.term4.polyp.mechanics.explosion;

import io.github.term4.polyp.config.FieldFns;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * <em>How</em> a blast decides one living target's damage - the explosion system's damage abstraction (the
 * {@code BlockingBehavior} / {@code ConsumableBehavior} idiom). The vanilla curve and Hypixel's flat 2.0 are two
 * behaviors, not two config modes: a preset picks one, and a server can supply its own without touching polyp.
 *
 * <p>Set on {@link ExplosionConfig#damageModel}; one axis, so a scaled curve is {@link #scaled}, not a second knob.
 */
@FunctionalInterface
public interface DamageModel {

    /** One target of a blast, with the falloff amount it would take under {@link #CURVE}.
     *  {@code owner} is who set it off - a projectile's shooter, not the projectile - or null for a sourceless blast. */
    record Hit(@NotNull Entity target, @Nullable Entity owner, double distance, float exposure, float curve) {
        public boolean hitOwner() { return owner != null && owner == target; }
    }

    float amount(@NotNull Hit hit);

    /** Vanilla: distance + exposure falloff. */
    DamageModel CURVE = Hit::curve;

    /** The same {@code amount} to every target in range, whatever the distance (Hypixel/BedWars = 2.0). */
    static @NotNull DamageModel flat(double amount) {
        return hit -> (float) amount;
    }

    /** {@code model} times {@code factor} - the vanilla floored curve at 5% is MineMen's fireball fight. */
    static @NotNull DamageModel scaled(double factor, @NotNull DamageModel model) {
        return hit -> (float) (model.amount(hit) * factor);
    }

    /** Whoever set the blast off reads {@code owner}, everyone else {@code others} - a fireball jump costs its thrower less. */
    static @NotNull DamageModel byOwner(@NotNull DamageModel owner, @NotNull DamageModel others) {
        return hit -> (hit.hitOwner() ? owner : others).amount(hit);
    }

    /** Names these for data paths ({@code explosion/damageModel = flat(2.0)}); a server registers its own too. */
    static void registerFactories() {
        FieldFns.register(DamageModel.class, "curve", "vanilla distance + exposure falloff", args -> CURVE);
        FieldFns.register(DamageModel.class, "flat(amount)", "the same damage to everything in range",
                args -> flat(args.arity(1).dbl(0)));
        FieldFns.register(DamageModel.class, "scale(factor, model)", "another model, multiplied",
                args -> scaled(args.arity(2).dbl(0), args.of(1, DamageModel.class)));
        FieldFns.register(DamageModel.class, "byOwner(owner, others)", "one model for whoever set it off, another for the rest",
                args -> byOwner(args.arity(2).of(0, DamageModel.class), args.of(1, DamageModel.class)));
    }
}
