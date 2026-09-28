package io.github.term4.polyp.mechanics.mobs.ai;

import io.github.term4.polyp.mechanics.mobs.MobEntity;
import net.minestom.server.coordinate.Vec;

/** 1.8 EntityAIWander: a random point within 10 x 7 blocks, one chance in {@code chance} per check. */
public class WanderGoal extends Goal {

    protected final MobEntity entity;
    private final double speed;
    private int chance;
    private boolean mustUpdate;
    private double x, y, z;

    public WanderGoal(MobEntity entity, double speed) {
        this(entity, speed, 120);
    }

    public WanderGoal(MobEntity entity, double speed, int chance) {
        this.entity = entity;
        this.speed = speed;
        this.chance = chance;
        mutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        if (!mustUpdate) {
            if (entity.age() >= 100) return false;
            if (entity.random().nextInt(entity.wanderChance(chance)) != 0) return false;
        }
        Vec point = RandomPositions.find(entity, 10, 7);
        if (point == null) return false;
        x = point.x();
        y = point.y();
        z = point.z();
        mustUpdate = false;
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

    public void makeUpdate() {
        mustUpdate = true;
    }

    public void chance(int chance) {
        this.chance = chance;
    }
}
