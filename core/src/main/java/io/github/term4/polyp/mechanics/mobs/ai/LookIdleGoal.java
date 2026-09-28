package io.github.term4.polyp.mechanics.mobs.ai;

import io.github.term4.polyp.mechanics.mobs.MobEntity;
import net.minestom.server.coordinate.Pos;

/** 1.8 EntityAILookIdle: glance in a random direction for 1-2 seconds. */
public final class LookIdleGoal extends Goal {

    private final MobEntity entity;
    private double lookX, lookZ;
    private int idleTime;

    public LookIdleGoal(MobEntity entity) {
        this.entity = entity;
        mutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        return entity.random().nextFloat() < 0.02f;
    }

    @Override
    public boolean continueExecuting() {
        return idleTime >= 0;
    }

    @Override
    public void startExecuting() {
        double angle = Math.PI * 2.0 * entity.random().nextDouble();
        lookX = Math.cos(angle);
        lookZ = Math.sin(angle);
        idleTime = 20 + entity.random().nextInt(20);
    }

    @Override
    public void updateTask() {
        --idleTime;
        Pos p = entity.getPosition();
        entity.lookControl().lookAt(p.x() + lookX, p.y() + entity.getEyeHeight(), p.z() + lookZ, 10.0f, entity.verticalFaceSpeed());
    }
}
