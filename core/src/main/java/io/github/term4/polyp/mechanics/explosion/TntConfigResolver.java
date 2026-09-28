package io.github.term4.polyp.mechanics.explosion;

import io.github.term4.polyp.codegen.CheckResolveOrder;
import net.minestom.server.instance.Instance;
import net.minestom.server.coordinate.Point;
import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.config.SubjectContext;
import io.github.term4.polyp.Services;
import io.github.term4.polyp.api.event.explosion.TntPrimeEvent;
import io.github.term4.polyp.config.FieldValue;
import io.github.term4.polyp.entity.PrimedTnt;
import net.minestom.server.item.ItemStack;
import net.kyori.adventure.key.Key;
import io.github.term4.polyp.item.ItemKind;
import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.entity.Entity;
import org.jetbrains.annotations.Nullable;


@CheckResolveOrder
public final class TntConfigResolver {

    /** One prime's inputs; {@code igniter} null for a sourceless prime, {@code item} the stack placed, null for a block lit. */
    public record TntContext(@Nullable Entity igniter, MechanicsWorld world, TntPrimeEvent.Cause cause,
                             Services services, @Nullable ItemStack item) implements SubjectContext {
        public TntContext(@Nullable Entity igniter, MechanicsWorld world, TntPrimeEvent.Cause cause, Services services) {
            this(igniter, world, cause, services, null);
        }

        @Override public @Nullable Entity subject() { return igniter(); }
    }

    private TntConfigResolver() {}

    /** {@link #resolve(TntConfig, TntContext)} with the context built inline. */
    public static PrimedTnt.Config resolve(@Nullable TntConfig cfg, @Nullable Entity igniter, MechanicsWorld world,
                                           TntPrimeEvent.Cause cause, Services services) {
        return resolve(cfg, new TntContext(igniter, world, cause, services));
    }

    /** The concrete knobs for one prime; {@code null} config = vanilla; a stamped item's kind layers over the config. */
    public static PrimedTnt.Config resolve(@Nullable TntConfig cfg, TntContext ctx) {
        PrimedTnt.Config v = PrimedTnt.VANILLA;
        if (cfg == null) return v;
        Key kind = ItemKind.of(ctx.item());
        TntConfig own = kind != null ? cfg.kind(kind) : null;
        if (own != null) cfg = own;
        return new PrimedTnt.Config(
                FieldValue.resolve(cfg.fuseTicks, ctx, v.fuseTicks()),
                FieldValue.resolve(cfg.power, ctx, v.power()),
                FieldValue.resolve(cfg.detonateAtFeet, ctx, v.detonateAtFeet()),
                FieldValue.resolve(cfg.wire, ctx, v.wire()),
                FieldValue.resolve(cfg.bounce, ctx, v.bounce()),
                FieldValue.resolve(cfg.tntVictimScale, ctx, v.tntVictimScale()),
                FieldValue.resolve(cfg.igniteOnPlace, ctx, v.igniteOnPlace()));
    }

    /** Primes {@code cfg}'s TNT at {@code tntBlock} with no igniter - the preset-level spawn helper. */
    public static @Nullable PrimedTnt spawn(ExplosionSystem explosion, Instance instance, Point tntBlock, TntConfig cfg) {
        MechanicsWorld world = MechanicsWorld.of(instance);
        return PrimedTnt.spawn(explosion, world, tntBlock,
                resolve(cfg, null, world, TntPrimeEvent.Cause.API, Polyp.getInstance().services()));
    }
}
