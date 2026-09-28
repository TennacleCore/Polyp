package io.github.term4.polyp.mechanics.mobs;

import net.minestom.server.collision.BoundingBox;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.LivingEntity;

/** When a mob's melee swing lands. */
@FunctionalInterface
public interface MeleeReach {

    boolean test(MobEntity mob, LivingEntity target);

    /** 1.8 EntityAIAttackOnCollide: centre to the target's feet within (2w)^2 + the target's width. */
    MeleeReach LEGACY = (mob, target) -> {
        double w = mob.width();
        double reach = w * 2.0 * w * 2.0 + target.getBoundingBox().width();
        Pos p = mob.getPosition(), q = target.getPosition();
        double dx = q.x() - p.x(), dy = q.y() - p.y(), dz = q.z() - p.z();
        return dx * dx + dy * dy + dz * dz <= reach;
    };

    /** 26.1 Mob.isWithinMeleeAttackRange: the mob's box grown sqrt(2.04) - 0.6 sideways meets the target's. */
    MeleeReach MODERN = (mob, target) -> {
        double grow = (Math.sqrt(2.04) - 0.6) * 2.0;
        BoundingBox box = mob.getBoundingBox().expand(grow, 0, grow);
        return box.intersectEntity(mob.getPosition(), target);
    };
}
