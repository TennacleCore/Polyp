package io.github.term4.polyp.fx;

import io.github.term4.polyp.Polyp;
import io.github.term4.polyp.config.FieldFns;
import io.github.term4.polyp.world.Recipients;
import net.minestom.server.entity.Player;

/**
 * The core's additions to {@link Recipients}: the scope-shaped sets live in polyp-world beside the world
 * methods that define them, and what needs the CLIENT (prediction) belongs here, where the client tracker is.
 * Also where the data vocabulary is registered - the world module has no config layer.
 */
public final class FxAudiences {

    private FxAudiences() {}

    /**
     * The source alone, and only when its client will NOT produce the effect itself: modern clients predict
     * their own world sounds locally, while 1.8's local sound sinks are stubs, so its doer hears nothing
     * unless we echo it. The one set defined by client CAPABILITY rather than by scope.
     */
    private static final Recipients LEGACY_SOURCE = (world, at, source, to) -> {
        Polyp polyp = Polyp.getInstance();
        var info = polyp.isInitialized() ? polyp.clientInfo() : null;
        if (info != null && source instanceof Player p && info.isLegacy(p)) to.accept(p, at);
    };

    /**
     * {@code base} minus the source, plus the source when it cannot predict the effect - the shape every
     * client-predicted effect takes, whichever set of onlookers it starts from.
     */
    public static Recipients predictedFor(Recipients base) {
        return (world, at, source, to) -> {
            base.each(world, at, source, (p, point) -> { if (p != source) to.accept(p, point); });
            LEGACY_SOURCE.each(world, at, source, to);
        };
    }

    /** Entity-anchored prediction: the source's viewers, plus a legacy source. */
    public static final Recipients PREDICTED = predictedFor(Recipients.VIEWERS);

    /** Names the vocabulary for data paths; a scope not listed is a composition, not a missing feature. */
    public static void registerFactories() {
        FieldFns.register(Recipients.class, "members", "the world's players; spectators excluded", args -> Recipients.MEMBERS);
        FieldFns.register(Recipients.class, "watchers", "everyone rendering the world, spectators included", args -> Recipients.WATCHERS);
        FieldFns.register(Recipients.class, "instance", "every player on the Minestom instance, all worlds", args -> Recipients.INSTANCE);
        FieldFns.register(Recipients.class, "server", "every player online, across all instances", args -> Recipients.SERVER);
        FieldFns.register(Recipients.class, "viewers", "the source's viewers, not the source itself", args -> Recipients.VIEWERS);
        FieldFns.register(Recipients.class, "source", "the source player alone", args -> Recipients.SOURCE);
        FieldFns.register(Recipients.class, "block-viewers", "everyone rendering the world's blocks", args -> Recipients.BLOCK_VIEWERS);
        FieldFns.register(Recipients.class, "predicted-for(recipients)", "those, minus the source unless its client cannot predict the effect",
                args -> predictedFor(args.arity(1).of(0, Recipients.class)));

        FieldFns.register(Recipients.class, "both(recipients...)", "everyone any of them reaches; both() is nobody",
                args -> {
                    if (args.size() == 0) return Recipients.NOBODY;
                    Recipients out = args.of(0, Recipients.class);
                    for (int i = 1; i < args.size(); i++) out = Recipients.both(out, args.of(i, Recipients.class));
                    return out;
                });
        FieldFns.register(Recipients.class, "except(a, b)", "everyone in a that b does not reach",
                args -> Recipients.except(args.arity(2).of(0, Recipients.class), args.of(1, Recipients.class)));
        FieldFns.register(Recipients.class, "only(a, b)", "everyone both sides reach",
                args -> Recipients.only(args.arity(2).of(0, Recipients.class), args.of(1, Recipients.class)));
        FieldFns.register(Recipients.class, "tree(recipients)", "evaluated across the whole layered world family",
                args -> Recipients.tree(args.arity(1).of(0, Recipients.class)));
        FieldFns.register(Recipients.class, "within(blocks, recipients)", "limited to that radius",
                args -> Recipients.within(args.arity(2).dbl(0), args.of(1, Recipients.class)));
        FieldFns.register(Recipients.class, "at-listener(recipients)", "anchored on each so distance never fades it",
                args -> Recipients.atListener(args.arity(1).of(0, Recipients.class)));
    }
}
