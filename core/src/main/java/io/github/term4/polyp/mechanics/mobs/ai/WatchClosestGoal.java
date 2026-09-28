package io.github.term4.polyp.mechanics.mobs.ai;

import io.github.term4.polyp.mechanics.mobs.MobEntity;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;

import java.util.function.Predicate;

/** 1.8 EntityAIWatchClosest: now and then, look at the nearest player (or the attack target) for 2-4 seconds. */
public final class WatchClosestGoal extends Goal {

    private final MobEntity watcher;
    private final Predicate<Entity> watched;
    private final float maxDistance;
    private final float chance;
    private Entity closest;
    private int lookTime;

    public WatchClosestGoal(MobEntity watcher, Predicate<Entity> watched, float maxDistance) {
        this(watcher, watched, maxDistance, 0.02f);
    }

    public WatchClosestGoal(MobEntity watcher, Predicate<Entity> watched, float maxDistance, float chance) {
        this.watcher = watcher;
        this.watched = watched;
        this.maxDistance = maxDistance;
        this.chance = chance;
        mutexBits(2);
    }

    @Override
    public boolean shouldExecute() {
        if (watcher.random().nextFloat() >= chance) return false;
        if (watcher.attackTarget() != null) closest = watcher.attackTarget();
        closest = watcher.closestPlayer(maxDistance, watched::test);
        return closest != null;
    }

    @Override
    public boolean continueExecuting() {
        if (closest.isRemoved() || closest instanceof Player p && p.isDead()) return false;
        if (watcher.getPosition().distanceSquared(closest.getPosition()) > maxDistance * maxDistance) return false;
        return lookTime > 0;
    }

    @Override
    public void startExecuting() {
        lookTime = 40 + watcher.random().nextInt(40);
    }

    @Override
    public void resetTask() {
        closest = null;
    }

    @Override
    public void updateTask() {
        watcher.lookControl().lookAt(closest.getPosition().x(), closest.getPosition().y() + closest.getEyeHeight(),
                closest.getPosition().z(), 10.0f, watcher.verticalFaceSpeed());
        --lookTime;
    }
}
