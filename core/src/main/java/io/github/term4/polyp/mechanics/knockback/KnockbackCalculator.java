package io.github.term4.polyp.mechanics.knockback;

import io.github.term4.polyp.MechanicsKeys;
import io.github.term4.polyp.Services;
import io.github.term4.polyp.tracking.SprintTracker;
import io.github.term4.polyp.tracking.motion.VelocityContext;
import io.github.term4.polyp.tracking.motion.VelocityRule;
import io.github.term4.polyp.util.Directions;
import io.github.term4.polyp.util.tick.TickScaler;
import net.minestom.server.ServerFlag;
import net.minestom.server.coordinate.Point;
import net.minestom.server.coordinate.Vec;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Computes the knockback vector for a snapshot: assembles the inputs (directions, strengths, victim velocity) and
 * runs the {@link KnockbackPipeline}. Two override levels: a stage's MATH via the rule knobs
 * ({@link KnockbackConfig.FrictionRule} etc., consulted by the built-in stages), or the STRUCTURE via
 * {@code KnockbackConfig.stages} (replace/insert/remove/reorder). Vanilla math is exposed
 * ({@link #vanillaFriction}, {@link #vanillaCombine}, {@link #vanillaBounds}) so overrides can decorate it.
 */
public final class KnockbackCalculator {

    private final Services services;
    private final KnockbackConfig defaults;

    private final double tps = ServerFlag.SERVER_TICKS_PER_SECOND;

    public KnockbackCalculator(Services services, KnockbackConfig defaults) {
        this.services = services;
        this.defaults = defaults;
    }

    /**
     * Resolves the plain values for a snapshot (its config merged over the defaults, against the hit context) - the
     * exact values {@link #compute} will use. Cheap; backs {@link io.github.term4.polyp.api.event.knockback.KnockbackEvent#resolvedConfig()}.
     */
    public KnockbackConfigResolver.ResolvedKnockbackConfig resolveConfig(KnockbackSnapshot snap) {
        KnockbackConfig merged = snap.config() != null ? snap.config().fromBase(defaults) : defaults;
        KnockbackConfigResolver.KnockbackContext ctx = KnockbackConfigResolver.KnockbackContext.of(snap, services);
        return KnockbackConfigResolver.resolve(merged, ctx);
    }

    /** Knockback vector + the resolved config it was computed against. */
    public record KnockbackResult(Vec velocity, KnockbackConfigResolver.ResolvedKnockbackConfig config) {}

    public @Nullable Vec compute(KnockbackSnapshot snap) {
        KnockbackResult r = computeResult(snap);
        return r != null ? r.velocity() : null;
    }

    /** Like {@link #compute} but also returns the resolved config, so the velocity broadcast can reuse it instead of resolving twice. */
    public @Nullable KnockbackResult computeResult(KnockbackSnapshot snap) {
        KnockbackConfig base = snap.config();
        if (base == null) return null;

        KnockbackConfig merged = base.fromBase(defaults);
        KnockbackConfigResolver.KnockbackContext ctx = KnockbackConfigResolver.KnockbackContext.of(snap, services);
        KnockbackConfigResolver.ResolvedKnockbackConfig cfg = KnockbackConfigResolver.resolve(merged, ctx);

        DirContext dctx = dirCtx(snap);
        if (dctx == null) return null;
        int extraLevel = extraLevel(snap, cfg);
        boolean hasExtra = extraLevel > 0;

        Entity t = snap.target();
        Point oPt = dctx.oPt();
        Point tPt = t.getPosition();
        Vec dir3D = dctx.dir();

        Vec dDirH = Directions.horizontalBetween(oPt, tPt);
        Vec yDirH = Directions.horizontalOf(dir3D);
        Vec dDirV = Directions.verticalBetween(oPt, tPt);
        Vec pDirV = Directions.verticalOf(dir3D);

        RawDirs raw = new RawDirs(dDirH, dDirV, yDirH, pDirV);
        DirAndStrength normKb = resolveDS(raw, cfg, false);
        DirAndStrength extraKb = hasExtra ? resolveDS(raw, cfg, true) : null;

        Vec kb = normKb.direction().mul(normKb.h(), normKb.v(), normKb.h());
        // vanilla scales the extra horizontally by level; vertical is fixed
        Vec kbe = extraKb != null
                ? extraKb.direction().mul(extraKb.h() * extraLevel, extraKb.v(), extraKb.h() * extraLevel)
                : null;

        // threaded onto the state ctx so every stage/component reads the same velocity
        VelocityRule velRule = KnockbackConfigResolver.effectiveVelocity(cfg.velocity(), t, services);
        Vec vel = velRule.estimate(VelocityContext.of(t, services.sprintTracker()));

        var state = new KnockbackPipeline.State(ctx.withVelocity(velRule), cfg, vel, kb, kbe, extraLevel);
        Vec kbVec = KnockbackPipeline.run(cfg.stages(), state);

        return new KnockbackResult(new Vec(kbVec.x() * tps, kbVec.y() * tps, kbVec.z() * tps), cfg);
    }

    /**
     * The extra-knockback level (vanilla {@code i}): the snapshot's explicit extra (melee Knockback enchant, set by the
     * attack ruleset which has the weapon; or a projectile's Punch) plus {@code +1} for a recently-sprinting melee
     * attacker. Each level scales the config's {@code extra}* knobs. {@code 0} = none.
     */
    private int extraLevel(KnockbackSnapshot snap, KnockbackConfigResolver.ResolvedKnockbackConfig cfg) {
        int level = snap.extraKnockback();
        Entity a = snap.source();
        // sprint is server state, not on the snapshot; the leniency window scales to live TPS (identity at 20)
        if (snap.melee() && a != null) {
            int sprBuf = TickScaler.duration(cfg.sprintBuffer(), services.profiles().resolve(a, MechanicsKeys.TICK_SCALING), KnockbackSystem.KEY);
            if (SprintTracker.wasRecentlySprinting(services.sprintTracker(), a, sprBuf)) level++;
        }
        return level;
    }

    private record DirContext(Point oPt, Vec dir) {}

    private @Nullable DirContext dirCtx(KnockbackSnapshot snap) {
        Entity t = snap.target();
        Point tPt = t.getPosition();
        Entity s = snap.source();
        Point oPt = snap.origin();
        Vec dir = snap.direction();

        // explicit direction outranks the source's live look (a swing-filled hit carries the intersecting ray's aim)
        if (s != null) {
            var from = s.getPosition();
            return new DirContext(from, dir != null ? from.withDirection(dir).direction() : from.direction());
        }
        if (oPt != null && dir == null) return new DirContext(oPt, oPt.asPos().withLookAt(tPt).direction());
        if (dir != null) {
            Point pt = oPt != null ? oPt : tPt;
            return new DirContext(pt, pt.asPos().withDirection(dir).direction());
        } return null;
    }

    private record DirAndStrength(Vec direction, double h, double v) {}

    private record RawDirs(Vec posH, Vec posV, Vec yaw, Vec pitch) {}

    private DirAndStrength resolveDS(RawDirs raw, KnockbackConfigResolver.ResolvedKnockbackConfig cfg, boolean extra) {
        double h = extra ? cfg.extraHorizontal() : cfg.horizontal();
        double v = extra ? cfg.extraVertical() : cfg.vertical();
        double yw = extra ? cfg.extraYawWeight() : cfg.yawWeight();
        double pw = extra ? cfg.extraPitchWeight() : cfg.pitchWeight();
        double hw = extra ? cfg.extraHeightDelta() : cfg.heightDelta();

        KnockbackConfig.DirectionMode.Directed dh = cfg.horizontalCombine().horizontal(raw.posH(), raw.yaw(), h, yw);
        KnockbackConfig.DirectionMode.Directed dv = cfg.verticalCombine().vertical(raw.pitch(), raw.posV(), v, pw, hw);
        return new DirAndStrength(new Vec(dh.dir().x(), dv.dir().y(), dh.dir().z()), dh.magnitude(), dv.magnitude());
    }

    /** Vanilla friction stage: {@code kb + victimVel * coeff} per axis ({@link #frictionCoeff}). All b/t. */
    public static Vec vanillaFriction(Vec vel, Vec kb, KnockbackConfigResolver.ResolvedKnockbackConfig cfg) {
        double iFH = frictionCoeff(cfg.frictionH(), cfg.frictionModeH());
        double iFV = frictionCoeff(cfg.frictionV(), cfg.frictionModeV());
        return new Vec(vel.x() * iFH + kb.x(), vel.y() * iFV + kb.y(), vel.z() * iFH + kb.z());
    }

    /** Vanilla bounds stage: the configured {@link KnockbackConfig.Bounds} clamps for the pass ({@code afterExtra} picks the extra pair). */
    public static Vec vanillaBounds(Vec kb, boolean afterExtra, KnockbackConfigResolver.ResolvedKnockbackConfig cfg) {
        KnockbackConfig.Bounds h = afterExtra ? cfg.extraHorizontalBounds() : cfg.horizontalBounds();
        KnockbackConfig.Bounds v = afterExtra ? cfg.extraVerticalBounds() : cfg.verticalBounds();
        if (h != null) kb = applyHorizontalBounds(kb, h);
        if (v != null) kb = applyVerticalBounds(kb, v);
        return kb;
    }

    private static Vec applyVerticalBounds(Vec v, KnockbackConfig.Bounds b) {
        double y = v.y();
        if (b.lower() != null) y = Math.max(y, b.lower());
        if (b.upper() != null) y = Math.min(y, b.upper());
        return new Vec(v.x(), y, v.z());
    }

    private static Vec applyHorizontalBounds(Vec v, KnockbackConfig.Bounds b) {
        double hMag = Math.sqrt(v.x() * v.x() + v.z() * v.z());
        if (hMag < Directions.EPSILON) return v;
        double mag = hMag;
        if (b.lower() != null && mag < b.lower()) mag = b.lower();
        if (b.upper() != null && mag > b.upper()) mag = b.upper();
        double scale = mag / hMag;
        return new Vec(v.x() * scale, v.y(), v.z() * scale);
    }

    /** Vanilla combine stage: base+extra per the config's {@link KnockbackConfig.DirectionMode}s (vector addition or magnitude-preserving blend). */
    public static Vec vanillaCombine(Vec a, Vec b, KnockbackConfigResolver.ResolvedKnockbackConfig cfg) {
        Vec horizontal = cfg.horizontalCombine().combineHorizontal(a, b);
        return new Vec(horizontal.x(), cfg.verticalCombine().combineVertical(a, b), horizontal.z());
    }

    /** Friction term coefficient: {@code FACTOR} multiplies mot by the value directly; {@code DIVISOR} (default, incl. null) uses {@code 1/value}. */
    private static double frictionCoeff(double f, @Nullable KnockbackConfig.FrictionMode mode) {
        return (mode != null ? mode : KnockbackConfig.FrictionMode.DIVISOR).coefficient(f);
    }
}
