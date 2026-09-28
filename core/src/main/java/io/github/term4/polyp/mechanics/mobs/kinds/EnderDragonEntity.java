package io.github.term4.polyp.mechanics.mobs.kinds;

import io.github.term4.polyp.Services;
import io.github.term4.polyp.api.event.damage.DamageEvent;
import io.github.term4.polyp.fx.Fx;
import io.github.term4.polyp.fx.FxContext;
import io.github.term4.polyp.mechanics.attribute.Attribute;
import io.github.term4.polyp.mechanics.attribute.AttributeSystem;
import io.github.term4.polyp.mechanics.attribute.combat.CombatFacts;
import io.github.term4.polyp.mechanics.damage.DamageSnapshot;
import io.github.term4.polyp.mechanics.damage.DamageSystem;
import io.github.term4.polyp.mechanics.damage.DamageSystem.DamageOutcome;
import io.github.term4.polyp.mechanics.damage.types.explosion.ExplosionDamage;
import io.github.term4.polyp.mechanics.damage.types.melee.MeleeDamage;
import io.github.term4.polyp.mechanics.damage.types.mob.MobDamage;
import io.github.term4.polyp.mechanics.knockback.KnockbackSnapshot;
import io.github.term4.polyp.mechanics.knockback.KnockbackSystem;
import io.github.term4.polyp.mechanics.mobs.MobEntity;
import io.github.term4.polyp.mechanics.mobs.MobsSystem;
import io.github.term4.polyp.mechanics.mobs.PartDamage;
import io.github.term4.polyp.mechanics.mobs.path.Blocks;
import io.github.term4.polyp.tracking.motion.MotionTracker;
import io.github.term4.polyp.api.event.mobs.MobBreakBlockEvent;
import io.github.term4.polyp.world.MechanicsWorld;
import io.github.term4.polyp.world.WorldPolicy;
import net.minestom.server.event.EventDispatcher;
import net.minestom.server.coordinate.BlockVec;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.EntityType;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.LivingEntity;
import net.minestom.server.entity.Player;
import net.minestom.server.instance.block.Block;
import net.minestom.server.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.function.Predicate;

/**
 * 1.8 EntityDragon: the flight loop, seven parts on ids the clients derive from its own (id + 1 + i), wing
 * shoves, the bite, and blocks eaten along the way. No crystals, no portal.
 */
public final class EnderDragonEntity extends MobEntity {

    private static final int PARTS = 7;
    private static final Predicate<LivingEntity> ANY = e -> true;

    private final DragonPart head, body, tail1, tail2, tail3, wing1, wing2;
    private final DragonPart[] parts;
    private final double[][] ring = new double[64][3];
    private int ringIndex = -1;
    private final Set<LivingEntity> bitten = new HashSet<>(); // this charge's, under biteOncePerCharge
    private final Set<LivingEntity> pushed = new HashSet<>(); // this charge's, under pushOncePerCharge
    private double targetX, targetY = 100.0, targetZ;
    private @Nullable Entity flightTarget;
    private boolean forceNewTarget;
    private boolean slowed;
    private float randomYawVelocity;
    private @Nullable DragonPart hit;
    private @Nullable Point roam;

    public EnderDragonEntity(MobsSystem mobs) {
        super(mobs, EntityType.ENDER_DRAGON);
        int base = getEntityId();
        // both clients build the parts on the ids right after the dragon's: take them before anything else can
        for (int i = 0; i < PARTS; i++) {
            int reserved = Entity.generateId();
            if (reserved != base + 1 + i) throw new IllegalStateException("dragon part ids not consecutive");
        }
        head = new DragonPart("head", base + 1, 6.0, 6.0);
        body = new DragonPart("body", base + 2, 8.0, 8.0);
        tail1 = new DragonPart("tail", base + 3, 4.0, 4.0);
        tail2 = new DragonPart("tail", base + 4, 4.0, 4.0);
        tail3 = new DragonPart("tail", base + 5, 4.0, 4.0);
        wing1 = new DragonPart("wing", base + 6, 4.0, 4.0);
        wing2 = new DragonPart("wing", base + 7, 4.0, 4.0);
        parts = new DragonPart[] {head, body, tail1, tail2, tail3, wing1, wing2};
        setNoGravity(true);
    }

