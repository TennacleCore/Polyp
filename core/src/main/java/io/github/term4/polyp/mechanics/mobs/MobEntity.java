package io.github.term4.polyp.mechanics.mobs;

import io.github.term4.polyp.Services;
import io.github.term4.polyp.config.FieldValue;
import io.github.term4.polyp.fx.Fx;
import io.github.term4.polyp.fx.FxContext;
import io.github.term4.polyp.mechanics.damage.DamageSnapshot;
import io.github.term4.polyp.mechanics.damage.DamageSystem;
import io.github.term4.polyp.mechanics.damage.DamageSystem.DamageOutcome;
import io.github.term4.polyp.mechanics.damage.types.mob.MobDamage;
import io.github.term4.polyp.mechanics.knockback.KnockbackSnapshot;
import io.github.term4.polyp.mechanics.knockback.KnockbackSystem;
import io.github.term4.polyp.mechanics.mobs.MobsConfigResolver.MobContext;
import io.github.term4.polyp.mechanics.mobs.ai.BodyControl;
import io.github.term4.polyp.mechanics.mobs.ai.GoalSelector;
import io.github.term4.polyp.mechanics.mobs.ai.JumpControl;
import io.github.term4.polyp.mechanics.mobs.ai.LookControl;
import io.github.term4.polyp.mechanics.mobs.ai.MoveControl;
import io.github.term4.polyp.mechanics.mobs.ai.Senses;
import io.github.term4.polyp.mechanics.mobs.path.Blocks;
import io.github.term4.polyp.mechanics.mobs.path.Navigation;
import io.github.term4.polyp.world.ExternallyTickable;
import io.github.term4.polyp.world.MechanicsWorld;
import io.github.term4.polyp.world.WorldPolicy;
import net.kyori.adventure.key.Key;
import net.minestom.server.ServerFlag;
import net.minestom.server.collision.Aerodynamics;
import net.minestom.server.collision.BoundingBox;
import net.minestom.server.collision.PhysicsResult;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.entity.attribute.Attribute;
import net.minestom.server.instance.Chunk;
import net.minestom.server.instance.block.Block;
import net.minestom.server.network.packet.server.play.EntityHeadLookPacket;
import net.minestom.server.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

/**
 * A mob running 1.8's EntityLiving loop on its own motion: goals pick, controls steer, {@link #travel} moves it
 * against its own world. Nothing here reads a version; the knobs in {@link MobKindConfig} do.
 */
public abstract class MobEntity extends LivingEntity implements ExternallyTickable {

    protected static final double TPS = ServerFlag.SERVER_TICKS_PER_SECOND;
    private static final Aerodynamics COLLISION_ONLY = new Aerodynamics(0, 1, 1);
    private static final Predicate<LivingEntity> ANY = e -> true;

    private final MobsSystem mobs;
    private final Random random = new Random();
    private final GoalSelector goals;
    private final GoalSelector targetGoals;
    private final Navigation navigation;
    private final MoveControl moveControl;
    private final JumpControl jumpControl;
    private final LookControl lookControl;
    private final BodyControl bodyControl;
    private final Senses senses;
    private MobKindConfig kind = MobKindConfig.builder().build();
    private Sides sides = Sides.SCOREBOARD;
    private MechanicsWorld world;

    protected Vec motion = Vec.ZERO; // b/t
    private float yaw, pitch, headYaw, bodyYaw;
    private float moveForward, moveStrafing, aiMoveSpeed, randomYawVelocity;
    protected float jumpMovementFactor = 0.02f;
    private boolean jumping;
    private int jumpTicks;
    protected boolean collidedHorizontally, collidedVertically;
    private @Nullable PhysicsResult lastPhysics;
    private Vec lastSentMotion = Vec.ZERO;
    private float lastSentHeadYaw, lastYaw, lastPitch;
    private @Nullable LivingEntity attackTarget;
    private @Nullable LivingEntity revengeTarget;
    private long revengeTimer;
    private int livingSoundTime;
    protected int hurtTime;
    protected int deathTicks;
    private BlockVec home = BlockVec.ZERO;
    private float homeDistance = -1.0f;
    private float walkedOnStep;
    private int nextStep;

