package io.github.term4.polyp.mechanics.mobs.ai;

import io.github.term4.polyp.mechanics.mobs.MobEntity;
import io.github.term4.polyp.mechanics.mobs.path.Path;
import io.github.term4.polyp.mechanics.mobs.path.PathPoint;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import org.jetbrains.annotations.Nullable;

/** 1.8 EntityAITarget: holds a target while it stays alive, in range, on another side and (with sight) seen within 60 ticks. */
public abstract class TargetGoal extends Goal {

    protected final MobEntity owner;
    protected final boolean checkSight;
    private final boolean nearbyOnly;
    private int searchStatus;
    private int searchDelay;
    private int unseenTicks;

    protected TargetGoal(MobEntity owner, boolean checkSight, boolean nearbyOnly) {
        this.owner = owner;
        this.checkSight = checkSight;
        this.nearbyOnly = nearbyOnly;
    }

    @Override
    public boolean continueExecuting() {
        LivingEntity target = owner.attackTarget();
        if (target == null || target.isDead() || target.isRemoved()) return false;
        if (owner.sides().same(owner, target)) return false;
        double range = targetDistance();
        if (owner.getPosition().distanceSquared(target.getPosition()) > range * range) return false;
        if (checkSight) {
            if (owner.senses().canSee(target)) unseenTicks = 0;
            else if (++unseenTicks > 60) return false;
        }
        return !(target instanceof Player p) || !invincible(p);
    }

    protected double targetDistance() {
        return owner.getAttributeValue(Attribute.FOLLOW_RANGE);
    }

    @Override
    public void startExecuting() {
        searchStatus = 0;
        searchDelay = 0;
        unseenTicks = 0;
    }

    @Override
    public void resetTask() {
        owner.attackTarget(null);
    }

    static boolean invincible(Player p) {
        return p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR || p.isInvulnerable();
    }

    public static boolean suitable(MobEntity attacker, @Nullable LivingEntity target, boolean includeInvincibles, boolean checkSight) {
        if (target == null || target == attacker) return false;
        if (target.isDead() || target.isRemoved()) return false;
        if (!attacker.canAttack(target)) return false;
        if (attacker.sides().same(attacker, target)) return false;
        if (target instanceof Player p && !includeInvincibles && invincible(p)) return false;
        return !checkSight || attacker.senses().canSee(target);
    }

    protected boolean suitable(@Nullable LivingEntity target, boolean includeInvincibles) {
        if (!suitable(owner, target, includeInvincibles, checkSight)) return false;
        if (!owner.withinHome(MobEntity.cell(target.getPosition()))) return false;
        if (nearbyOnly) {
            if (--searchDelay <= 0) searchStatus = 0;
            if (searchStatus == 0) searchStatus = easilyReached(target) ? 1 : 2;
            return searchStatus != 2;
        }
        return true;
    }

    private boolean easilyReached(LivingEntity target) {
        searchDelay = 10 + owner.random().nextInt(5);
        Path path = owner.navigation().pathTo(target);
        if (path == null) return false;
        PathPoint end = path.finalPoint();
        if (end == null) return false;
        int dx = end.x - target.getPosition().blockX();
        int dz = end.z - target.getPosition().blockZ();
        return dx * dx + dz * dz <= 2.25;
    }
}
