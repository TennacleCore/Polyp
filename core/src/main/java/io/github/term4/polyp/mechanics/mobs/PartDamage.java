package io.github.term4.polyp.mechanics.mobs;

/** What a hit on a dragon part other than the head becomes. */
@FunctionalInterface
public interface PartDamage {

    float of(float amount);

    /** 1.8 attackEntityFromPart. */
    PartDamage LEGACY = amount -> amount / 4.0f + 1.0f;

    /** 26.1 EnderDragon.hurt: the plus-one never exceeds the hit itself. */
    PartDamage MODERN = amount -> amount / 4.0f + Math.min(amount, 1.0f);
}