    protected MobEntity(MobsSystem mobs, EntityType type) {
        super(type);
        this.mobs = mobs;
        this.goals = new GoalSelector(this::goalTickRate);
        this.targetGoals = new GoalSelector(this::goalTickRate);
        this.navigation = new Navigation(this);
        this.moveControl = new MoveControl(this);
        this.jumpControl = new JumpControl(this);
        this.lookControl = new LookControl(this);
        this.bodyControl = new BodyControl(this);
        this.senses = new Senses(this);
        setCanPickupItem(false);
    }

    /** Resolves the kind's knobs for {@code world} and applies the attribute bases; the goals are the kind's own. */
    void configure(MechanicsWorld world) {
        this.world = world;
        MobContext ctx = new MobContext(this, world, services());
        MobsConfig cfg = mobs.configFor(world).withOverlay(ctx);
        MobKindConfig k = cfg.kind(getEntityType());
        this.kind = k != null ? k.withOverlay(ctx) : MobKindConfig.builder().build();
        this.sides = FieldValue.resolve(cfg.sides, ctx, Sides.SCOREBOARD);
        double w = knob(kind.width, getEntityType().width()), h = knob(kind.height, getEntityType().height());
        setBoundingBox(w, h, w);
        Double maxHealth = knob(kind.maxHealth, null);
        if (maxHealth != null) {
            getAttribute(Attribute.MAX_HEALTH).setBaseValue(maxHealth);
            setHealth(maxHealth.floatValue());
        }
        Double speed = knob(kind.speed, null);
        if (speed != null) getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(speed);
        getAttribute(Attribute.FOLLOW_RANGE).setBaseValue(knob(kind.followRange, 16.0));
        getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(knob(kind.knockbackResistance, 0.0));
        Float attack = knob(kind.attackDamage, null);
        if (attack != null) getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(attack);
        navigation.avoidsWater(knob(kind.avoidsWater, false));
        goals();
    }

    /** Registers the kind's goals, 1.8's lists. */
    protected abstract void goals();

    protected <T> T knob(@Nullable FieldValue<MobContext, T> knob, T def) {
        return FieldValue.resolve(knob, ctx(), def);
    }

    public MobContext ctx() {
        return new MobContext(this, world(), services());
    }

    public MobsSystem mobs() { return mobs; }
    public Services services() { return mobs.services(); }
    public MobKindConfig kind() { return kind; }
    public Random random() { return random; }
    public Sides sides() { return sides; }
    public void sides(Sides sides) { this.sides = sides; }

    public MechanicsWorld world() {
        MechanicsWorld w = world;
        return w != null ? w : MechanicsWorld.of(this);
    }

    private int goalTickRate() {
        return FieldValue.resolve(mobs.configFor(world()).goalTickRate, ctx(), 3);
    }

    public GoalSelector goalSelector() { return goals; }
    public GoalSelector targetGoals() { return targetGoals; }
    public Navigation navigation() { return navigation; }
    public MoveControl moveControl() { return moveControl; }
    public JumpControl jumpControl() { return jumpControl; }
    public LookControl lookControl() { return lookControl; }
    public Senses senses() { return senses; }

    public double width() { return getBoundingBox().width(); }
    public double height() { return getBoundingBox().height(); }

    @Override
    public double getEyeHeight() {
        return knob(kind.eyeHeight, super.getEyeHeight());
    }

    public float yaw() { return yaw; }
    public void yaw(float yaw) { this.yaw = yaw; }
    public float pitch() { return pitch; }
    public void pitch(float pitch) { this.pitch = pitch; }
    public float headYaw() { return headYaw; }
    public void headYaw(float headYaw) { this.headYaw = headYaw; }
    public float bodyYaw() { return bodyYaw; }
    public void bodyYaw(float bodyYaw) { this.bodyYaw = bodyYaw; }

    public void moveForward(float v) { moveForward = v; }
    public void moveStrafing(float v) { moveStrafing = v; }

