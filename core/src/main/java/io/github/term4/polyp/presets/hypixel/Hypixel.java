package io.github.term4.polyp.presets.hypixel;

import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.mechanics.damage.types.burning.InFireDamage;
import io.github.term4.polyp.mechanics.damage.types.burning.BurningConfig;
import io.github.term4.polyp.mechanics.damage.DamageConfig;
import io.github.term4.polyp.mechanics.attack.AttackConfig;
import io.github.term4.polyp.fx.FxRegistry;
import io.github.term4.polyp.MechanicsProfile;
import io.github.term4.polyp.fx.Fx;
import io.github.term4.polyp.fx.FxHandler;
import io.github.term4.polyp.presets.vanilla18.Vanilla18;

/**
 * <b>Hypixel</b> preset - the vanilla 1.8 baseline ({@link Vanilla18}) with Hypixel's velocity/damage/knockback/explosion
 * deltas and the measured BedWars fireball (in this package).
 *
 * <p>Carries mechanics only - cross-version compat and fixes install separately.
 */
public final class Hypixel {

    private Hypixel() {}

    public static MechanicsProfile profile() {
        return Vanilla18.profile().toBuilder()
                .mutate(MechanicsKeys.ATTACK, attack -> AttackConfig.builder(attack)
                        .suppressSprintResetEcho(true).build())
                .set(MechanicsKeys.DAMAGE, Damage.config())
                .set(MechanicsKeys.KNOCKBACK, Knockback.melee())
                .set(MechanicsKeys.VELOCITY, Movement.velocity())
                .set(MechanicsKeys.EXPLOSION, Explosion.config())
                .set(MechanicsKeys.TNT, Tnt.config())
                .set(MechanicsKeys.PROJECTILES, Projectiles.config())
                // arrow hit-marker ding to the shooter; vanilla presets don't. No fireball launch sound on Hypixel.
                .set(MechanicsKeys.FX, fx())
                .build();
    }

    /** Hypixel's shared fx: the pearl landing is positional here, as it is in SkyWars and everywhere but BedWars. */
    public static FxRegistry fx() {
        return Fx.vanilla18()
                .register(Fx.ARROW_HIT_PLAYER, Fx.arrowHitMarker())
                .register(Fx.THROW_FIREBALL, FxHandler.NONE)
                .register(Fx.PEARL_TELEPORT, Fx.pearlTeleport());
    }

    /**
     * {@link #profile()} with the BedWars-only quirks: the game-wide pearl landing
     * ({@link Fx#pearlTeleportGameWide}), and fire that takes on contact rather than on the second felt hit.
     */
    public static MechanicsProfile bedwars() {
        return profile().toBuilder()
                .set(MechanicsKeys.FX, fx().register(Fx.PEARL_TELEPORT, Fx.pearlTeleportGameWide()))
                .mutate(MechanicsKeys.DAMAGE, damage -> DamageConfig.builder(damage)
                        .typeConfig(((BurningConfig) damage.typeConfig(InFireDamage.KEY)).toBuilder()
                                .igniteWarmupTicks(1).build())
                        .build())
                .build();
    }
}