    /** Tries fresh dragons until one gets its seven ids in a row (another thread may take an id between). */
    public static EnderDragonEntity spawn(MobsSystem mobs, MechanicsWorld world, Pos position) {
        for (int attempt = 0; attempt < 8; attempt++) {
            try {
                return mobs.spawn(new EnderDragonEntity(mobs), world, position);
            } catch (IllegalStateException ignored) {
                // ids interleaved: the discarded dragon never spawned
            }
        }
        throw new IllegalStateException("could not reserve consecutive dragon part ids");
    }

    @Override
    protected void goals() {}

    @Override
    protected boolean pushes() { return false; }

    @Override
    public boolean attack(LivingEntity target) { return false; }

    public DragonPart[] parts() { return parts; }
    public DragonPart head() { return head; }
    public boolean slowed() { return slowed; }
    public @Nullable Entity flightTarget() { return flightTarget; }
    public Vec target() { return new Vec(targetX, targetY, targetZ); }

    public int partIndex(int entityId) {
        for (int i = 0; i < PARTS; i++) if (parts[i].id == entityId) return i;
        return -1;
    }

    /** The part the next hit lands on, from the id a client swung at. */
    public void hitPart(int entityId) {
        int i = partIndex(entityId);
        hit = i >= 0 ? parts[i] : null;
    }

    public @Nullable DragonPart partAt(Point p) {
        for (DragonPart part : parts) if (part.contains(p)) return part;
        return null;
    }

    public void forceNewTarget() { forceNewTarget = true; }

    /** Where the random flight points spread from; the End's fountain, 0 0, when unset and no home is. */
    public void roamAround(@Nullable Point centre) { roam = centre; }

    // ---- the tick

    @Override
    protected void livingTick() {
        if (hurtTime > 0) --hurtTime;
        if (isDead()) {
            deathTick();
            return;
        }
        ambient();
        yaw(wrap(yaw()));
        if (ringIndex < 0) {
            for (double[] entry : ring) {
                entry[0] = yaw();
                entry[1] = getPosition().y();
            }
        }
        if (++ringIndex == ring.length) ringIndex = 0;
        ring[ringIndex][0] = yaw();
        ring[ringIndex][1] = getPosition().y();
        fly();
        bodyYaw(yaw());
        placeParts();
        if (hurtTime == 0) {
            shove(wing1);
            shove(wing2);
            bite();
        }
        slowed = eat(head.grown(0, 0, 0)) | eat(body.grown(0, 0, 0));
        sync();
    }

    private void ambient() {
        // EntityLiving.onEntityUpdate's roll, on the growl
        if (random().nextInt(1000) < livingSoundTime++) {
            livingSoundTime = -80;
            var growl = knob(kind().ambientSound, null);
            if (growl != null) play(Fx.MOB_AMBIENT, growl);
        }
    }

    private int livingSoundTime;

    private void fly() {
        Pos pos = getPosition();
        double dx = targetX - pos.x();
        double dy = targetY - pos.y();
        double dz = targetZ - pos.z();
        double d14 = dx * dx + dy * dy + dz * dz;
        if (flightTarget != null) {
            if (flightTarget.isRemoved() || flightTarget instanceof Player p && (p.isDead() || p.getGameMode() == GameMode.SPECTATOR)) {
                flightTarget = null;
            } else {
                targetX = flightTarget.getPosition().x();
                targetZ = flightTarget.getPosition().z();
                double tx = targetX - pos.x();
                double tz = targetZ - pos.z();
                double horizontal = Math.sqrt(tx * tx + tz * tz);
                double above = 0.4000000059604645 + horizontal / 80.0 - 1.0;
                if (above > 10.0) above = 10.0;
                targetY = flightTarget.getPosition().y() + above;
            }
        }
        if (flightTarget == null) {
            targetX += random().nextGaussian() * 2.0;
            targetZ += random().nextGaussian() * 2.0;
        }
        if (forceNewTarget || d14 < 100.0 || d14 > 22500.0 || collidedHorizontally || collidedVertically) newTarget();
        dy /= Math.sqrt(dx * dx + dz * dz);
        dy = Math.clamp(dy, -0.6f, 0.6f);
        motion = motion.add(0, dy * 0.10000000149011612, 0);
        yaw(wrap(yaw()));
        double wanted = 180.0 - Math.atan2(dx, dz) * 180.0 / Math.PI;
        double turn = wrap(wanted - yaw());
        if (turn > 50.0) turn = 50.0;
        if (turn < -50.0) turn = -50.0;
        Vec toTarget = new Vec(targetX - pos.x(), targetY - pos.y(), targetZ - pos.z()).normalize();
        double facingZ = -Math.cos(yaw() * Math.PI / 180.0f);
        Vec facing = new Vec(Math.sin(yaw() * Math.PI / 180.0f), motion.y(), facingZ).normalize();
        float f5 = ((float) facing.dot(toTarget) + 0.5f) / 1.5f;
        if (f5 < 0.0f) f5 = 0.0f;
        randomYawVelocity *= 0.8f;
        float f6 = (float) Math.sqrt(motion.x() * motion.x() + motion.z() * motion.z()) * 1.0f + 1.0f;
        double d9 = Math.sqrt(motion.x() * motion.x() + motion.z() * motion.z()) * 1.0 + 1.0;
        if (d9 > 40.0) d9 = 40.0;
        randomYawVelocity = (float) (randomYawVelocity + turn * (0.699999988079071 / d9 / f6));
        yaw(yaw() + randomYawVelocity * 0.1f);
        float f7 = (float) (2.0 / (d9 + 1.0));
        moveFlying(0.0f, -1.0f, 0.06f * (f5 * f7 + (1.0f - f7)));
        Vec step = slowed ? motion.mul(0.800000011920929) : motion;
        glide(step);
        Vec heading = motion.normalize();
        float f9 = ((float) heading.dot(facing) + 1.0f) / 2.0f;
        f9 = 0.8f + 0.15f * f9;
        motion = new Vec(motion.x() * f9, motion.y() * 0.9100000262260437, motion.z() * f9);
    }

