package io.github.term4.polyp.config;

import io.github.term4.polyp.mechanics.attack.AttackConfigResolver.AttackContext;
import io.github.term4.polyp.mechanics.attribute.AttributeConfigResolver.AttributeContext;
import io.github.term4.polyp.mechanics.blocking.BlockingConfigResolver.BlockingContext;
import io.github.term4.polyp.mechanics.consumable.ConsumableConfigResolver.ConsumableContext;
import io.github.term4.polyp.mechanics.damage.DamageConfigResolver.DamageContext;
import io.github.term4.polyp.mechanics.damage.DeathConfig.DeathContext;
import io.github.term4.polyp.mechanics.explosion.ExplosionConfigResolver.ExplosionContext;
import io.github.term4.polyp.mechanics.explosion.TntConfigResolver.TntContext;
import io.github.term4.polyp.mechanics.knockback.KnockbackConfigResolver.KnockbackContext;
import io.github.term4.polyp.mechanics.projectile.ProjectileConfigResolver.ProjectileContext;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The entity a resolution context is ABOUT - the same entity that system resolves its scoped profile against,
 * written down once so a {@link FieldValue#targeted targeted} knob can ask "is this player on red?" without
 * knowing which system is asking. Damage, knockback and death are about the victim; attack and projectiles
 * about the doer; an explosion about whoever set it off, if anyone.
 */
public final class Subjects {

    private Subjects() {}

    private static final Map<Class<?>, Function<Object, @Nullable Entity>> BY_CONTEXT = new ConcurrentHashMap<>();

    static {
        register(ProjectileContext.class, ctx -> ctx.snap().shooter());
        register(DamageContext.class, ctx -> ctx.snap().target());
        register(KnockbackContext.class, ctx -> ctx.snap().target());
        register(AttackContext.class, ctx -> ctx.snap().attacker());
        register(ExplosionContext.class, ExplosionContext::source);
        register(TntContext.class, TntContext::igniter);
        register(BlockingContext.class, BlockingContext::defender);
        register(ConsumableContext.class, ConsumableContext::user);
        register(DeathContext.class, DeathContext::victim);
        register(AttributeContext.class, AttributeContext::entity);
    }

    /** A context type polyp does not ship (a server's own system) names its subject here. */
    @SuppressWarnings("unchecked")
    public static <C> void register(@NotNull Class<C> context, @NotNull Function<C, @Nullable Entity> subject) {
        BY_CONTEXT.put(context, (Function<Object, Entity>) subject);
    }

    /** The subject of {@code ctx}, or {@code null} for a sourceless or unregistered context. */
    public static @Nullable Entity of(@Nullable Object ctx) {
        if (ctx == null) return null;
        Function<Object, Entity> subject = BY_CONTEXT.get(ctx.getClass());
        return subject != null ? subject.apply(ctx) : null;
    }
}
