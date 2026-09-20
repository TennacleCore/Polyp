package io.github.term4.polyp.mechanics.projectile.shootables;

import io.github.term4.polyp.config.FieldFns;
import org.jetbrains.annotations.NotNull;

/**
 * How long a bow must be drawn to reach what power - the launch curve, resolved per shot from the arrow
 * type's config, so it can differ by scope (a powerup that removes the charge-up) rather than being baked
 * into the {@link Bow} instance. A shootable installs once for the server; this is what varies.
 *
 * <p>Power {@code 1} is a full draw: the launch speed multiplier, and the crit gate.
 */
@FunctionalInterface
public interface DrawPower {

    /** @param seconds real seconds held, TPS-invariant (ticks / server rate) */
    float at(float seconds);

    /** Vanilla 1.8: {@code (s² + 2s) / 3}, capped at 1 - full draw at one second. */
    DrawPower VANILLA = seconds -> {
        float power = (seconds * seconds + 2 * seconds) / 3.0f;
        return power > 1f ? 1f : power;
    };

    /** Full power the instant the bow is released, however briefly it was held. */
    DrawPower INSTANT = seconds -> 1f;

    /** The vanilla curve stretched (or squeezed) so full draw lands at {@code seconds}. */
    static @NotNull DrawPower fullDrawAt(float seconds) {
        if (seconds <= 0f) return INSTANT;
        return held -> VANILLA.at(held / seconds);
    }

    /**
     * One factory, because {@code instant} and {@code vanilla} are just {@code full-draw-at(0)} and
     * {@code full-draw-at(1)} - naming them would be naming cases. A curve of a different SHAPE is open-ended
     * game logic: define it in code and register it here by name, the way any behavior is selected.
     */
    static void registerFactories() {
        FieldFns.register(DrawPower.class, "full-draw-at(seconds)",
                "the 1.8 curve reaching full power at that time; 0 needs no charge, 1 is vanilla",
                args -> fullDrawAt(args.arity(1).flt(0)));
    }
}