    // noClip: the world never stops it, so the collided flags stay false as 1.8's do
    private void glide(Vec step) {
        Pos to = getPosition().add(step.x(), step.y(), step.z()).withView(yaw(), pitch());
        if (!world().isChunkLoaded(to)) return;
        refreshPosition(to, false, true);
    }

    private void newTarget() {
        forceNewTarget = false;
        bitten.clear();
        pushed.clear();
        List<Player> players = new ArrayList<>();
        Predicate<LivingEntity> selector = knob(kind().targetSelector, ANY);
        for (Player p : world().players()) {
            if (p.getGameMode() == GameMode.SPECTATOR || p.isDead() || !WorldPolicy.canAffect(this, p)) continue;
            if (sides().same(this, p) || !selector.test(p)) continue;
            players.add(p);
        }
        if (random().nextInt(2) == 0 && !players.isEmpty()) {
            flightTarget = players.get(random().nextInt(players.size()));
            return;
        }
        Point centre = roam != null ? roam : hasHome() ? home() : BlockVec.ZERO;
        double radius = knob(kind().roamRadius, 60.0);
        double minY = knob(kind().roamMinY, 70.0);
        double height = knob(kind().roamHeight, 50.0);
        Pos pos = getPosition();
        while (true) {
            targetX = centre.x() + (random().nextFloat() * radius * 2 - radius);
            targetY = minY + random().nextFloat() * height;
            targetZ = centre.z() + (random().nextFloat() * radius * 2 - radius);
            double dx = pos.x() - targetX, dy = pos.y() - targetY, dz = pos.z() - targetZ;
            if (dx * dx + dy * dy + dz * dz > 100.0) break;
        }
        flightTarget = null;
    }

    private double[] offsets(int back) {
        int i = ringIndex - back & 63;
        int j = ringIndex - back - 1 & 63;
        double[] out = new double[3];
        out[0] = ring[i][0];
        out[1] = ring[i][1];
        out[2] = ring[i][2];
        return out;
    }

