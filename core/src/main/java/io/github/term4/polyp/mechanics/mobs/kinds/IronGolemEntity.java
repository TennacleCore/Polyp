package io.github.term4.polyp.mechanics.mobs.kinds;

import io.github.term4.polyp.fx.Fx;
import io.github.term4.polyp.fx.FxContext;
import io.github.term4.polyp.mechanics.mobs.MobEntity;
import io.github.term4.polyp.mechanics.mobs.MobSound;
import io.github.term4.polyp.mechanics.mobs.MobsSystem;
import io.github.term4.polyp.mechanics.mobs.ai.HurtByTargetGoal;
import io.github.term4.polyp.mechanics.mobs.ai.LookIdleGoal;
import io.github.term4.polyp.mechanics.mobs.ai.MeleeAttackGoal;
import io.github.term4.polyp.mechanics.mobs.ai.MoveTowardsRestrictionGoal;
import io.github.term4.polyp.mechanics.mobs.ai.MoveTowardsTargetGoal;
import io.github.term4.polyp.mechanics.mobs.ai.NearestAttackableTargetGoal;
import io.github.term4.polyp.mechanics.mobs.ai.WanderGoal;
import io.github.term4.polyp.mechanics.mobs.ai.WatchClosestGoal;
import io.github.term4.polyp.mechanics.mobs.path.Blocks;
import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.metadata.golem.IronGolemMeta;
import net.minestom.server.instance.block.Block;

/** 1.8 EntityIronGolem, less the village. */
public final class IronGolemEntity extends MobEntity {

    private int attackTimer;

    public IronGolemEntity(MobsSystem mobs) {
        super(mobs, EntityType.IRON_GOLEM);
    }

    public static IronGolemEntity spawn(MobsSystem mobs, MechanicsWorld world, Pos position, boolean playerCreated) {
        IronGolemEntity golem = new IronGolemEntity(mobs);
        golem.playerCreated(playerCreated);
        return mobs.spawn(golem, world, position);
    }

    @Override
    protected void goals() {
        goalSelector().add(1, new MeleeAttackGoal(this, 1.0, true));
        goalSelector().add(2, new MoveTowardsTargetGoal(this, 0.9, 32.0f));
        goalSelector().add(4, new MoveTowardsRestrictionGoal(this, 1.0));
        goalSelector().add(6, new WanderGoal(this, 0.6));
        goalSelector().add(7, new WatchClosestGoal(this, e -> e instanceof Player, 6.0f));
        goalSelector().add(8, new LookIdleGoal(this));
        targetGoals().add(2, new HurtByTargetGoal(this, false));
        targetGoals().add(3, new NearestAttackableTargetGoal(this, targetSight(), targetNearbyOnly()));
    }

    public boolean playerCreated() {
        return ((IronGolemMeta) getEntityMeta()).isPlayerCreated();
    }

    public void playerCreated(boolean playerCreated) {
        ((IronGolemMeta) getEntityMeta()).setPlayerCreated(playerCreated);
    }

    public int attackTimer() { return attackTimer; }

    @Override
    protected void livingTick() {
        super.livingTick();
        if (attackTimer > 0) --attackTimer;
        if (isDead()) return;
        if (motion.x() * motion.x() + motion.z() * motion.z() > 2.500000277905201E-7 && random().nextInt(5) == 0) {
            Pos p = getPosition();
            Block under = Blocks.at(world(), p.blockX(), (int) Math.floor(p.y() - 0.20000000298023224), p.blockZ());
            if (!under.air()) {
                double w = width();
                Pos at = new Pos(p.x() + (random().nextFloat() - 0.5) * w, p.y() + 0.1, p.z() + (random().nextFloat() - 0.5) * w);
                Fx.play(services(), Fx.MOB_WALK_DUST, FxContext.at(world(), at, this).withDetail(under));
            }
        }
    }

    // a hostile mob bumping the golem sometimes becomes its target
    @Override
    protected void pushNearby() {
        super.pushNearby();
        Pos pos = getPosition();
        for (Entity e : world().nearbyEntities(pos, width() + 2.0)) {
            if (e instanceof MobEntity m && m != this && m.hostile() && random().nextInt(20) == 0
                    && getBoundingBox().expand(0.4, 0, 0.4).intersectEntity(pos, e) && canAttack(m)) {
                attackTarget(m);
            }
        }
    }

    /** 1.8 attackEntityAsMob: the arm swing to everyone, the roll straight through, a lift on any landed hit. */
    @Override
    public boolean attack(LivingEntity target) {
        attackTimer = 10;
        triggerStatus((byte) 4);
        boolean landed = hurt(target, knob(kind().attackDamage, 0.0f), false, knob(kind().attackLift, 0.0));
        MobSound swing = knob(kind().attackSound, null);
        if (swing != null) Fx.play(services(), Fx.MOB_ATTACK, FxContext.of(this).withDetail(swing));
        return landed;
    }
}
