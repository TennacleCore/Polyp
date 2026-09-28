package io.github.term4.polyp.presets.mmc18;

import io.github.term4.polyp.entity.PrimedTnt;
import io.github.term4.polyp.mechanics.explosion.ExplosionSystem;
import io.github.term4.polyp.mechanics.explosion.TntConfig;
import io.github.term4.polyp.mechanics.explosion.TntConfigResolver;
import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.Instance;
import org.jetbrains.annotations.Nullable;

/**
 * MineMen TNT: fuse 52, feet detonation, the MINEMEN wire shape (see {@link PrimedTnt}), a ~1.1 push on TNT victims,
 * place-ignition. NOT replicated: their full-block-only collision (MineMen TNT falls through fences).
 */
public final class Tnt {

    private Tnt() {}

    // below-center blast is plain vanilla DOWN at this scale; the "up" on a grounded victim is PrimedTnt's ground
    // bounce, not a rule. Fireball sources keep the profile's KB_SCALE.
    private static final double TNT_VICTIM_SCALE = 1.1;

    /** The stamp a TNT carries to be this one wherever it is placed. */
    public static final Key KIND = Key.key("mmc18:tnt");

    public static TntConfig kind() {
        return TntConfig.builder().fuseTicks(52).detonateAtFeet(true).wire(PrimedTnt.Wire.MINEMEN)
                .tntVictimScale(TNT_VICTIM_SCALE).igniteOnPlace(true).build();
    }

    /** The world's TNT, and the kind registered under {@link #KIND}. */
    public static TntConfig config() {
        return kind().toBuilder().kind(KIND, kind()).build();
    }

    public static @Nullable PrimedTnt spawn(ExplosionSystem explosion, Instance instance, Point tntBlock) {
        return TntConfigResolver.spawn(explosion, instance, tntBlock, config());
    }
}
