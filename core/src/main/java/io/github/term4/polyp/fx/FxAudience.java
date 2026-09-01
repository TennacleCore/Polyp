package io.github.term4.polyp.fx;

import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

/**
 * <em>Who</em> perceives an fx, and from where - the axis {@link FxEffect} ("what") deliberately does not carry.
 * Keeping them apart is what stops the vocabulary from being a grid: a new audience works with every effect and a
 * new effect with every audience, instead of one factory per pairing.
 *
 * <p>Each recipient gets its own anchor point, which is how a sound can be positional for some audiences and
 * distance-proof for others: {@link #EVERYWHERE} anchors at the LISTENER, so nothing attenuates.
 */
@FunctionalInterface
public interface FxAudience {

    /** Calls {@code to} once per recipient with the point that recipient should perceive the fx at. */
    void each(@NotNull FxContext ctx, @NotNull BiConsumer<Player, Point> to);

    /** Everyone in the shard, at the fx position - vanilla, so distance attenuates. */
    FxAudience SHARD = (ctx, to) -> {
        for (Player p : ctx.world().players()) to.accept(p, ctx.position());
    };

    /**
     * The source's viewers but NOT the source, at the fx position: the doer's own client predicts its sound or
     * animation locally, so echoing it back doubles it. No-op without a source.
     */
    FxAudience VIEWERS = (ctx, to) -> {
        Entity source = ctx.source();
        if (source == null) return;
        for (Player p : source.getViewers()) to.accept(p, ctx.position());
    };

    /** {@link #VIEWERS} plus the source when it is a LEGACY client: 1.8's local sound sinks are empty stubs. */
    FxAudience PREDICTED = (ctx, to) -> {
        VIEWERS.each(ctx, to);
        var polyp = io.github.term4.polyp.Polyp.getInstance();
        if (ctx.source() instanceof Player p && polyp.clientInfo() != null && polyp.clientInfo().isLegacy(p)) {
            to.accept(p, ctx.position());
        }
    };

    /** The source alone, anchored on itself - the private "ding" of a hit marker. */
    FxAudience SOURCE = (ctx, to) -> {
        if (ctx.source() instanceof Player p) to.accept(p, p.getPosition());
    };

    /** Everyone in the shard, each anchored on THEMSELVES: full volume wherever they stand (BedWars' pearl). */
    FxAudience EVERYWHERE = (ctx, to) -> {
        for (Player p : ctx.world().players()) to.accept(p, p.getPosition());
    };

    /** Everyone in the shard within {@code blocks} of the fx, still positionally. */
    static @NotNull FxAudience within(double blocks) {
        double squared = blocks * blocks;
        return (ctx, to) -> {
            for (Player p : ctx.world().players()) {
                if (p.getPosition().distanceSquared(ctx.position()) <= squared) to.accept(p, ctx.position());
            }
        };
    }

    /** Names the built-ins for data paths ({@code to(everywhere, sound(...))}); a server registers its own. */
    static void registerFactories() {
        io.github.term4.polyp.config.FieldFns.register(FxAudience.class, "shard", args -> SHARD);
        io.github.term4.polyp.config.FieldFns.register(FxAudience.class, "viewers", args -> VIEWERS);
        io.github.term4.polyp.config.FieldFns.register(FxAudience.class, "predicted", args -> PREDICTED);
        io.github.term4.polyp.config.FieldFns.register(FxAudience.class, "source", args -> SOURCE);
        io.github.term4.polyp.config.FieldFns.register(FxAudience.class, "everywhere", args -> EVERYWHERE);
        io.github.term4.polyp.config.FieldFns.register(FxAudience.class, "within", args -> within(args.arity(1).dbl(0)));
    }
}
