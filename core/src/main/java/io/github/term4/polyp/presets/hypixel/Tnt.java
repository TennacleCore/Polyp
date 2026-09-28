package io.github.term4.polyp.presets.hypixel;

import io.github.term4.polyp.entity.PrimedTnt;
import io.github.term4.polyp.mechanics.explosion.ExplosionSystem;
import io.github.term4.polyp.mechanics.explosion.TntConfig;
import io.github.term4.polyp.mechanics.explosion.TntConfigResolver;
import net.kyori.adventure.key.Key;
import net.minestom.server.coordinate.Point;
import net.minestom.server.instance.Instance;
import org.jetbrains.annotations.Nullable;

/** Hypixel TNT: fuse 50 (vanilla 80), FEET detonation (capture: expl.y − feet = 0 across 11 TNTs, not vanilla +height/16 nor Spigot +length/2), HYPIXEL wire shape, no ground bounce, BedWars place-ignition. */
public final class Tnt {

    private Tnt() {}

    /** The stamp a TNT carries to be this one wherever it is placed. */
    public static final Key KIND = Key.key("hypixel:tnt");

    // tntVictimScale unset: Hypixel's explosion KB is already vanilla 1.0
    public static TntConfig kind() {
        return TntConfig.builder().fuseTicks(50).detonateAtFeet(true).bounce(false).igniteOnPlace(true).build();
    }

    /** The world's TNT, and the kind registered under {@link #KIND}. */
    public static TntConfig config() {
        return kind().toBuilder().kind(KIND, kind()).build();
    }

    public static @Nullable PrimedTnt spawn(ExplosionSystem explosion, Instance instance, Point tntBlock) {
        return TntConfigResolver.spawn(explosion, instance, tntBlock, config());
    }
}
