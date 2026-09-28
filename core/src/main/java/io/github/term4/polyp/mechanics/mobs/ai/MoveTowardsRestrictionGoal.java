package io.github.term4.polyp.mechanics.mobs.ai;

import io.github.term4.polyp.mechanics.mobs.MobEntity;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Vec;

/** 1.8 EntityAIMoveTowardsRestriction: head back toward home once outside it. */
public final class MoveTowardsRestrictionGoal extends Goal {

    private final MobEntity entity;
    private final double speed;
    private double x, y, z;

    public MoveTowardsRestrictionGoal(MobEntity entity, double speed) {
        this.entity = entity;
        this.speed = speed;
        mutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        if (entity.withinHomeNow()) return false;
        BlockVec home = entity.home();
        Vec point = RandomPositions.findTowards(entity, 16, 7, new Vec(home.x(), home.y(), home.z()));
        if (point == null) return false;
        x = point.x();
        y = point.y();
        z = point.z();
        return true;
    }

    @Override
    public boolean continueExecuting() {
        return !entity.navigation().noPath();
    }

    @Override
    public void startExecuting() {
        entity.navigation().moveTo(x, y, z, speed);
    }
}