    /** 1.8 setAIMoveSpeed: the land factor and the forward input together. */
    public void aiMoveSpeed(float speed) {
        aiMoveSpeed = speed;
        moveForward = speed;
    }

    public float aiMoveSpeed() { return aiMoveSpeed; }
    public void jumping(boolean jumping) { this.jumping = jumping; }
    public Vec motion() { return motion; }

    public void motion(Vec motion) {
        this.motion = motion;
        this.velocity = motion.mul(TPS);
    }

    public void addMotion(Vec delta) { motion(motion.add(delta)); }

    public @Nullable LivingEntity attackTarget() { return attackTarget; }
    public void attackTarget(@Nullable LivingEntity target) { attackTarget = target; }
    public @Nullable LivingEntity revengeTarget() { return revengeTarget; }
    public long revengeTimer() { return revengeTimer; }

    /** Persistent, like a named mob: the despawn clock never runs. */
    public int age() { return 0; }

    public int verticalFaceSpeed() { return 40; }

    public boolean hostile() { return knob(kind.hostile, false); }
    public Predicate<LivingEntity> targetSelector() { return knob(kind.targetSelector, ANY); }
    public boolean canAttack(LivingEntity target) { return knob(kind.attackable, ANY).test(target); }
    public int targetChance() { return knob(kind.targetChance, 10); }
    public boolean targetSight() { return knob(kind.targetSight, true); }
    public boolean targetNearbyOnly() { return knob(kind.targetNearbyOnly, false); }
    public boolean seesInvisible() { return knob(kind.seesInvisible, false); }
    public int attackInterval() { return knob(kind.attackInterval, 20); }
    public MeleeReach reach() { return knob(kind.reach, MeleeReach.LEGACY); }
    public double stepHeight() { return knob(kind.stepHeight, 0.6); }
    public boolean takesFallDamage() { return knob(kind.fallDamage, true); }

    /** 1.8 EntityCreature.getBlockPathWeight: a wander candidate's appeal. */
    public float pathWeight(BlockVec cell) { return 0.0f; }

    public void home(BlockVec at, int distance) {
        home = at;
        homeDistance = distance;
    }

    public void detachHome() { homeDistance = -1.0f; }
    public boolean hasHome() { return homeDistance != -1.0f; }
    public BlockVec home() { return home; }
    public float homeDistance() { return homeDistance; }

    public boolean withinHome(Point at) {
        return homeDistance == -1.0f || home.distanceSquared(at) < homeDistance * homeDistance;
    }

    public boolean withinHomeNow() { return withinHome(cell(getPosition())); }

    public static BlockVec cell(Point p) { return new BlockVec(p.blockX(), p.blockY(), p.blockZ()); }

    /** 1.8 EntityLiving.getMaxFallHeight. */
    public int maxFallHeight() {
        if (attackTarget == null) return 3;
        int i = (int) (getHealth() - getAttributeValue(Attribute.MAX_HEALTH) * 0.33f);
        i -= (3 - mobs.difficulty(ctx()).id()) * 4;
        if (i < 0) i = 0;
        return i + 3;
    }

    /** 1.8 getEntitiesWithinAABB over the box grown {@code xz} and {@code y} per side. */
    public List<LivingEntity> livingWithin(double xz, double y, Predicate<LivingEntity> filter) {
        Pos pos = getPosition();
        BoundingBox box = getBoundingBox().expand(xz * 2, y * 2, xz * 2);
        double reach = Math.max(xz, y) + Math.max(width(), height()) + 4.0;
        List<LivingEntity> out = new ArrayList<>();
        for (Entity e : world().nearbyEntities(pos, reach)) {
            if (e == this || !(e instanceof LivingEntity le) || le.isDead() || le.isRemoved()) continue;
            if (e instanceof Player p && p.getGameMode() == GameMode.SPECTATOR) continue;
            if (!WorldPolicy.canAffect(this, e)) continue;
            if (!box.intersectEntity(pos, e)) continue;
            if (filter.test(le)) out.add(le);
        }
        return out;
    }

