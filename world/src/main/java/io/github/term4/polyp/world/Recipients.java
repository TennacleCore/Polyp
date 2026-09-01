package io.github.term4.polyp.world;

import net.minestom.server.MinecraftServer;
import net.minestom.server.coordinate.Point;
import net.minestom.server.entity.Entity;
import net.minestom.server.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * <em>Who</em> perceives something that happens at a point in a world, and from where. A world-level concept
 * rather than an fx one: picking recipients is what every outward send does - effects, hurt broadcasts,
 * velocity, block feedback - so the scopes live beside {@link MechanicsWorld#players()} /
 * {@link MechanicsWorld#watchers()} / {@link MechanicsWorld#family()} that define them.
 *
 * <p>Not a menu of cases. Six primitives closed under {@link #both union}, {@link #except difference},
 * {@link #only intersection}, {@link #tree}, {@link #within}, and {@link #atListener} for the anchor -
 * "on the instance but not in this world" is {@code except(INSTANCE, WATCHERS)}, not another constant.
 *
 * <p>Scope, widest last: {@link #MEMBERS} / {@link #WATCHERS} are ONE world, {@link #tree} spans its layered
 * family, {@link #INSTANCE} every world sharing the Minestom instance, {@link #SERVER} the whole process.
 * Each recipient carries its own anchor, which is how {@link #atListener} makes a sound distance-proof.
 */
@FunctionalInterface
public interface Recipients {

    /**
     * Calls {@code to} once per recipient with the point that recipient should perceive it at.
     *
     * @param source the entity responsible, when there is one (viewer- and self-scoped sets need it)
     */
    void each(@NotNull MechanicsWorld world, @NotNull Point at, @Nullable Entity source,
              @NotNull BiConsumer<Player, Point> to);

    /** The world's own members - the players actually in it. Spectators watching do NOT perceive it. */
    Recipients MEMBERS = (world, at, source, to) -> {
        for (Player p : world.players()) to.accept(p, at);
    };

    /** Everyone RENDERING the world: its members plus observers (spectators, all-seeing staff). The default. */
    Recipients WATCHERS = (world, at, source, to) -> {
        for (Player p : world.watchers()) to.accept(p, at);
    };

    /** Every player on the underlying Minestom instance - all worlds sharing it, plus anyone unsharded. */
    Recipients INSTANCE = (world, at, source, to) -> {
        for (Player p : MechanicsWorld.of(world.instance()).players()) to.accept(p, at);
    };

    /**
     * Every player online, across ALL instances - the one scope no combinator reaches, since {@link #INSTANCE}
     * never crosses an instance. A position in one instance means nothing in another, so pair it with
     * {@link #atListener} unless what you are sending is genuinely positional server-wide.
     */
    Recipients SERVER = (world, at, source, to) -> {
        for (Player p : MinecraftServer.getConnectionManager().getOnlinePlayers()) to.accept(p, at);
    };

    /** The source's viewers, NOT the source: a doer's own client predicts its own feedback locally. */
    Recipients VIEWERS = (world, at, source, to) -> {
        if (source == null) return;
        for (Player p : source.getViewers()) to.accept(p, at);
    };

    /** The source alone, anchored on itself - a private cue like a hit marker. */
    Recipients SOURCE = (world, at, source, to) -> {
        if (source instanceof Player p) to.accept(p, p.getPosition());
    };

    /** No one: the identity for {@link #both}, and a branch that contributes nothing. */
    Recipients NOBODY = (world, at, source, to) -> {};

    /** Union, each recipient once even when both sides reach them (the left side's anchor wins). */
    static @NotNull Recipients both(@NotNull Recipients a, @NotNull Recipients b) {
        return (world, at, source, to) -> {
            Set<Player> seen = identitySet();
            a.each(world, at, source, (p, point) -> { if (seen.add(p)) to.accept(p, point); });
            b.each(world, at, source, (p, point) -> { if (seen.add(p)) to.accept(p, point); });
        };
    }

    /** {@code a} minus everyone {@code b} reaches. */
    static @NotNull Recipients except(@NotNull Recipients a, @NotNull Recipients b) {
        return (world, at, source, to) -> {
            Set<Player> excluded = identitySet();
            b.each(world, at, source, (p, point) -> excluded.add(p));
            a.each(world, at, source, (p, point) -> { if (!excluded.contains(p)) to.accept(p, point); });
        };
    }

    /** Intersection - only those both sides reach. */
    static @NotNull Recipients only(@NotNull Recipients a, @NotNull Recipients b) {
        return (world, at, source, to) -> {
            Set<Player> allowed = identitySet();
            b.each(world, at, source, (p, point) -> allowed.add(p));
            a.each(world, at, source, (p, point) -> { if (allowed.contains(p)) to.accept(p, point); });
        };
    }

    /** {@code base} evaluated on every world of the layered {@link MechanicsWorld#family() family}, deduplicated. */
    static @NotNull Recipients tree(@NotNull Recipients base) {
        return (world, at, source, to) -> {
            Set<Player> seen = identitySet();
            for (MechanicsWorld layer : world.family()) {
                base.each(layer, at, source, (p, point) -> { if (seen.add(p)) to.accept(p, point); });
            }
        };
    }

    /** {@code base} narrowed to recipients within {@code blocks} of the point. */
    static @NotNull Recipients within(double blocks, @NotNull Recipients base) {
        double squared = blocks * blocks;
        return (world, at, source, to) -> base.each(world, at, source, (p, point) -> {
            if (p.getPosition().distanceSquared(at) <= squared) to.accept(p, point);
        });
    }

    /** {@code base} re-anchored on each recipient, so nothing attenuates with distance. */
    static @NotNull Recipients atListener(@NotNull Recipients base) {
        return (world, at, source, to) -> base.each(world, at, source, (p, point) -> to.accept(p, p.getPosition()));
    }

    /** {@code base} narrowed by an arbitrary test - the seam a caller uses without growing this vocabulary. */
    static @NotNull Recipients filter(@NotNull java.util.function.Predicate<Player> keep, @NotNull Recipients base) {
        return (world, at, source, to) -> base.each(world, at, source, (p, point) -> {
            if (keep.test(p)) to.accept(p, point);
        });
    }

    private static Set<Player> identitySet() {
        return Collections.newSetFromMap(new IdentityHashMap<>());
    }
}