    private void placeParts() {
        head.size(3.0, 3.0);
        tail1.size(2.0, 2.0);
        tail2.size(2.0, 2.0);
        tail3.size(2.0, 2.0);
        body.size(5.0, 3.0);
        wing1.size(4.0, 2.0);
        wing2.size(4.0, 3.0);
        Pos pos = getPosition();
        float tilt = (float) (offsets(5)[1] - offsets(10)[1]) * 10.0f / 180.0f * (float) Math.PI;
        float tiltCos = (float) Math.cos(tilt);
        float tiltSin = -(float) Math.sin(tilt);
        float rad = yaw() * (float) Math.PI / 180.0f;
        float sin = (float) Math.sin(rad);
        float cos = (float) Math.cos(rad);
        body.moveTo(pos.x() + sin * 0.5f, pos.y(), pos.z() - cos * 0.5f);
        wing1.moveTo(pos.x() + cos * 4.5f, pos.y() + 2.0, pos.z() + sin * 4.5f);
        wing2.moveTo(pos.x() - cos * 4.5f, pos.y() + 2.0, pos.z() - sin * 4.5f);
        double[] back5 = offsets(5);
        double[] now = offsets(0);
        float headSin = (float) Math.sin(yaw() * Math.PI / 180.0f - randomYawVelocity * 0.01f);
        float headCos = (float) Math.cos(yaw() * Math.PI / 180.0f - randomYawVelocity * 0.01f);
        head.moveTo(pos.x() + headSin * 5.5f * tiltCos, pos.y() + (now[1] - back5[1]) * 1.0 + tiltSin * 5.5f,
                pos.z() - headCos * 5.5f * tiltCos);
        DragonPart[] tails = {tail1, tail2, tail3};
        for (int j = 0; j < 3; j++) {
            double[] tail = offsets(12 + j * 2);
            float angle = yaw() * (float) Math.PI / 180.0f + wrap(tail[0] - back5[0]) * (float) Math.PI / 180.0f * 1.0f;
            float tSin = (float) Math.sin(angle);
            float tCos = (float) Math.cos(angle);
            float f23 = 1.5f;
            float f24 = (j + 1) * 2.0f;
            tails[j].moveTo(pos.x() - (sin * f23 + tSin * f24) * tiltCos,
                    pos.y() + (tail[1] - back5[1]) * 1.0 - (f24 + f23) * tiltSin + 1.5,
                    pos.z() + (cos * f23 + tCos * f24) * tiltCos);
        }
    }

    /** 1.8 collideWithEntities: living things under a wing fly off, harder the closer to the body. */
    private void shove(DragonPart wing) {
        DragonPart.Box zone = wing.grown(4.0, 2.0, -2.0);
        double bx = body.position().x(), bz = body.position().z();
        double push = knob(kind().wingPush, 4.0);
        double lift = knob(kind().wingLift, 0.20000000298023224);
        boolean once = knob(kind().pushOncePerCharge, false);
        for (LivingEntity e : victims(zone)) {
            if (once && !pushed.add(e)) continue;
            double dx = e.getPosition().x() - bx;
            double dz = e.getPosition().z() - bz;
            double d4 = dx * dx + dz * dz;
            if (d4 == 0) continue;
            deliver(e, new Vec(dx / d4 * push, lift, dz / d4 * push));
        }
    }

    // 1.8 addVelocity: on top of what the victim already carries
    private void deliver(LivingEntity e, Vec push) {
        if (e instanceof MobEntity mob) {
            mob.addMotion(push);
            return;
        }
        KnockbackSystem kb = services().knockback();
        if (kb == null) return;
        Vec current = e instanceof Player ? tracked(e) : e.getVelocity().div(TPS);
        kb.deliver(e, current.add(push).mul(TPS));
    }

    private static Vec tracked(Entity e) {
        Vec h = MotionTracker.horizontalMot(e, 0);
        Double y = MotionTracker.serverMotY(e, 0, false);
        return new Vec(h.x(), y != null ? y : 0.0, h.z());
    }

    /** 1.8 attackEntitiesInList: the head's bite. */
    private void bite() {
        float amount = knob(kind().biteDamage, 10.0f);
        Services s = services();
        DamageSystem damage = s.damage();
        if (damage == null) return;
        boolean once = knob(kind().biteOncePerCharge, false);
        for (LivingEntity e : victims(head.grown(1.0, 1.0, 0.0))) {
            if (once && !bitten.add(e)) continue;
            DamageSnapshot snap = MobDamage.INSTANCE.snapshot(this, e, amount, false, s);
            DamageOutcome outcome = damage.apply(snap);
            KnockbackSystem kb = s.knockback();
            if (kb != null && outcome == DamageOutcome.FRESH_DAMAGE) {
                kb.apply(new KnockbackSnapshot(e, true, this, getPosition(), null, null));
            }
        }
    }

    private List<LivingEntity> victims(DragonPart.Box zone) {
        List<LivingEntity> out = new ArrayList<>();
        Pos centre = new Pos((zone.minX() + zone.maxX()) / 2, (zone.minY() + zone.maxY()) / 2, (zone.minZ() + zone.maxZ()) / 2);
        double reach = Math.max(zone.maxX() - zone.minX(), zone.maxY() - zone.minY()) + 4.0;
        for (Entity e : world().nearbyEntities(centre, reach)) {
            if (e == this || !(e instanceof LivingEntity le) || le.isDead() || le.isRemoved()) continue;
            if (e instanceof Player p && p.getGameMode() == GameMode.SPECTATOR) continue;
            if (!WorldPolicy.canAffect(this, e) || !canAttack(le) || sides().same(this, e)) continue;
            if (zone.meets(e.getPosition(), e.getBoundingBox())) out.add(le);
        }
        return out;
    }

