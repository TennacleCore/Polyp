package io.github.term4.polyp.presets.scrims18;

import io.github.term4.polyp.presets.hypixel.Hypixel;
import io.github.term4.polyp.mechanics.attack.AttackConfig;
import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.MechanicsProfile;
import io.github.term4.polyp.mechanics.projectile.ProjectileConfig;

/**
 * <b>Scrims 1.8</b> preset - {@link Hypixel} with a 10-tick invul window, a reach gate wider still, motY on
 * 1.8's per-packet clock ({@link Movement}), and fully client-predicted {@link Projectiles} (spawn + velocity,
 * then never synchronizes).
 */
public final class Scrims18 {

    private Scrims18() {}

    public static MechanicsProfile profile() {
        return Hypixel.profile().toBuilder()
                .mutate(MechanicsKeys.ATTACK, attack -> AttackConfig.builder(attack)
                        .reachPadding(AttackConfig.SCRIMS_REACH_PADDING).build())
                .mutate(MechanicsKeys.DAMAGE, Damage::config)
                .set(MechanicsKeys.VELOCITY, Movement.velocity())
                .set(MechanicsKeys.PROJECTILES, Projectiles.config())
                .build();
    }

    public static ProjectileConfig projectiles() { return Projectiles.config(); }
}
