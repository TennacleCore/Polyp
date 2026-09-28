package io.github.term4.polyp.mechanics.mobs.ai;

import io.github.term4.polyp.mechanics.mobs.MobEntity;
import net.minestom.server.entity.LivingEntity;

/** 1.8 EntityAIHurtByTarget: turn on whoever hit last; {@code callsForHelp} alerts idle mobs of the same kind in range. */
public final class HurtByTargetGoal extends TargetGoal {

    private final boolean callsForHelp;
    private long revengeTimerOld;

    public HurtByTargetGoal(MobEntity owner, boolean callsForHelp) {
        super(owner, false, false);
        this.callsForHelp = callsForHelp;
        mutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        return owner.revengeTimer() != revengeTimerOld && suitable(owner.revengeTarget(), false);
    }

    @Override
    public void startExecuting() {
        LivingEntity target = owner.revengeTarget();
        owner.attackTarget(target);
        revengeTimerOld = owner.revengeTimer();
        if (callsForHelp && target != null) {
            double range = targetDistance();
            for (LivingEntity other : owner.livingWithin(range, 10.0, e -> e instanceof MobEntity)) {
                MobEntity mob = (MobEntity) other;
                if (mob == owner || mob.getEntityType() != owner.getEntityType()) continue;
                if (mob.attackTarget() == null && !mob.sides().same(mob, target)) mob.attackTarget(target);
            }
        }
        super.startExecuting();
    }
}
