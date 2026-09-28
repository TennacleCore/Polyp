package io.github.term4.polyp.mechanics.mobs.ai;

import io.github.term4.polyp.mechanics.mobs.MobEntity;

/** 1.8 EntityAISwimming: paddle up while in liquid. */
public final class SwimGoal extends Goal {

    private final MobEntity entity;

    public SwimGoal(MobEntity entity) {
        this.entity = entity;
        mutexBits(4);
        entity.navigation().canSwim(true);
    }

    @Override
    public boolean shouldExecute() {
        return entity.inWater() || entity.inLava();
    }

    @Override
    public void updateTask() {
        if (entity.random().nextFloat() < 0.8f) entity.jumpControl().jump();
    }
}
