package io.github.term4.polyp.presets.vanilla;

import io.github.term4.polyp.mechanics.mobs.DifficultyScaling;
import io.github.term4.polyp.mechanics.mobs.MeleeReach;
import io.github.term4.polyp.mechanics.mobs.MobKindConfig;
import io.github.term4.polyp.mechanics.mobs.MobSound;
import io.github.term4.polyp.mechanics.mobs.MobsConfig;
import io.github.term4.polyp.mechanics.mobs.PartDamage;
import io.github.term4.polyp.mechanics.mobs.path.Pathing;
import net.minestom.server.entity.EntityType;
import net.minestom.server.sound.SoundEvent;

/** 26.1's mobs: the typed walker, a swing that needs sight, the golem wading; the dragon still flies 1.8's circuit, not the End's phases. */
public final class Mobs {

    private Mobs() {}

    public static MobsConfig config() {
        MobsConfig base = io.github.term4.polyp.presets.vanilla18.Mobs.config();
        MobKindConfig golem = base.kinds.get(EntityType.IRON_GOLEM.key()).toBuilder()
                .height(2.7).eyeHeight(2.7 * 0.85)
                .knockbackResistance(1.0)
                .avoidsWater(false) // no WATER malus in IronGolem
                // IronGolem.doHurtTarget: attack 15 -> half plus rand(15)
                .attackDamage(ctx -> 15.0f / 2.0f + ctx.random().nextInt(15))
                .build();
        MobKindConfig dragon = base.kinds.get(EntityType.ENDER_DRAGON.key()).toBuilder()
                .partDamage(PartDamage.MODERN)
                .deathSound(MobSound.of(SoundEvent.ENTITY_ENDER_DRAGON_DEATH, 5.0f))
                .build();
        return base.toBuilder()
                .difficultyScaling(DifficultyScaling.MODERN)
                .goalTickRate(2)
                .defaults(base.defaults.toBuilder()
                        .pathing(Pathing.MODERN)
                        .reach(MeleeReach.MODERN)
                        .attackNeedsSight(true)
                        .build())
                .kind(EntityType.IRON_GOLEM, golem)
                .kind(EntityType.ENDER_DRAGON, dragon)
                .build();
    }
}
