package io.github.term4.polyp.config;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The shipped behaviour vocabularies, registered exactly once and BEFORE any {@link FieldFns} read - so a
 * help command or a server's own registration that touches the registry first does not see it empty, and
 * the order the classes happened to load in stops being load-bearing.
 */
public final class Vocabulary {

    private Vocabulary() {}

    private static final AtomicBoolean INSTALLED = new AtomicBoolean();

    public static void ensure() {
        if (!INSTALLED.compareAndSet(false, true)) return; // set first: the registrations below read the registry
        io.github.term4.polyp.mechanics.explosion.DamageModel.registerFactories();
        KeySet.registerFactories();
        io.github.term4.polyp.mechanics.projectile.shootables.DrawPower.registerFactories();
        io.github.term4.polyp.fx.FxAudiences.registerFactories();
        io.github.term4.polyp.fx.FxEffect.registerFactories();
        io.github.term4.polyp.mechanics.knockback.KnockbackConfig.DirectionMode.registerFactories();
        io.github.term4.polyp.mechanics.knockback.KnockbackConfig.FrictionMode.registerFactories();
        io.github.term4.polyp.mechanics.explosion.ExplosionConfig.FireScope.registerFactories();
        io.github.term4.polyp.mechanics.explosion.ExplosionExposure.Rays.registerFactories();
        io.github.term4.polyp.entity.DroppedItemEntity.Model.registerFactories();
        FieldFns.register(io.github.term4.polyp.tracking.motion.VelocityRule.class, "simulated",
                "the server-tracked arc with its default knobs; velocity/<knob> edits them",
                args -> io.github.term4.polyp.tracking.motion.VelocityRule.simulated());
    }
}
