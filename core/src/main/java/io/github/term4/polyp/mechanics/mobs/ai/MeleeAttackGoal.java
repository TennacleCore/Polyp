package io.github.term4.polyp.mechanics.mobs.ai;

import io.github.term4.polyp.mechanics.mobs.MobEntity;
import io.github.term4.polyp.mechanics.mobs.path.Path;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.LivingEntity;

/** 1.8 EntityAIAttackOnCollide: chase the target along a path re-planned every 4-10 ticks, swing when in reach. */
public final class MeleeAttackGoal extends Goal {

    private final MobEntity attacker;
    private final double speed;
    private final boolean longMemory;
    private Path path;
    private int attackTick;
    private int delayCounter;
    private double targetX, targetY, targetZ;

    public MeleeAttackGoal(MobEntity attacker, double speed, boolean longMemory) {
        this.attacker = attacker;
        this.speed = speed;
        this.longMemory = longMemory;
        mutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        LivingEntity target = attacker.attackTarget();
        if (target == null || target.isDead() || target.isRemoved()) return false;
        path = attacker.navigation().pathTo(target);
        return path != null;
    }

    @Override
    public boolean continueExecuting() {
        LivingEntity target = attacker.attackTarget();
        if (target == null || target.isDead() || target.isRemoved()) return false;
        if (!longMemory) return !attacker.navigation().noPath();
        return attacker.withinHome(MobEntity.cell(target.getPosition()));
    }

    @Override
    public void startExecuting() {
        attacker.navigation().setPath(path, speed);
        delayCounter = 0;
    }

    @Override
    public void resetTask() {
        attacker.navigation().clear();
    }

    @Override
    public void updateTask() {
        LivingEntity target = attacker.attackTarget();
        if (target == null) return;
        attacker.lookControl().lookAt(target, 30.0f, 30.0f);
        Pos t = target.getPosition();
        --delayCounter;
        if ((longMemory || attacker.senses().canSee(target)) && delayCounter <= 0
                && (targetX == 0.0 && targetY == 0.0 && targetZ == 0.0
                || t.distanceSquared(targetX, targetY, targetZ) >= 1.0
                || attacker.random().nextFloat() < 0.05f)) {
            targetX = t.x();
            targetY = t.y();
            targetZ = t.z();
            delayCounter = 4 + attacker.random().nextInt(7);
            double d0 = attacker.getPosition().distanceSquared(t);
            if (d0 > 1024.0) delayCounter += 10;
            else if (d0 > 256.0) delayCounter += 5;
            if (!attacker.navigation().moveTo(target, speed)) delayCounter += 15;
        }
        attackTick = Math.max(attackTick - 1, 0);
        if (attackTick <= 0 && attacker.reach().test(attacker, target)
                && (!attacker.attackNeedsSight() || attacker.senses().canSee(target))) {
            attackTick = attacker.attackInterval();
            attacker.attack(target);
        }
    }
}
