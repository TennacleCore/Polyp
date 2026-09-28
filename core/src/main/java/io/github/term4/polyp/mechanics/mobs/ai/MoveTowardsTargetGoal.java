package io.github.term4.polyp.mechanics.mobs.ai;

import io.github.term4.polyp.mechanics.mobs.MobEntity;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.LivingEntity;

/** 1.8 EntityAIMoveTowardsTarget: drift toward a target within {@code maxDistance} by random points in its direction. */
public final class MoveTowardsTargetGoal extends Goal {

    private final MobEntity entity;
    private final double speed;
    private final float maxDistance;
    private LivingEntity target;
    private double x, y, z;

    public MoveTowardsTargetGoal(MobEntity entity, double speed, float maxDistance) {
        this.entity = entity;
        this.speed = speed;
        this.maxDistance = maxDistance;
        mutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        target = entity.attackTarget();
        if (target == null) return false;
        if (target.getPosition().distanceSquared(entity.getPosition()) > maxDistance * maxDistance) return false;
        Vec point = RandomPositions.findTowards(entity, 16, 7, target.getPosition());
        if (point == null) return false;
        x = point.x();
        y = point.y();
        z = point.z();
        return true;
    }

    @Override
    public boolean continueExecuting() {
        return !entity.navigation().noPath() && !target.isDead() && !target.isRemoved()
                && target.getPosition().distanceSquared(entity.getPosition()) < maxDistance * maxDistance;
    }

    @Override
    public void resetTask() {
        target = null;
    }

    @Override
    public void startExecuting() {
        entity.navigation().moveTo(x, y, z, speed);
    }
}