    /** The nearest non-spectator player within {@code distance}, or null. */
    public @Nullable Player closestPlayer(double distance, Predicate<Entity> filter) {
        Player best = null;
        double bestSq = distance * distance;
        for (Player p : world().players()) {
            if (p.getGameMode() == GameMode.SPECTATOR || p.isDead() || !filter.test(p)) continue;
            if (!WorldPolicy.canAffect(this, p)) continue;
            double d = p.getPosition().distanceSquared(getPosition());
            if (d < bestSq) {
                bestSq = d;
                best = p;
            }
        }
        return best;
    }

    public boolean inWater() {
        return liquidAt(Blocks::water);
    }

    public boolean inLava() {
        return liquidAt(Blocks::lava);
    }

    // 1.8 handleMaterialAcceleration: the box shrunk 0.4 off the top, so a head in water does not count
    private boolean liquidAt(Predicate<Block> liquid) {
        Pos pos = getPosition();
        BoundingBox box = getBoundingBox();
        double minX = pos.x() + box.minX() + 0.001, maxX = pos.x() + box.maxX() - 0.001;
        double minY = pos.y() + box.minY() + 0.001, maxY = pos.y() + box.maxY() - 0.4 - 0.001;
        double minZ = pos.z() + box.minZ() + 0.001, maxZ = pos.z() + box.maxZ() - 0.001;
        if (maxY < minY) return false;
        for (int x = (int) Math.floor(minX); x <= (int) Math.floor(maxX); x++) {
            for (int y = (int) Math.floor(minY); y <= (int) Math.floor(maxY); y++) {
                for (int z = (int) Math.floor(minZ); z <= (int) Math.floor(maxZ); z++) {
                    Block b = Blocks.at(world(), x, y, z);
                    if (liquid.test(b) && maxY >= y + Blocks.liquidTop(b)) return true;
                }
            }
        }
        return false;
    }

    private boolean onLadder() {
        return Blocks.climbable(Blocks.at(world(), getPosition()));
    }

    private float frictionBelow() {
        Pos pos = getPosition();
        return Blocks.at(world(), pos.blockX(), (int) Math.floor(pos.y()) - 1, pos.blockZ()).friction();
    }

    // bail on a foreign clock, or state advances on both and drifts
    @Override
    public void tick(long time) {
        if (!MechanicsWorld.ownsCurrentTick(this)) return;
        super.tick(time);
    }

    // @ApiStatus.Internal override: an externally ticked entity in the global dispatcher double-ticks
    @Override
    protected void refreshCurrentChunk(@NotNull Chunk chunk) {
        if (MechanicsWorld.externallyTicked(this)) {
            currentChunk = chunk;
            return;
        }
        super.refreshCurrentChunk(chunk);
    }

    // the mob moves itself in travel(), against its own world
    @Override
    protected void movementTick() {}

    @Override
    public void update(long time) {
        super.update(time);
        livingTick();
    }

    /** 1.8 EntityLivingBase.onUpdate for a mob. */
    protected void livingTick() {
        if (hurtTime > 0) --hurtTime;
        if (isDead()) {
            if (++deathTicks >= 20) {
                remove();
                return;
            }
        } else {
            ambientTick();
        }
        if (jumpTicks > 0) --jumpTicks;
        motion = new Vec(small(motion.x()), small(motion.y()), small(motion.z()));
        if (isDead()) {
            jumping = false;
            moveStrafing = 0.0f;
            moveForward = 0.0f;
            randomYawVelocity = 0.0f;
        } else {
            actionState();
        }
        if (jumping) {
            if (inWater() || inLava()) {
                motion = motion.add(0, 0.03999999910593033, 0);
            } else if (isOnGround() && jumpTicks == 0) {
                jump();
                jumpTicks = 10;
            }
        } else {
            jumpTicks = 0;
        }
        moveStrafing *= 0.98f;
        moveForward *= 0.98f;
        randomYawVelocity *= 0.9f;
        travel(moveStrafing, moveForward);
        pushNearby();
        bodyControl.tick();
        sync();
    }

    private static double small(double v) {
        return Math.abs(v) < 0.005 ? 0.0 : v;
    }

