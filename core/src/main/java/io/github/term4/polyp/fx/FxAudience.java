package io.github.term4.polyp.fx;

import io.github.term4.polyp.config.FieldFns;
import io.github.term4.polyp.world.MechanicsWorld;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * <em>Who</em> perceives an fx, and from where - the axis {@link FxEffect} ("what") deliberately does not carry.
 * Keeping them apart is what stops the vocabulary from being a grid: a new audience works with every effect and a
 * new effect with every audience, instead of one factory per pairing.
 *
 * <p>These are not a menu of cases. Five primitive sets ({@link #MEMBERS}, {@link #WATCHERS}, {@link #INSTANCE},
 * {@link #VIEWERS}, {@link #SOURCE}) are closed under {@link #both union}, {@link #except difference},
 * {@link #only intersection}, {@link #tree}, {@link #within}, {@link #protocolBelow}, and {@link #atListener}
 * for the anchor. Anything nameable is a composition: "on the instance but not in this shard" is
 * {@code except(instance, watchers)}. Only {@link #PREDICTED} keeps a name, and it is spelled out as the
 * composition it equals.
 *
 * <p>Scope: a world is ONE shard. {@link #MEMBERS} / {@link #WATCHERS} stop there, {@link #tree} spans a layered
 * family, {@link #INSTANCE} spans every world sharing the Minestom instance plus anyone unsharded.
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

    /** Everyone RENDERING the world: its members plus observers (spectators, all-seeing staff). The default. */
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

    /** Nobody: the identity for {@link #both}, and a branch that contributes nothing. */
    FxAudience NOBODY = (ctx, to) -> {};

    // ---------------------------------------------------------------- combinators

    /** Union, each recipient once even when both sides reach them (the left side's anchor wins). */
    static @NotNull FxAudience both(@NotNull FxAudience a, @NotNull FxAudience b) {
        return (ctx, to) -> {
            Set<Player> seen = identitySet();
            a.each(ctx, (p, at) -> { if (seen.add(p)) to.accept(p, at); });
            b.each(ctx, (p, at) -> { if (seen.add(p)) to.accept(p, at); });
        };
    }

    /** {@code a} minus everyone {@code b} reaches - "on the instance but not in this shard". */
    static @NotNull FxAudience except(@NotNull FxAudience a, @NotNull FxAudience b) {
        return (ctx, to) -> {
            Set<Player> excluded = identitySet();
            b.each(ctx, (p, at) -> excluded.add(p));
            a.each(ctx, (p, at) -> { if (!excluded.contains(p)) to.accept(p, at); });
        };
    }

    /** Intersection - only those both sides reach. */
    static @NotNull FxAudience only(@NotNull FxAudience a, @NotNull FxAudience b) {
        return (ctx, to) -> {
            Set<Player> allowed = identitySet();
            b.each(ctx, (p, at) -> allowed.add(p));
            a.each(ctx, (p, at) -> { if (allowed.contains(p)) to.accept(p, at); });
        };
    }

    /** {@code base} evaluated on every world of the layered {@link MechanicsWorld#family() family}, deduplicated. */
    static @NotNull FxAudience tree(@NotNull FxAudience base) {
        return (ctx, to) -> {
            Set<Player> seen = identitySet();
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

    /**
     * {@code base} narrowed to clients older than {@code protocol} - fx a legacy client needs because it does
     * not predict them locally. 1.8 is 47, so {@code protocolBelow(48, ...)} is "1.8 and older".
     */
    static @NotNull FxAudience protocolBelow(int protocol, @NotNull FxAudience base) {
        return (ctx, to) -> {
            var polyp = io.github.term4.polyp.Polyp.getInstance();
            var info = polyp.isInitialized() ? polyp.clientInfo() : null;
            if (info == null) return;
            base.each(ctx, (player, at) -> { if (info.getProtocol(player) < protocol) to.accept(player, at); });
        };
    }

    /** {@code base} re-anchored on each recipient, so nothing attenuates with distance. */
    static @NotNull FxAudience atListener(@NotNull FxAudience base) {
        return (ctx, to) -> base.each(ctx, (player, at) -> to.accept(player, player.getPosition()));
    }

    /** The one surviving alias: {@code both(VIEWERS, protocolBelow(48, SOURCE))} - 1.8's local sound sinks are stubs. */
    FxAudience PREDICTED = both(VIEWERS, protocolBelow(48, SOURCE));

    private static Set<Player> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }

    /**
     * Names the vocabulary for data paths. A scope that is not listed is a composition, not a missing feature:
     * {@code except(instance, watchers)}, {@code tree(members)}, {@code at-listener(tree(watchers))}.
     */
    static void registerFactories() {
        FieldFns.register(FxAudience.class, "members", "the world's players; spectators excluded", args -> MEMBERS);
        FieldFns.register(FxAudience.class, "watchers", "everyone rendering the world, spectators included", args -> WATCHERS);
        FieldFns.register(FxAudience.class, "instance", "every player on the Minestom instance, all worlds", args -> INSTANCE);
        FieldFns.register(FxAudience.class, "viewers", "the source's viewers, not the source itself", args -> VIEWERS);
        FieldFns.register(FxAudience.class, "source", "the source player alone", args -> SOURCE);
        FieldFns.register(FxAudience.class, "nobody", "no one - the identity for both()", args -> NOBODY);
        FieldFns.register(FxAudience.class, "predicted", "viewers + a pre-1.9 source, which cannot predict it", args -> PREDICTED);

        FieldFns.register(FxAudience.class, "both(a, b)", "everyone either side reaches",
                args -> both(args.arity(2).of(0, FxAudience.class), args.of(1, FxAudience.class)));
        FieldFns.register(FxAudience.class, "except(a, b)", "everyone in a that b does not reach",
                args -> except(args.arity(2).of(0, FxAudience.class), args.of(1, FxAudience.class)));
        FieldFns.register(FxAudience.class, "only(a, b)", "everyone both sides reach",
                args -> only(args.arity(2).of(0, FxAudience.class), args.of(1, FxAudience.class)));
        FieldFns.register(FxAudience.class, "tree(audience)", "the audience across the whole layered world family",
                args -> tree(args.arity(1).of(0, FxAudience.class)));
        FieldFns.register(FxAudience.class, "within(blocks, audience)", "the audience, limited to that radius",
                args -> within(args.arity(2).dbl(0), args.of(1, FxAudience.class)));
        FieldFns.register(FxAudience.class, "protocol-below(version, audience)", "the audience, older clients only (1.8 = 47)",
                args -> protocolBelow(args.arity(2).integer(0), args.of(1, FxAudience.class)));
        FieldFns.register(FxAudience.class, "at-listener(audience)", "same recipients, anchored on each so distance never fades it",
                args -> atListener(args.arity(1).of(0, FxAudience.class)));
    }
}
