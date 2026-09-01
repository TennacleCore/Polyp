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
 * <p>Each recipient gets its own anchor point, so the anchor is a third axis rather than another set of names:
 * {@link #atListener} re-anchors any audience on the listener, which is what makes a sound distance-proof.
 *
 * <p>Scope note: a {@code MechanicsWorld} is ONE shard, not a shard tree. {@link #SHARD} and {@link #MEMBERS}
 * reach that shard alone; {@link #INSTANCE} reaches every player on the underlying Minestom instance, which is
 * every shard sharing it plus anyone unsharded. Parent/child shards are an Archipelago concept the world seam
 * does not expose, so a tree audience is a server-side registration (see {@link #registerFactories()}).
 */
@FunctionalInterface
public interface FxAudience {

    /** Calls {@code to} once per recipient with the point that recipient should perceive the fx at. */
    void each(@NotNull FxContext ctx, @NotNull BiConsumer<Player, Point> to);

    /**
     * Everyone RENDERING this shard - its members plus observers (spectators, all-seeing staff) - at the fx
     * position, so distance attenuates. The default, and what {@code world.playSound} has always meant.
     */
    FxAudience SHARD = (ctx, to) -> {
        for (Player p : ctx.world().watchers()) to.accept(p, ctx.position());
    };

    /** The shard's own members only: spectators watching do NOT perceive it. */
    FxAudience MEMBERS = (ctx, to) -> {
        for (Player p : ctx.world().players()) to.accept(p, ctx.position());
    };

    /** Every player on the underlying instance - ALL shards sharing it, plus anyone unsharded. */
    FxAudience INSTANCE = (ctx, to) -> {
        for (Player p : ctx.world().instance().getPlayers()) to.accept(p, ctx.position());
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

    /** {@link #MEMBERS} anchored on each listener: full volume wherever they stand (BedWars' pearl landing). */
    FxAudience EVERYWHERE = atListener(MEMBERS);

    /** {@code base} re-anchored on each recipient, so nothing attenuates with distance. */
    static @NotNull FxAudience atListener(@NotNull FxAudience base) {
        return (ctx, to) -> base.each(ctx, (player, at) -> to.accept(player, player.getPosition()));
    }

    /** {@code base} narrowed to recipients within {@code blocks} of the fx ({@link #SHARD} when unqualified). */
    static @NotNull FxAudience within(double blocks, @NotNull FxAudience base) {
        double squared = blocks * blocks;
        return (ctx, to) -> base.each(ctx, (player, at) -> {
            if (player.getPosition().distanceSquared(ctx.position()) <= squared) to.accept(player, at);
        });
    }

    /**
     * Names the built-ins for data paths ({@code to(everywhere, sound(...))}). Combinators take an audience
     * argument, so scopes compose: {@code at-listener(instance)}, {@code within(20, members)}. A server that
     * wants a shard-tree audience registers one here - the vocabulary is open, not this list.
     */
    static void registerFactories() {
        io.github.term4.polyp.config.FieldFns.register(FxAudience.class, "shard", args -> SHARD);
        io.github.term4.polyp.config.FieldFns.register(FxAudience.class, "members", args -> MEMBERS);
        io.github.term4.polyp.config.FieldFns.register(FxAudience.class, "instance", args -> INSTANCE);
        io.github.term4.polyp.config.FieldFns.register(FxAudience.class, "viewers", args -> VIEWERS);
        io.github.term4.polyp.config.FieldFns.register(FxAudience.class, "predicted", args -> PREDICTED);
        io.github.term4.polyp.config.FieldFns.register(FxAudience.class, "source", args -> SOURCE);
        io.github.term4.polyp.config.FieldFns.register(FxAudience.class, "everywhere", args -> EVERYWHERE);
        io.github.term4.polyp.config.FieldFns.register(FxAudience.class, "at-listener",
                args -> atListener(args.arity(1).of(0, FxAudience.class)));
        io.github.term4.polyp.config.FieldFns.register(FxAudience.class, "within",
                args -> within(args.dbl(0), args.size() > 1 ? args.of(1, FxAudience.class) : SHARD));
    }
}