    private void ambientTick() {
        if (random.nextInt(1000) < livingSoundTime++) {
            livingSoundTime = -80;
            MobSound ambient = knob(kind.ambientSound, null);
            if (ambient != null) play(Fx.MOB_AMBIENT, ambient);
        }
    }

    /** 1.8 EntityLiving.updateEntityActionState. */
    protected void actionState() {
        senses.clear();
        targetGoals.tick();
        goals.tick();
        navigation.tick();
        aiTick();
        moveControl.tick();
        lookControl.tick();
        jumpControl.tick();
    }

    /** 1.8 updateAITasks. */
    protected void aiTick() {}

    protected void jump() {
        double up = 0.41999998688697815;
        int boost = getEffectLevel(PotionEffect.JUMP_BOOST);
        if (boost >= 0) up += (boost + 1) * 0.1f;
        motion = motion.withY(up);
    }

    /** 1.8 moveEntityWithHeading. */
    protected void travel(float strafe, float forward) {
        Pos before = getPosition();
        if (inWater()) {
            moveFlying(strafe, forward, 0.02f);
            move(motion);
            motion = new Vec(motion.x() * 0.800000011920929, motion.y() * 0.800000011920929 - 0.02, motion.z() * 0.800000011920929);
            if (collidedHorizontally && freeAbove(before, 0.6)) motion = motion.withY(0.30000001192092896);
        } else if (inLava()) {
            moveFlying(strafe, forward, 0.02f);
            move(motion);
            motion = new Vec(motion.x() * 0.5, motion.y() * 0.5 - 0.02, motion.z() * 0.5);
            if (collidedHorizontally && freeAbove(before, 0.6)) motion = motion.withY(0.30000001192092896);
        } else {
            boolean ground = isOnGround();
            float f4 = ground ? frictionBelow() * 0.91f : 0.91f;
            float f = 0.16277136f / (f4 * f4 * f4);
            float f5 = ground ? aiMoveSpeed * f : jumpMovementFactor;
            moveFlying(strafe, forward, f5);
            if (onLadder()) {
                double x = Math.clamp(motion.x(), -0.15, 0.15), z = Math.clamp(motion.z(), -0.15, 0.15);
                double y = Math.max(motion.y(), -0.15);
                motion = new Vec(x, y, z);
            }
            move(motion);
            if (collidedHorizontally && onLadder()) motion = motion.withY(0.2);
            double y = (motion.y() - 0.08) * 0.9800000190734863;
            motion = new Vec(motion.x() * f4, y, motion.z() * f4);
        }
    }

    // 1.8 isOffsetPositionInLiquid: the box shifted by the motion plus the climb is clear of blocks and liquid
    private boolean freeAbove(Pos before, double climb) {
        Pos pos = getPosition();
        BoundingBox box = getBoundingBox();
        double ox = motion.x(), oy = motion.y() + climb - pos.y() + before.y(), oz = motion.z();
        double minX = pos.x() + box.minX() + ox, maxX = pos.x() + box.maxX() + ox;
        double minY = pos.y() + box.minY() + oy, maxY = pos.y() + box.maxY() + oy;
        double minZ = pos.z() + box.minZ() + oz, maxZ = pos.z() + box.maxZ() + oz;
        for (int x = (int) Math.floor(minX); x <= (int) Math.floor(maxX); x++) {
            for (int y = (int) Math.floor(minY); y <= (int) Math.floor(maxY); y++) {
                for (int z = (int) Math.floor(minZ); z <= (int) Math.floor(maxZ); z++) {
                    Block b = Blocks.at(world(), x, y, z);
                    if (b.liquid() || !Blocks.passable(b)) return false;
                }
            }
        }
        return true;
    }

    protected void moveFlying(float strafe, float forward, float friction) {
        float f = strafe * strafe + forward * forward;
        if (f < 1.0E-4f) return;
        f = (float) Math.sqrt(f);
        if (f < 1.0f) f = 1.0f;
        f = friction / f;
        strafe *= f;
        forward *= f;
        float sin = (float) Math.sin(yaw * Math.PI / 180.0f);
        float cos = (float) Math.cos(yaw * Math.PI / 180.0f);
        motion = motion.add(strafe * cos - forward * sin, 0, forward * cos + strafe * sin);
    }

