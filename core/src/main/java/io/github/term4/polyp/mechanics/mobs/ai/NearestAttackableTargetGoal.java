package io.github.term4.polyp.mechanics.mobs.ai;

import io.github.term4.polyp.mechanics.mobs.MobEntity;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;

import java.util.Comparator;
import java.util.List;

/** 1.8 EntityAINearestAttackableTarget over the kind's {@code targetSelector}: the closest suitable living entity in range. */
public final class NearestAttackableTargetGoal extends TargetGoal {

    private LivingEntity found;

    public NearestAttackableTargetGoal(MobEntity owner, boolean checkSight, boolean nearbyOnly) {
        super(owner, checkSight, nearbyOnly);
        mutexBits(1);
    }

    /** On the kind's {@code targetSight} and {@code targetNearbyOnly}, read as they stand. */
    public NearestAttackableTargetGoal(MobEntity owner) {
        super(owner, owner::targetSight, owner::targetNearbyOnly);
        mutexBits(1);
    }

    private boolean selectable(LivingEntity candidate) {
        if (!owner.targetSelector().test(candidate)) return false;
        if (candidate instanceof Player p) {
            double range = targetDistance();
            if (p.isSneaking()) range *= 0.800000011920929;
            if (p.isInvisible() && !owner.seesInvisible()) {
                float armor = owner.mobs().armorVisibility(p);
                if (armor < 0.1f) armor = 0.1f;
                range *= 0.7f * armor;
            }
            if (p.getPosition().distance(owner.getPosition()) > range) return false;
        }
        return suitable(candidate, false);
    }

    @Override
    public boolean shouldExecute() {
        int chance = owner.targetChance();
        if (chance > 0 && owner.random().nextInt(chance) != 0) return false;
        double range = targetDistance();
        List<LivingEntity> candidates = owner.livingWithin(range, 4.0, this::selectable);
        if (candidates.isEmpty()) return false;
        candidates.sort(Comparator.comparingDouble(e -> owner.getPosition().distanceSquared(e.getPosition())));
        found = candidates.get(0);
        return true;
    }

    @Override
    public void startExecuting() {
        owner.attackTarget(found);
        super.startExecuting();
    }
}
