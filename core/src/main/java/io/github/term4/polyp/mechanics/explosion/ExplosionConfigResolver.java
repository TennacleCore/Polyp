package io.github.term4.polyp.mechanics.explosion;

import io.github.term4.polyp.codegen.CheckResolveOrder;
import io.github.term4.polyp.config.SubjectContext;
import io.github.term4.polyp.Services;
import io.github.term4.polyp.config.FieldValue;
import io.github.term4.polyp.mechanics.attribute.defense.Bypass;
import io.github.term4.polyp.mechanics.knockback.KnockbackConfig;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Entity;
import net.minestom.server.instance.Instance;
import org.jetbrains.annotations.Nullable;

import java.util.function.Function;
import java.util.function.Predicate;

/** Resolves {@link ExplosionConfig} with context into plain values. */

@CheckResolveOrder
public final class ExplosionConfigResolver {

    private ExplosionConfigResolver() {}

    /** Resolution context for one explosion (not per-victim: power/exposure falloff are computed by the system per entity). */
    public record ExplosionContext(Instance instance, Point center, @Nullable Entity source, Services services,
                                   @Nullable Double power) implements SubjectContext {
        @Override public @Nullable Entity subject() { return source(); }

        /** Power-less: only for calls that derive the radius FROM the config being resolved. */
        public static ExplosionContext of(Instance instance, Point center, @Nullable Entity source, Services services) {
            return of(instance, center, source, services, null);
        }

        public static ExplosionContext of(Instance instance, Point center, @Nullable Entity source, Services services,
                                          @Nullable Double power) {
            return new ExplosionContext(instance, center, source, services, power);
        }
    }

    /** Plain values coalesced to the modern-vanilla baseline; the 1.8 deltas (damageConstant 8.0, floored) come from the config. */
    public static ResolvedExplosionConfig resolve(@Nullable ExplosionConfig config, ExplosionContext ctx) {
        ExplosionConfig cfg = config != null ? config.withOverlay(ctx) : null;
        BlockBreaking breaking = FieldValue.resolve(cfg != null ? cfg.blockBreaking : null, ctx);
        BlockBreaking.BreakRule appRule = FieldValue.resolve(cfg != null ? cfg.breakRule : null, ctx);
        if (breaking != null && appRule != null) breaking = breaking.toBuilder().addBreakRule(appRule).build();
        return new ResolvedExplosionConfig(
                FieldValue.resolve(cfg != null ? cfg.power : null, ctx),
                FieldValue.resolve(cfg != null ? cfg.damageConstant : null, ctx, 7.0),
                FieldValue.resolve(cfg != null ? cfg.floorDamage : null, ctx, false),
                FieldValue.resolve(cfg != null ? cfg.damageModel : null, ctx, DamageModel.CURVE),
                FieldValue.resolve(cfg != null ? cfg.itemDamage : null, ctx),
                cfg != null ? cfg.damageBypass : null,
                FieldValue.resolve(cfg != null ? cfg.knockbackMultiplier : null, ctx, 1.0),
                cfg != null ? cfg.damageKnockback : null,
                FieldValue.resolve(cfg != null ? cfg.packetPush : null, ctx, true),
                FieldValue.resolve(cfg != null ? cfg.baseKnockback : null, ctx, 0.0),
                FieldValue.resolve(cfg != null ? cfg.baseHeight : null, ctx, 1.0),
                cfg != null && cfg.baseScale != null ? cfg.baseScale : RadialScale.ISOTROPIC,
                FieldValue.resolve(cfg != null ? cfg.exposure : null, ctx, ExplosionExposure.Rays.MODERN),
                FieldValue.resolve(cfg != null ? cfg.knockbackImpactFloor : null, ctx),
                FieldValue.resolve(cfg != null ? cfg.fire : null, ctx, false),
                FieldValue.resolve(cfg != null ? cfg.fireScope : null, ctx, ExplosionConfig.FireScope.SELECTED),
                FieldValue.resolve(cfg != null ? cfg.affectsSource : null, ctx, false),
                cfg != null ? cfg.knockbackTargets : null,
                cfg != null ? cfg.pushEye : null,
                breaking,
                FieldValue.resolve(cfg != null ? cfg.blockBreakDelayTicks : null, ctx, 0),
                FieldValue.resolve(cfg != null ? cfg.exposureAfterBreak : null, ctx, false));
    }

    /** Resolved explosion knobs. {@code power} is {@code null} when neither the call nor the config set one (the system defaults it). */
    public record ResolvedExplosionConfig(
            @Nullable Double power,
            double damageConstant,
            boolean floorDamage,
            DamageModel damageModel,
            @Nullable Double itemDamage,
            @Nullable Bypass damageBypass,
            double knockbackMultiplier,
            @Nullable KnockbackConfig damageKnockback,
            boolean packetPush,
            double baseKnockback,
            double baseHeight,
            RadialScale baseScale,
            ExplosionExposure.Rays exposure,
            @Nullable Double knockbackImpactFloor,
            boolean fire,
            ExplosionConfig.FireScope fireScope,
            boolean affectsSource,
            @Nullable Predicate<Entity> knockbackTargets,
            @Nullable Function<Entity, Double> pushEye,
            @Nullable BlockBreaking blockBreaking,
            int blockBreakDelayTicks,
            boolean exposureAfterBreak
    ) {}
}