    /** 1.8 Entity.moveEntity: a collision step with the stepHeight climb, then the blocked axes zeroed. */
    protected void move(Vec delta) {
        MechanicsWorld world = world();
        Pos from = getPosition();
        boolean groundBefore = isOnGround();
        PhysicsResult r = sweep(world, from, delta, lastPhysics);
        boolean stepped = false;
        boolean blocked = r.collisionX() || r.collisionZ();
        double step = stepHeight();
        if (step > 0 && blocked && (groundBefore || delta.y() < 0 && r.collisionY())) {
            PhysicsResult up = sweep(world, from, new Vec(0, step, 0), null);
            double risen = up.newPosition().y() - from.y();
            PhysicsResult across = sweep(world, up.newPosition(), new Vec(delta.x(), 0, delta.z()), null);
            PhysicsResult down = sweep(world, across.newPosition(), new Vec(0, -risen, 0), null);
            if (horizontalSq(down.newPosition(), from) > horizontalSq(r.newPosition(), from)) {
                r = new PhysicsResult(down.newPosition(), delta, down.isOnGround() || down.collisionY(),
                        across.collisionX(), down.collisionY(), across.collisionZ(), delta, null, null, null,
                        across.hasCollision() || down.hasCollision(), 1.0);
                stepped = true;
            }
        }
        if (!world.isChunkLoaded(r.newPosition())) return;
        lastPhysics = stepped ? null : r;
        collidedHorizontally = r.collisionX() || r.collisionZ();
        collidedVertically = r.collisionY();
        onGround = r.isOnGround();
        if (r.collisionX()) motion = motion.withX(0);
        if (r.collisionY()) motion = motion.withY(0);
        if (r.collisionZ()) motion = motion.withZ(0);
        refreshPosition(r.newPosition().withView(yaw, pitch), false, true);
        steps(from, r.newPosition());
    }

    private PhysicsResult sweep(MechanicsWorld world, Pos from, Vec delta, @Nullable PhysicsResult last) {
        return world.simulateMovement(from, delta, getBoundingBox(), COLLISION_ONLY, true, true, isOnGround(), last);
    }

    private static double horizontalSq(Pos to, Pos from) {
        double dx = to.x() - from.x(), dz = to.z() - from.z();
        return dx * dx + dz * dz;
    }

    // 1.8 step sounds: every 0.6-scaled block walked, off the block under the feet
    private void steps(Pos from, Pos to) {
        MobSound step = knob(kind.stepSound, null);
        if (step == null) return;
        double dx = to.x() - from.x(), dz = to.z() - from.z();
        walkedOnStep += (float) (Math.sqrt(dx * dx + dz * dz) * 0.6);
        if (walkedOnStep <= nextStep) return;
        Block below = Blocks.at(world(), to.blockX(), (int) Math.floor(to.y() - 0.20000000298023224), to.blockZ());
        if (below.air()) return;
        nextStep = (int) walkedOnStep + 1;
        if (inWater()) return;
        play(Fx.MOB_STEP, step);
    }

    /** 1.8 collideWithNearbyEntities; a player's client shoves itself. */
    protected void pushNearby() {
        Pos pos = getPosition();
        BoundingBox box = getBoundingBox().expand(0.4, 0, 0.4);
        for (Entity e : world().nearbyEntities(pos, width() + 2.0)) {
            if (e == this || !pushable(e) || !box.intersectEntity(pos, e)) continue;
            double dx = e.getPosition().x() - pos.x();
            double dz = e.getPosition().z() - pos.z();
            double d2 = Math.max(Math.abs(dx), Math.abs(dz));
            if (d2 < 0.009999999776482582) continue;
            d2 = Math.sqrt(d2);
            dx /= d2;
            dz /= d2;
            double d3 = Math.min(1.0, 1.0 / d2);
            dx *= d3 * 0.05000000074505806;
            dz *= d3 * 0.05000000074505806;
            addMotion(new Vec(-dx, 0, -dz));
            if (e instanceof MobEntity other) other.addMotion(new Vec(dx, 0, dz));
        }
    }

