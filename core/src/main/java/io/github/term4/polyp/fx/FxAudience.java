package io.github.term4.polyp.fx;

import io.github.term4.polyp.config.FieldFns;
import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * <em>Who</em> perceives an fx, and from where - the axis {@link FxEffect} ("what") deliberately does not carry.
 * Keeping them apart is what stops the vocabulary from being a grid: a new audience works with every effect and a
 * new effect with every audience, instead of one factory per pairing.
 *
 * <p>The scopes below are not a menu of cases: there are four PRIMITIVE sets ({@link #MEMBERS}, {@link #WATCHERS},
 * {@link #INSTANCE}, {@link #VIEWERS}, {@link #SOURCE}) and combinators that close over them - {@link #both union},
 * {@link #except difference}, {@link #only intersection}, {@link #tree} across a layered world family,
 * {@link #within}, {@link #legacy}, and {@link #atListener} for the anchor. Anything nameable is a composition:
 * "on the instance but not in this shard" is {@code except(instance, shard)}, not another constant. The named
 * conveniences at the bottom are spelled out as the compositions they equal.
 */
@FunctionalInterface
public interface FxAudience {

    /** Calls {@code to} once per recipient with the point that recipient should perceive the fx at. */
    void each(@NotNull FxContext ctx, @NotNull BiConsumer<Player, Point> to);

    // ---------------------------------------------------------------- primitive sets

    /** The world's own members - the players actually in it. Spectators watching do NOT perceive it. */
    FxAudience MEMBERS = (ctx, to) -> {
        for (Player p : ctx.world().players()) to.accept(p, ctx.position());
    };

    /** Everyone RENDERING the world: its members plus observers (spectators, all-seeing staff). */
    FxAudience WATCHERS = (ctx, to) -> {
        for (Player p : ctx.world().watchers()) to.accept(p, ctx.position());
    };

    /** Every player on the underlying Minestom instance - all worlds sharing it, plus anyone unsharded. */
    FxAudience INSTANCE = (ctx, to) -> {
        for (Player p : ctx.world().instance().getPlayers()) to.accept(p, ctx.position());
    };

    /** The source entity's viewers, NOT the source: a doer's own client predicts its sound/animation locally. */
    FxAudience VIEWERS = (ctx, to) -> {
        Entity source = ctx.source();
        if (source == null) return;
        for (Player p : source.getViewers()) to.accept(p, ctx.position());
    };

    /** The source alone, anchored on itself - the private "ding" of a hit marker. */
    FxAudience SOURCE = (ctx, to) -> {
        if (ctx.source() instanceof Player p) to.accept(p, p.getPosition());
    };

    /** Nobody. The identity for {@link #both}, and a way to silence one branch of a composition. */
    FxAudience NONE = (ctx, to) -> {};

    // ---------------------------------------------------------------- combinators

    /** Union, each recipient once even when both sides reach them (the left side's anchor wins). */
    static @NotNull FxAudience both(@NotNull FxAudience a, @NotNull FxAudience b) {
        return (ctx, to) -> {
            Set<Player> seen = new HashSet<>();
            a.each(ctx, (p, at) -> { if (seen.add(p)) to.accept(p, at); });
            b.each(ctx, (p, at) -> { if (seen.add(p)) to.accept(p, at); });
        };
    }

    /** {@code a} minus everyone {@code b} reaches - "on the instance but not in this shard". */
    static @NotNull FxAudience except(@NotNull FxAudience a, @NotNull FxAudience b) {
        return (ctx, to) -> {
            Set<Player> excluded = new HashSet<>();
            b.each(ctx, (p, at) -> excluded.add(p));
            a.each(ctx, (p, at) -> { if (!excluded.contains(p)) to.accept(p, at); });
        };
    }

    /** Intersection - only those both sides reach. */
    static @NotNull FxAudience only(@NotNull FxAudience a, @NotNull FxAudience b) {
        return (ctx, to) -> {
            Set<Player> allowed = new HashSet<>();
            b.each(ctx, (p, at) -> allowed.add(p));
            a.each(ctx, (p, at) -> { if (allowed.contains(p)) to.accept(p, at); });
        };
    }

    /** {@code base} evaluated on every world of the layered {@link MechanicsWorld#family() family}, deduplicated. */
    static @NotNull FxAudience tree(@NotNull FxAudience base) {
        return (ctx, to) -> {
            Set<Player> seen = new HashSet<>();
            for (MechanicsWorld world : ctx.world().family()) {
                base.each(ctx.withWorld(world), (p, at) -> { if (seen.add(p)) to.accept(p, at); });
            }
        };
    }

    /** {@code base} narrowed to recipients within {@code blocks} of the fx. */
    static @NotNull FxAudience within(double blocks, @NotNull FxAudience base) {
        double squared = blocks * blocks;
        return (ctx, to) -> base.each(ctx, (player, at) -> {
            if (player.getPosition().distanceSquared(ctx.position()) <= squared) to.accept(player, at);
        });
    }

    /** {@code base} narrowed to LEGACY clients - fx a 1.8 client needs and a modern one predicts itself. */
    static @NotNull FxAudience legacy(@NotNull FxAudience base) {
        return (ctx, to) -> {
            var polyp = io.github.term4.polyp.Polyp.getInstance();
            var info = polyp.isInitialized() ? polyp.clientInfo() : null;
            if (info == null) return;
            base.each(ctx, (player, at) -> { if (info.isLegacy(player)) to.accept(player, at); });
        };
    }

    /** {@code base} re-anchored on each recipient, so nothing attenuates with distance. */
    static @NotNull FxAudience atListener(@NotNull FxAudience base) {
        return (ctx, to) -> base.each(ctx, (player, at) -> to.accept(player, player.getPosition()));
    }

    // ---------------------------------------------------------------- named compositions (sugar, not capability)

    /** {@link #WATCHERS} - the default, and what {@code world.playSound} has always meant. */
    FxAudience SHARD = WATCHERS;
    /** {@code atListener(MEMBERS)}: full volume wherever a member stands (BedWars' pearl landing). */
    FxAudience EVERYWHERE = atListener(MEMBERS);
    /** {@code both(VIEWERS, legacy(SOURCE))}: 1.8's local sound sinks are stubs, so its doer needs the packet. */
    FxAudience PREDICTED = both(VIEWERS, legacy(SOURCE));

    /**
     * Names the vocabulary for data paths. Primitives, combinators, and the three aliases - a scope that is not
     * listed is a composition, not a missing feature: {@code except(instance, shard)}, {@code tree(members)},
     * {@code at-listener(tree(watchers))}, {@code within(20, except(shard, source))}.
     */
    static void registerFactories() {
        FieldFns.register(FxAudience.class, "members", args -> MEMBERS);
        FieldFns.register(FxAudience.class, "watchers", args -> WATCHERS);
        FieldFns.register(FxAudience.class, "instance", args -> INSTANCE);
        FieldFns.register(FxAudience.class, "viewers", args -> VIEWERS);
        FieldFns.register(FxAudience.class, "source", args -> SOURCE);
        FieldFns.register(FxAudience.class, "nobody", args -> NONE);

        FieldFns.register(FxAudience.class, "both",
                args -> both(args.arity(2).of(0, FxAudience.class), args.of(1, FxAudience.class)));
        FieldFns.register(FxAudience.class, "except",
                args -> except(args.arity(2).of(0, FxAudience.class), args.of(1, FxAudience.class)));
        FieldFns.register(FxAudience.class, "only",
                args -> only(args.arity(2).of(0, FxAudience.class), args.of(1, FxAudience.class)));
        FieldFns.register(FxAudience.class, "tree", args -> tree(args.arity(1).of(0, FxAudience.class)));
        FieldFns.register(FxAudience.class, "legacy", args -> legacy(args.arity(1).of(0, FxAudience.class)));
        FieldFns.register(FxAudience.class, "at-listener", args -> atListener(args.arity(1).of(0, FxAudience.class)));
        FieldFns.register(FxAudience.class, "within",
                args -> within(args.dbl(0), args.size() > 1 ? args.of(1, FxAudience.class) : WATCHERS));

        FieldFns.register(FxAudience.class, "shard", args -> SHARD);
        FieldFns.register(FxAudience.class, "everywhere", args -> EVERYWHERE);
        FieldFns.register(FxAudience.class, "predicted", args -> PREDICTED);
    }
}
