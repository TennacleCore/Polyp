package io.github.term4.polyp.presets.scrims18;

import io.github.term4.polyp.mechanics.damage.DamageConfig;

/** Scrims damage: the Hypixel base with a 10-tick invul window. */
public final class Damage {

    private Damage() {}

    /** A delta over the parent profile's member (see {@code Scrims18.profile}): never restate a base here. */
    public static DamageConfig config(DamageConfig base) {
        return DamageConfig.builder(base).invulTicks(10).build();
    }
}