    protected boolean pushable(Entity e) {
        if (e instanceof MobEntity m) return m.pushes() && !m.isDead();
        if (e instanceof Player p) return p.getGameMode() != GameMode.SPECTATOR && !p.isDead();
        return e instanceof LivingEntity le && !le.isDead();
    }

    /** 1.8 noClip entities shove nothing and are shoved by nothing. */
    protected boolean pushes() { return true; }

    protected void sync() {
        boolean viewChanged = yaw != lastYaw || pitch != lastPitch;
        if (headYaw != lastSentHeadYaw || viewChanged && headYaw != yaw) {
            sendPacketToViewers(new EntityHeadLookPacket(getEntityId(), headYaw));
            lastSentHeadYaw = headYaw;
        }
        lastYaw = yaw;
        lastPitch = pitch;
        this.velocity = motion.mul(TPS);
        Vec d = motion.sub(lastSentMotion);
        double d3 = d.lengthSquared();
        if (d3 > 4.0E-4 || d3 > 0.0 && motion.isZero()) {
            sendPacketToViewers(getVelocityPacket());
            lastSentMotion = motion;
        }
    }

    // a delivered knockback lands on the 1.8 motion, not only Minestom's velocity
    @Override
    public void setVelocity(@NotNull Vec velocity) {
        super.setVelocity(velocity);
        this.motion = getVelocity().div(TPS);
        this.lastSentMotion = motion;
    }

    /** The kind's swing at {@code target}: damage through {@link MobDamage}, then vanilla knockback on a fresh hit. */
    public boolean attack(LivingEntity target) {
        return hurt(target, knob(kind.attackDamage, 0.0f), true, knob(kind.attackLift, 0.0));
    }

    protected boolean hurt(LivingEntity target, float amount, boolean attributes, double lift) {
        Services s = services();
        DamageSystem damage = s.damage();
        if (damage == null || !WorldPolicy.canAffect(this, target)) return false;
        DamageSnapshot snap = MobDamage.INSTANCE.snapshot(this, target, amount, attributes, s);
        DamageOutcome outcome = damage.apply(snap);
        boolean landed = outcome == DamageOutcome.FRESH_DAMAGE || outcome == DamageOutcome.OVERDAMAGE;
        KnockbackSystem kb = s.knockback();
        if (kb != null && landed) {
            Vec impulse = lift > 0 ? new Vec(0, lift * TPS, 0) : null;
            // vanilla knocks on a fresh hit only; the lift rides any landed hit
            if (outcome == DamageOutcome.FRESH_DAMAGE) {
                kb.apply(new KnockbackSnapshot(target, true, this, getPosition(), null, null), impulse);
            } else if (impulse != null) {
                kb.apply(new KnockbackSnapshot(target, true, this, getPosition(), null, null).withDirection(Vec.ZERO), impulse);
            }
        }
        return landed;
    }

    /** 1.8 attackEntityFrom's bookkeeping after a hit: revenge, the flash window, the hurt or death sound. */
    protected void hurtBy(@Nullable Entity source, DamageOutcome outcome) {
        if (source instanceof LivingEntity le && le != this) {
            revengeTarget = le;
            revengeTimer = getAliveTicks();
        }
        if (isDead()) {
            MobSound death = knob(kind.deathSound, null);
            if (death != null) play(Fx.MOB_DEATH, death);
            return;
        }
        if (outcome != DamageOutcome.FRESH_DAMAGE) return;
        hurtTime = 10;
        MobSound hurt = knob(kind.hurtSound, null);
        if (hurt != null) play(Fx.MOB_HURT, hurt);
    }

    final void hurt(@Nullable Entity source, DamageOutcome outcome) { hurtBy(source, outcome); }

    protected void play(Key fx, MobSound sound) {
        Fx.play(services(), fx, FxContext.of(this).withDetail(sound.rolled(random)));
    }
}