    /** 1.8 destroyBlocksInAABB: everything the kind lets it eat goes; anything else slows the flight. */
    private boolean eat(DragonPart.Box box) {
        Predicate<Block> breaks = knob(kind().breaks, b -> true);
        boolean stopped = false;
        boolean broke = false;
        MechanicsWorld world = world();
        for (int x = (int) Math.floor(box.minX()); x <= (int) Math.floor(box.maxX()); x++) {
            for (int y = (int) Math.floor(box.minY()); y <= (int) Math.floor(box.maxY()); y++) {
                for (int z = (int) Math.floor(box.minZ()); z <= (int) Math.floor(box.maxZ()); z++) {
                    Block block = Blocks.at(world, x, y, z);
                    if (block.air()) continue;
                    if (breaks.test(block)) {
                        MobBreakBlockEvent event = new MobBreakBlockEvent(world, this, new BlockVec(x, y, z), block);
                        EventDispatcher.call(event);
                        if (event.isCancelled()) {
                            stopped = true;
                            continue;
                        }
                        world.setBlock(new BlockVec(x, y, z), Block.AIR);
                        broke = true;
                    } else {
                        stopped = true;
                    }
                }
            }
        }
        if (broke) Fx.play(services(), Fx.DRAGON_BREAK, FxContext.at(world, box.random(random()), this));
        return stopped;
    }

    // ---- being hit

    /**
     * 1.8 attackEntityFromPart: any hit turns the flight ahead; only players and explosions hurt; a body hit is
     * a quarter plus one; an unenchanted blade when the kind says so.
     */
    public void onHit(DamageEvent e) {
        DragonPart part = hit;
        hit = null;
        boolean explosion = ExplosionDamage.KEY.equals(e.type().key());
        if (part == null) {
            Point at = e.finalSnap().point();
            part = explosion && at != null ? partAt(at) : null;
            if (part == null && explosion) part = body;
        }
        if (part == null) {
            e.setCancelled(true);
            return;
        }
        Pos pos = getPosition();
        float rad = yaw() * (float) Math.PI / 180.0f;
        targetX = pos.x() + Math.sin(rad) * 5.0f + (random().nextFloat() - 0.5f) * 2.0f;
        targetY = pos.y() + random().nextFloat() * 3.0f + 1.0;
        targetZ = pos.z() - Math.cos(rad) * 5.0f + (random().nextFloat() - 0.5f) * 2.0f;
        flightTarget = null;
        if (!(e.source() instanceof Player) && !explosion) {
            e.setCancelled(true);
            return;
        }
        float amount = e.amount();
        if (MeleeDamage.KEY.equals(e.type().key()) && !knob(kind().enchantBonus, true)) amount -= enchantFlat(e);
        if (part != head) amount = knob(kind().partDamage, PartDamage.LEGACY).of(amount);
        e.amount(amount);
    }

    private float enchantFlat(DamageEvent e) {
        AttributeSystem attrs = services().attributes();
        if (attrs == null || !(e.source() instanceof LivingEntity attacker)) return 0f;
        ItemStack weapon = e.item() != null ? e.item() : attacker.getItemInMainHand();
        return (float) attrs.context(attacker, weapon).with(CombatFacts.TARGET, this).value(Attribute.MELEE_FLAT_ADD, 0);
    }

    // ---- dying: 1.8 onDeathUpdate, rising and spinning for ten seconds

    private void deathTick() {
        ++deathTicks;
        if (deathTicks == 1) Fx.play(services(), Fx.DRAGON_DEATH, FxContext.of(this));
        if (deathTicks >= 180 && deathTicks <= 200) {
            Pos p = getPosition();
            Vec at = new Vec(p.x() + (random().nextFloat() - 0.5f) * 8.0f, p.y() + 2.0 + (random().nextFloat() - 0.5f) * 4.0f,
                    p.z() + (random().nextFloat() - 0.5f) * 8.0f);
            Fx.play(services(), Fx.DRAGON_DEATH_BURST, FxContext.at(world(), at, this));
        }
        yaw(yaw() + 20.0f);
        bodyYaw(yaw());
        glide(new Vec(0, 0.10000000149011612, 0));
        if (deathTicks >= 200) remove();
    }

    private static float wrap(double angle) {
        angle %= 360.0;
        if (angle >= 180.0) angle -= 360.0;
        if (angle < -180.0) angle += 360.0;
        return (float) angle;
    }
}
