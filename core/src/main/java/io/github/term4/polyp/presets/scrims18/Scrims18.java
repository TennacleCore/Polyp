package io.github.term4.polyp.presets.scrims18;

import io.github.term4.polyp.mechanics.damage.DamageConfig;
import io.github.term4.polyp.presets.hypixel.Hypixel;
import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.MechanicsProfile;
import io.github.term4.polyp.mechanics.projectile.ProjectileConfig;

/**
 * <b>Scrims 1.8</b> preset - {@link Hypixel} with a 10-tick invul window and fully client-predicted
 * {@link Projectiles} (spawn + velocity, then never synchronizes). More scrims deltas land here as the preset is
 * fleshed out.
 */
public final class Scrims18 {

    private Scrims18() {}

    public static MechanicsProfile profile() {
        return Hypixel.profile().toBuilder()
                .mutate(MechanicsKeys.DAMAGE, damage -> DamageConfig.builder(damage).invulTicks(10).build())
                .set(MechanicsKeys.PROJECTILES, Projectiles.config())
                .build();
    }

    public static ProjectileConfig projectiles() { return Projectiles.config(); }
}
