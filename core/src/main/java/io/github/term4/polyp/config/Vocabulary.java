package io.github.term4.polyp.config;


/**
 * The shipped behavior vocabularies, registered exactly once and BEFORE any {@link FieldFns} read - so a
 * help command or a server's own registration that touches the registry first does not see it empty, and
 * the order the classes happened to load in stops being load-bearing.
 */
public final class Vocabulary {

    private Vocabulary() {}

    private static volatile boolean installed;
    private static boolean installing; // under the lock: the registrations re-enter through FieldFns

    public static void ensure() {
        if (installed) return;
        synchronized (Vocabulary.class) {
            if (installed || installing) return;
            installing = true;
            try {
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
            io.github.term4.polyp.mechanics.containers.ContainerKey.registerFactories();
            io.github.term4.polyp.mechanics.containers.ContainerFill.registerFactories();
            FieldFns.register(io.github.term4.polyp.tracking.motion.VelocityRule.class, "simulated",
                    "the server-tracked arc with its default knobs; velocity/<knob> edits them",
                    args -> io.github.term4.polyp.tracking.motion.VelocityRule.simulated());
                installed = true;
            } finally {
                installing = false; // a throw must not wedge every later ensure()
            }
        }
    }
}
